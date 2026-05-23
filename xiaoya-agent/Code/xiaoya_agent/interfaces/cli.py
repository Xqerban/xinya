#!/usr/bin/env python
"""
主程序 - 集成CBT、心理能量和危机干预
"""
import sys
import os
import json
import threading
import re
from colorama import init, Fore, Style
from xiaoya_agent.core.agent import EnhancedChatAgent
from xiaoya_agent.config import Config
from xiaoya_agent.database import database_storage_enabled
from xiaoya_agent.features.crisis import build_crisis_alarm
from xiaoya_agent.runtime.session import (
    build_agent_psych_model_payload,
    delete_user,
    get_user_model_dir,
    list_user_conversations,
    list_user_summaries,
    sanitize_user_id,
    sync_user_conversation_history,
)
from xiaoya_agent.features.cohort_learning import (
    get_cohort_learning_model,
    rebuild_cohort_learning_model,
)
from xiaoya_agent.features.harbor import (
    build_harbor_conversation_data,
    build_harbor_energy_payload,
    create_harbor_practice,
    list_harbor_catalog,
)
from xiaoya_agent.utils.formatting import markdown_to_plain_text

# 初始化 Colorama 终端颜色输出
init(autoreset=True)

DEFAULT_CLI_USER_ID = os.getenv("CLI_DEFAULT_USER_ID", "cli-default")


def get_cli_data_dir(user_id: str) -> str:
    """返回某个用户专属的 CLI 对话目录。"""
    data_dir = os.path.abspath(os.path.join(get_user_model_dir(user_id), "cli_session"))
    if not database_storage_enabled():
        os.makedirs(data_dir, exist_ok=True)
    return data_dir


def create_cli_agent(user_id: str = DEFAULT_CLI_USER_ID) -> EnhancedChatAgent:
    """创建绑定到用户独立心理模型的 CLI 智能体。"""
    resolved_user_id = str(user_id or DEFAULT_CLI_USER_ID).strip() or DEFAULT_CLI_USER_ID
    data_dir = get_cli_data_dir(resolved_user_id)
    psych_model_dir = get_user_model_dir(resolved_user_id)
    agent = EnhancedChatAgent(
        data_dir=data_dir,
        user_id=resolved_user_id,
        psych_model_dir=psych_model_dir,
    )
    agent.graph_thread_id = f"cli_{sanitize_user_id(resolved_user_id)}"
    agent.storage_source = "cli"
    agent.storage_conversation_id = "cli"

    history_path = os.path.join(data_dir, "chat_history.json")
    if database_storage_enabled() or os.path.exists(history_path):
        agent.load_history()
    return agent


def list_cli_users() -> list:
    return sorted(list_user_summaries(), key=lambda item: item["safeUserId"])


def print_current_user(agent: EnhancedChatAgent):
    user_id = getattr(agent, "user_id", None) or DEFAULT_CLI_USER_ID
    print(Fore.YELLOW + f"当前用户: {user_id}")
    if database_storage_enabled():
        conversation_id = getattr(agent, "storage_conversation_id", None) or "cli"
        print(Fore.YELLOW + "存储后端: MySQL 数据库")
        print(Fore.YELLOW + f"CLI 会话标识: {conversation_id}")
    else:
        print(Fore.YELLOW + f"用户目录: {getattr(agent, 'psych_model_dir', '')}")
        print(Fore.YELLOW + f"CLI 会话目录: {getattr(agent, 'data_dir', '')}")


def build_cli_psych_model_payload(agent: EnhancedChatAgent) -> dict:
    return build_agent_psych_model_payload(
        agent,
        thread_id=getattr(agent, "graph_thread_id", None),
    )


def print_psych_model(agent: EnhancedChatAgent):
    print(Fore.CYAN + "\n当前用户心理模型:")
    print(json.dumps(build_cli_psych_model_payload(agent), ensure_ascii=False, indent=2))
    print()


