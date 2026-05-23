"""心之港湾：面向移植患者的轻量心理调节工具库。"""
from __future__ import annotations

from copy import deepcopy
from typing import Any, Dict, List, Optional


DURATION_OPTIONS = [30, 60, 120, 180, 300]

SCENARIOS: Dict[str, Dict[str, Any]] = {
    "anxiety": {
        "name": "焦虑紧张",
        "aliases": ["焦虑", "紧张", "慌", "担心", "害怕失败", "anxiety"],
        "default_tool": "breathing_regulation",
        "default_duration": 60,
    },
    "fear": {
        "name": "恐惧害怕",
        "aliases": ["恐惧", "害怕", "害怕治疗", "怕", "fear"],
        "default_tool": "grounding_54321",
        "default_duration": 90,
    },
    "insomnia": {
        "name": "失眠入睡困难",
        "aliases": ["失眠", "睡不着", "入睡", "睡眠", "insomnia", "sleep"],
        "default_tool": "meditation",
        "default_duration": 180,
    },
    "pain": {
        "name": "疼痛不适",
        "aliases": ["疼", "疼痛", "痛", "不舒服", "酸胀", "pain"],
        "default_tool": "progressive_muscle_relaxation",
        "default_duration": 120,
    },
    "overwhelm": {
        "name": "情绪崩溃",
        "aliases": ["崩溃", "受不了", "撑不住", "难受", "情绪爆炸", "overwhelm"],
        "default_tool": "grounding_54321",
        "default_duration": 60,
    },
    "daily_relax": {
        "name": "日常放松",
        "aliases": ["放松", "休息", "缓一缓", "调节", "relax"],
        "default_tool": "mindfulness_guidance",
        "default_duration": 120,
    },
}

TOOLS: Dict[str, Dict[str, Any]] = {
    "mindfulness_guidance": {
        "name": "正念引导",
        "aliases": ["正念", "正念引导", "mindfulness"],
        "description": "把注意力轻轻放回当下，适合焦虑、杂念多或情绪起伏时使用。",
    },
    "meditation": {
        "name": "冥想训练",
        "aliases": ["冥想", "冥想训练", "meditation"],
        "description": "用安静、慢速的引导帮助入睡前或夜间醒来时稳定心绪。",
    },
    "music_relaxation": {
        "name": "音乐放松",
        "aliases": ["音乐", "音乐放松", "白噪音", "music"],
        "description": "配合低音量舒缓音乐或白噪音，减少病房环境带来的紧绷感。",
    },
    "breathing_regulation": {
        "name": "呼吸调节",
        "aliases": ["呼吸", "呼吸调节", "呼吸练习", "breathing"],
        "description": "用短呼吸节律降低身体紧张，适合焦虑、恐慌和等待检查前。",
    },
    "progressive_muscle_relaxation": {
        "name": "肌肉渐进放松",
        "aliases": ["肌肉", "肌肉放松", "渐进放松", "pmr"],
        "description": "不做大动作，只做微小感知和松开，适合疼痛、僵硬或长期卧床。",
    },
    "grounding_54321": {
        "name": "54321 接地练习",
        "aliases": ["54321", "接地", "落地", "grounding"],
        "description": "用看见、触碰、听见、闻到和呼吸把注意力拉回当下。",
    },
}

