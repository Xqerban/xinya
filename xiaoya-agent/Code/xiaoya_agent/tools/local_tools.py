"""LangGraph 单轮图和可选模型工具调用使用的本地工具。

这些工具都是确定性且较快的工具，不会调用大模型。把它们放进图里，
既能让首 token 路径保持轻量，也为后续模型主动调用工具预留结构。
"""
import json
from typing import Any, Dict, List, Optional

from langchain_core.tools import tool

from xiaoya_agent.config import Config
from xiaoya_agent.keywords.library import MEDICAL_RED_FLAG_KEYWORDS, contains_any
from xiaoya_agent.mcp_services import invoke_mcp_services, should_use_mcp_services
from xiaoya_agent.retrieval.rag import retrieve_knowledge
from xiaoya_agent.domain.transplant import (
    Scenario,
    TransplantPhase,
    detect_phase_from_text,
    detect_scenario,
    get_template,
)


def _matched_keywords(text: str, keywords: List[str]) -> List[str]:
    normalized_text = (text or "").lower()
    return [keyword for keyword in keywords if keyword.lower() in normalized_text]


def _coerce_phase(value: Any, fallback: TransplantPhase = TransplantPhase.PREP) -> TransplantPhase:
    if isinstance(value, TransplantPhase):
        return value
    try:
        return TransplantPhase(value)
    except Exception:
        return fallback


def _coerce_scenario(value: Any) -> Optional[Scenario]:
    if isinstance(value, Scenario):
        return value
    if not value:
        return None
    try:
        return Scenario(value)
    except Exception:
        return None


def should_use_knowledge_retrieval(text: str) -> bool:
    """控制 Dify RAG 的触发，避免普通情绪支持轮次承担检索开销。"""
    if not getattr(Config, "RAG_ENABLED", True):
        return False
    if not getattr(Config, "RAG_AUTO_TRIGGER_ENABLED", True):
        return True

    normalized = (text or "").strip().lower()
    if not normalized:
        return False

    generic_question_terms = [
        "?",
        "？",
        "是什么",
        "什么是",
        "是什么东西",
        "什么东西",
        "是啥",
        "啥是",
        "啥意思",
        "什么意思",
        "指什么",
        "指的是",
        "代表什么",
        "干嘛",
        "干什么",
        "做什么",
        "有啥",
        "哪来的",
        "哪里来的",
        "来源",
        "定义",
        "用途",
        "意思",
        "怎么",
        "如何",
        "为什么",
        "哪些",
        "区别",
        "含义",
        "解释",
        "介绍",
        "说明",
        "资料",
        "文档",
        "知识库",
        "注意事项",
        "流程",
        "方法",
        "标准",
        "查询",
        "查一下",
        "检索",
        "在资料里",
        "资料中",
        "文档中",
    ]
    if any(term in normalized for term in generic_question_terms):
        return True
    return False

@tool
def medical_red_flag_scan(text: str) -> Dict[str, Any]:
    """快速筛查移植病房身体红旗词，命中时应优先提示联系医护。"""
    matched = _matched_keywords(text, MEDICAL_RED_FLAG_KEYWORDS)
    return {
        "has_medical_red_flag": contains_any(text, MEDICAL_RED_FLAG_KEYWORDS),
        "matched_keywords": matched,
        "source": "local_tool",
    }


@tool
def transplant_context_lookup(
    text: str,
    current_phase: str,
    emotional_severity: int = 0,
) -> Dict[str, Any]:
    """根据当前文本查找骨髓移植分期、场景和可供主回复参考的话术模板。"""
    phase = _coerce_phase(current_phase)
    context: Dict[str, Any] = {
        "should_trigger": False,
        "phase": phase.value,
        "scenario": None,
        "template": None,
        "confidence": 0.0,
        "reason": "移植分期支持未启用",
        "source": "local_tool",
    }
    if not getattr(Config, "TRANSPLANT_SUPPORT_ENABLED", True):
        return context

    inferred_phase = detect_phase_from_text(text)
    phase = inferred_phase or phase
    scenario = detect_scenario(text, phase)
    context["phase"] = phase.value

    if not scenario:
        context["reason"] = "未命中移植场景"
        return context

    template = get_template(phase, scenario)
    if not template:
        context["reason"] = "命中场景但没有可用模板"
        context["scenario"] = scenario.value
        return context

    confidence = 0.65 + (0.1 if int(emotional_severity or 0) >= 6 else 0.0)
    context.update({
        "should_trigger": True,
        "scenario": scenario.value,
        "template": template,
        "confidence": min(confidence, 0.95),
        "reason": "本地工具命中移植场景",
    })
    return context