def print_harbor_catalog():
    """展示心之港湾可用场景、工具和时长。"""
    catalog = list_harbor_catalog()
    print(Fore.CYAN + "\n心之港湾工具目录:")
    print(Fore.YELLOW + "  可用场景:")
    for item in catalog.get("scenarios", []):
        print(f"    - {item.get('name')} ({item.get('key')})，默认 {item.get('defaultDurationSeconds')} 秒")

    print(Fore.YELLOW + "\n  可用工具:")
    for item in catalog.get("tools", []):
        print(f"    - {item.get('name')} ({item.get('key')})：{item.get('description')}")

    durations = ", ".join(str(value) for value in catalog.get("durationOptionsSeconds", []))
    print(Fore.YELLOW + f"\n  可选时长: {durations} 秒")
    print("  示例: harbor 焦虑 60 呼吸")
    print("  示例: harbor 失眠 180 冥想\n")


def parse_harbor_command(user_input: str) -> dict:
    """解析 CLI 的心之港湾命令参数。"""
    parts = user_input.strip().split()
    tokens = parts[1:] if parts and parts[0].lower() == "harbor" else parts
    params = {
        "scenario": "",
        "tool_type": "",
        "duration_seconds": 0,
        "query": " ".join(tokens),
        "mode": "voice",
    }

    text_tokens = []
    for token in tokens:
        if token.isdigit():
            params["duration_seconds"] = int(token)
        else:
            text_tokens.append(token)

    if text_tokens:
        params["scenario"] = text_tokens[0]
    if len(text_tokens) > 1:
        params["tool_type"] = text_tokens[1]
    return params


def display_harbor_practice(practice: dict):
    """以适合命令行阅读的方式展示心之港湾练习。"""
    print(Fore.CYAN + f"\n心之港湾: {practice.get('title')}")
    print(
        Fore.YELLOW
        + f"  场景: {practice.get('scenario', {}).get('name')} | "
        + f"工具: {practice.get('toolType', {}).get('name')} | "
        + f"时长: {practice.get('durationLabel')}"
    )
    print(Fore.BLUE + "\n  语音引导词:")
    print(f"  {practice.get('voiceGuideText', '')}")

    segments = practice.get("segments") or []
    if segments:
        print(Fore.YELLOW + "\n  分段引导:")
        for segment in segments:
            order = segment.get("order") or segment.get("step") or "-"
            duration = segment.get("durationSeconds") or segment.get("duration") or 0
            text = segment.get("text") or segment.get("instruction") or ""
            title = segment.get("title")
            if title:
                print(f"    {order}. {title} ({duration}秒): {text}")
            else:
                print(f"    {order}. ({duration}秒) {text}")

    music = practice.get("musicSuggestion")
    if isinstance(music, dict):
        if music.get("enabled", True):
            print(Fore.MAGENTA + f"\n  音乐建议: {music.get('description') or music.get('text') or ''}")
    elif music:
        print(Fore.MAGENTA + f"\n  音乐建议: {music}")
    print(Fore.RED + f"\n  安全提示: {practice.get('safetyNote')}\n" + Style.RESET_ALL)


def record_harbor_practice(agent: EnhancedChatAgent, practice: dict, message: str = ""):
    """把用户主动完成的调节练习记录进心理能量系统。"""
    energy_result = agent.energy_model.apply_llm_assessment(
        build_harbor_conversation_data(practice, message=message),
        build_harbor_energy_payload(practice),
    )
    if Config.ENERGY_FEEDBACK_ENABLED and energy_result:
        energy_report = agent.energy_model.get_energy_report()
        display_energy_feedback(energy_result, energy_report)
    if Config.AUTO_SAVE_PROGRESS:
        agent.energy_model.save_progress()


def switch_cli_user(agent: EnhancedChatAgent, user_id: str):
    new_user_id = str(user_id or "").strip()
    if not new_user_id:
        raise ValueError("用户 ID 不能为空")

    if Config.AUTO_SAVE_PROGRESS:
        save_cli_agent(agent)

    switched = create_cli_agent(new_user_id)
    return switched