TOOL_SCRIPTS: Dict[str, Dict[str, Any]] = {
    "mindfulness_guidance": {
        "title": "一小段正念停靠",
        "musicSuggestion": "可搭配低音量环境白噪音，不需要节拍明显的音乐。",
        "segments": [
            ("把身体交给床面，先不用调整姿势。", 10),
            ("注意鼻尖或胸口的一次吸气，再注意一次呼气。", 20),
            ("如果有念头出现，只在心里说一句：我看见它了。", 25),
            ("现在把注意力放到手心，感觉一点点温度或重量。", 25),
            ("最后轻轻告诉自己：这一分钟，我只需要待在这里。", 20),
        ],
    },
    "meditation": {
        "title": "睡前安静冥想",
        "musicSuggestion": "可搭配 60-70 BPM 的轻柔纯音乐或雨声。",
        "segments": [
            ("让眼睛自然休息，不需要用力闭紧。", 15),
            ("想象病房外有一盏很柔和的灯，光线慢慢变暗。", 35),
            ("每一次呼气，都像把今天的一小部分辛苦放下。", 35),
            ("如果还睡不着，也没关系，先让身体休息就已经很好。", 35),
            ("把注意力留在呼吸的起伏里，安静地陪自己一会儿。", 40),
        ],
    },
    "music_relaxation": {
        "title": "音乐放松停靠",
        "musicSuggestion": "建议播放轻柔纯音乐、雨声、海浪声或低音量白噪音。",
        "segments": [
            ("先把音量调到只够自己听见，不盖过病房提醒声。", 15),
            ("让声音在耳边经过，你不需要追着它听。", 25),
            ("注意一个最柔和的声音，把注意力轻轻放在那里。", 30),
            ("如果身体有不适，允许它存在，同时把呼气放慢一点。", 35),
            ("这一小段音乐只是帮你靠岸，不要求马上变好。", 30),
        ],
    },
    "breathing_regulation": {
        "title": "短呼吸调节",
        "musicSuggestion": "不需要音乐；如需背景音，可用极轻的白噪音。",
        "segments": [
            ("先不用深呼吸，只把呼气放慢一点点。", 10),
            ("用鼻子轻轻吸气两拍，再慢慢呼气四拍。", 20),
            ("再来一次：吸气两拍，呼气四拍，肩膀不用用力。", 20),
            ("如果头晕，就恢复自然呼吸，只观察空气进出。", 15),
            ("最后把手心放松，告诉自己：我正在一点点稳下来。", 20),
        ],
    },
    "progressive_muscle_relaxation": {
        "title": "床旁微动作肌肉放松",
        "musicSuggestion": "可搭配很轻的舒缓音乐；如疼痛明显，优先停止动作并联系医护。",
        "segments": [
            ("这不是用力训练，只做很小的感知和松开。", 10),
            ("轻轻感受脚趾，不需要移动，只在心里说：松一点。", 25),
            ("感受小腿和膝盖，把不需要的力气慢慢放掉。", 25),
            ("感受肩膀和下颌，如果咬紧了，就松开一点点。", 25),
            ("哪里痛就不碰哪里，只把呼气送到旁边舒服的位置。", 25),
        ],
    },
    "grounding_54321": {
        "title": "54321 当下接地",
        "musicSuggestion": "不建议播放复杂音乐，保持能听见环境和呼叫铃。",
        "segments": [
            ("先看见 5 样东西，可以是灯、床栏、被子或墙面。", 25),
            ("再感觉 4 个触点，比如背、手、脚或被子的重量。", 25),
            ("听见 3 种声音，不用判断，只是听见。", 20),
            ("注意 2 个气味，若没有，就注意空气的温度。", 20),
            ("最后做 1 次慢慢的呼气，把自己带回此刻。", 20),
        ],
    },
}


def _normalize_text(value: Any) -> str:
    return str(value or "").strip().lower()


def _lookup_by_alias(value: Any, mapping: Dict[str, Dict[str, Any]], default_key: str) -> str:
    normalized = _normalize_text(value)
    if not normalized:
        return default_key
    if normalized in mapping:
        return normalized
    for key, meta in mapping.items():
        if normalized == _normalize_text(meta.get("name")):
            return key
        if normalized in [_normalize_text(item) for item in meta.get("aliases", [])]:
            return key
    for key, meta in mapping.items():
        candidates = [key, meta.get("name", ""), *(meta.get("aliases", []) or [])]
        if any(_normalize_text(item) and _normalize_text(item) in normalized for item in candidates):
            return key
    return default_key


def normalize_scenario(value: Any = None, text: str = "") -> str:
    return _lookup_by_alias(value or text, SCENARIOS, "daily_relax")


def normalize_tool_type(value: Any = None, scenario: str = "daily_relax", text: str = "") -> str:
    default_tool = SCENARIOS.get(scenario, SCENARIOS["daily_relax"])["default_tool"]
    return _lookup_by_alias(value or text, TOOLS, default_tool)


def normalize_duration(value: Any = None, scenario: str = "daily_relax") -> int:
    default_duration = int(SCENARIOS.get(scenario, SCENARIOS["daily_relax"])["default_duration"])
    if value in (None, ""):
        return default_duration
    try:
        seconds = int(float(value))
    except (TypeError, ValueError):
        return default_duration
    seconds = max(DURATION_OPTIONS[0], min(DURATION_OPTIONS[-1], seconds))
    return min(DURATION_OPTIONS, key=lambda item: abs(item - seconds))


def should_use_harbor_regulation(text: str) -> bool:
    normalized = _normalize_text(text)
    if not normalized:
        return False
    trigger_terms = [
        "心之港湾", "港湾", "放松", "呼吸", "放松练习", "带我放松", "呼吸练习", "呼吸调节",
        "54321", "接地练习", "正念", "冥想", "音乐放松", "肌肉放松",
        "睡不着", "失眠", "疼痛", "害怕", "焦虑", "崩溃", "撑不住",
    ]
    return any(term in normalized for term in trigger_terms)


def list_harbor_catalog() -> Dict[str, Any]:
    scenarios = []
    for key, meta in SCENARIOS.items():
        scenarios.append({
            "key": key,
            "name": meta["name"],
            "aliases": list(meta.get("aliases", [])),
            "defaultToolType": meta["default_tool"],
            "defaultDurationSeconds": meta["default_duration"],
        })
    tools = []
    for key, meta in TOOLS.items():
        tools.append({
            "key": key,
            "name": meta["name"],
            "aliases": list(meta.get("aliases", [])),
            "description": meta["description"],
        })
    return {
        "name": "心之港湾",
        "description": "面向骨髓移植患者的床旁轻量心理调节工具，覆盖正念、冥想、音乐放松、呼吸调节、肌肉渐进放松和 54321 接地练习。",
        "durationOptionsSeconds": list(DURATION_OPTIONS),
        "scenarios": scenarios,
        "tools": tools,
        "supportsVoiceGuide": True,
        "suitableForBedside": True,
        "requiresComplexMovement": False,
    }