@tool
def conversation_state_snapshot(
    transplant_phase: str,
    history_length: int,
    has_memory_core: bool,
) -> Dict[str, Any]:
    """返回本轮对话可观测的轻量会话状态，供图节点追踪与调试。"""
    return {
        "transplant_phase": transplant_phase,
        "history_length": int(history_length or 0),
        "has_memory_core": bool(has_memory_core),
        "source": "local_tool",
    }


@tool
def knowledge_retrieval(query: str, top_k: int = 3) -> Dict[str, Any]:
    """检索 Dify 知识库中与用户问题相关的知识片段；File/ 不作为 RAG 来源。"""
    return retrieve_knowledge(query, top_k=top_k)


@tool
def mcp_service_router(query: str) -> Dict[str, Any]:
    """调用统一 MCP 风格服务层，处理当前时间等确定性实时信息。"""
    return invoke_mcp_services(query)


def get_agent_tools():
    """返回图编排使用的工具注册表。"""
    return [
        medical_red_flag_scan,
        transplant_context_lookup,
        conversation_state_snapshot,
        knowledge_retrieval,
        mcp_service_router,
    ]


def get_model_tool_definitions() -> List[Dict[str, Any]]:
    """返回兼容 OpenAI 函数调用的可选模型工具定义。"""
    return [
        {
            "type": "function",
            "function": {
                "name": "knowledge_retrieval",
                "description": (
                    "当用户询问资料、知识库、项目术语、测试词、希望之树、蓝色纸鹤，"
                    "或需要外部资料支撑的事实问题时调用。检索 Dify 知识库中与用户问题相关的知识片段；"
                    "File/ 不作为 RAG 来源。"
                ),
                "parameters": {
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "需要检索的用户问题或关键词。",
                        },
                        "top_k": {
                            "type": "integer",
                            "description": "最多返回的资料片段数。",
                            "minimum": 1,
                            "maximum": 5,
                        },
                    },
                    "required": ["query"],
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "mcp_service_router",
                "description": (
                    "当用户询问当前时间、日期、星期几等实时事实时调用。"
                    "该工具返回确定性结果，不调用大模型；不要自行猜测当前时间。"
                ),
                "parameters": {
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "用户关于当前时间、日期等实时事实的问题。",
                        },
                    },
                    "required": ["query"],
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "transplant_context_lookup",
                "description": (
                    "当用户表达骨髓移植治疗过程、回输、预处理、血象波动、出院恢复等场景，"
                    "且需要识别移植分期或获取病房陪伴话术素材时调用；不调用大模型。"
                ),
                "parameters": {
                    "type": "object",
                    "properties": {
                        "text": {
                            "type": "string",
                            "description": "用户原话。",
                        },
                        "current_phase": {
                            "type": "string",
                            "description": "当前系统记录的移植阶段。",
                        },
                        "emotional_severity": {
                            "type": "integer",
                            "description": "情绪痛苦强度，0 到 10。",
                            "minimum": 0,
                            "maximum": 10,
                        },
                    },
                    "required": ["text"],
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "medical_red_flag_scan",
                "description": (
                    "当用户描述胸痛、喘不过气、发热、出血、意识异常等身体安全风险时调用。"
                    "该工具只做快速身体红旗筛查，命中时应优先联系医护；不调用大模型。"
                ),
                "parameters": {
                    "type": "object",
                    "properties": {
                        "text": {
                            "type": "string",
                            "description": "用户原话。",
                        },
                    },
                    "required": ["text"],
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "conversation_state_snapshot",
                "description": "当回答需要了解当前移植阶段、历史长度或是否已有记忆摘要时调用；不调用大模型。",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "transplant_phase": {
                            "type": "string",
                            "description": "当前移植阶段。",
                        },
                        "history_length": {
                            "type": "integer",
                            "description": "当前会话历史条数。",
                        },
                        "has_memory_core": {
                            "type": "boolean",
                            "description": "是否已有记忆中枢摘要。",
                        },
                    },
                    "required": [],
                },
            },
        },
    ]