def sync_cli_conversation(agent: EnhancedChatAgent) -> None:
    user_id = str(getattr(agent, "user_id", None) or DEFAULT_CLI_USER_ID)
    sync_user_conversation_history(
        user_id=user_id,
        conversation_id="cli",
        source="cli",
        history=agent.get_history(),
        metadata={
            "sessionId": "cli",
            "title": f"CLI 会话 - {user_id}",
            "dataDir": getattr(agent, "data_dir", None),
        },
    )


def save_cli_agent(agent: EnhancedChatAgent) -> None:
    agent.save_all_progress()
    sync_cli_conversation(agent)


def save_progress_async(agent):
    """复用 API 的对话持久化逻辑，同时不阻塞流式回复。"""
    if Config.AUTO_SAVE_PROGRESS:
        threading.Thread(target=save_cli_agent, args=(agent,), daemon=True).start()


def print_welcome():
    """打印欢迎信息"""
    print(Fore.CYAN + "=" * 60)
    print(Fore.YELLOW + " 欢迎使用小芽智能体")
    print(Fore.CYAN + "=" * 60)
    print(Fore.YELLOW + " 集成的功能:")
    print(Fore.YELLOW + "  - CBT (认知行为疗法) 对话策略")
    print(Fore.YELLOW + "  - 心理能量评估系统")
    print(Fore.YELLOW + "  - 实时危机干预检测")
    print(Fore.CYAN + "-" * 60)
    print_help()
    print(Fore.CYAN + "-" * 60)

def print_help():
    """打印帮助信息"""
    print(Fore.YELLOW + "\n 可用命令:")
    print("  quit/exit     - 退出程序")
    print("  save          - 保存所有进度")
    print("  load          - 加载对话历史")
    print("  user          - 查看当前用户")
    print("  user <id>     - 切换到指定用户，自动加载该用户心理模型")
    print("  users         - 列出已有用户心理模型")
    print("  user-history [id] - 查看用户统一会话历史索引")
    print("  delete-user <id>  - 删除用户及其关联会话")
    print("  phase         - 查看/设置骨髓移植分期（pre/key/post）")
    print("  energy        - 查看心理能量报告")
    print("  achievements  - 查看成就系统")
    print("  progress      - 查看综合进步报告")
    print("  grounding     - 获取正念接地练习")
    print("  harbor        - 查看心之港湾工具目录")
    print("  harbor <场景> [秒数] [工具] - 启动床旁放松练习，如 harbor 焦虑 60 呼吸")
    print("  reset         - 重置所有数据（清除所有历史记录）")
    print("  help          - 显示此帮助信息")
    print("  psych-model   - 查看当前用户心理模型")
    print("  model         - psych-model 的简写")
    print("  cohort        - 查看匿名群体学习模型")
    print("  cohort rebuild - 重建匿名群体学习模型")
    print()

def display_energy_feedback(energy_assessment: dict, energy_report: dict):
    """显示能量反馈"""
    if not energy_assessment:
        return

    print(Fore.BLUE + "\n 心理能量反馈:")

    # 显示本次增益
    gains = energy_assessment.get("dimension_gains", {})
    positive_gains = {
        dimension: gain
        for dimension, gain in gains.items()
        if isinstance(gain, (int, float)) and gain > 0
    }
    if positive_gains:
        print("  本次成长:")
        for dimension, gain in positive_gains.items():
            print(f"    {dimension}: +{gain} 点")

    # 显示新成就
    new_achievements = energy_assessment.get("new_achievements", [])
    if new_achievements:
        print(Fore.GREEN + "\n  *** 新成就解锁!")
        for achievement in new_achievements:
            name = achievement.get('name', '未知成就')
            description = achievement.get('description', '')
            reward = achievement.get('reward', 0)
            print(Fore.GREEN + f"    {name}")
            print(f"    {description} (+{reward}点能量)")

    # 显示当前等级
    if energy_report:
        level = energy_report.get("current_level", {})
        progress = energy_report.get("level_progress", 0)
        total_energy = energy_report.get("total_energy", 0)
        consecutive_days = energy_report.get("consecutive_days", 0)

        print(f"\n  当前等级: {level.get('name', '未知')}")
        print(f"  总能量: {total_energy} 点")
        print(f"  等级进度: {progress:.1f}%")
        if consecutive_days > 0:
            print(f"  连续对话: {consecutive_days} 天")

    print(Style.RESET_ALL)

