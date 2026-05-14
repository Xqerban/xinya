"""小芽 Agent API 服务"""
import json
import logging
import os
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from enum import Enum
from typing import Dict, Any, Optional, List

from flask import Flask, request, jsonify, Response, stream_with_context
from openai import OpenAI

from xiaoya_agent.config import Config
from xiaoya_agent.runtime.session import (
    auto_name_session,
    build_agent_psych_model_payload,
    create_session_metadata,
    delete_session,
    delete_user,
    get_session_history,
    get_or_create_session,
    list_user_conversations,
    list_user_summaries,
    list_session_summaries,
    prepare_session_for_chat,
    read_user_psych_model,
    read_session_metadata,
    rename_session,
    sanitize_user_id,
    update_session_after_chat,
    write_session_metadata,
)
from xiaoya_agent.runtime.state_store import save_session_state
from xiaoya_agent.domain.transplant import TransplantPhase
from xiaoya_agent.features.crisis import build_crisis_alarm
from xiaoya_agent.integrations.dify import (
    dify_knowledge_configured,
    dify_replacement_status,
    should_use_dify_knowledge,
)
from xiaoya_agent.mcp_services import invoke_mcp_services
from xiaoya_agent.retrieval.rag import retrieve_knowledge
from xiaoya_agent.prompts.runtime import (
    compare_prompt_versions,
    delete_prompt_entry,
    get_prompt_entry,
    get_prompt_registry_snapshot,
    preview_prompt_candidate,
    reload_prompt_registry,
    rollback_prompt_entry,
    update_prompt_entry,
    update_prompt_settings,
)
from xiaoya_agent.prompts.preview import render_prompt_messages
from xiaoya_agent.keywords.library import POSITIVE_EMOTION_LABELS
from xiaoya_agent.utils.formatting import markdown_to_plain_text

app = Flask(__name__)
app.config["JSON_AS_ASCII"] = False
app.json.ensure_ascii = False
logger = logging.getLogger(__name__)
USER_FACING_ERROR_MESSAGE = "服务器暂时有点忙，请稍后再试。"
DIFY_OPENAPI_FILE = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..", "..", "docs", "dify_openapi.yaml")
)


def build_energy_assessment(energy_result: Optional[Dict]) -> Dict[str, Any]:
    if not energy_result:
        return {
            "cognitiveGrowth": 0,
            "emotionRegulation": 0,
            "behaviorChange": 0,
            "socialConnection": 0,
            "selfEfficacy": 0,
            "totalDelta": 0,
            "hopeTreeExpDelta": 0,
            "assessmentNote": "未进行能量评估"
        }
    gains = energy_result.get("dimension_gains", {})
    total_delta = energy_result.get("total_gain", 0)
    return {
        "cognitiveGrowth": gains.get("认知成长", 0),
        "emotionRegulation": gains.get("情绪调节", 0),
        "behaviorChange": gains.get("行为改变", 0),
        "socialConnection": gains.get("社交连接", 0),
        "selfEfficacy": gains.get("自我效能", 0),
        "totalDelta": total_delta,
        "hopeTreeExpDelta": min(15, int(total_delta * 1.2)),
        "assessmentNote": f"本轮对话获得 {total_delta} 点心理能量"
    }


def build_crisis_assessment(crisis_detection: Dict, cbt_analysis: Dict) -> Dict[str, Any]:
    emotional_state = cbt_analysis.get("emotional_state", {})
    emotion = emotional_state.get("primary", "")
    severity = emotional_state.get("severity", 0)
    emotion_signals = []
    if emotion == "anxiety":
        emotion_signals.append("重度焦虑" if severity >= 7 else "中度焦虑" if severity >= 5 else "轻度焦虑")
    elif emotion == "sadness":
        emotion_signals.append("中度抑郁" if severity >= 5 else "轻度抑郁")
    elif emotion == "hopelessness":
        emotion_signals.append("绝望感")
    elif emotion == "anger":
        emotion_signals.append("愤怒")
    elif emotion in POSITIVE_EMOTION_LABELS:
        emotion_signals.append("积极应对")
    alarm = build_crisis_alarm(crisis_detection, emotional_state)
    crisis_level = alarm["level"]
    action = alarm["action"]
    crisis_keywords = []
    alert_type = crisis_detection.get("alert_type")
    if alert_type == "medical_red_flag":
        crisis_keywords = ["身体红旗"]
    elif alert_type == "severe_emotional_distress":
        crisis_keywords = ["严重情绪痛苦"]
    elif alarm["crisisTypes"]:
        crisis_keywords = alarm["crisisTypes"]
    elif crisis_detection.get("alert", False):
        crisis_keywords = ["危机信号"]
    mindfulness_guide = None
    if action == "mindfulness_guide":
        mindfulness_guide = {
            "type": "breathing",
            "title": "4-7-8 放松呼吸练习",
            "instruction": "我们一起来做个呼吸练习好吗？用鼻子吸气4秒，屏住呼吸7秒，再用嘴慢慢呼出8秒。让我们重复几次，感受身体慢慢放松下来...",
            "durationSeconds": 120,
            "mediaUrl": None,
        }
    return {
        "crisisAlert": alarm["shouldNotify"],
        "crisisLevel": crisis_level,
        "alarm": alarm,
        "severityScore": alarm["severityScore"],
        "crisisKeywords": crisis_keywords,
        "emotionSignals": emotion_signals,
        "action": action,
        "mindfulnessGuide": mindfulness_guide,
    }


def build_sse_event(event: str, data: Dict[str, Any]) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


def generate_recommended_questions(stage: str, psych_energy: int, emotional_state: Optional[Dict[str, Any]] = None, crisis_detection: Optional[Dict[str, Any]] = None) -> List[str]:
    questions: List[str] = []
    emotion = (emotional_state or {}).get("primary", "")
    severity = int((emotional_state or {}).get("severity", 0) or 0)
    crisis_alert = bool((crisis_detection or {}).get("alert", False))
    if crisis_alert:
        questions.extend([
            "现在你身边有医护、家人，或者能立刻联系上的人吗？",
            "要不要先只做一件最小的事：告诉我你现在是否安全？",
            "如果你愿意，我们先一起做一个很短的呼吸练习，好吗？",
        ])
    elif emotion == "anxiety" or severity >= 7:
        questions.extend([
            "此刻最让你紧绷的那件事，是什么？",
            "要不要先把注意力放回呼吸，我陪你慢慢来？",
            "如果把担心拆成一小块，现在最想先处理哪一块？",
        ])
    elif emotion in {"sadness", "hopelessness"}:
        questions.extend([
            "这份难受现在最像什么感觉？你愿意多说一点吗？",
            "今天有没有哪怕一点点没那么糟的时刻？",
            "你现在更想被安静陪着，还是更想一起理一理这些感受？",
        ])
    elif psych_energy < 30:
        questions.extend([
            "今天感觉怎么样？有什么特别难受的地方吗？",
            "想聊聊现在最困扰你的事情吗？",
            "要不要一起做个放松练习？",
        ])
    elif psych_energy < 60:
        questions.extend([
            "今天心情怎么样？",
            "身体上有什么新的不适感吗？",
            "这两天有没有哪件事让你有一点点压力？",
        ])
    else:
        questions.extend([
            "今天有什么让你感到开心或踏实的事情吗？",
            "想分享一下最近的一个小进步吗？",
            "接下来你最想继续保持的状态是什么？",
        ])
    questions.append({
        "PRETREATMENT": "对即将开始的治疗，你现在最在意或最担心的是什么？",
        "TRANSPLANT": "现在的治疗过程中，哪一刻最辛苦、最需要陪伴？",
        "RECOVERY": "恢复过程中，你最近有没有感受到一点点新的变化？",
    }.get(stage, "对即将开始的治疗，你现在最在意或最担心的是什么？"))
    deduplicated, seen = [], set()
    for question in questions:
        if question not in seen:
            seen.add(question)
            deduplicated.append(question)
    return deduplicated[:4]


def _optional_int(value: Any) -> Optional[int]:
    if value in (None, ""):
        return None
    return int(value)