def parse_tool_arguments(raw_arguments: Any) -> Dict[str, Any]:
    """防御性解析模型返回的工具参数。"""
    if isinstance(raw_arguments, dict):
        return raw_arguments
    if not raw_arguments:
        return {}
    try:
        parsed = json.loads(raw_arguments)
    except Exception:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def execute_model_tool_call(
    tool_name: str,
    arguments: Dict[str, Any],
    agent: Any = None,
    user_message: str = "",
) -> Dict[str, Any]:
    """执行模型请求调用且被允许的本地工具。"""
    arguments = dict(arguments or {})

    if tool_name == "knowledge_retrieval":
        query = arguments.get("query") or user_message
        top_k = int(arguments.get("top_k") or getattr(Config, "RAG_TOP_K", 3) or 3)
        return knowledge_retrieval.invoke({"query": query, "top_k": top_k})

    if tool_name == "mcp_service_router":
        return mcp_service_router.invoke({"query": arguments.get("query") or user_message})

    if tool_name == "transplant_context_lookup":
        current_phase = arguments.get("current_phase")
        if not current_phase and agent is not None:
            current_phase = agent.get_transplant_phase().value
        return transplant_context_lookup.invoke({
            "text": arguments.get("text") or user_message,
            "current_phase": current_phase or TransplantPhase.PREP.value,
            "emotional_severity": int(arguments.get("emotional_severity") or 0),
        })

    if tool_name == "medical_red_flag_scan":
        return medical_red_flag_scan.invoke({"text": arguments.get("text") or user_message})

    if tool_name == "conversation_state_snapshot":
        phase = arguments.get("transplant_phase")
        if not phase and agent is not None:
            phase = agent.get_transplant_phase().value
        history_length = arguments.get("history_length")
        if history_length is None and agent is not None:
            history_length = len(getattr(agent, "conversation_history", []) or [])
        has_memory_core = arguments.get("has_memory_core")
        if has_memory_core is None and agent is not None:
            has_memory_core = bool(getattr(agent, "memory_core", None))
        return conversation_state_snapshot.invoke({
            "transplant_phase": phase or TransplantPhase.PREP.value,
            "history_length": int(history_length or 0),
            "has_memory_core": bool(has_memory_core),
        })

    return {
        "error": "unknown_tool",
        "tool": tool_name,
    }


def summarize_tool_output(
    tool_name: str,
    result: Dict[str, Any],
    source: str = "langgraph_prepare_turn",
    arguments: Optional[Dict[str, Any]] = None,
    tool_call_id: Optional[str] = None,
) -> Dict[str, Any]:
    """构建紧凑且可序列化的工具追踪信息，用于日志和 API 元数据。"""
    result = result or {}
    summary: Dict[str, Any] = {
        "name": tool_name,
        "source": source,
        "ok": "error" not in result,
    }
    if tool_call_id:
        summary["toolCallId"] = tool_call_id
    if arguments:
        summary["arguments"] = arguments

    if tool_name == "knowledge_retrieval":
        matches = result.get("matches") or []
        summary.update({
            "matchCount": len(matches),
            "hasContext": bool(result.get("context")),
            "reason": result.get("reason"),
            "scoringMode": result.get("scoringMode"),
            "effectiveSearchMethod": result.get("effectiveSearchMethod"),
            "retrievalBackend": result.get("retrievalBackend"),
            "semanticEnabled": result.get("semanticEnabled"),
            "fallbackUsed": bool(result.get("fallbackUsed", False)),
            "errors": list(result.get("errors") or [])[:3],
            "topSources": [
                {
                    "source": match.get("source"),
                    "score": match.get("score"),
                }
                for match in matches[:3]
            ],
        })
    elif tool_name == "mcp_service_router":
        services = list(result.get("services") or [])
        summary.update({
            "services": services,
            "serviceCount": len(services),
            "hasContext": bool(result.get("context")),
            "reason": result.get("reason"),
        })
    elif tool_name == "transplant_context_lookup":
        summary.update({
            "shouldTrigger": bool(result.get("should_trigger")),
            "phase": result.get("phase"),
            "scenario": result.get("scenario"),
            "confidence": result.get("confidence"),
        })
    elif tool_name == "medical_red_flag_scan":
        matched = result.get("matched_keywords") or []
        summary.update({
            "hasMedicalRedFlag": bool(result.get("has_medical_red_flag")),
            "matchedCount": len(matched),
        })
    elif tool_name == "conversation_state_snapshot":
        summary.update({
            "transplantPhase": result.get("transplant_phase"),
            "historyLength": result.get("history_length"),
            "hasMemoryCore": result.get("has_memory_core"),
        })
    elif "error" in result:
        summary["error"] = result.get("error")

    return summary


