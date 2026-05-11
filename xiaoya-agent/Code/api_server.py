"""小芽 Agent API 服务"""
import json
import logging
import os
import re
import threading
import time
from dataclasses import dataclass, field
from typing import Dict, Any, Optional, List

from flask import Flask, request, jsonify, Response, stream_with_context

from simple_agent import EnhancedChatAgent
from config import Config
from transplant_support import TransplantPhase
from keyword_library import POSITIVE_EMOTION_LABELS
from response_formatting import markdown_to_plain_text

app = Flask(__name__)
logger = logging.getLogger(__name__)
SESSION_TTL_SECONDS = 21600
USER_FACING_ERROR_MESSAGE = "服务器暂时有点忙，请稍后再试。"


@dataclass
class SessionManager:
    agent: EnhancedChatAgent
    last_access: float = field(default_factory=time.time)
    lock: threading.RLock = field(default_factory=threading.RLock)


agent_sessions: Dict[str, SessionManager] = {}
agent_sessions_lock = threading.Lock()


def get_session_data_dir(session_id: str) -> str:
    safe_session_id = re.sub(r"[^0-9A-Za-z_.-]+", "_", str(session_id)).strip("._")[:80]
    if not safe_session_id:
        safe_session_id = "default"
    data_dir = os.path.abspath(os.path.join(Config.DATA_DIR, "sessions", safe_session_id))
    os.makedirs(data_dir, exist_ok=True)
    return data_dir


def cleanup_expired_sessions() -> None:
    now = time.time()
    expired_ids = []
    with agent_sessions_lock:
        for session_id, session in agent_sessions.items():
            if now - session.last_access > SESSION_TTL_SECONDS:
                expired_ids.append(session_id)
        for session_id in expired_ids:
            agent_sessions.pop(session_id, None)
    if expired_ids:
        logger.info("已清理 %s 个过期会话", len(expired_ids))


def get_or_create_session(session_id: str) -> SessionManager:
    cleanup_expired_sessions()
    with agent_sessions_lock:
        session = agent_sessions.get(session_id)
        if session is None:
            session = SessionManager(agent=EnhancedChatAgent(data_dir=get_session_data_dir(session_id)))
            agent_sessions[session_id] = session
        session.last_access = time.time()
        return session


def get_or_create_agent(session_id: str) -> EnhancedChatAgent:
    return get_or_create_session(session_id).agent


def map_stage_to_phase(stage: str) -> TransplantPhase:
    stage_mapping = {
        "PRETREATMENT": TransplantPhase.PREP,
        "TRANSPLANT": TransplantPhase.KEY,
        "RECOVERY": TransplantPhase.RECOVERY,
    }
    return stage_mapping.get(stage, TransplantPhase.PREP)


def rebuild_agent_history(agent: EnhancedChatAgent, history: List[Dict[str, str]]) -> None:
    rebuilt_history = [{"role": "system", "content": agent.system_prompt}]
    for msg in history:
        role = msg.get("role")
        content = msg.get("content", "")
        if role in {"user", "assistant"} and content:
            rebuilt_history.append({"role": role, "content": content})
    agent.conversation_history = rebuilt_history


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
    alert = crisis_detection.get("alert", False)
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
    if alert:
        alert_type = crisis_detection.get("alert_type", "crisis_signal")
        if alert_type == "medical_red_flag":
            crisis_level, action, crisis_keywords = "critical", "contact_medical_staff", ["身体红旗"]
        elif alert_type == "severe_emotional_distress":
            crisis_level, action, crisis_keywords = "warning", "notify_support", ["严重情绪痛苦"]
        else:
            crisis_level, action, crisis_keywords = "critical", "alert_and_notify", ["危机信号"]
    elif severity >= 7:
        crisis_level, action, crisis_keywords = "warning", "mindfulness_guide", []
    elif severity >= 5:
        crisis_level, action, crisis_keywords = "watch", "log_only", []
    else:
        crisis_level, action, crisis_keywords = "none", "none", []
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
        "crisisAlert": alert,
        "crisisLevel": crisis_level,
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


@app.route('/v1/psych/chat', methods=['POST'])
def psych_chat():
    try:
        data = request.get_json() or {}
        session_id = data.get("sessionId")
        patient_context = data.get("patientContext", {})
        history = data.get("history", [])
        message = data.get("message", "")
        if not session_id or not message:
            return jsonify({"error": "invalid_request", "message": "sessionId 和 message 不能为空"}), 400
        session = get_or_create_session(session_id)
        agent = session.agent
        stage = patient_context.get("stage", "PRETREATMENT")

        def generate_stream():
            try:
                with session.lock:
                    stream_started_at = time.perf_counter()
                    first_delta_at = None
                    phase = map_stage_to_phase(stage)
                    if agent.get_transplant_phase() != phase:
                        agent.set_transplant_phase(phase)
                    if history:
                        rebuild_agent_history(agent, history)

                    yield build_sse_event("start", {
                        "sessionId": session_id,
                        "message": "model-first stream started"
                    })
                    for chunk in agent.stream_chat(message):
                        plain_chunk = markdown_to_plain_text(chunk, strip=False)
                        if first_delta_at is None and plain_chunk:
                            first_delta_at = time.perf_counter()
                        if plain_chunk:
                            yield build_sse_event("delta", {"content": plain_chunk, "stage": "response"})

                    agent.wait_for_background_analysis(Config.POST_STREAM_ANALYSIS_WAIT_SECONDS)
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
                        },
                    }
                    if Config.AUTO_SAVE_PROGRESS:
                        threading.Thread(target=agent.save_all_progress, daemon=True).start()
                    yield build_sse_event("done", response)
            except Exception:
                logger.exception("处理流式请求时发生错误")
                yield build_sse_event("error", {"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE})

        return Response(stream_with_context(generate_stream()), mimetype='text/event-stream', headers={'Cache-Control': 'no-cache', 'X-Accel-Buffering': 'no'})
    except Exception:
        logger.exception("处理请求时发生错误")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/v1/psych/recommendations', methods=['POST'])
def psych_recommendations():
    try:
        data = request.get_json() or {}
        patient_context = data.get("patientContext", {})
        return jsonify({
            "questions": generate_recommended_questions(
                patient_context.get("stage", "PRETREATMENT"),
                int(patient_context.get("psychEnergy", 50) or 50),
                patient_context.get("emotionalState", {}),
                patient_context.get("crisisDetection", {}),
            )
        }), 200
    except Exception:
        logger.exception("处理推荐请求时发生错误")
        return jsonify({"error": "internal_error", "message": USER_FACING_ERROR_MESSAGE}), 500


@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({"status": "healthy", "service": "xinya-psych-agent", "version": "1.0.0"}), 200


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
    print("   POST /v1/psych/recommendations - 推荐提问")
    print("   GET  /health - 健康检查")
    print("=" * 60)
    print()
    app.run(host='0.0.0.0', port=8001, debug=False, threaded=True)


if __name__ == '__main__':
    main()