def display_crisis_alert(crisis_detection: dict):
    """显示危机警报"""
    alarm = build_crisis_alarm(crisis_detection)
    level = alarm["level"]
    if level == "none":
        return
    color = Fore.RED if level in {"high", "critical"} else Fore.YELLOW
    print(color + f"\n {alarm['label']}: {alarm['title']}")
    print(color + f" {alarm['message']}")
    print(color + f" 处置动作: {alarm['action']}\n" + Style.RESET_ALL)


def _knowledge_tools_from_trace(tool_trace: dict) -> list:
    """从工具轨迹中取出知识库检索记录。"""
    if not isinstance(tool_trace, dict):
        return []
    tools = tool_trace.get("tools")
    if not isinstance(tools, list):
        return []
    return [
        tool for tool in tools
        if isinstance(tool, dict) and tool.get("name") == "knowledge_retrieval"
    ]


def display_knowledge_retrieval_trace(result: dict, agent: EnhancedChatAgent = None):
    """显示本轮 Dify 知识库召回情况，方便人工判断 RAG 是否生效。"""
    tool_trace = (result or {}).get("tool_trace") or (result or {}).get("toolTrace")
    if not tool_trace and agent is not None:
        tool_trace = getattr(agent, "last_tool_trace", None)

    knowledge_tools = _knowledge_tools_from_trace(tool_trace)
    if not knowledge_tools:
        return

    visible_tools = []
    for tool in knowledge_tools:
        reason = str(tool.get("reason") or "").strip()
        match_count = int(tool.get("matchCount") or 0)
        errors = tool.get("errors") or []
        attempted = (
            match_count > 0
            or bool(tool.get("hasContext"))
            or bool(errors)
            or reason not in {"", "auto_skipped_for_speed", "auto_skipped"}
        )
        if attempted:
            visible_tools.append(tool)

    if not visible_tools:
        return

    print(Fore.CYAN + "\n 知识库召回:")
    for tool in visible_tools:
        backend = tool.get("retrievalBackend") or "dify"
        reason = tool.get("reason") or "unknown"
        match_count = int(tool.get("matchCount") or 0)
        has_context = bool(tool.get("hasContext"))
        method = tool.get("effectiveSearchMethod") or tool.get("scoringMode") or "-"

        if has_context:
            status = "已触发并注入上下文"
        elif reason == "no_relevant_chunks":
            status = "已触发但未召回相关片段"
        elif reason == "dify_retrieval_failed":
            status = "已触发但检索失败"
        else:
            status = "已触发"

        print(Fore.YELLOW + f"  状态: {status}")
        print(f"  后端: {backend}")
        print(f"  检索方式: {method}")
        print(f"  召回片段数: {match_count}")

        sources = tool.get("topSources") or []
        if sources:
            print("  来源:")
            for source in sources[:3]:
                if not isinstance(source, dict):
                    continue
                source_name = source.get("source") or "未知来源"
                score = source.get("score")
                if score is None:
                    print(f"    - {source_name}")
                else:
                    print(f"    - {source_name} (score={score})")

        errors = tool.get("errors") or []
        if errors:
            print(Fore.RED + "  错误:")
            for error in errors[:2]:
                print(Fore.RED + f"    - {error}")
        elif not has_context and reason:
            print(f"  原因: {reason}")

    print(Style.RESET_ALL)