def summarize_tool_outputs(
    tool_outputs: Dict[str, Any],
    source: str = "langgraph_prepare_turn",
) -> Dict[str, Any]:
    """汇总工具输出映射，同时避免暴露过长的提示词上下文。"""
    tool_outputs = tool_outputs or {}
    tools = [
        summarize_tool_output(name, output, source=source)
        for name, output in tool_outputs.items()
    ]
    return {
        "source": source,
        "toolCount": len(tools),
        "tools": tools,
    }


def invoke_turn_tools(
    agent: Any,
    user_message: str,
    current_phase: TransplantPhase,
    analysis: Dict[str, Any],
) -> Dict[str, Any]:
    """在主流式模型启动前调用本轮需要的本地工具。"""
    emotional = (analysis or {}).get("emotional_state", {}) or {}
    emotional_severity = int(emotional.get("severity", 0) or 0)
    if should_use_knowledge_retrieval(user_message):
        knowledge_output = knowledge_retrieval.invoke({
            "query": user_message,
            "top_k": int(getattr(Config, "RAG_TOP_K", 3) or 3),
        })
    else:
        knowledge_output = {
            "enabled": bool(getattr(Config, "RAG_ENABLED", True)),
            "matches": [],
            "context": "",
            "reason": "auto_skipped_for_speed",
            "retrievalBackend": "dify",
            "fallbackUsed": False,
        }
    if should_use_mcp_services(user_message):
        mcp_output = mcp_service_router.invoke({"query": user_message})
    else:
        mcp_output = {
            "enabled": bool(getattr(Config, "MCP_SERVICES_ENABLED", True)),
            "services": [],
            "results": {},
            "context": "",
            "reason": "auto_skipped",
        }

    return {
        "medical_red_flag_scan": medical_red_flag_scan.invoke({"text": user_message}),
        "transplant_context_lookup": transplant_context_lookup.invoke({
            "text": user_message,
            "current_phase": current_phase.value,
            "emotional_severity": emotional_severity,
        }),
        "conversation_state_snapshot": conversation_state_snapshot.invoke({
            "transplant_phase": current_phase.value,
            "history_length": len(getattr(agent, "conversation_history", []) or []),
            "has_memory_core": bool(getattr(agent, "memory_core", None)),
        }),
        "knowledge_retrieval": knowledge_output,
        "mcp_service_router": mcp_output,
    }


def build_response_context_from_tool_outputs(
    tool_outputs: Dict[str, Any],
    default_phase: TransplantPhase,
) -> Dict[str, Any]:
    """把工具输出转换为智能体回复所需的 response_context 结构。"""
    transplant_context = (tool_outputs or {}).get("transplant_context_lookup", {}) or {}
    phase = _coerce_phase(transplant_context.get("phase"), fallback=default_phase)
    scenario = _coerce_scenario(transplant_context.get("scenario"))
    template = transplant_context.get("template") if transplant_context.get("should_trigger") else None
    knowledge = ((tool_outputs or {}).get("knowledge_retrieval") or {})
    mcp = ((tool_outputs or {}).get("mcp_service_router") or {})

    return {
        "phase": phase,
        "scenario": scenario,
        "template": template if scenario else None,
        "mcp_context": mcp.get("context", ""),
        "mcp_services": mcp.get("services", []),
        "knowledge_context": knowledge.get("context", ""),
        "knowledge_matches": knowledge.get("matches", []),
        "knowledge_backend": knowledge.get("retrievalBackend"),
        "knowledge_scoring_mode": knowledge.get("scoringMode"),
        "knowledge_effective_search_method": knowledge.get("effectiveSearchMethod"),
        "knowledge_reason": knowledge.get("reason"),
        "knowledge_errors": knowledge.get("errors", []),
    }