def _fit_segments_to_duration(segments: List[tuple], duration_seconds: int) -> List[Dict[str, Any]]:
    total = sum(seconds for _, seconds in segments) or 1
    fitted = []
    consumed = 0
    for idx, (text, seconds) in enumerate(segments):
        if idx == len(segments) - 1:
            duration = max(5, duration_seconds - consumed)
        else:
            duration = max(5, round(duration_seconds * seconds / total))
            consumed += duration
        fitted.append({
            "order": idx + 1,
            "durationSeconds": duration,
            "text": text,
        })
    return fitted


def create_harbor_practice(
    scenario: Any = None,
    tool_type: Any = None,
    duration_seconds: Any = None,
    query: str = "",
    mode: str = "voice",
) -> Dict[str, Any]:
    scenario_key = normalize_scenario(scenario, query)
    tool_key = normalize_tool_type(tool_type, scenario_key, query)
    duration = normalize_duration(duration_seconds, scenario_key)
    scenario_meta = SCENARIOS[scenario_key]
    tool_meta = TOOLS[tool_key]
    script = deepcopy(TOOL_SCRIPTS[tool_key])
    segments = _fit_segments_to_duration(script["segments"], duration)
    voice_text = " ".join(segment["text"] for segment in segments)
    return {
        "practiceId": f"harbor-{scenario_key}-{tool_key}-{duration}",
        "name": "心之港湾",
        "title": f"{scenario_meta['name']} · {script['title']}",
        "scenario": {
            "key": scenario_key,
            "name": scenario_meta["name"],
        },
        "toolType": {
            "key": tool_key,
            "name": tool_meta["name"],
            "description": tool_meta["description"],
        },
        "durationSeconds": duration,
        "durationLabel": f"{duration // 60}分{duration % 60}秒" if duration >= 60 else f"{duration}秒",
        "mode": mode or "voice",
        "segments": segments,
        "voiceGuideText": voice_text,
        "displayText": "\n".join(f"{item['order']}. {item['text']}" for item in segments),
        "musicSuggestion": script.get("musicSuggestion"),
        "mediaUrl": None,
        "oneClickStart": True,
        "voiceStartPhrases": [
            "开始心之港湾",
            f"开始{tool_meta['name']}",
            f"带我做{scenario_meta['name']}练习",
        ],
        "suitableForBedside": True,
        "requiresComplexMovement": False,
        "safetyNote": "如果出现明显胸痛、喘不过气、严重出血、持续高热、意识异常或动作带来疼痛，请立刻停止练习并联系护士或医生。",
        "nextAction": "start_harbor_practice",
    }


def build_harbor_energy_payload(practice: Dict[str, Any]) -> Dict[str, Any]:
    tool_key = ((practice or {}).get("toolType") or {}).get("key", "")
    base = {
        "cognitive_growth": 0,
        "emotion_regulation": 10,
        "behavior_change": 3,
        "social_connection": 0,
        "self_efficacy": 3,
        "assessment_note": f"完成一次心之港湾练习：{((practice or {}).get('toolType') or {}).get('name', '心理调节')}",
        "achievement_signals": {
            "mindfulness_practice": tool_key in {
                "mindfulness_guidance",
                "meditation",
                "breathing_regulation",
                "grounding_54321",
                "progressive_muscle_relaxation",
            },
            "positive_emotion": True,
        },
    }
    if tool_key == "breathing_regulation":
        base["emotion_regulation"] = 12
    elif tool_key == "progressive_muscle_relaxation":
        base["behavior_change"] = 5
    elif tool_key == "grounding_54321":
        base["self_efficacy"] = 4
    return base


def build_harbor_conversation_data(practice: Dict[str, Any], message: str = "") -> Dict[str, Any]:
    tool_name = ((practice or {}).get("toolType") or {}).get("name", "心理调节")
    scenario_name = ((practice or {}).get("scenario") or {}).get("name", "日常放松")
    return {
        "user_message": message or f"我完成了一次心之港湾{tool_name}",
        "analysis": {
            "emotional_state": {"primary": "calm", "severity": 2},
            "cognitive_distortions": [],
            "recommended_technique": "MINDFULNESS",
        },
        "cbt_response": f"完成了一次面向{scenario_name}的{tool_name}。",
    }


def build_harbor_context(practice: Dict[str, Any]) -> str:
    if not practice:
        return ""
    return (
        f"[心之港湾]\n"
        f"标题：{practice.get('title')}\n"
        f"工具：{((practice.get('toolType') or {}).get('name'))}\n"
        f"场景：{((practice.get('scenario') or {}).get('name'))}\n"
        f"时长：{practice.get('durationSeconds')}秒\n"
        f"语音引导：{practice.get('voiceGuideText')}\n"
        f"安全提示：{practice.get('safetyNote')}"
    )