def _normalize_cli_stream_chunk(text: str, previous_was_space: bool = False) -> str:
    """合并模型输出中的段落换行，让 CLI 回复保持单行可读。"""
    text = str(text or "").replace("\r\n", "\n").replace("\r", "\n")
    text = re.sub(r"\s*\n+\s*", " ", text)
    text = re.sub(r"[ \t\f\v]{2,}", " ", text)
    if previous_was_space:
        text = text.lstrip()
    return text


def run_chat_turn(agent, user_input):
    """按 API 相同的核心流式链路和后处理执行一轮 CLI 对话。"""
    print(Fore.BLUE + "智能体: " + Style.RESET_ALL, end="")

    previous_was_space = False
    for chunk in agent.stream_chat(user_input):
        plain_chunk = _normalize_cli_stream_chunk(
            markdown_to_plain_text(chunk, strip=False),
            previous_was_space=previous_was_space,
        )
        if plain_chunk:
            sys.stdout.write(plain_chunk)
            sys.stdout.flush()
            previous_was_space = plain_chunk[-1].isspace()

    print()
    if hasattr(agent, "wait_for_background_analysis"):
        agent.wait_for_background_analysis(0)

    result = agent.last_result or {
        "response": "",
        "response_type": "cbt_response",
        "crisis_detection": {},
        "energy_assessment": None,
        "energy_report": None,
    }

    crisis_detection = result.get("crisis_detection", {})
    display_crisis_alert(crisis_detection)

    display_knowledge_retrieval_trace(result, agent=agent)

    if Config.ENERGY_FEEDBACK_ENABLED and not crisis_detection.get("alert", False):
        display_energy_feedback(result.get("energy_assessment"), result.get("energy_report"))

    save_progress_async(agent)
    print()