def _json_safe(value: Any) -> Any:
    if isinstance(value, Enum):
        return value.value
    if isinstance(value, dict):
        return {str(key): _json_safe(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_json_safe(item) for item in value]
    if isinstance(value, tuple):
        return [_json_safe(item) for item in value]
    if value is None or isinstance(value, (str, int, float, bool)):
        return value
    return str(value)


def _stage_from_phase(phase: TransplantPhase) -> str:
    return {
        TransplantPhase.PREP: "PRETREATMENT",
        TransplantPhase.KEY: "TRANSPLANT",
        TransplantPhase.RECOVERY: "RECOVERY",
    }.get(phase, "PRETREATMENT")


def _coerce_phase(value: Any) -> TransplantPhase:
    raw = str(value or "").strip()
    if not raw:
        raise ValueError("phase 不能为空")

    aliases = {
        "PRETREATMENT": TransplantPhase.PREP,
        "PREP": TransplantPhase.PREP,
        "BEFORE": TransplantPhase.PREP,
        "TRANSPLANT": TransplantPhase.KEY,
        "KEY": TransplantPhase.KEY,
        "MIDDLE": TransplantPhase.KEY,
        "RECOVERY": TransplantPhase.RECOVERY,
        "POST": TransplantPhase.RECOVERY,
        "AFTER": TransplantPhase.RECOVERY,
    }
    upper = raw.upper()
    if upper in aliases:
        return aliases[upper]
    try:
        return TransplantPhase(raw)
    except Exception as exc:
        raise ValueError("phase 必须是 PRETREATMENT、TRANSPLANT、RECOVERY 或对应中文分期") from exc


def _persist_session_state(session) -> Dict[str, Any]:
    state = save_session_state(
        session_id=session.session_id,
        thread_id=session.thread_id,
        data_dir=session.data_dir,
        agent=session.agent,
    )
    return state.model_dump()


def _session_state_payload(session) -> Dict[str, Any]:
    agent = session.agent
    phase = agent.get_transplant_phase()
    history = agent.get_history()
    visible_history = [message for message in history if message.get("role") != "system"]
    runtime = agent._resolve_prompt_runtime()
    return {
        "sessionId": session.session_id,
        "userId": session.user_id,
        "safeUserId": sanitize_user_id(session.user_id),
        "threadId": session.thread_id,
        "dataDir": session.data_dir,
        "psychModelDir": session.psych_model_dir,
        "phase": phase.value,
        "stage": _stage_from_phase(phase),
        "userState": _json_safe(getattr(agent, "user_state", {}) or {}),
        "psychModel": {
            "memoryCore": _json_safe(getattr(agent, "memory_core", None)),
            "personalizationProfile": _json_safe(getattr(agent, "personalization_profile", {}) or {}),
        },
        "messageCount": len([message for message in visible_history if message.get("role") == "user"]),
        "historyCount": len(visible_history),
        "promptProfile": runtime.profile,
        "outputMode": runtime.output_mode,
        "promptProfileVersion": runtime.profile_version,
        "outputModeVersion": runtime.output_mode_version,
        "lastToolTrace": _json_safe(getattr(agent, "last_tool_trace", None)),
    }


def _now_iso() -> str:
    return datetime.now().replace(microsecond=0).isoformat()


def _strip_wrapping_quotes(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value


def _split_top_level(text: str, separator: str) -> List[str]:
    parts: List[str] = []
    depth = 0
    quote: Optional[str] = None
    start = 0
    escaped = False
    for index, char in enumerate(text):
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in {"'", '"'}:
            quote = char
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth < 0:
                return []
        elif char == separator and depth == 0:
            parts.append(text[start:index])
            start = index + 1
    if quote or depth != 0:
        return []
    parts.append(text[start:])
    return parts


def _parse_relaxed_json_value(text: str) -> Any:
    value = text.strip()
    if value.startswith("{") and value.endswith("}"):
        parsed = _parse_relaxed_json_object(value)
        if parsed is None:
            raise ValueError("invalid relaxed object")
        return parsed
    unquoted = _strip_wrapping_quotes(value).strip()
    lower = unquoted.lower()
    if lower == "true":
        return True
    if lower == "false":
        return False
    if lower == "null":
        return None
    try:
        return int(unquoted)
    except ValueError:
        pass
    try:
        return float(unquoted)
    except ValueError:
        return unquoted


def _parse_relaxed_json_object(text: str) -> Optional[Dict[str, Any]]:
    candidate = text.strip()
    if not (candidate.startswith("{") and candidate.endswith("}")):
        return None
    result: Dict[str, Any] = {}
    inner = candidate[1:-1].strip()
    if not inner:
        return result
    for part in _split_top_level(inner, ","):
        key_value = _split_top_level(part, ":")
        if len(key_value) < 2:
            return None
        key = _strip_wrapping_quotes(key_value[0]).strip()
        if not key:
            return None
        value = ":".join(key_value[1:])
        try:
            result[key] = _parse_relaxed_json_value(value)
        except ValueError:
            return None
    return result


def _json_body() -> Dict[str, Any]:
    data = request.get_json(silent=True)
    if isinstance(data, dict):
        return data
    raw_body = request.get_data(cache=True)
    if raw_body:
        for encoding in ("utf-8-sig", "gb18030"):
            try:
                text = raw_body.decode(encoding).strip()
            except UnicodeDecodeError:
                continue
            if len(text) >= 2 and text[0] == text[-1] == "'" and text[1:-1].lstrip().startswith("{"):
                text = text[1:-1].strip()
            try:
                parsed = json.loads(text)
            except json.JSONDecodeError:
                parsed = _parse_relaxed_json_object(text)
                if parsed is None:
                    continue
            if isinstance(parsed, dict):
                return parsed
            raise ValueError("请求体必须是 JSON 对象，不能是数组或字符串")
        logger.warning("无法解析 JSON 请求体: preview=%r hex=%s", raw_body[:200], raw_body[:200].hex())
        raise ValueError("请求体必须是有效的 JSON 对象，请检查引号、花括号和 Content-Type")
    return {}


def _request_user_id(data: Dict[str, Any], session_id: Optional[str] = None) -> str:
    patient_context = data.get("patientContext", {}) or {}
    if not isinstance(patient_context, dict):
        patient_context = {}
    user_id = (
        data.get("userId")
        or data.get("patientId")
        or patient_context.get("userId")
        or patient_context.get("patientId")
        or session_id
        or "default"
    )
    return str(user_id)


def _get_existing_session(session_id: str):
    if read_session_metadata(session_id) is None:
        return None
    return get_or_create_session(session_id)


def _analysis_status_for(agent, message: str) -> str:
    try:
        if not agent._should_start_background_analysis(message):
            return "not_required"
        return "pending" if getattr(agent, "_pending_analysis_task", None) else "completed"
    except Exception:
        return "unknown"


def _dict_or_empty(value: Any) -> Dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _first_non_empty(*values: Any) -> Optional[Any]:
    for value in values:
        if value is None:
            continue
        if isinstance(value, str) and not value.strip():
            continue
        return value
    return None


def _safe_int(value: Any, default: int) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _request_bool(value: Any, default: bool) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() not in {"0", "false", "no", "off", "none", "null"}


def _append_instruction(existing: Any, addition: str) -> str:
    parts = [str(existing or "").strip(), str(addition or "").strip()]
    return "\n".join(part for part in parts if part)


def _analysis_wait_seconds_from_request(data: Dict[str, Any]) -> float:
    inputs = _dict_or_empty(data.get("inputs"))
    wait_for_analysis = _first_non_empty(
        data.get("waitForAnalysis"),
        inputs.get("waitForAnalysis"),
        data.get("wait_for_analysis"),
        inputs.get("wait_for_analysis"),
    )
    if not _request_bool(wait_for_analysis, True):
        return 0.0
    configured = _first_non_empty(
        data.get("analysisWaitSeconds"),
        inputs.get("analysisWaitSeconds"),
        data.get("analysis_wait_seconds"),
        inputs.get("analysis_wait_seconds"),
        getattr(Config, "POST_STREAM_ANALYSIS_WAIT_SECONDS", 0),
    )
    try:
        return max(0.0, float(configured or 0))
    except (TypeError, ValueError):
        return max(0.0, float(getattr(Config, "POST_STREAM_ANALYSIS_WAIT_SECONDS", 0) or 0))


def _normalize_dify_chat_request(data: Dict[str, Any]) -> Dict[str, Any]:
    inputs = _dict_or_empty(data.get("inputs"))
    message = _first_non_empty(
        data.get("query"),
        data.get("message"),
        inputs.get("query"),
        inputs.get("message"),
    )
    if not message:
        raise ValueError("Dify 请求必须包含 query 或 message")

    user_id = _first_non_empty(
        data.get("user"),
        data.get("userId"),
        data.get("patientId"),
        inputs.get("user"),
        inputs.get("userId"),
        inputs.get("patientId"),
    )
    session_id = _first_non_empty(
        data.get("conversation_id"),
        data.get("conversationId"),
        data.get("sessionId"),
        inputs.get("conversation_id"),
        inputs.get("conversationId"),
        inputs.get("sessionId"),
    )
    if not session_id:
        if user_id:
            session_id = f"dify-{sanitize_user_id(str(user_id))}"
        else:
            raise ValueError("Dify 请求必须包含 conversation_id/sessionId，或至少包含 user/userId")
    if not user_id:
        user_id = session_id

    patient_context: Dict[str, Any] = {}
    patient_context.update(_dict_or_empty(inputs.get("patientContext")))
    patient_context.update(_dict_or_empty(data.get("patientContext")))
    for key in ("stage", "psychEnergy", "emotionalState", "patientId", "userId"):
        if key in inputs and key not in patient_context:
            patient_context[key] = inputs[key]
        if key in data:
            patient_context[key] = data[key]
    patient_aliases = {
        "psych_energy": "psychEnergy",
        "emotional_state": "emotionalState",
        "patient_id": "patientId",
        "user_id": "userId",
    }
    for raw_key, normalized_key in patient_aliases.items():
        if raw_key in inputs and normalized_key not in patient_context:
            patient_context[normalized_key] = inputs[raw_key]
        if raw_key in data:
            patient_context[normalized_key] = data[raw_key]
    patient_context["userId"] = str(user_id)

    prompt_config = _dict_or_empty(inputs.get("promptConfig"))
    prompt_config.update(_dict_or_empty(data.get("promptConfig")))
    for key in ("promptProfile", "profile", "outputMode", "mode", "systemPrompt", "extraInstructions"):
        if key in inputs and key not in prompt_config:
            prompt_config[key] = inputs[key]
        if key in data:
            prompt_config[key] = data[key]
    prompt_aliases = {
        "prompt_profile": "promptProfile",
        "output_mode": "outputMode",
        "system_prompt": "systemPrompt",
        "extra_instructions": "extraInstructions",
    }
    for raw_key, normalized_key in prompt_aliases.items():
        if raw_key in inputs and normalized_key not in prompt_config:
            prompt_config[normalized_key] = inputs[raw_key]
        if raw_key in data:
            prompt_config[normalized_key] = data[raw_key]

    response_style = _first_non_empty(
        inputs.get("responseStyle"),
        inputs.get("supportStyle"),
        inputs.get("replyStyle"),
        data.get("responseStyle"),
        data.get("supportStyle"),
        data.get("replyStyle"),
    )
    if response_style and "extraInstructions" not in prompt_config:
        prompt_config["extraInstructions"] = f"本轮 Dify 外层流程选择的回应风格：{response_style}。"

    workflow_context = _first_non_empty(
        inputs.get("workflowContext"),
        inputs.get("difyContext"),
        inputs.get("flowContext"),
        data.get("workflowContext"),
        data.get("difyContext"),
        data.get("flowContext"),
    )
    if workflow_context:
        prompt_config["extraInstructions"] = _append_instruction(
            prompt_config.get("extraInstructions"),
            f"[Dify 外层流程上下文] {str(workflow_context)[:1000]}",
        )

    normalized = {
        "sessionId": str(session_id),
        "userId": str(user_id),
        "message": str(message),
        "patientContext": patient_context,
        "promptConfig": prompt_config,
        "dify": {
            "conversationId": str(session_id),
            "user": str(user_id),
            "responseMode": data.get("response_mode") or data.get("responseMode"),
        },
    }
    for key in ("waitForAnalysis", "analysisWaitSeconds", "wait_for_analysis", "analysis_wait_seconds"):
        value = _first_non_empty(data.get(key), inputs.get(key))
        if value is not None:
            normalized[key] = value
    history = _first_non_empty(data.get("history"), inputs.get("history"))
    if isinstance(history, list):
        normalized["history"] = history
    return normalized


def _knowledge_tool_summary(tool_trace: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    if not isinstance(tool_trace, dict):
        return {}
    for tool in tool_trace.get("tools") or []:
        if isinstance(tool, dict) and tool.get("name") == "knowledge_retrieval":
            return tool
    return {}


def _dify_outputs_from_turn(turn: Dict[str, Any]) -> Dict[str, Any]:
    questions = list(turn.get("recommendedQuestions") or [])[:4]
    crisis = _dict_or_empty(turn.get("crisisAssessment"))
    alarm = _dict_or_empty(crisis.get("alarm"))
    energy = _dict_or_empty(turn.get("energyAssessment"))
    agent_meta = _dict_or_empty(turn.get("agentMeta"))
    session = _dict_or_empty(turn.get("session"))
    knowledge = _knowledge_tool_summary(_dict_or_empty(agent_meta.get("toolTrace")))
    should_notify = bool(crisis.get("crisisAlert") or alarm.get("shouldNotify"))
    crisis_level = str(crisis.get("crisisLevel") or alarm.get("level") or "none")
    if alarm.get("requiresImmediateAction") or crisis_level == "critical":
        next_action = "emergency_alert"
    elif should_notify:
        next_action = "alert_and_notify"
    elif questions:
        next_action = "show_recommended_questions"
    else:
        next_action = "answer_only"

    return {
        "answer": turn.get("reply", ""),
        "nextAction": next_action,
        "shouldNotify": should_notify,
        "crisisLevel": crisis_level,
        "crisisAction": crisis.get("action") or alarm.get("action"),
        "alarmTitle": alarm.get("title"),
        "alarmMessage": alarm.get("message"),
        "recommendedQuestions": questions,
        "question1": questions[0] if len(questions) > 0 else "",
        "question2": questions[1] if len(questions) > 1 else "",
        "question3": questions[2] if len(questions) > 2 else "",
        "question4": questions[3] if len(questions) > 3 else "",
        "energyDelta": energy.get("totalDelta", 0),
        "energyNote": energy.get("assessmentNote", ""),
        "sessionId": session.get("sessionId"),
        "userId": session.get("userId"),
        "sessionTitle": session.get("title"),
        "messageCount": session.get("messageCount"),
        "stage": agent_meta.get("stage"),
        "threadId": agent_meta.get("threadId"),
        "promptProfile": agent_meta.get("promptProfile"),
        "outputMode": agent_meta.get("outputMode"),
        "analysisStatus": agent_meta.get("analysisStatus"),
        "retrievalBackend": knowledge.get("retrievalBackend"),
        "knowledgeReason": knowledge.get("reason"),
        "knowledgeErrors": knowledge.get("errors", []),
        "knowledgeSearchMethod": knowledge.get("effectiveSearchMethod") or knowledge.get("scoringMode"),
        "knowledgeFallbackUsed": bool(knowledge.get("fallbackUsed", False)),
        "knowledgeMatchCount": knowledge.get("matchCount", 0),
        "knowledgeSources": knowledge.get("topSources", []),
    }


def _dify_session_context(session) -> Dict[str, Any]:
    state = _session_state_payload(session)
    profile = _dict_or_empty(state.get("psychModel", {}).get("personalizationProfile"))
    energy_report = _json_safe(session.agent.energy_model.get_energy_report())
    crisis_report = _json_safe(session.agent.crisis_module.get_crisis_history_report())
    metadata = read_session_metadata(session.session_id) or {}
    energy_level = _dict_or_empty(energy_report).get("current_level") or _dict_or_empty(energy_report).get("level")
    return {
        "sessionId": session.session_id,
        "userId": session.user_id,
        "threadId": session.thread_id,
        "sessionTitle": metadata.get("title"),
        "messageCount": state.get("messageCount", 0),
        "historyCount": state.get("historyCount", 0),
        "stage": state.get("stage"),
        "phase": state.get("phase"),
        "promptProfile": state.get("promptProfile"),
        "outputMode": state.get("outputMode"),
        "preferredName": profile.get("preferred_name"),
        "lastEmotion": profile.get("last_emotion"),
        "lastSeverity": profile.get("last_severity"),
        "mainConcerns": profile.get("current_main_concerns") or [],
        "supportPreferences": profile.get("support_preferences") or [],
        "effectiveStrategies": profile.get("effective_strategies") or [],
        "riskNotes": profile.get("risk_notes") or [],
        "energyLevel": energy_level,
        "energyReport": energy_report,
        "crisisReport": crisis_report,
        "lastToolTrace": state.get("lastToolTrace"),
        "nextAction": "show_context",
    }


def _normalize_dify_session_request(data: Dict[str, Any]) -> Dict[str, str]:
    inputs = _dict_or_empty(data.get("inputs"))
    user_id = _first_non_empty(
        data.get("user"),
        data.get("userId"),
        data.get("patientId"),
        inputs.get("user"),
        inputs.get("userId"),
        inputs.get("patientId"),
    )
    session_id = _first_non_empty(
        data.get("conversation_id"),
        data.get("conversationId"),
        data.get("sessionId"),
        inputs.get("conversation_id"),
        inputs.get("conversationId"),
        inputs.get("sessionId"),
    )
    if not session_id:
        if user_id:
            session_id = f"dify-{sanitize_user_id(str(user_id))}"
        else:
            raise ValueError("Dify 请求必须包含 conversation_id/sessionId，或至少包含 user/userId")
    if not user_id:
        user_id = session_id
    return {"sessionId": str(session_id), "userId": str(user_id)}


def _run_blocking_chat_turn(data: Dict[str, Any], source: str = "api") -> Dict[str, Any]:
    session_id = data.get("sessionId")
    patient_context = _dict_or_empty(data.get("patientContext"))
    message = data.get("message", "")
    if not session_id or not message:
        raise ValueError("sessionId 和 message 不能为空")

    session = get_or_create_session(session_id, user_id=_request_user_id(data, session_id))
    agent = session.agent
    with session.lock:
        stream_started_at = time.perf_counter()
        first_delta_at = None
        response_parts: List[str] = []
        runtime_meta = prepare_session_for_chat(session, data)
        stage = runtime_meta.get("stage", patient_context.get("stage", "PRETREATMENT"))

        for chunk in agent.stream_chat(message):
            plain_chunk = markdown_to_plain_text(chunk, strip=False)
            if first_delta_at is None and plain_chunk:
                first_delta_at = time.perf_counter()
            if plain_chunk:
                response_parts.append(plain_chunk)

        wait_seconds = _analysis_wait_seconds_from_request(data)
        analysis_wait_started_at = time.perf_counter()
        if wait_seconds > 0:
            agent.wait_for_background_analysis(wait_seconds)
        analysis_wait_ms = int((time.perf_counter() - analysis_wait_started_at) * 1000)

        result = agent.last_result or {}
        reply = markdown_to_plain_text(result.get("response") or "".join(response_parts))
        cbt_analysis = result.get("cbt_analysis", {})
        crisis_detection = result.get("crisis_detection", {})
        latency_ms = int((time.perf_counter() - stream_started_at) * 1000)
        first_delta_ms = int((first_delta_at - stream_started_at) * 1000) if first_delta_at else None
        session_meta = update_session_after_chat(
            session=session,
            user_message=message,
            stage=stage,
            prompt_meta=runtime_meta,
            save_state_sync=True,
        )
        if Config.AUTO_SAVE_PROGRESS:
            threading.Thread(target=agent.save_all_progress, daemon=True).start()

        return {
            "reply": reply,
            "energyAssessment": build_energy_assessment(result.get("energy_assessment")),
            "crisisAssessment": build_crisis_assessment(crisis_detection, cbt_analysis),
            "recommendedQuestions": generate_recommended_questions(
                stage,
                _safe_int(patient_context.get("psychEnergy", 50), 50),
                cbt_analysis.get("emotional_state", {}),
                crisis_detection,
            ),
            "agentMeta": {
                "model": Config.MODEL_NAME,
                "tokensUsed": 0,
                "latencyMs": latency_ms,
                "firstDeltaMs": first_delta_ms,
                "streamMode": f"{source}_blocking_model_first_background_analysis",
                "toolTrace": result.get("tool_trace"),
                "analysisStatus": _analysis_status_for(agent, message),
                "analysisWaitMs": analysis_wait_ms,
                "integration": {
                    "entrypoint": source,
                    "outerOrchestrator": "dify" if source == "dify" else None,
                    "innerOrchestrator": "langgraph" if getattr(Config, "AGENT_GRAPH_ENABLED", True) else "legacy_stream",
                    "threadId": runtime_meta.get("threadId"),
                },
                **runtime_meta,
            },
            "session": {
                "sessionId": session_meta.get("sessionId"),
                "userId": session_meta.get("userId"),
                "title": session_meta.get("title"),
                "messageCount": session_meta.get("messageCount"),
                "updatedAt": session_meta.get("updatedAt"),
            },
        }


def _warmup_rag_index() -> None:
    if not getattr(Config, "RAG_WARMUP_ON_START", True):
        return
    status = "configured" if should_use_dify_knowledge() and dify_knowledge_configured() else "not_configured"
    logger.info(
        "RAG warmup skipped: runtime RAG is Dify-only; File/ is not indexed locally. dify=%s",
        status,
    )


@app.route('/v1/psych/chat', methods=['POST'])
def psych_chat():
    try:
        data = _json_body()
        session_id = data.get("sessionId")
        patient_context = data.get("patientContext", {})
        message = data.get("message", "")
        if not session_id or not message:
            return jsonify({"error": "invalid_request", "message": "sessionId 和 message 不能为空"}), 400
        session = get_or_create_session(session_id, user_id=_request_user_id(data, session_id))
        agent = session.agent

        def generate_stream():
            try:
                with session.lock:
                    stream_started_at = time.perf_counter()
                    first_delta_at = None
                    runtime_meta = prepare_session_for_chat(session, data)
                    stage = runtime_meta.get("stage", patient_context.get("stage", "PRETREATMENT"))

                    yield build_sse_event("start", {
                        "sessionId": session_id,
                        "message": "model-first stream started",
                        **runtime_meta,
                    })
                    for chunk in agent.stream_chat(message):
                        plain_chunk = markdown_to_plain_text(chunk, strip=False)
                        if first_delta_at is None and plain_chunk:
                            first_delta_at = time.perf_counter()
                        if plain_chunk:
                            yield build_sse_event("delta", {"content": plain_chunk, "stage": "response"})

                    wait_seconds = float(getattr(Config, "POST_STREAM_ANALYSIS_WAIT_SECONDS", 0) or 0)
                    analysis_wait_started_at = time.perf_counter()
                    if wait_seconds > 0:
                        agent.wait_for_background_analysis(wait_seconds)
                    analysis_wait_ms = int((time.perf_counter() - analysis_wait_started_at) * 1000)
                    result = agent.last_result or {}
                    cbt_analysis = result.get("cbt_analysis", {})
                    crisis_detection = result.get("crisis_detection", {})
                    latency_ms = int((time.perf_counter() - stream_started_at) * 1000)
                    first_delta_ms = int((first_delta_at - stream_started_at) * 1000) if first_delta_at else None
                    response = {
                        "reply": markdown_to_plain_text(result.get("response", "")),
                        "energyAssessment": build_energy_assessment(result.get("energy_assessment")),
                        "crisisAssessment": build_crisis_assessment(crisis_detection, cbt_analysis),
                        "recommendedQuestions": generate_recommended_questions(stage, int(patient_context.get("psychEnergy", 50) or 50), cbt_analysis.get("emotional_state", {}), crisis_detection),
                        "agentMeta": {
                            "model": Config.MODEL_NAME,
                            "tokensUsed": 0,
                            "latencyMs": latency_ms,
                            "firstDeltaMs": first_delta_ms,
                            "streamMode": "model_first_background_analysis",
                            "toolTrace": result.get("tool_trace"),
                            "analysisStatus": _analysis_status_for(agent, message),
                            "analysisWaitMs": analysis_wait_ms,
                            **runtime_meta,
                        },
                    }
                    session_meta = update_session_after_chat(
                        session=session,
                        user_message=message,
                        stage=stage,
                        prompt_meta=runtime_meta,
                        save_state_sync=True,
                    )
                    response["session"] = {
                        "sessionId": session_meta.get("sessionId"),
                        "userId": session_meta.get("userId"),
                        "title": session_meta.get("title"),
                        "messageCount": session_meta.get("messageCount"),
                        "updatedAt": session_meta.get("updatedAt"),
                    }
                    if Config.AUTO_SAVE_PROGRESS:
                        threading.Thread(target=agent.save_all_progress, daemon=True).start()
                    yield build_sse_event("done", response)
            except Exception:
                logger.exception("处理流式请求时发生错误")
                yield build_sse_event("error", {"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE})

        return Response(stream_with_context(generate_stream()), mimetype='text/event-stream', headers={'Cache-Control': 'no-cache', 'X-Accel-Buffering': 'no'})
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("处理请求时发生错误")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/dify/chat', methods=['POST'])
def dify_chat():
    try:
        data = _json_body()
        normalized = _normalize_dify_chat_request(data)
        turn = _run_blocking_chat_turn(normalized, source="dify")
        dify_outputs = _dify_outputs_from_turn(turn)
        payload = {
            "answer": turn.get("reply", ""),
            "sessionId": turn.get("session", {}).get("sessionId"),
            "userId": turn.get("session", {}).get("userId"),
            "sessionTitle": dify_outputs.get("sessionTitle"),
            "recommendedQuestions": turn.get("recommendedQuestions", []),
            "nextAction": dify_outputs.get("nextAction"),
            "shouldNotify": dify_outputs.get("shouldNotify"),
            "crisisLevel": dify_outputs.get("crisisLevel"),
            "difyOutputs": dify_outputs,
            "metadata": turn,
        }
        return jsonify(_json_safe(payload)), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("处理 Dify 对话请求时发生错误")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/dify/openapi.yaml', methods=['GET'])
def dify_openapi_yaml():
    try:
        with open(DIFY_OPENAPI_FILE, "r", encoding="utf-8") as f:
            content = f.read()
    except FileNotFoundError:
        return jsonify({"error": "not_found", "message": "Dify OpenAPI 描述文件不存在"}), 404
    return Response(content, content_type="application/yaml; charset=utf-8")


@app.route('/v1/dify/status', methods=['GET'])
def dify_status():
    return jsonify({"dify": _json_safe(dify_replacement_status())}), 200


@app.route('/v1/dify/options', methods=['GET'])
def dify_options():
    registry = get_prompt_registry_snapshot(include_history=False)
    profiles = registry.get("profiles", {}) if isinstance(registry, dict) else {}
    output_modes = registry.get("outputModes", {}) if isinstance(registry, dict) else {}
    return jsonify({
        "dify": _json_safe(dify_replacement_status()),
        "stages": [
            {"value": "PRETREATMENT", "label": "移植前准备期"},
            {"value": "TRANSPLANT", "label": "移植中关键期"},
            {"value": "RECOVERY", "label": "移植后恢复期"},
        ],
        "promptProfiles": [
            {
                "key": key,
                "version": value.get("version"),
                "description": value.get("description", ""),
                "builtin": bool(value.get("builtin", False)),
            }
            for key, value in profiles.items()
        ],
        "outputModes": [
            {
                "key": key,
                "version": value.get("version"),
                "description": value.get("description", ""),
                "builtin": bool(value.get("builtin", False)),
            }
            for key, value in output_modes.items()
        ],
        "acceptedInputs": [
            "query",
            "conversation_id",
            "user",
            "inputs.stage",
            "inputs.psychEnergy",
            "inputs.promptProfile",
            "inputs.outputMode",
            "inputs.extraInstructions",
            "inputs.responseStyle",
            "inputs.workflowContext",
        ],
        "difyOutputFields": [
            "answer",
            "nextAction",
            "shouldNotify",
            "crisisLevel",
            "recommendedQuestions",
            "question1",
            "question2",
            "question3",
            "question4",
            "sessionTitle",
            "retrievalBackend",
            "knowledgeMatchCount",
        ],
    }), 200


@app.route('/v1/dify/recommendations', methods=['POST'])
def dify_recommendations():
    try:
        data = _json_body()
        inputs = _dict_or_empty(data.get("inputs"))
        patient_context = {}
        patient_context.update(_dict_or_empty(inputs.get("patientContext")))
        patient_context.update(_dict_or_empty(data.get("patientContext")))
        for key in ("stage", "psychEnergy", "emotionalState", "crisisDetection"):
            if key in inputs and key not in patient_context:
                patient_context[key] = inputs[key]
            if key in data:
                patient_context[key] = data[key]
        questions = generate_recommended_questions(
            patient_context.get("stage", "PRETREATMENT"),
            _safe_int(patient_context.get("psychEnergy", 50), 50),
            _dict_or_empty(patient_context.get("emotionalState")),
            _dict_or_empty(patient_context.get("crisisDetection")),
        )
        output = {
            "recommendedQuestions": questions,
            "question1": questions[0] if len(questions) > 0 else "",
            "question2": questions[1] if len(questions) > 1 else "",
            "question3": questions[2] if len(questions) > 2 else "",
            "question4": questions[3] if len(questions) > 3 else "",
            "nextAction": "show_recommended_questions" if questions else "answer_only",
        }
        return jsonify({"questions": questions, "difyOutputs": output}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("处理 Dify 推荐问题请求时发生错误")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/dify/context', methods=['POST'])
def dify_context():
    try:
        data = _json_body()
        identity = _normalize_dify_session_request(data)
        session = get_or_create_session(identity["sessionId"], user_id=identity["userId"])
        with session.lock:
            context = _dify_session_context(session)
        return jsonify({"difyContext": _json_safe(context)}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("处理 Dify 上下文请求时发生错误")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/dify/grounding', methods=['POST'])
def dify_grounding():
    try:
        data = _json_body()
        identity = _normalize_dify_session_request(data)
        session = get_or_create_session(identity["sessionId"], user_id=identity["userId"])
        with session.lock:
            exercise = session.agent.get_grounding_exercise()
            output = {
                "exercise": exercise,
                "nextAction": "show_grounding_exercise",
                "sessionId": session.session_id,
                "userId": session.user_id,
                "threadId": session.thread_id,
            }
        return jsonify({"difyOutputs": _json_safe(output)}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("处理 Dify 接地练习请求时发生错误")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/psych/recommendations', methods=['POST'])
def psych_recommendations():
    try:
        data = _json_body()
        patient_context = data.get("patientContext", {})
        return jsonify({
            "questions": generate_recommended_questions(
                patient_context.get("stage", "PRETREATMENT"),
                int(patient_context.get("psychEnergy", 50) or 50),
                patient_context.get("emotionalState", {}),
                patient_context.get("crisisDetection", {}),
            )
        }), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("处理推荐请求时发生错误")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/psych/analyze', methods=['POST'])
def psych_analyze():
    try:
        data = _json_body()
        session_id = data.get("sessionId")
        message = str(data.get("message", "") or "").strip()
        if not session_id or not message:
            return jsonify({"error": "invalid_request", "message": "sessionId 和 message 不能为空"}), 400

        session = get_or_create_session(session_id, user_id=_request_user_id(data, session_id))
        with session.lock:
            patient_context = data.get("patientContext", {}) or {}
            raw_phase = data.get("phase") or patient_context.get("phase") or patient_context.get("stage")
            phase = _coerce_phase(raw_phase) if raw_phase else session.agent.get_transplant_phase()
            runtime = session.agent._resolve_prompt_runtime()
            runtime_meta = {
                "sessionId": session.session_id,
                "userId": session.user_id,
                "threadId": session.thread_id,
                "psychModelDir": session.psych_model_dir,
                "stage": _stage_from_phase(phase),
                "promptProfile": runtime.profile,
                "outputMode": runtime.output_mode,
                "promptProfileVersion": runtime.profile_version,
                "outputModeVersion": runtime.output_mode_version,
            }
            analysis = session.agent._llm_unified_analyze(message, phase)
            if analysis is None:
                return jsonify({
                    "error": "analysis_failed",
                    "message": "结构化语义分析失败，请检查模型配置或稍后重试",
                    "agentMeta": runtime_meta,
                }), 502
            return jsonify({
                "sessionId": session_id,
                "userId": session.user_id,
                "stage": runtime_meta.get("stage"),
                "phase": phase.value,
                "analysis": _json_safe(analysis),
                "agentMeta": runtime_meta,
            }), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("结构化语义分析接口失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/knowledge/search', methods=['GET', 'POST'])
@app.route('/v1/rag/search', methods=['GET', 'POST'])
def knowledge_search():
    try:
        if request.method == "POST":
            data = _json_body()
            query = str(data.get("query", "") or "").strip()
            top_k = data.get("topK", data.get("top_k"))
        else:
            query = str(request.args.get("query", "") or "").strip()
            top_k = request.args.get("topK") or request.args.get("top_k")
        if not query:
            return jsonify({"error": "invalid_request", "message": "query 不能为空"}), 400
        try:
            top_k_value = _optional_int(top_k)
        except (TypeError, ValueError):
            return jsonify({"error": "invalid_request", "message": "topK 必须是整数"}), 400
        result = retrieve_knowledge(query, top_k=top_k_value)
        return jsonify({"query": query, "result": _json_safe(result)}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("知识库检索接口失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/mcp/services', methods=['GET'])
def mcp_services():
    return jsonify({
        "enabled": bool(getattr(Config, "MCP_SERVICES_ENABLED", True)),
        "timezone": getattr(Config, "MCP_TIMEZONE", "Asia/Shanghai"),
        "services": [
            {
                "name": "current_time",
                "description": "返回当前日期、时间、星期和时区，用于回答“现在几点/今天几号/星期几”。",
                "deterministic": True,
                "callsLLM": False,
            }
        ],
    }), 200


@app.route('/v1/mcp/invoke', methods=['GET', 'POST'])
def mcp_invoke():
    try:
        if request.method == "POST":
            data = _json_body()
            query = str(data.get("query", data.get("message", "")) or "").strip()
        else:
            query = str(request.args.get("query", request.args.get("message", "")) or "").strip()
        if not query:
            return jsonify({"error": "invalid_request", "message": "query 不能为空"}), 400
        return jsonify({"query": query, "result": _json_safe(invoke_mcp_services(query))}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("MCP service invoke failed")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/prompts', methods=['GET'])
def prompt_registry():
    include_history = request.args.get("includeHistory", "true").lower() != "false"
    return jsonify({
        "hotReload": True,
        "registry": get_prompt_registry_snapshot(include_history=include_history),
    }), 200


@app.route('/v1/prompts/reload', methods=['POST'])
def prompt_registry_reload():
    return jsonify({
        "hotReload": True,
        "registry": reload_prompt_registry(),
    }), 200


@app.route('/v1/prompts/settings', methods=['PATCH'])
def update_prompt_registry_settings():
    try:
        data = _json_body()
        settings = update_prompt_settings(
            default_profile=data.get("defaultProfile") if "defaultProfile" in data else None,
            default_output_mode=data.get("defaultOutputMode") if "defaultOutputMode" in data else None,
        )
        return jsonify({"settings": settings, "hotReload": True}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400


@app.route('/v1/prompts/profiles/<path:key>', methods=['GET'])
def get_prompt_profile(key: str):
    try:
        include_history = request.args.get("includeHistory", "true").lower() != "false"
        return jsonify({"profile": get_prompt_entry("profile", key, include_history=include_history)}), 200
    except KeyError as exc:
        return jsonify({"error": "not_found", "message": str(exc)}), 404


@app.route('/v1/prompts/profiles/<path:key>', methods=['PUT'])
def update_prompt_profile(key: str):
    try:
        data = _json_body()
        content = data.get("content", data.get("suffix", data.get("systemPromptSuffix", "")))
        entry = update_prompt_entry(
            kind="profile",
            key=key,
            content=content,
            description=data.get("description"),
            change_note=data.get("changeNote") or data.get("change_note"),
            metadata=data.get("metadata"),
        )
        return jsonify({"profile": entry, "hotReload": True}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400


@app.route('/v1/prompts/profiles/<path:key>/rollback', methods=['POST'])
def rollback_prompt_profile(key: str):
    try:
        data = _json_body()
        entry = rollback_prompt_entry("profile", key, int(data.get("version")))
        return jsonify({"profile": entry, "hotReload": True}), 200
    except KeyError as exc:
        return jsonify({"error": "not_found", "message": str(exc)}), 404
    except (TypeError, ValueError) as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400


@app.route('/v1/prompts/profiles/<path:key>', methods=['DELETE'])
def delete_prompt_profile(key: str):
    try:
        purge_history = request.args.get("purgeHistory", request.args.get("purge_history", "false")).lower() == "true"
        return jsonify(delete_prompt_entry("profile", key, purge_history=purge_history)), 200
    except KeyError as exc:
        return jsonify({"error": "not_found", "message": str(exc)}), 404


@app.route('/v1/prompts/output-modes/<path:key>', methods=['GET'])
def get_output_mode(key: str):
    try:
        include_history = request.args.get("includeHistory", "true").lower() != "false"
        return jsonify({"outputMode": get_prompt_entry("mode", key, include_history=include_history)}), 200
    except KeyError as exc:
        return jsonify({"error": "not_found", "message": str(exc)}), 404


@app.route('/v1/prompts/output-modes/<path:key>', methods=['PUT'])
def update_output_mode(key: str):
    try:
        data = _json_body()
        content = data.get("content", data.get("instruction", ""))
        entry = update_prompt_entry(
            kind="mode",
            key=key,
            content=content,
            description=data.get("description"),
            change_note=data.get("changeNote") or data.get("change_note"),
            metadata=data.get("metadata"),
        )
        return jsonify({"outputMode": entry, "hotReload": True}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400


@app.route('/v1/prompts/output-modes/<path:key>/rollback', methods=['POST'])
def rollback_output_mode(key: str):
    try:
        data = _json_body()
        entry = rollback_prompt_entry("mode", key, int(data.get("version")))
        return jsonify({"outputMode": entry, "hotReload": True}), 200
    except KeyError as exc:
        return jsonify({"error": "not_found", "message": str(exc)}), 404
    except (TypeError, ValueError) as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400


@app.route('/v1/prompts/output-modes/<path:key>', methods=['DELETE'])
def delete_output_mode(key: str):
    try:
        purge_history = request.args.get("purgeHistory", request.args.get("purge_history", "false")).lower() == "true"
        return jsonify(delete_prompt_entry("mode", key, purge_history=purge_history)), 200
    except KeyError as exc:
        return jsonify({"error": "not_found", "message": str(exc)}), 404


@app.route('/v1/prompts/compare', methods=['GET'])
def compare_prompts():
    try:
        result = compare_prompt_versions(
            kind=request.args.get("kind", "profile"),
            key=request.args.get("key", ""),
            from_version=_optional_int(request.args.get("fromVersion")),
            to_version=_optional_int(request.args.get("toVersion")),
        )
        return jsonify(result), 200
    except (KeyError, ValueError) as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400


def _preview_prompt_route(kind: Optional[str] = None, key: Optional[str] = None):
    try:
        data = _json_body()
        prompt_kind = kind or data.get("kind", "profile")
        prompt_key = key or data.get("key", "")
        content = data.get("candidateContent", data.get("content", ""))
        message = str(data.get("message", "") or "")
        preview = preview_prompt_candidate(
            kind=prompt_kind,
            key=prompt_key,
            candidate_content=content,
            description=data.get("description"),
            change_note=data.get("changeNote") or data.get("change_note"),
            metadata=data.get("metadata"),
            from_version=_optional_int(data.get("fromVersion")),
            base_system_prompt=data.get("baseSystemPrompt") or Config.SYSTEM_PROMPT,
            default_profile=Config.PROMPT_PROFILE,
            default_output_mode=Config.OUTPUT_MODE,
            prompt_profile=data.get("promptProfile") or data.get("profile"),
            output_mode=data.get("outputMode") or data.get("mode"),
            system_prompt_override=data.get("systemPrompt"),
            extra_instructions=data.get("extraInstructions"),
        )

        generate = bool(data.get("generate", bool(message)))
        current_messages = render_prompt_messages(preview["currentRuntime"], message)
        candidate_messages = render_prompt_messages(preview["candidateRuntime"], message)
        preview["current"] = {
            "runtime": preview.pop("currentRuntime"),
            "messages": current_messages,
        }
        preview["candidate"] = {
            "runtime": preview.pop("candidateRuntime"),
            "messages": candidate_messages,
        }

        if generate:
            if not message:
                return jsonify({"error": "invalid_request", "message": "message 不能为空"}), 400
            client = OpenAI(api_key=Config.API_KEY, base_url=Config.API_BASE_URL)
            model = data.get("model") or Config.MODEL_NAME
            temperature = float(data.get("temperature", Config.TEMPERATURE))
            max_tokens = int(data.get("maxTokens") or data.get("max_tokens") or 240)

            def generate_reply(messages: List[Dict[str, str]]) -> str:
                response = client.chat.completions.create(
                    model=model,
                    messages=messages,
                    temperature=temperature,
                    max_tokens=max_tokens,
                )
                return (response.choices[0].message.content or "").strip()

            with ThreadPoolExecutor(max_workers=2) as executor:
                current_future = executor.submit(generate_reply, current_messages)
                candidate_future = executor.submit(generate_reply, candidate_messages)
                preview["current"]["reply"] = current_future.result()
                preview["candidate"]["reply"] = candidate_future.result()
            preview["model"] = model

        apply_endpoint = (
            f"/v1/prompts/profiles/{prompt_key}"
            if preview.get("kind") == "profile"
            else f"/v1/prompts/output-modes/{prompt_key}"
        )
        preview["manualApply"] = {
            "kind": preview.get("kind"),
            "endpoint": apply_endpoint,
            "method": "PUT",
            "body": {
                "content": content,
                "description": data.get("description"),
                "changeNote": data.get("changeNote") or data.get("change_note"),
                "metadata": data.get("metadata"),
            },
        }
        return jsonify(preview), 200
    except (KeyError, ValueError, TypeError) as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("提示词预览失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/prompts/preview', methods=['POST'])
def preview_prompt_generic():
    return _preview_prompt_route()


@app.route('/v1/prompts/profiles/<path:key>/preview', methods=['POST'])
def preview_prompt_profile(key: str):
    return _preview_prompt_route(kind="profile", key=key)


@app.route('/v1/prompts/output-modes/<path:key>/preview', methods=['POST'])
def preview_prompt_output_mode(key: str):
    return _preview_prompt_route(kind="mode", key=key)


@app.route('/v1/sessions', methods=['GET'])
def sessions_list():
    return jsonify({"sessions": list_session_summaries()}), 200


@app.route('/v1/sessions', methods=['POST'])
def sessions_create():
    try:
        data = _json_body()
        session_id = data.get("sessionId") or f"session-{int(time.time() * 1000)}"
        metadata = create_session_metadata(
            session_id,
            title=data.get("title"),
            user_id=_request_user_id(data, session_id),
        )
        return jsonify({"session": metadata}), 201
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400


@app.route('/v1/sessions/<path:session_id>/history', methods=['GET'])
def session_history(session_id: str):
    try:
        include_system = request.args.get("includeSystem", "false").lower() == "true"
        history = get_session_history(session_id, include_system=include_system)
        if history is None:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        return jsonify({
            "sessionId": session_id,
            "messageCount": len([message for message in history if message.get("role") == "user"]),
            "historyCount": len(history),
            "includeSystem": include_system,
            "history": history,
        }), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400


@app.route('/v1/sessions/<path:session_id>/auto-name', methods=['POST'])
def session_auto_name(session_id: str):
    try:
        data = _json_body()
        metadata = auto_name_session(session_id, message=data.get("message"))
        if not metadata:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        return jsonify({"session": metadata}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400


@app.route('/v1/sessions/<path:session_id>/state', methods=['GET'])
def session_state(session_id: str):
    try:
        session = _get_existing_session(session_id)
        if not session:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        with session.lock:
            return jsonify({"state": _session_state_payload(session)}), 200
    except Exception:
        logger.exception("读取会话状态失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/sessions/<path:session_id>/psych-model', methods=['GET'])
def session_psych_model(session_id: str):
    try:
        session = _get_existing_session(session_id)
        if not session:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        with session.lock:
            payload = build_agent_psych_model_payload(
                session.agent,
                session_id=session.session_id,
                thread_id=session.thread_id,
            )
            return jsonify({"psychModel": _json_safe(payload)}), 200
    except Exception:
        logger.exception("读取会话心理模型失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/sessions/<path:session_id>/state', methods=['PATCH'])
@app.route('/v1/sessions/<path:session_id>/phase', methods=['PATCH'])
def session_state_update(session_id: str):
    try:
        data = _json_body()
        raw_phase = data.get("phase", data.get("stage"))
        if raw_phase in (None, ""):
            return jsonify({"error": "invalid_request", "message": "phase 或 stage 不能为空"}), 400
        phase = _coerce_phase(raw_phase)
        session = _get_existing_session(session_id)
        if not session:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        with session.lock:
            session.agent.set_transplant_phase(phase)
            persisted = _persist_session_state(session)
            metadata = write_session_metadata(
                session_id,
                updates={
                    "stage": _stage_from_phase(phase),
                    "updatedAt": _now_iso(),
                },
            )
            return jsonify({
                "state": _session_state_payload(session),
                "session": metadata,
                "persistedState": persisted,
            }), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("更新会话状态失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/sessions/<path:session_id>/phase', methods=['GET'])
def session_phase(session_id: str):
    try:
        session = _get_existing_session(session_id)
        if not session:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        with session.lock:
            phase = session.agent.get_transplant_phase()
            return jsonify({
                "sessionId": session_id,
                "phase": phase.value,
                "stage": _stage_from_phase(phase),
            }), 200
    except Exception:
        logger.exception("读取会话分期失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/sessions/<path:session_id>/energy', methods=['GET'])
def session_energy(session_id: str):
    try:
        session = _get_existing_session(session_id)
        if not session:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        with session.lock:
            return jsonify({
                "sessionId": session_id,
                "energyReport": _json_safe(session.agent.energy_model.get_energy_report()),
            }), 200
    except Exception:
        logger.exception("读取心理能量报告失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/sessions/<path:session_id>/achievements', methods=['GET'])
def session_achievements(session_id: str):
    try:
        session = _get_existing_session(session_id)
        if not session:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        with session.lock:
            energy_model = session.agent.energy_model
            return jsonify({
                "sessionId": session_id,
                "stats": _json_safe(energy_model.get_achievement_stats()),
                "byCategory": _json_safe(energy_model.get_achievements_by_category()),
            }), 200
    except Exception:
        logger.exception("读取成就信息失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/sessions/<path:session_id>/progress', methods=['GET'])
def session_progress(session_id: str):
    try:
        session = _get_existing_session(session_id)
        if not session:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        with session.lock:
            return jsonify({
                "sessionId": session_id,
                "report": _json_safe(session.agent.get_comprehensive_report()),
            }), 200
    except Exception:
        logger.exception("读取综合进度报告失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/sessions/<path:session_id>/crisis-report', methods=['GET'])
def session_crisis_report(session_id: str):
    try:
        session = _get_existing_session(session_id)
        if not session:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        with session.lock:
            return jsonify({
                "sessionId": session_id,
                "crisisReport": _json_safe(session.agent.crisis_module.get_crisis_history_report()),
            }), 200
    except Exception:
        logger.exception("读取危机记录失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/sessions/<path:session_id>/grounding', methods=['GET', 'POST'])
def session_grounding(session_id: str):
    try:
        session = _get_existing_session(session_id)
        if not session:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        with session.lock:
            exercise = session.agent.get_grounding_exercise()
            if request.method == "GET":
                return jsonify({
                    "sessionId": session_id,
                    "exercise": exercise,
                    "recorded": False,
                }), 200

            data = _json_body()
            record_raw = data.get("record", True)
            should_record = record_raw if isinstance(record_raw, bool) else str(record_raw).lower() not in {"false", "0", "no"}
            energy_assessment = None
            if should_record and getattr(Config, "ENERGY_MODEL_ENABLED", True):
                conversation_data = {
                    "user_message": data.get("message") or "我进行了正念接地练习",
                    "analysis": {
                        "emotional_state": {"primary": "calm", "severity": 2},
                        "cognitive_distortions": [],
                        "recommended_technique": "MINDFULNESS",
                    },
                    "cbt_response": "完成了一次正念接地练习。",
                }
                energy_assessment = session.agent.energy_model.apply_llm_assessment(conversation_data, {
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
                if Config.AUTO_SAVE_PROGRESS:
                    session.agent.energy_model.save_progress()
                _persist_session_state(session)

            return jsonify({
                "sessionId": session_id,
                "exercise": exercise,
                "recorded": should_record,
                "energyAssessment": _json_safe(energy_assessment),
                "energyReport": _json_safe(session.agent.energy_model.get_energy_report()),
            }), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("接地练习接口失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/sessions/<path:session_id>/save', methods=['POST'])
def session_save(session_id: str):
    try:
        session = _get_existing_session(session_id)
        if not session:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        with session.lock:
            session.agent.save_all_progress()
            persisted = _persist_session_state(session)
            metadata = write_session_metadata(
                session_id,
                updates={
                    "updatedAt": _now_iso(),
                    "messageCount": len([
                        message for message in session.agent.get_history()
                        if message.get("role") == "user"
                    ]),
                    "stage": _stage_from_phase(session.agent.get_transplant_phase()),
                },
            )
            return jsonify({
                "saved": True,
                "session": metadata,
                "state": _json_safe(persisted),
            }), 200
    except Exception:
        logger.exception("保存会话进度失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/sessions/<path:session_id>/reset', methods=['POST'])
def session_reset(session_id: str):
    try:
        session = _get_existing_session(session_id)
        if not session:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        with session.lock:
            result = session.agent.reset()
            persisted = _persist_session_state(session)
            metadata = write_session_metadata(
                session_id,
                updates={
                    "updatedAt": _now_iso(),
                    "lastMessageAt": None,
                    "messageCount": 0,
                    "stage": "PRETREATMENT",
                },
            )
            return jsonify({
                "sessionId": session_id,
                "reset": _json_safe(result),
                "session": metadata,
                "state": _json_safe(persisted),
            }), 200
    except Exception:
        logger.exception("重置会话失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/sessions/<path:session_id>', methods=['GET'])
def session_detail(session_id: str):
    try:
        metadata = read_session_metadata(session_id)
        if not metadata:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        return jsonify({"session": metadata}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400


@app.route('/v1/sessions/<path:session_id>', methods=['PATCH'])
def session_update(session_id: str):
    try:
        data = _json_body()
        metadata = rename_session(session_id, data.get("title", ""))
        if not metadata:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        return jsonify({"session": metadata}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400


@app.route('/v1/sessions/<path:session_id>', methods=['DELETE'])
def session_delete(session_id: str):
    try:
        deleted = delete_session(session_id)
        if not deleted:
            return jsonify({"error": "not_found", "message": "会话不存在"}), 404
        return jsonify({"deleted": True, "sessionId": session_id}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400


@app.route('/v1/users/<path:user_id>/psych-model', methods=['GET'])
def user_psych_model(user_id: str):
    try:
        payload = read_user_psych_model(user_id)
        if payload is None:
            return jsonify({"error": "not_found", "message": "用户心理模型不存在"}), 404
        return jsonify({"psychModel": _json_safe(payload)}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("读取用户心理模型失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/users', methods=['GET'])
def users_list():
    try:
        return jsonify({"users": _json_safe(list_user_summaries())}), 200
    except Exception:
        logger.exception("读取用户列表失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/users/<path:user_id>/conversations', methods=['GET'])
@app.route('/v1/users/<path:user_id>/history', methods=['GET'])
def user_conversations(user_id: str):
    try:
        include_history = request.args.get("includeHistory", "false").lower() == "true"
        payload = list_user_conversations(user_id, include_history=include_history)
        if not payload.get("exists"):
            return jsonify({"error": "not_found", "message": "用户不存在"}), 404
        return jsonify({"userConversations": _json_safe(payload)}), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("读取用户会话历史失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/users/<path:user_id>', methods=['DELETE'])
def user_delete(user_id: str):
    try:
        result = delete_user(user_id)
        if not result.get("deleted"):
            return jsonify({"error": "not_found", "message": "用户不存在或没有可删除的数据", "result": result}), 404
        return jsonify(_json_safe(result)), 200
    except ValueError as exc:
        return jsonify({"error": "invalid_request", "message": str(exc)}), 400
    except Exception:
        logger.exception("删除用户失败")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/capabilities', methods=['GET'])
def capabilities():
    return jsonify({
        "chat": [
            "POST /v1/psych/chat",
            "POST /v1/dify/chat",
            "GET /v1/dify/openapi.yaml",
            "GET /v1/dify/status",
            "GET /v1/dify/options",
            "POST /v1/dify/recommendations",
            "POST /v1/dify/context",
            "POST /v1/dify/grounding",
            "GET /v1/mcp/services",
            "GET|POST /v1/mcp/invoke",
            "POST /v1/psych/recommendations",
            "POST /v1/psych/analyze",
        ],
        "mcp": [
            "GET /v1/mcp/services",
            "GET|POST /v1/mcp/invoke",
            "Services live in Code/xiaoya_agent/mcp_services/",
        ],
        "dify": [
            "POST /v1/dify/chat",
            "GET /v1/dify/openapi.yaml",
            "GET /v1/dify/status",
            "GET /v1/dify/options",
            "POST /v1/dify/recommendations",
            "POST /v1/dify/context",
            "POST /v1/dify/grounding",
            "Import docs/dify_openapi.yaml as a Dify custom tool schema",
        ],
        "rag": [
            "GET|POST /v1/knowledge/search",
            "GET|POST /v1/rag/search",
        ],
        "sessions": [
            "GET|POST /v1/sessions",
            "GET|PATCH|DELETE /v1/sessions/{sessionId}",
            "GET /v1/sessions/{sessionId}/history",
            "POST /v1/sessions/{sessionId}/auto-name",
            "GET|PATCH /v1/sessions/{sessionId}/state",
            "GET /v1/sessions/{sessionId}/psych-model",
            "GET|PATCH /v1/sessions/{sessionId}/phase",
            "GET /v1/sessions/{sessionId}/energy",
            "GET /v1/sessions/{sessionId}/achievements",
            "GET /v1/sessions/{sessionId}/progress",
            "GET /v1/sessions/{sessionId}/crisis-report",
            "GET|POST /v1/sessions/{sessionId}/grounding",
            "POST /v1/sessions/{sessionId}/save",
            "POST /v1/sessions/{sessionId}/reset",
        ],
        "users": [
            "GET /v1/users",
            "DELETE /v1/users/{userId}",
            "GET /v1/users/{userId}/psych-model",
            "GET /v1/users/{userId}/conversations",
            "GET /v1/users/{userId}/history",
        ],
        "prompts": [
            "GET /v1/prompts",
            "POST /v1/prompts/reload",
            "PATCH /v1/prompts/settings",
            "GET|PUT|DELETE /v1/prompts/profiles/{key}",
            "POST /v1/prompts/profiles/{key}/rollback",
            "POST /v1/prompts/profiles/{key}/preview",
            "GET|PUT|DELETE /v1/prompts/output-modes/{key}",
            "POST /v1/prompts/output-modes/{key}/rollback",
            "POST /v1/prompts/output-modes/{key}/preview",
            "GET /v1/prompts/compare",
            "POST /v1/prompts/preview",
        ],
    }), 200


@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        "status": "healthy",
        "service": "xinya-psych-agent",
        "version": "1.0.0",
        "jsonParser": "strict-plus-powershell-relaxed",
    }), 200


@app.errorhandler(404)
def not_found(error):
    return jsonify({"error": "not_found", "message": "请求的接口不存在"}), 404


@app.errorhandler(500)
def internal_error(error):
    return jsonify({"error": "internal_error", "message": "服务器内部错误"}), 500


def main():
    logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(name)s: %(message)s')
    print("=" * 60)
    print(" 小芽 Agent API 服务")
    print("=" * 60)
    print(f" 模型: {Config.MODEL_NAME}")
    print(f" API Base URL: {Config.API_BASE_URL}")
    print(" 可用接口:")
    print("   POST /v1/psych/chat - 心理陪护对话")
    print("   POST /v1/dify/chat - Dify 自定义工具对话入口")
    print("   GET  /v1/dify/openapi.yaml - Dify OpenAPI 工具描述")
    print("   GET  /v1/dify/status - Dify 替换状态")
    print("   GET  /v1/dify/options - Dify 可用变量与输出字段")
    print("   POST /v1/dify/recommendations - Dify 推荐问题输出")
    print("   POST /v1/dify/context - Dify 会话上下文")
    print("   POST /v1/dify/grounding - Dify 接地练习输出")
    print("   POST /v1/psych/recommendations - 推荐提问")
    print("   POST /v1/psych/analyze - 结构化语义分析")
    print("   GET  /v1/knowledge/search - RAG 检索调试")
    print("   GET  /v1/prompts - 提示词配置")
    print("   GET  /v1/sessions - 会话列表")
    print("   GET  /v1/capabilities - 用户功能接口清单")
    print("   GET  /health - 健康检查")
    print("=" * 60)
    print()
    _warmup_rag_index()
    app.run(host='0.0.0.0', port=8001, debug=False, threaded=True)


if __name__ == '__main__':
    main()
