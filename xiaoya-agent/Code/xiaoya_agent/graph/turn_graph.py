"""
LangGraph 编排层。

第一阶段迁移只把现有 stream_chat 前置流程节点化，仍复用 EnhancedChatAgent
里的回复生成、后台分析、记忆和持久化逻辑，避免一次性重写造成行为漂移。
"""
from functools import lru_cache
from typing import Any, Dict, Iterator, Optional, TypedDict

from langgraph.graph import END, START, StateGraph

from xiaoya_agent.tools.local_tools import build_response_context_from_tool_outputs, invoke_turn_tools
from xiaoya_agent.config import Config


class XiaoyaTurnState(TypedDict, total=False):
    agent: Any
    user_message: str
    current_phase: Any
    cbt_analysis: Dict[str, Any]
    crisis_detection: Dict[str, Any]
    response_context: Dict[str, Any]
    conversation_data: Dict[str, Any]
    local_tool_outputs: Dict[str, Any]
    analysis_task: Optional[Dict[str, Any]]
    safety_alert: Optional[Dict[str, Any]]
    response_type: str


def _prepare_turn(state: XiaoyaTurnState) -> Dict[str, Any]:
    agent = state["agent"]
    user_message = state["user_message"]

    current_phase = agent.get_transplant_phase()
    cbt_analysis = agent._pending_semantic_cbt_analysis()
    crisis_detection = agent._assess_crisis_for_stream(user_message, cbt_analysis)
    local_tool_outputs = {}
    model_decides_tools = bool(
        getattr(Config, "AGENT_TOOLS_ENABLED", True)
        and getattr(Config, "AGENT_MODEL_TOOL_CALLING_ENABLED", True)
    )
    if getattr(Config, "AGENT_TOOLS_ENABLED", True) and not model_decides_tools:
        local_tool_outputs = invoke_turn_tools(
            agent=agent,
            user_message=user_message,
            current_phase=current_phase,
            analysis=cbt_analysis,
        )
        response_context = build_response_context_from_tool_outputs(
            local_tool_outputs,
            default_phase=current_phase,
        )
    elif model_decides_tools:
        response_context = {
            "phase": current_phase,
            "scenario": None,
            "template": None,
        }
    else:
        response_context = agent._build_stream_response_context(
            user_message=user_message,
            current_phase=current_phase,
            analysis=cbt_analysis,
        )
    conversation_data = {
        "user_message": user_message,
        "analysis": cbt_analysis,
        "crisis_detection": crisis_detection,
        "local_tools": local_tool_outputs,
    }

    return {
        "current_phase": current_phase,
        "cbt_analysis": cbt_analysis,
        "crisis_detection": crisis_detection,
        "response_context": response_context,
        "conversation_data": conversation_data,
        "local_tool_outputs": local_tool_outputs,
    }


def _start_background_analysis(state: XiaoyaTurnState) -> Dict[str, Any]:
    agent = state["agent"]
    user_message = state["user_message"]
    analysis_task = None
    if (
        agent._background_analysis_start_mode() in {"before_stream", "parallel", "immediate"}
        and agent._should_start_background_analysis(user_message)
    ):
        analysis_task = agent._start_unified_analysis_task(
            user_message,
            state["current_phase"],
        )
    return {"analysis_task": analysis_task}