def main():
    """主函数"""
    try:
        # 创建智能体实例
        current_user_id = DEFAULT_CLI_USER_ID
        agent = create_cli_agent(current_user_id)
        print_welcome()
        print_current_user(agent)

        while True:
            # 获取用户输入
            user_input = input(Fore.GREEN + f"[{current_user_id}] 你: " + Style.RESET_ALL).strip()
            lower_input = user_input.lower()

            # 处理特殊命令
            if lower_input in ['quit', 'exit', 'q']:
                # 自动保存进度
                if Config.AUTO_SAVE_PROGRESS:
                    save_cli_agent(agent)
                print(Fore.CYAN + "再见！感谢使用小芽智能体。")
                break

            elif lower_input == 'save':
                save_cli_agent(agent)
                print(Fore.YELLOW + "对话历史已保存。")

            elif lower_input == 'load':
                agent.load_history()
                sync_cli_conversation(agent)
                if database_storage_enabled():
                    print(Fore.YELLOW + "对话历史已从数据库加载。")
                else:
                    print(Fore.YELLOW + "对话历史已从 chat_history.json 加载。")

            elif lower_input == 'user':
                print_current_user(agent)
                print(Fore.YELLOW + "切换用户：user <用户ID>")

            elif lower_input.startswith('user '):
                target_user_id = user_input.split(maxsplit=1)[1].strip()
                try:
                    agent = switch_cli_user(agent, target_user_id)
                    current_user_id = target_user_id
                    print(Fore.GREEN + f"已切换到用户：{current_user_id}")
                    print_current_user(agent)
                except ValueError as exc:
                    print(Fore.RED + str(exc))

            elif lower_input == 'users':
                users = list_cli_users()
                if not users:
                    print(Fore.YELLOW + "暂无已保存的用户心理模型。")
                else:
                    print(Fore.YELLOW + "\n已有用户心理模型:")
                    for item in users:
                        marker = "*" if item["userId"] == current_user_id else " "
                        print(f" {marker} {item['userId']}  ({item['safeUserId']})  会话:{item.get('conversationCount', 0)}")
                    print()

            elif lower_input.startswith('user-history'):
                parts = user_input.split(maxsplit=1)
                target_user_id = parts[1].strip() if len(parts) > 1 else current_user_id
                payload = list_user_conversations(target_user_id, include_history=False)
                if not payload.get("exists"):
                    print(Fore.YELLOW + f"用户不存在或暂无历史：{target_user_id}")
                else:
                    print(Fore.CYAN + f"\n用户 {payload['userId']} 的统一会话历史:")
                    conversations = payload.get("conversations") or []
                    if not conversations:
                        print("  暂无会话历史")
                    for item in conversations:
                        print(
                            f"  - [{item.get('source')}] {item.get('conversationId')} "
                            f"{item.get('title') or ''}  消息:{item.get('messageCount', 0)}"
                        )
                    print()

            elif lower_input.startswith('delete-user '):
                target_user_id = user_input.split(maxsplit=1)[1].strip()
                if not target_user_id:
                    print(Fore.RED + "用户 ID 不能为空")
                    continue
                print(Fore.RED + f"\n警告：将删除用户 {target_user_id} 的心理模型、统一会话历史和关联 API/CLI 会话。")
                confirm = input(Fore.YELLOW + "确认删除请输入 'yes' 或 'y': " + Style.RESET_ALL).strip().lower()
                if confirm not in {'yes', 'y'}:
                    print(Fore.YELLOW + "删除操作已取消。\n")
                    continue
                result = delete_user(target_user_id)
                print(Fore.GREEN + f"删除完成：{json.dumps(result, ensure_ascii=False)}")
                if sanitize_user_id(target_user_id) == sanitize_user_id(current_user_id):
                    current_user_id = DEFAULT_CLI_USER_ID
                    agent = create_cli_agent(current_user_id)
                    print(Fore.YELLOW + f"当前用户已切换为：{current_user_id}")
                print()

            elif lower_input in {'psych-model', 'model'}:
                print_psych_model(agent)

            elif lower_input == 'cohort':
                payload = get_cohort_learning_model(refresh_if_stale=True)
                print(Fore.CYAN + "\n匿名群体学习模型:")
                print(json.dumps(payload, ensure_ascii=False, indent=2))
                print()

            elif lower_input == 'cohort rebuild':
                payload = rebuild_cohort_learning_model(force=True)
                print(Fore.GREEN + "\n匿名群体学习模型已重建:")
                print(json.dumps(payload, ensure_ascii=False, indent=2))
                print()

            elif lower_input.startswith('phase'):
                # phase            -> 显示当前分期
                # phase pre/key/post -> 设置分期
                parts = user_input.strip().split()
                if len(parts) == 1:
                    print(Fore.YELLOW + f"当前分期：{agent.get_transplant_phase().value}")
                    print(Fore.YELLOW + "可用：phase pre | phase key | phase post")
                else:
                    key = parts[1].lower()
                    mapping = {
                        "pre": "移植前准备期",
                        "prep": "移植前准备期",
                        "key": "移植中关键期",
                        "mid": "移植中关键期",
                        "post": "移植后恢复期",
                        "recovery": "移植后恢复期",
                    }
                    if key not in mapping:
                        print(Fore.RED + "分期参数无效，请使用 pre/key/post")
                    else:
                        # 延迟导入避免循环依赖
                        from xiaoya_agent.domain.transplant import TransplantPhase
                        agent.set_transplant_phase(TransplantPhase(mapping[key]))
                        print(Fore.YELLOW + f"已设置分期：{agent.get_transplant_phase().value}")

            elif lower_input == 'energy':
                report = agent.energy_model.get_energy_report()
                print(Fore.BLUE + "\n 心理能量报告:")
                print(f"  总能量: {report['total_energy']} 点")
                print(f"  当前等级: {report['current_level']['name']}")
                print(f"  等级进度: {report['level_progress']:.1f}%")
                print(f"  连续对话: {report.get('consecutive_days', 0)} 天")

                print("  维度得分:")
                for dim, score in report['dimension_scores'].items():
                    print(f"    {dim}: {score} 点")

                achievement_stats = report.get('achievement_stats', {})
                if achievement_stats:
                    print(f"  成就进度: {achievement_stats['unlocked_achievements']}/{achievement_stats['total_achievements']} ({achievement_stats['completion_rate']}%)")
                print()

            elif lower_input == 'achievements':
                # 显示成就系统
                achievement_stats = agent.energy_model.get_achievement_stats()
                categorized = agent.energy_model.get_achievements_by_category()
                
                print(Fore.CYAN + "\n" + "=" * 60)
                print(Fore.YELLOW + " 成就系统")
                print(Fore.CYAN + "=" * 60)
                
                # 总体统计
                print(Fore.GREEN + f"\n 总体进度: {achievement_stats['unlocked_achievements']}/{achievement_stats['total_achievements']} ({achievement_stats['completion_rate']}%)")
                
                # 计数器统计
                counters = achievement_stats['counters']
                print(Fore.BLUE + "\n 当前进度:")
                print(f"  正念练习: {counters['mindfulness_count']} 次")
                print(f"  认知重构: {counters['cognitive_restructure_count']} 次")
                print(f"  行为激活: {counters['behavioral_activation_count']} 次")
                print(f"  连续对话: {counters['consecutive_days']} 天")
                print(f"  积极情绪: {counters['positive_emotion_count']} 次")
                print(f"  克服危机: {counters['crisis_overcome_count']} 次")
                print(f"  高质量对话: {counters['high_quality_session_count']} 次")
                
                # 按类别显示成就
                print(Fore.YELLOW + "\n 成就列表（按类别）:")
                category_order = ["基础", "认知", "情绪", "行为", "正念", "社交", "效能", "韧性", "里程碑", "质量", "特殊"]
                
                for category in category_order:
                    if category not in categorized:
                        continue
                    
                    achievements = categorized[category]
                    category_stat = achievement_stats['category_stats'].get(category, {})
                    unlocked = category_stat.get('unlocked', 0)
                    total = category_stat.get('total', 0)
                    
                    print(Fore.CYAN + f"\n [{category}] {unlocked}/{total}")
                    
                    # 按解锁状态排序：已解锁的在前
                    achievements.sort(key=lambda x: (not x['unlocked'], x['threshold']))
                    
                    for ach in achievements:
                        if ach['unlocked']:
                            # 已解锁：绿色显示
                            print(Fore.GREEN + f"  ✓ {ach['name']}")
                            print(f"    {ach['description']} (+{ach['reward']}点)")
                        else:
                            # 未解锁：显示进度
                            progress = ach.get('progress', {})
                            current = progress.get('current', 0)
                            threshold = progress.get('threshold', 0)
                            percentage = progress.get('percentage', 0)
                            print(Fore.WHITE + f"  ○ {ach['name']}")
                            print(f"    {ach['description']} (+{ach['reward']}点)")
                            print(Fore.YELLOW + f"    进度: {current}/{threshold} ({percentage:.1f}%)")
                
                # 最近解锁的成就
                recent = achievement_stats.get('recent_achievements', [])
                if recent:
                    print(Fore.MAGENTA + "\n 最近解锁:")
                    for ach in recent[:5]:
                        print(f"  * {ach['name']} - {ach['description']}")
                
                print(Fore.CYAN + "\n" + "=" * 60 + "\n")

            elif lower_input == 'progress':
                report = agent.get_comprehensive_report()
                print(Fore.BLUE + "\n 综合进步报告:")

                # CBT 进度
                cbt = report['cbt_progress']
                print(f"  CBT会话: {cbt['total_sessions']} 次")
                print(f"  进步水平: {cbt['progress_level']}%")
                if cbt['common_patterns']:
                    print(f"  常见模式: {', '.join(cbt['common_patterns'][:3])}")

                # 危机报告
                crisis = report['crisis_report']
                print(f"  危机事件: {crisis['total_crises']} 次")
                recent_count = crisis.get('recent_crises_count', 0)
                if recent_count > 0:
                    print(f"  近期危机: {recent_count} 次")

                print(f"  总会话数: {report['session_count']} 次")
                print()

            elif lower_input == 'grounding':
                # 获取正念接地练习并记录为正念练习
                exercise = agent.get_grounding_exercise()
                print(Fore.CYAN + "\n" + exercise + "\n")
                
                # 模拟一次正念练习对话，触发成就计数
                conversation_data = {
                    "user_message": "我进行了正念接地练习",
                    "analysis": {
                        "emotional_state": {"primary": "calm", "severity": 2},
                        "cognitive_distortions": [],
                        "recommended_technique": "MINDFULNESS"
                    },
                    "cbt_response": "很好的正念练习！"
                }
                
                # 接地练习是用户显式触发的确定性事件，不再走关键词能量评分。
                energy_result = agent.energy_model.apply_llm_assessment(conversation_data, {
                    "cognitive_growth": 0,
                    "emotion_regulation": 10,
                    "behavior_change": 3,
                    "social_connection": 0,
                    "self_efficacy": 3,
                    "assessment_note": "完成一次正念接地练习",
                    "achievement_signals": {
                        "mindfulness_practice": True,
                        "positive_emotion": True,
                    },
                })
                
                # 显示能量反馈
                if Config.ENERGY_FEEDBACK_ENABLED and energy_result:
                    energy_report = agent.energy_model.get_energy_report()
                    display_energy_feedback(energy_result, energy_report)
                
                # 自动保存进度
                if Config.AUTO_SAVE_PROGRESS:
                    agent.energy_model.save_progress()

            elif lower_input == 'harbor':
                print_harbor_catalog()

            elif lower_input.startswith('harbor '):
                params = parse_harbor_command(user_input)
                practice = create_harbor_practice(
                    scenario=params["scenario"],
                    tool_type=params["tool_type"],
                    duration_seconds=params["duration_seconds"],
                    query=params["query"],
                    mode=params["mode"],
                )
                display_harbor_practice(practice)
                record_harbor_practice(agent, practice, message=params["query"])

            elif lower_input == 'reset':
                # 确认重置操作
                print(Fore.RED + f"\n警告：此操作将清除当前用户 {current_user_id} 的数据，包括：")
                print("  - 当前 CLI 对话历史")
                print("  - 用户心理模型状态（分期等）")
                print("  - CBT 用户档案")
                print("  - 心理能量进度")
                print("  - 危机历史记录")
                if database_storage_enabled():
                    print("  - 当前用户在数据库中的持久化数据")
                else:
                    print("  - 当前用户的持久化文件")
                confirm = input(Fore.YELLOW + "\n确认要重置吗？输入 'yes' 或 'y' 确认: " + Style.RESET_ALL).strip().lower()
                if confirm in ['yes', 'y']:
                    result = agent.reset()
                    if database_storage_enabled():
                        save_cli_agent(agent)
                    print(Fore.GREEN + f"\n{result['message']}")
                    if result['deleted_files']:
                        print(f"已删除文件: {', '.join(result['deleted_files'])}")
                    print()
                else:
                    print(Fore.YELLOW + "重置操作已取消。\n")

            elif lower_input == 'help':
                print_help()

            elif user_input == '':
                continue  # 跳过空输入

            else:
                run_chat_turn(agent, user_input)

    except KeyboardInterrupt:
        print(Fore.CYAN + "\n\n程序被用户中断。")
        if Config.AUTO_SAVE_PROGRESS:
            try:
                save_cli_agent(agent)
            except:
                pass
    except Exception as e:
        print(Fore.RED + f"发生错误: {str(e)}")
        return 1

    return 0

if __name__ == "__main__":
    sys.exit(main())
