#!/usr/bin/env python
"""
主程序 - 集成CBT、心理能量和危机干预
"""
import sys
import os
from colorama import init, Fore, Style
from simple_agent import EnhancedChatAgent
from config import Config

# 初始化colorama
init(autoreset=True)

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
    print("  phase         - 查看/设置骨髓移植分期（pre/key/post）")
    print("  energy        - 查看心理能量报告")
    print("  achievements  - 查看成就系统")
    print("  progress      - 查看综合进步报告")
    print("  grounding     - 获取正念接地练习")
    print("  reset         - 重置所有数据（清除所有历史记录）")
    print("  help          - 显示此帮助信息")
    print()

def display_energy_feedback(energy_assessment: dict, energy_report: dict):
    """显示能量反馈"""
    if not energy_assessment:
        return

    print(Fore.BLUE + "\n 心理能量反馈:")

    # 显示本次增益
    gains = energy_assessment.get("dimension_gains", {})
    if gains:
        print("  本次成长:")
        for dimension, gain in gains.items():
            if gain > 0:
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
    if crisis_detection.get("alert", False):
        print(Fore.RED + "\n 已触发危机报警\n" + Style.RESET_ALL)

def main():
    """主函数"""
    try:
        # 创建智能体实例
        agent = EnhancedChatAgent()
        print_welcome()

        while True:
            # 获取用户输入
            user_input = input(Fore.GREEN + "你: " + Style.RESET_ALL).strip()

            # 处理特殊命令
            if user_input.lower() in ['quit', 'exit', 'q']:
                # 自动保存进度
                if Config.AUTO_SAVE_PROGRESS:
                    agent.save_all_progress()
                print(Fore.CYAN + "再见！感谢使用小芽智能体。")
                break

            elif user_input.lower() == 'save':
                agent.save_all_progress()
                print(Fore.YELLOW + "对话历史已保存。")

            elif user_input.lower() == 'load':
                agent.load_history()
                print(Fore.YELLOW + "对话历史已从 chat_history.json 加载。")

            elif user_input.lower().startswith('phase'):
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
                        from transplant_support import TransplantPhase
                        agent.set_transplant_phase(TransplantPhase(mapping[key]))
                        print(Fore.YELLOW + f"已设置分期：{agent.get_transplant_phase().value}")

            elif user_input.lower() == 'energy':
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

            elif user_input.lower() == 'achievements':
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

            elif user_input.lower() == 'progress':
                report = agent.get_comprehensive_report()
                print(Fore.BLUE + "\n 综合进步报告:")

                # CBT进度
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

            elif user_input.lower() == 'grounding':
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
                
                # 评估并更新能量
                energy_result = agent.energy_model.assess_conversation_quality(conversation_data)
                
                # 显示能量反馈
                if Config.ENERGY_FEEDBACK_ENABLED and energy_result:
                    energy_report = agent.energy_model.get_energy_report()
                    display_energy_feedback(energy_result, energy_report)
                
                # 自动保存进度
                if Config.AUTO_SAVE_PROGRESS:
                    agent.energy_model.save_progress()

            elif user_input.lower() == 'reset':
                # 确认重置操作
                print(Fore.RED + "\n警告：此操作将清除所有历史记录，包括：")
                print("  - 对话历史")
                print("  - 用户状态（分期等）")
                print("  - CBT 用户档案")
                print("  - 心理能量进度")
                print("  - 危机历史记录")
                print("  - 所有持久化文件")
                confirm = input(Fore.YELLOW + "\n确认要重置吗？输入 'yes' 或 'y' 确认: " + Style.RESET_ALL).strip().lower()
                if confirm in ['yes', 'y']:
                    result = agent.reset()
                    print(Fore.GREEN + f"\n{result['message']}")
                    if result['deleted_files']:
                        print(f"已删除文件: {', '.join(result['deleted_files'])}")
                    print()
                else:
                    print(Fore.YELLOW + "重置操作已取消。\n")

            elif user_input.lower() == 'help':
                print_help()

            elif user_input == '':
                continue  # 跳过空输入

            else:
                # 正常对话 - 使用更快的流式链路
                print(Fore.BLUE + "智能体: " + Style.RESET_ALL, end="")

                for chunk in agent.stream_chat(user_input):
                    sys.stdout.write(chunk)
                    sys.stdout.flush()

                print()

                result = agent.last_result or {
                    "response": "",
                    "response_type": "cbt_response",
                    "crisis_detection": {},
                    "energy_assessment": None,
                    "energy_report": None,
                }

                # 显示危机警报（如果有）
                display_crisis_alert(result.get("crisis_detection", {}))

                # 显示能量反馈（如果启用且不是危机干预）
                if (Config.ENERGY_FEEDBACK_ENABLED and
                    result.get("response_type") != "crisis_intervention"):
                    display_energy_feedback(result.get("energy_assessment"), result.get("energy_report"))

                print()  # 添加空行

    except KeyboardInterrupt:
        print(Fore.CYAN + "\n\n程序被用户中断。")
        if Config.AUTO_SAVE_PROGRESS:
            try:
                agent.save_all_progress()
            except:
                pass
    except Exception as e:
        print(Fore.RED + f"发生错误: {str(e)}")
        return 1

    return 0

if __name__ == "__main__":
    sys.exit(main())