def _evaluate_safety(state: XiaoyaTurnState) -> Dict[str, Any]:
    agent = state["agent"]
    user_message = state["user_message"]
    cbt_analysis = state["cbt_analysis"]
    crisis_detection = dict(state["crisis_detection"])
    conversation_data = dict(state["conversation_data"])
    local_tool_outputs = state.get("local_tool_outputs") or {}
    medical_scan = local_tool_outputs.get("medical_red_flag_scan") or {}

    if getattr(Config, "MEDICAL_RED_FLAG_RULE_ENABLED", False) and medical_scan.get("has_medical_red_flag"):
        safety_alert = {
            "alert_type": "medical_red_flag",
            "crisis_level": "critical",
            "response_type": "medical_safety_alert",
            "notify": True,
            "response": (
                "我需要先提醒你：这种身体情况在移植病房里要优先让医护知道。"
                "请现在按床头呼叫铃，或请身边人马上联系护士/医生。"
                "先把身体安全稳住，你已经在正确地求助。"
            ),
        }
    else:
        safety_alert = agent._build_safety_alert(
            user_message,
            cbt_analysis,
            crisis_detection,
        )
    if not safety_alert:
        return {"safety_alert": None}

    crisis_detection = {
        **crisis_detection,
        "alert": True,
        "alert_type": safety_alert["alert_type"],
        "crisis_level": safety_alert.get("crisis_level"),
    }
    conversation_data["crisis_detection"] = crisis_detection
    if safety_alert.get("notify", False):
        alert_payload = {
            "alert": True,
            "alert_type": safety_alert["alert_type"],
            "crisis_level": safety_alert.get("crisis_level"),
            "severity_score": crisis_detection.get("severity_score"),
            "crisis_types": crisis_detection.get("crisis_types") or [],
        }
        agent.crisis_module._record_crisis_event(
            user_message,
            crisis_detection.get("severity_score"),
            alert_payload,
        )
        agent.crisis_module._trigger_alert(alert_payload)

    return {
        "safety_alert": safety_alert,
        "crisis_detection": crisis_detection,
        "conversation_data": conversation_data,
    }


def _apply_response_context(state: XiaoyaTurnState) -> Dict[str, Any]:
    agent = state["agent"]
    response_context = state.get("response_context") or {}
    current_phase = state["current_phase"]
    safety_alert = state.get("safety_alert")

    if not safety_alert and response_context.get("phase") and response_context["phase"] != current_phase:
        agent.set_transplant_phase(response_context["phase"])

    if safety_alert:
        response_type = safety_alert["response_type"]
    else:
        response_type = "cbt_response"

    return {"response_type": response_type}


@lru_cache(maxsize=1)
def build_turn_graph():
    builder = StateGraph(XiaoyaTurnState)
    builder.add_node("prepare_turn", _prepare_turn)
    builder.add_node("start_background_analysis", _start_background_analysis)
    builder.add_node("evaluate_safety", _evaluate_safety)
    builder.add_node("apply_response_context", _apply_response_context)

    builder.add_edge(START, "prepare_turn")
    builder.add_edge("prepare_turn", "start_background_analysis")
    builder.add_edge("start_background_analysis", "evaluate_safety")
    builder.add_edge("evaluate_safety", "apply_response_context")
    builder.add_edge("apply_response_context", END)
    return builder.compile()


def prepare_stream_turn(agent: Any, user_message: str) -> XiaoyaTurnState:
    graph = build_turn_graph()
    thread_id = getattr(agent, "graph_thread_id", "local") or "local"
    return graph.invoke({
        "agent": agent,
        "user_message": user_message,
    }, config={"configurable": {"thread_id": thread_id}})


def run_graph_stream(agent: Any, user_message: str) -> Iterator[str]:
    state = prepare_stream_turn(agent, user_message)
    safety_alert = state.get("safety_alert")

    if safety_alert:
        return agent._stream_static_response(
            user_message=user_message,
            response=safety_alert["response"],
            response_type=state["response_type"],
            cbt_analysis=state["cbt_analysis"],
            crisis_detection=state["crisis_detection"],
            conversation_data=state["conversation_data"],
            current_phase=state["current_phase"],
            analysis_task=state.get("analysis_task"),
            chunk_size=36,
        )

    return agent._stream_and_finalize_cbt_response(
        user_message=user_message,
        analysis=state["cbt_analysis"],
        crisis_detection=state["crisis_detection"],
        conversation_data=state["conversation_data"],
        response_type=state["response_type"],
        current_phase=state["current_phase"],
        response_context=state.get("response_context"),
        analysis_task=state.get("analysis_task"),
    )
