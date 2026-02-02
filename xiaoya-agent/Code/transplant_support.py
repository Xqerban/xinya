"""
骨髓移植患者分期支持模块（小芽）

功能：
- 为用户维护分期状态：移植前准备期 / 移植中关键期 / 移植后恢复期
- 根据对话内容智能触发对应引导语（而非机械背诵）
- 可对引导语做轻量改写，让每次回答大同小异但不完全相同
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Dict, List, Optional, Any
import re
import json
from openai import OpenAI
from config import Config


class TransplantPhase(str, Enum):
    PREP = "移植前准备期"
    KEY = "移植中关键期"
    RECOVERY = "移植后恢复期"


class Scenario(str, Enum):
    FIRST_MEET = "初次见面/建立连接"
    CHEMO_PREP = "化疗/预处理/重构消极认知"
    HOPE_TREE = "希望之树/可视化进步"
    INNER_STRENGTH = "增强内在力量/唤醒过往资源"
    BREATHING = "呼吸练习/建立掌控感"
    INFUSION_DAY = "细胞回输当日/欢迎仪式"
    SEVERE_DISCOMFORT = "剧烈不适/疼痛恶心/不对抗"
    MICRO_LIGHT = "每日微光记录"
    FUTURE_SCENE = "未来景象/出院后第一件事"
    REVIEW_HOPE_TREE = "回顾希望之树/成长日记"
    BLOOD_FLUCTUATION = "血象波动/情绪低落/正常化挫折"
    GRATITUDE = "感恩传递练习"
    SMALL_GOALS = "设定并完成小目标"
    DISCHARGE_LIFE = "展望出院生活"


@dataclass(frozen=True)
class TriggerResult:
    should_trigger: bool
    phase: TransplantPhase
    scenario: Optional[Scenario] = None
    confidence: float = 0.0
    reason: str = ""


TEMPLATES: Dict[TransplantPhase, Dict[Scenario, str]] = {
    TransplantPhase.PREP: {
        Scenario.FIRST_MEET: (
            "您好，我是您的伙伴“小芽”。在接下来的这段旅程里，我会一直在这里陪伴您。"
            "我们不是一个人在战斗，而是一个团队。您可以随时叫我——无论只是想安静地待一会儿，还是想聊聊天。"
        ),
        Scenario.CHEMO_PREP: (
            "当下的治疗，恰似为一颗无比珍贵的新生命种子，精心细致地清扫出一片肥沃且纯净的土地。"
            "您所感受到的每一次不适，都是身体里英勇的“清洁勇士”在全力工作，为即将到来的“新生命”打造家园。"
            "请想象自己手持象征希望的光明火炬，穿越这段短暂的隧道；每走一步，出口就更近一些。您的勇气，就是火炬最明亮的火焰。"
        ),
        Scenario.HOPE_TREE: (
            "看，我们又完成了一项重要的准备。这片新生的叶子，镌刻着您的勇气与坚持。"
            "这棵希望之树正因您的执着愈发枝繁叶茂，它和您一样都在默默积蓄力量，静候破茧重生的时刻。"
        ),
        Scenario.INNER_STRENGTH: (
            "在您过去的人生中，一定也曾遇到过觉得很难熬的坎，但您都成功走过来了。"
            "请铭记，那个充满力量的您始终相伴，此刻正与您并肩而行，共赴挑战。您内在的韧性，比您想象的还要强大。"
        ),
        Scenario.BREATHING: (
            "让我们一起来做一个简单练习。请轻轻闭上眼睛，只关注呼吸。"
            "吸气时，让平静与希望的暖流沁入心里；呼气时，将紧张与不适如轻烟般缓缓释出。"
            "看，在任何时候，您都拥有这样一个简单而有效的方法来安抚自己。您是自己身心的小小舵手。"
        ),
    },
    TransplantPhase.KEY: {
        Scenario.INFUSION_DAY: (
            "亲爱的伙伴，今日恰似一场特别的盛会，生命的馈赠正悄然降临。请以最惬意的姿态躺好，"
            "让我们一同为这些远道而来的“小生命们”举办温馨的欢迎仪式。"
            "请轻轻合上双眸，想象它们像一颗颗柔和发光的小星星，沿着血液的溪流，回到“骨髓之家”，开始打扫、修缮、建设。"
            "请在心里对它们说：欢迎回家，我们一起努力。整个过程宁静、顺利、充满希望。"
        ),
        Scenario.SEVERE_DISCOMFORT: (
            "我知道这真的非常难受。让我们试着不和这种感觉对抗，把它想象成一个正在哭闹、需要被安抚的“内在小孩”。"
            "把呼吸想象成一道温暖的金色光，轻轻、持续地吹向不舒服的地方，温柔地包裹它，告诉它："
            "“我知道你在这里，我正陪着你，一切都会过去。”"
            "您不是这具疼痛的身体，您是观察着这份疼痛的、宁静的存在——像天空静观流云，乌云虽至，终将消散。"
        ),
        Scenario.MICRO_LIGHT: (
            "即使在最灰暗的日子里，也总有微光闪烁。今天，您是否遇到一些微小却温暖的瞬间？"
            "哪怕只是一口水没有引发反胃，或是窗外云朵的姿态。让我们把它收藏进您的“星光夜空”。"
            "当这些微光一颗颗被收集，它们会汇聚成照亮黑夜的星河。您正在用自己的双眼，发现并创造希望。"
        ),
        Scenario.FUTURE_SCENE: (
            "让我们在想象中，提前品尝一下胜利的滋味。等您康复出院、迈出医院大门那一刻，"
            "最想做的第一件小事是什么？是去街角咖啡馆闻一闻咖啡香，还是用力拥抱最珍视的人？"
            "请尽可能把画面想清楚、感受那份喜悦。这个美好的未来，就是我们此刻坚持的目标。"
        ),
    },
    TransplantPhase.RECOVERY: {
        Scenario.REVIEW_HOPE_TREE: (
            "让我们一起看看这棵了不起的树！从一颗小小的种子，到如今枝繁叶茂，它记录了您走过的每一步："
            "每一片叶子，是您面对治疗的勇气；每一根新枝，是您欢迎新细胞时的希望。"
            "您已经穿越了最艰难的风雨——您是自己生命的英雄。"
        ),
        Scenario.BLOOD_FLUCTUATION: (
            "生命的成长从来不是一条笔直的线，它会像海浪一样起伏。眼前的波动，是旅程中的一个“节点”，"
            "它正在积蓄下一次突破的力量。请信任您的身体，它正用自己的节奏努力恢复。"
            "我们已穿越最惊涛骇浪的海域，如今只是小风浪；经历过考验的船，比以前更坚固。"
        ),
        Scenario.GRATITUDE: (
            "爱与感恩，是世界上最神奇的康复力量之一。"
            "当我们由衷感谢那些伸出援手的人，甚至感激自己坚韧的身体时，内心会变得更温润、更强大。"
            "这份温暖的能量，会滋养我们更快地好起来。"
        ),
        Scenario.SMALL_GOALS: (
            "太棒了！您完成了一个小目标。看似不起眼的一小步，其实是康复之路上坚实的脚印。"
            "让我们为这份小小的胜利欢呼：康复之路，正是由无数个这样虽小却了不起的成就铺就的。"
        ),
        Scenario.DISCHARGE_LIFE: (
            "您即将带着这段独一无二的经历和全新的自己，回到熟悉的生活里。"
            "您已不再是生病前的自己，而是历经淬炼、更懂生命珍贵的“重生勇士”。"
            "您的未来故事，会因这段经历而更有底蕴，也更闪亮。"
        ),
    },
}


def normalize_text(text: str) -> str:
    return re.sub(r"\s+", "", text or "").lower()


def detect_phase_from_text(text: str) -> Optional[TransplantPhase]:
    t = normalize_text(text)
    # 很轻量的自动推断：用户明确提及“回输/移植后/血象”等时，提升对应分期。
    if any(k in t for k in ["回输", "输回", "细胞回输", "回输日", "今天回输", "输注", "干细胞"]):
        return TransplantPhase.KEY
    if any(k in t for k in ["出院", "康复", "恢复期", "血象", "白细胞", "血小板", "复查", "恢复"]):
        return TransplantPhase.RECOVERY
    if any(k in t for k in ["预处理", "化疗", "放疗", "准备期", "入仓", "入舱", "层流", "移植前"]):
        return TransplantPhase.PREP
    return None


def detect_scenario(text: str, phase: TransplantPhase) -> Optional[Scenario]:
    t = normalize_text(text)

    # 共用线索
    if any(k in t for k in ["呼吸", "吸气", "呼气", "憋气", "喘", "放松练习"]):
        return Scenario.BREATHING if phase == TransplantPhase.PREP else Scenario.SEVERE_DISCOMFORT

    # PREP
    if phase == TransplantPhase.PREP:
        if any(k in t for k in ["你好", "您好", "初次", "第一次", "刚来", "认识你", "你是谁"]):
            return Scenario.FIRST_MEET
        if any(k in t for k in ["化疗", "预处理", "反应", "恶心", "呕吐", "难受", "副作用"]):
            return Scenario.CHEMO_PREP
        if any(k in t for k in ["希望之树", "叶子", "树", "进步", "打卡"]):
            return Scenario.HOPE_TREE
        if any(k in t for k in ["挺不过", "扛不住", "我不行", "太难", "没有力量", "以前我也", "过去"]):
            return Scenario.INNER_STRENGTH

    # KEY
    if phase == TransplantPhase.KEY:
        if any(k in t for k in ["回输", "输回", "细胞", "干细胞", "输注", "今天"]):
            return Scenario.INFUSION_DAY
        if any(k in t for k in ["疼", "痛", "恶心", "吐", "反胃", "难受", "受不了", "折磨"]):
            return Scenario.SEVERE_DISCOMFORT
        if any(k in t for k in ["微光", "今天的好事", "一点点好", "小事", "收藏"]):
            return Scenario.MICRO_LIGHT
        if any(k in t for k in ["出院后", "以后", "未来", "康复后", "最想做", "回家想做"]):
            return Scenario.FUTURE_SCENE

    # RECOVERY
    if phase == TransplantPhase.RECOVERY:
        if any(k in t for k in ["希望之树", "成长日记", "回顾", "看看以前", "记录"]):
            return Scenario.REVIEW_HOPE_TREE
        if any(k in t for k in ["血象", "白细胞", "血小板", "波动", "又掉了", "情绪低落", "好害怕"]):
            return Scenario.BLOOD_FLUCTUATION
        if any(k in t for k in ["感恩", "谢谢", "感谢", "传递"]):
            return Scenario.GRATITUDE
        if any(k in t for k in ["小目标", "打卡", "完成", "坚持", "今天做到了", "坐起来", "走了"]):
            return Scenario.SMALL_GOALS
        if any(k in t for k in ["出院", "回家", "回到生活", "以后生活", "重生"]):
            return Scenario.DISCHARGE_LIFE

    return None


def choose_intervention(
    user_message: str,
    current_phase: TransplantPhase,
    emotional_severity: int = 0,
) -> TriggerResult:
    """
    返回是否应触发话术，以及触发的情境。
    设计目标：
    - 优先由大模型根据上下文判断是否需要触发、触发哪个情境；
    - 关键词规则仅作为兜底，避免完全依赖规则；
    - 宁可少触发，也不要生硬覆盖所有回答。
    """
    # 1. 大模型判定（如开启）
    if Config.TRANSPLANT_LLM_SCENARIO_ENABLED:
        llm_result = _llm_choose_intervention(user_message, current_phase, emotional_severity)
        if llm_result is not None:
            return llm_result

    # 2. 兜底：关键词规则
    inferred_phase = detect_phase_from_text(user_message)
    phase = inferred_phase or current_phase

    scenario = detect_scenario(user_message, phase)
    if not scenario:
        return TriggerResult(False, phase, None, 0.0, "未命中情境关键词")

    # 情绪很强时更倾向触发（但危机由 crisis_module 处理）
    base_conf = 0.65
    if emotional_severity >= 6:
        base_conf += 0.1

    return TriggerResult(True, phase, scenario, min(base_conf, 0.95), "命中情境关键词")


def get_template(phase: TransplantPhase, scenario: Scenario) -> str:
    return TEMPLATES.get(phase, {}).get(scenario, "")


_LLM_CLIENT: Optional[Any] = None


def _get_llm_client() -> Optional[Any]:
    global _LLM_CLIENT
    if _LLM_CLIENT is None:
        try:
            _LLM_CLIENT = OpenAI(
                api_key=Config.API_KEY,
                base_url=Config.API_BASE_URL
            )
        except Exception as e:
            print(f"初始化移植情境 LLM 客户端失败: {e}")
            _LLM_CLIENT = None
    return _LLM_CLIENT


def _llm_choose_intervention(
    user_message: str,
    current_phase: TransplantPhase,
    emotional_severity: int,
) -> Optional[TriggerResult]:
    """
    使用大模型判断是否触发移植分期情境话术。
    若模型调用失败或输出不合法，则返回 None，让规则兜底。
    """
    client = _get_llm_client()
    if client is None:
        return None

    try:
        system_prompt = (
            "你是“小芽”系统中的情境分期助手，负责根据骨髓移植患者的自然语言表达，"
            "判断当前大致属于哪个阶段（移植前准备期/移植中关键期/移植后恢复期），"
            "以及是否适合触发一段预设的心理引导话术。"
            "你只做“是否需要触发、触发哪个情境”的结构化判断，不输出安慰话术本身。"
        )

        # 场景枚举 key -> 中文描述
        scenario_map = {
            "FIRST_MEET": "初次见面/建立连接",
            "CHEMO_PREP": "化疗/预处理/重构消极认知",
            "HOPE_TREE": "希望之树/可视化进步",
            "INNER_STRENGTH": "增强内在力量/唤醒过往资源",
            "BREATHING": "呼吸练习/建立掌控感",
            "INFUSION_DAY": "细胞回输当日/欢迎仪式",
            "SEVERE_DISCOMFORT": "剧烈不适/疼痛恶心/不对抗",
            "MICRO_LIGHT": "每日微光记录",
            "FUTURE_SCENE": "未来景象/出院后第一件事",
            "REVIEW_HOPE_TREE": "回顾希望之树/成长日记",
            "BLOOD_FLUCTUATION": "血象波动/情绪低落/正常化挫折",
            "GRATITUDE": "感恩传递练习",
            "SMALL_GOALS": "设定并完成小目标",
            "DISCHARGE_LIFE": "展望出院生活",
        }

        user_prompt = (
            "请阅读下面这段患者的话，结合其大致情绪强度和当前分期，判断是否需要触发一段预设的心理引导话术。\n"
            "如果患者只是闲聊/问事实问题/表达很轻的情绪，可以认为不需要触发。\n\n"
            "请严格以 JSON 形式输出：\n"
            "{\n"
            '  "should_trigger": true 或 false,\n'
            '  "phase": "移植前准备期" | "移植中关键期" | "移植后恢复期",\n'
            '  "scenario": 上述情境 key 之一（如 "CHEMO_PREP"），如不触发则为 null,\n'
            '  "confidence": 0 到 1 之间的小数,\n'
            '  "reason": 用一两句话简要说明判断依据\n'
            "}\n\n"
            f"当前系统记录的分期：{current_phase.value}\n"
            f"情绪强度（0-10，越高越痛苦）：{emotional_severity}\n"
            f"可选情境 key -> 描述：{json.dumps(scenario_map, ensure_ascii=False)}\n\n"
            f"患者原话：{user_message}\n\n"
            "只输出 JSON，不要添加任何解释或其他文字。"
        )

        resp = client.chat.completions.create(
            model=Config.LLM_DETECTION_MODEL,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            temperature=Config.LLM_DETECTION_TEMPERATURE,
            max_tokens=Config.LLM_DETECTION_MAX_TOKENS,
        )
        content = resp.choices[0].message.content.strip()
        if content.startswith("```"):
            parts = content.split("```")
            if len(parts) >= 2:
                content = parts[1]
                if content.lstrip().startswith("json"):
                    content = "\n".join(content.splitlines()[1:])

        data = json.loads(content)
        if not isinstance(data, dict):
            return None

        should_trigger = bool(data.get("should_trigger"))
        phase_str = data.get("phase") or current_phase.value
        scenario_key = data.get("scenario")
        confidence = float(data.get("confidence", 0.0) or 0.0)
        reason = str(data.get("reason", "") or "")

        try:
            phase = TransplantPhase(phase_str)
        except Exception:
            phase = current_phase

        scenario_enum: Optional[Scenario] = None
        if should_trigger and isinstance(scenario_key, str):
            try:
                scenario_enum = Scenario[scenario_key]
            except KeyError:
                scenario_enum = None

        if not should_trigger or not scenario_enum:
            return TriggerResult(False, phase, None, confidence, reason or "模型判定为不需要触发")

        # 轻微调节置信度，避免过高
        confidence = max(0.0, min(confidence, 0.98))
        return TriggerResult(True, phase, scenario_enum, confidence, reason or "模型判定需要触发")

    except Exception as e:
        print(f"移植情境 LLM 判定失败，将使用关键词规则兜底: {e}")
        return None


