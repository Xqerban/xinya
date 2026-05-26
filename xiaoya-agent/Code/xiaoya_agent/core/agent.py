"""
增强版对话智能体 - 集成CBT、心理能量和危机干预
"""
from openai import OpenAI
from typing import List, Dict, Optional, Iterator, Any, Tuple
import json
import os
import re
import threading
import logging
import time
from datetime import datetime
from xiaoya_agent.config import Config
from xiaoya_agent.database import database_storage_enabled, get_database_repository
from xiaoya_agent.features.cbt import CBTModule, CBTTechnique
from xiaoya_agent.features.energy import PsychologicalEnergyModel
from xiaoya_agent.features.crisis import CrisisInterventionModule, build_crisis_alarm
from xiaoya_agent.domain.transplant import (
    TransplantPhase,
    choose_intervention,
    get_template,
    Scenario,
    detect_phase_from_text,
    detect_scenario,
    TriggerResult,
)
from xiaoya_agent.prompts.runtime import resolve_prompt_runtime_config
from xiaoya_agent.tools.local_tools import (
    execute_model_tool_call,
    get_model_tool_definitions,
    parse_tool_arguments,
    summarize_tool_output,
    summarize_tool_outputs,
)
from xiaoya_agent.keywords.library import (
    CASUAL_INPUTS,
    MEDICAL_RED_FLAG_KEYWORDS,
    TRANSPLANT_SCENARIO_GUIDES,
    contains_any,
)
from xiaoya_agent.llm.structured import (
    UnifiedAnalysisPayload,
    create_chat_completion_json,
    parse_structured_json,
)
from xiaoya_agent.features.cohort_learning import (
    get_cohort_learning_context,
    mark_cohort_learning_dirty,
)
from xiaoya_agent.mcp_services import should_use_mcp_services

logger = logging.getLogger(__name__)
USER_FACING_ERROR_MESSAGE = "我刚刚有点卡住了，我们再试一次，好吗？"

class EnhancedChatAgent:
    """增强版对话智能体类 - 集成CBT、心理能量和危机干预"""

    PSYCH_MODEL_FILENAME = "psych_model.json"

    def __init__(
        self,
        data_dir: Optional[str] = None,
        load_persistent_data: bool = True,
        user_id: Optional[str] = None,
        psych_model_dir: Optional[str] = None,
    ):
        """初始化智能体"""
        Config.validate_config()
        self.data_dir = os.path.abspath(data_dir or Config.DATA_DIR)
        if not database_storage_enabled():
            os.makedirs(self.data_dir, exist_ok=True)
        self.user_id = user_id
        self.psych_model_enabled = bool(user_id or psych_model_dir)
        self.psych_model_dir = os.path.abspath(psych_model_dir or self.data_dir)
        if not database_storage_enabled():
            os.makedirs(self.psych_model_dir, exist_ok=True)

        self.client = OpenAI(
            api_key=Config.API_KEY,
            base_url=Config.API_BASE_URL
        )

        self.model = Config.MODEL_NAME
        self.temperature = Config.TEMPERATURE
        self.max_tokens = Config.MAX_TOKENS
        self.system_prompt = Config.SYSTEM_PROMPT
        self.prompt_profile = Config.PROMPT_PROFILE
        self.output_mode = Config.OUTPUT_MODE
        self.system_prompt_override: Optional[str] = None
        self.extra_prompt_instructions: Optional[str] = None
        self.graph_thread_id = "local"
        self.storage_session_id: Optional[str] = None
        self.storage_source: Optional[str] = None
        self.storage_conversation_id: Optional[str] = None
        self.last_result: Optional[Dict[str, Any]] = None
        self.last_tool_trace: Optional[Dict[str, Any]] = None
        self._pending_analysis_task: Optional[Dict[str, Any]] = None
        self._psych_model_lock = threading.RLock()

        # 对话历史
        self.conversation_history: List[Dict[str, str]] = [
            {"role": "system", "content": self.system_prompt}
        ]
        
        # 增量摘要：记忆中枢（每轮对话后更新）
        self.memory_core: Optional[str] = None
        self.personalization_profile: Dict[str, Any] = self._default_personalization_profile()

        # 用户状态（分期等），属于用户心理模型的一部分
        self.user_state: Dict[str, any] = {
            "transplant_phase": TransplantPhase.PREP.value
        }

        # 初始化增强模块
        self.cbt_module = CBTModule()
        self.energy_model = PsychologicalEnergyModel(data_dir=self.psych_model_dir)
        self.energy_model.user_id = self.user_id
        self.energy_model.safe_user_id = self._safe_storage_user_id()
        self.energy_model.psych_model_dir = self.psych_model_dir

        # 危机干预模块（带报警回调）
        self.crisis_module = CrisisInterventionModule(
            alert_callback=self._crisis_alert_callback,
            data_dir=self.psych_model_dir,
        )
        self.crisis_module.user_id = self.user_id
        self.crisis_module.safe_user_id = self._safe_storage_user_id()
        self.crisis_module.psych_model_dir = self.psych_model_dir

        # 加载历史数据
        if load_persistent_data:
            self._load_persistent_data()

    def configure_prompt_runtime(
        self,
        prompt_profile: Optional[str] = None,
        output_mode: Optional[str] = None,
        system_prompt: Optional[str] = None,
        extra_instructions: Optional[str] = None,
    ) -> None:
        """配置当前会话的提示词 profile 和输出模式。"""
        if prompt_profile:
            self.prompt_profile = prompt_profile
        if output_mode:
            self.output_mode = output_mode
        if system_prompt is not None:
            self.system_prompt_override = system_prompt.strip() or None
        if extra_instructions is not None:
            self.extra_prompt_instructions = extra_instructions.strip() or None

    def _resolve_prompt_runtime(self):
        return resolve_prompt_runtime_config(
            base_system_prompt=self.system_prompt,
            default_profile=getattr(Config, "PROMPT_PROFILE", "warm_cbt"),
            default_output_mode=getattr(Config, "OUTPUT_MODE", "brief_support"),
            prompt_profile=self.prompt_profile,
            output_mode=self.output_mode,
            system_prompt_override=self.system_prompt_override,
            extra_instructions=self.extra_prompt_instructions,
        )

    def _default_personalization_profile(self) -> Dict[str, Any]:
        return {
            "preferred_name": None,
            "communication_style": "warm_brief",
            "current_main_concerns": [],
            "recurring_emotions": {},
            "cognitive_patterns": [],
            "effective_strategies": [],
            "support_preferences": [],
            "risk_notes": [],
            "last_emotion": None,
            "last_severity": 0,
            "updated_turns": 0,
            "last_updated": None,
        }

    def _safe_storage_user_id(self) -> str:
        raw = str(self.user_id or "default")
        safe = re.sub(r"[^0-9A-Za-z_.-]+", "_", raw).strip("._")[:80]
        return safe or "default"

    def chat(self, user_message: str) -> Dict[str, any]:
        """
        增强版对话 - 集成CBT、能量评估和危机干预

        Args:
            user_message: 用户输入的消息

        Returns:
            包含回复和分析结果的字典
        """
        self.last_tool_trace = None

        # ===== 一次综合分析替代原来的三次独立 LLM 调用 =====
        current_phase = self.get_transplant_phase()
        unified = None
        if getattr(Config, "CBT_LLM_ENABLED", True) or getattr(Config, "CRISIS_LLM_DETECTION_ENABLED", True):
            unified = self._llm_unified_analyze(user_message, current_phase)

        # 1. CBT分析：优先使用综合分析结果，失败则降级到模块独立调用
        if unified is not None:
            cbt_analysis = {
                "emotional_state": unified["emotional_state"],
                "cognitive_distortions": unified["cognitive_distortions"],
                "problem_severity": unified["problem_severity"],
                "intervention_needed": unified["intervention_needed"],
                "recommended_technique": unified["recommended_technique"],
            }
        else:
            cbt_analysis = self.cbt_module.analyze_user_input(user_message)

        # 2. 危机检测：优先使用综合分析结果，失败则降级到模块独立调用
        if unified is not None:
            crisis_detection = self._crisis_detection_from_unified(unified)
            if crisis_detection.get("alert", False):
                self.crisis_module._record_crisis_event(
                    user_message,
                    crisis_detection.get("severity_score"),
                    crisis_detection,
                )
                self.crisis_module._trigger_alert(crisis_detection)
        else:
            crisis_detection = self.crisis_module.assess_crisis_semantic_only(
                user_message,
                cbt_analysis.get("emotional_state", {}),
                psych_model_context=self._build_crisis_assessment_context(),
            )
            if crisis_detection.get("alert", False):
                self.crisis_module._record_crisis_event(
                    user_message,
                    crisis_detection.get("severity_score"),
                    crisis_detection,
                )
                self.crisis_module._trigger_alert(crisis_detection)

        # 3. 准备对话数据
        conversation_data = {
            "user_message": user_message,
            "analysis": cbt_analysis,
            "crisis_detection": crisis_detection
        }

        # 4. 生成回复
        if crisis_detection.get("alert", False):
            # 对危机直接报警，不进行其他操作
            response = ""
            response_type = "crisis_alert"
        else:
            response = None
            response_type = None

            if getattr(Config, "TRANSPLANT_SUPPORT_ENABLED", True):
                # 移植情境：优先使用综合分析结果，失败则降级到模块独立调用
                if unified is not None:
                    tp = unified["transplant"]
                    if tp["should_trigger"] and tp["scenario"]:
                        template = get_template(tp["phase"], tp["scenario"])
                        if template:
                            self.set_transplant_phase(tp["phase"])
                            response = template
                            response_type = "transplant_guidance"
                else:
                    transplant_trigger = choose_intervention(
                        user_message=user_message,
                        current_phase=current_phase,
                        emotional_severity=cbt_analysis.get("emotional_state", {}).get("severity", 0),
                    )
                    if transplant_trigger.should_trigger and transplant_trigger.scenario:
                        template = get_template(transplant_trigger.phase, transplant_trigger.scenario)
                        if template:
                            self.set_transplant_phase(transplant_trigger.phase)
                            response = template
                            response_type = "transplant_guidance"

            if response is None:
                # 正常对话（含CBT引导，合并为一次调用）
                response = self._generate_cbt_response(user_message, cbt_analysis)
                response_type = "cbt_response"

        # 5. 添加到对话历史
        self.conversation_history.append({
            "role": "user",
            "content": user_message
        })

        # 序列化分析数据以支持 JSON 存储
        serialized_cbt_analysis = self._serialize_analysis_data(cbt_analysis)
        serialized_crisis_detection = self._serialize_analysis_data(crisis_detection)
        tool_trace = self.last_tool_trace

        self.conversation_history.append({
            "role": "assistant",
            "content": response,
            "metadata": {
                "response_type": response_type,
                "cbt_analysis": serialized_cbt_analysis,
                "crisis_detection": serialized_crisis_detection,
                "user_state": dict(self.user_state),
                "tool_trace": tool_trace,
            }
        })

        # 6. 根据语义分析结果更新 CBT 用户档案，不再从用户原话提取关键词触发点。
        self._record_cbt_profile_signal(cbt_analysis, count_session=True)

        # 7. 应用统一后台分析中的能量评估和心理模型补丁。
        energy_assessment = None
        if unified is not None:
            if not crisis_detection.get("alert", False):
                energy_assessment = self._apply_llm_energy_assessment(
                    unified=unified,
                    user_message=user_message,
                    response=response,
                    cbt_analysis=cbt_analysis,
                )
            self._apply_psych_model_patch(
                patch=unified.get("psych_model_patch") or {},
                cbt_analysis=cbt_analysis,
                crisis_detection=crisis_detection,
                count_turn=True,
            )

        # 8. 增量摘要：异步更新记忆中枢（不阻塞主回复返回）
        if Config.HISTORY_COMPRESSION_ENABLED:
            threading.Thread(
                target=self._update_memory_core,
                args=(user_message, response, cbt_analysis, crisis_detection),
                daemon=True
            ).start()

        # 9. 返回完整结果
        result = {
            "response": response,
            "response_type": response_type,
            "cbt_analysis": cbt_analysis,
            "crisis_detection": crisis_detection,
            "energy_assessment": energy_assessment,
            "energy_report": self.energy_model.get_energy_report() if energy_assessment else None,
            "tool_trace": tool_trace,
        }
        self.last_result = result
        return result

    def stream_chat(self, user_message: str) -> Iterator[str]:
        """默认通过 LangGraph 编排流式对话；配置关闭或依赖缺失时使用旧流程。"""
        if getattr(Config, "AGENT_GRAPH_ENABLED", True):
            try:
                from xiaoya_agent.graph.turn_graph import run_graph_stream
            except ImportError:
                logger.exception("LangGraph 编排层不可用，降级到旧 stream_chat 流程")
            else:
                return run_graph_stream(self, user_message)

        return self._stream_chat_legacy(user_message)

    def _stream_chat_legacy(self, user_message: str) -> Iterator[str]:
        """
        流式链路采用“语义安全预检 + 回复模型流式输出 + 后台综合分析增强”：
        - 默认在首 token 前做一次危机语义预检，明确危机先进入安全回应；
        - 预检会结合用户长期心理模型，但当前原话优先；
        - 身体红旗默认也由语义预检判断，本地关键词只作为可选院内兜底；
        - CBT 介入不再由本地关键词/规则触发，主回复模型直接按用户原话做语义判断；
        - CBT、危机和移植情境的综合分析在后台并行完成，用于本轮元数据或后续轮次。
        """
        current_phase = self.get_transplant_phase()
        cbt_analysis = self._pending_semantic_cbt_analysis()
        crisis_detection = self._assess_crisis_for_stream(user_message, cbt_analysis)
        response_context = self._build_stream_response_context(
            user_message=user_message,
            current_phase=current_phase,
            analysis=cbt_analysis,
        )

        conversation_data = {
            "user_message": user_message,
            "analysis": cbt_analysis,
            "crisis_detection": crisis_detection
        }

        analysis_task = None
        if (
            self._background_analysis_start_mode() in {"before_stream", "parallel", "immediate"}
            and self._should_start_background_analysis(user_message)
        ):
            analysis_task = self._start_unified_analysis_task(user_message, current_phase)

        safety_alert = self._build_safety_alert(user_message, cbt_analysis, crisis_detection)
        if safety_alert:
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
                self.crisis_module._record_crisis_event(
                    user_message,
                    crisis_detection.get("severity_score"),
                    alert_payload,
                )
                self.crisis_module._trigger_alert(alert_payload)
            response = safety_alert["response"]
            response_type = safety_alert["response_type"]
            return self._stream_static_response(
                user_message=user_message,
                response=response,
                response_type=response_type,
                cbt_analysis=cbt_analysis,
                crisis_detection=crisis_detection,
                conversation_data=conversation_data,
                current_phase=current_phase,
                analysis_task=analysis_task,
                chunk_size=36,
            )

        if response_context.get("phase") and response_context["phase"] != current_phase:
            self.set_transplant_phase(response_context["phase"])

        return self._stream_and_finalize_cbt_response(
            user_message=user_message,
            analysis=cbt_analysis,
            crisis_detection=crisis_detection,
            conversation_data=conversation_data,
            response_type="cbt_response",
            current_phase=current_phase,
            response_context=response_context,
            analysis_task=analysis_task,
        )

    def _pending_semantic_cbt_analysis(self) -> Dict[str, Any]:
        """流式首轮占位分析：不使用 CBT 关键词/规则，等待后台语义分析补充。"""
        return {
            "emotional_state": {"primary": "neutral", "severity": 1, "details": {}},
            "cognitive_distortions": [],
            "problem_severity": 1,
            "intervention_needed": False,
            "recommended_technique": None,
            "source": "semantic_background_pending",
        }

    def _finalize_chat_turn(
        self,
        user_message: str,
        response: str,
        response_type: str,
        cbt_analysis: Dict,
        crisis_detection: Dict,
        conversation_data: Dict,
        current_phase: Optional[TransplantPhase] = None,
        analysis_task: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """统一完成历史写入、画像更新、能量评估和记忆更新。"""
        if analysis_task is not None:
            cbt_analysis, crisis_detection = self._consume_completed_unified_analysis(
                task=analysis_task,
                user_message=user_message,
                cbt_analysis=cbt_analysis,
                crisis_detection=crisis_detection,
                current_phase=current_phase,
            )
            conversation_data = dict(conversation_data)
            conversation_data["analysis"] = cbt_analysis
            conversation_data["crisis_detection"] = crisis_detection

        self.conversation_history.append({
            "role": "user",
            "content": user_message
        })

        if crisis_detection.get("alert", False) and response_type == "cbt_response":
            response_type = "crisis_alert"

        serialized_cbt_analysis = self._serialize_analysis_data(cbt_analysis)
        serialized_crisis_detection = self._serialize_analysis_data(crisis_detection)
        tool_trace = self._build_turn_tool_trace(conversation_data)
        self.last_tool_trace = tool_trace

        self.conversation_history.append({
            "role": "assistant",
            "content": response,
            "metadata": {
                "response_type": response_type,
                "cbt_analysis": serialized_cbt_analysis,
                "crisis_detection": serialized_crisis_detection,
                "user_state": dict(self.user_state),
                "tool_trace": tool_trace,
            }
        })

        self._record_cbt_profile_signal(cbt_analysis, count_session=True)
        self._save_psych_model()

        energy_assessment = None

        if Config.HISTORY_COMPRESSION_ENABLED:
            threading.Thread(
                target=self._update_memory_core,
                args=(user_message, response, cbt_analysis, crisis_detection),
                daemon=True
            ).start()

        result = {
            "response": response,
            "response_type": response_type,
            "cbt_analysis": cbt_analysis,
            "crisis_detection": crisis_detection,
            "energy_assessment": energy_assessment,
            "energy_report": self.energy_model.get_energy_report() if energy_assessment else None,
            "tool_trace": tool_trace,
        }

        self.last_result = result

        if analysis_task is not None:
            self._pending_analysis_task = {
                "task": analysis_task,
                "user_message": user_message,
                "response": response,
                "current_phase": current_phase,
            }
            threading.Thread(
                target=self._finish_background_analysis_task,
                args=(analysis_task, user_message, response, current_phase),
                daemon=True,
            ).start()

        return result

    def _build_turn_tool_trace(self, conversation_data: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Build one metadata trace from deterministic graph tools and model-called tools."""
        conversation_data = conversation_data or {}
        model_trace = conversation_data.get("tool_trace")
        local_outputs = conversation_data.get("local_tools")
        local_trace = None
        if local_outputs:
            local_trace = summarize_tool_outputs(
                local_outputs,
                source="langgraph_prepare_turn",
            )
        if local_trace and model_trace:
            return self._merge_tool_traces(local_trace, model_trace)
        return model_trace or local_trace

    def _merge_tool_traces(
        self,
        local_trace: Dict[str, Any],
        model_trace: Dict[str, Any],
    ) -> Dict[str, Any]:
        """Merge graph-prefetched context with later model tool planning metadata."""
        combined_tools: List[Dict[str, Any]] = []
        tool_indexes: Dict[str, int] = {}

        def tool_score(tool: Dict[str, Any]) -> Tuple[int, int]:
            return (
                1 if tool.get("hasContext") else 0,
                int(tool.get("matchCount") or tool.get("serviceCount") or 0),
            )

        for trace in (local_trace or {}, model_trace or {}):
            for tool in trace.get("tools") or []:
                if not isinstance(tool, dict):
                    continue
                name = str(tool.get("name") or "")
                if not name:
                    combined_tools.append(tool)
                    continue
                existing_index = tool_indexes.get(name)
                if existing_index is None:
                    tool_indexes[name] = len(combined_tools)
                    combined_tools.append(tool)
                    continue
                if tool_score(tool) > tool_score(combined_tools[existing_index]):
                    combined_tools[existing_index] = tool

        merged = dict(model_trace or {})
        merged.update({
            "source": "hybrid_tool_context",
            "toolCount": len(combined_tools),
            "tools": combined_tools,
            "localToolCount": int((local_trace or {}).get("toolCount", 0) or 0),
            "modelToolCount": int((model_trace or {}).get("toolCount", 0) or 0),
        })
        return merged

    def _stream_static_response(
        self,
        user_message: str,
        response: str,
        response_type: str,
        cbt_analysis: Dict,
        crisis_detection: Dict,
        conversation_data: Dict,
        current_phase: Optional[TransplantPhase] = None,
        analysis_task: Optional[Dict[str, Any]] = None,
        chunk_size: int = 28,
    ) -> Iterator[str]:
        """将固定文本按小片段流式输出。"""
        chunks = [response[i:i + chunk_size] for i in range(0, len(response), chunk_size)] or [response]
        for chunk in chunks:
            yield chunk

        self.last_result = self._finalize_chat_turn(
            user_message=user_message,
            response=response,
            response_type=response_type,
            cbt_analysis=cbt_analysis,
            crisis_detection=crisis_detection,
            conversation_data=conversation_data,
            current_phase=current_phase,
            analysis_task=analysis_task,
        )

    def _stream_and_finalize_cbt_response(
        self,
        user_message: str,
        analysis: Dict,
        crisis_detection: Dict,
        conversation_data: Dict,
        response_type: str,
        current_phase: Optional[TransplantPhase] = None,
        response_context: Optional[Dict[str, Any]] = None,
        analysis_task: Optional[Dict[str, Any]] = None,
    ) -> Iterator[str]:
        """流式输出 CBT 回复，并在结束后补做落库与评估。"""
        response_parts: List[str] = []
        deferred_analysis_checked = analysis_task is not None
        background_mode = self._background_analysis_start_mode()

        try:
            stream, model_tool_trace = self._create_response_stream_with_model_tools(
                user_message,
                analysis,
                response_context=response_context,
            )
            if model_tool_trace is not None:
                conversation_data = dict(conversation_data)
                conversation_data["tool_trace"] = model_tool_trace
                if (
                    response_type == "cbt_response"
                    and int(model_tool_trace.get("toolCount", 0) or 0) > 0
                ):
                    response_type = "tool_augmented_response"
            first_chunk = True
            for chunk in stream:
                delta = getattr(chunk.choices[0].delta, "content", None)
                if delta:
                    text = delta
                    if first_chunk:
                        text = text.lstrip()
                        first_chunk = False
                        if not text:
                            continue
                    response_parts.append(text)
                    yield text
                    if (
                        not deferred_analysis_checked
                        and background_mode == "after_first_delta"
                        and self._should_start_background_analysis(user_message)
                    ):
                        deferred_analysis_checked = True
                        analysis_task = self._start_unified_analysis_task(
                            user_message,
                            current_phase or self.get_transplant_phase(),
                        )
        except Exception as e:
            logger.exception("流式回复生成失败")
            response_parts = [USER_FACING_ERROR_MESSAGE]
            yield USER_FACING_ERROR_MESSAGE
        finally:
            if (
                analysis_task is None
                and not deferred_analysis_checked
                and background_mode in {"after_stream", "after_first_delta"}
                and self._should_start_background_analysis(user_message)
            ):
                analysis_task = self._start_unified_analysis_task(
                    user_message,
                    current_phase or self.get_transplant_phase(),
                )
            full_response = "".join(response_parts)
            self.last_result = self._finalize_chat_turn(
                user_message=user_message,
                response=full_response,
                response_type=response_type,
                cbt_analysis=analysis,
                crisis_detection=crisis_detection,
                conversation_data=conversation_data,
                current_phase=current_phase,
                analysis_task=analysis_task,
            )

    def get_transplant_phase(self) -> TransplantPhase:
        """获取当前骨髓移植分期"""
        raw = (self.user_state or {}).get("transplant_phase", TransplantPhase.PREP.value)
        try:
            return TransplantPhase(raw)
        except Exception:
            return TransplantPhase.PREP

    def set_transplant_phase(self, phase: TransplantPhase):
        """设置骨髓移植分期"""
        if not isinstance(phase, TransplantPhase):
            raise ValueError("phase必须是TransplantPhase")
        self.user_state["transplant_phase"] = phase.value
        self._save_user_state()
        self._save_psych_model()

    def get_history(self) -> List[Dict[str, str]]:
        """获取对话历史"""
        return self.conversation_history.copy()

    def _serialize_analysis_data(self, data):
        """序列化分析数据，将枚举值转换为字符串"""
        if isinstance(data, dict):
            return {key: self._serialize_analysis_data(value) for key, value in data.items()}
        elif isinstance(data, list):
            return [self._serialize_analysis_data(item) for item in data]
        elif data is None or isinstance(data, (str, int, float, bool)):
            return data
        elif hasattr(data, 'value'):  # 枚举对象
            return data.value
        elif hasattr(data, '__str__'):  # 其他可序列化对象
            return str(data)
        else:
            return data

    def _merge_unique_list(self, key: str, values: List[Any], max_items: int = 8) -> None:
        current = list(self.personalization_profile.get(key) or [])
        for value in values:
            if value is None:
                continue
            text = str(value).strip()
            if not text or text in current:
                continue
            current.append(text)
        self.personalization_profile[key] = current[-max_items:]

    def _record_cbt_profile_signal(self, cbt_analysis: Dict[str, Any], count_session: bool = True) -> None:
        """根据语义分析字段更新 CBT 用户画像，不读取用户原话关键词。"""
        try:
            if count_session:
                self.cbt_module.user_profile["session_count"] += 1

            distortions = list((cbt_analysis or {}).get("cognitive_distortions") or [])
            if distortions:
                current = list(self.cbt_module.user_profile.get("cognitive_patterns") or [])
                for item in distortions:
                    if item not in current:
                        current.append(item)
                self.cbt_module.user_profile["cognitive_patterns"] = current

            emotional = (cbt_analysis or {}).get("emotional_state", {}) or {}
            emotion = emotional.get("primary")
            severity = int(emotional.get("severity", 0) or 0)
            if emotion and emotion != "neutral" and severity >= 5:
                triggers = list(self.cbt_module.user_profile.get("emotional_triggers") or [])
                if emotion not in triggers:
                    triggers.append(str(emotion))
                self.cbt_module.user_profile["emotional_triggers"] = triggers[-10:]

            progress_change = 0
            if severity and severity <= 3:
                progress_change = 2
            elif severity >= 7:
                progress_change = -1
            self.cbt_module.user_profile["progress_level"] = max(
                0,
                min(100, int(self.cbt_module.user_profile.get("progress_level", 0) or 0) + progress_change),
            )
        except Exception:
            logger.exception("更新 CBT 用户画像语义信号失败")

    def _apply_llm_energy_assessment(
        self,
        unified: Dict[str, Any],
        user_message: str,
        response: str,
        cbt_analysis: Dict[str, Any],
    ) -> Optional[Dict[str, Any]]:
        """应用大模型结构化能量评估；这里不做关键词打分。"""
        energy_payload = (unified or {}).get("energy_assessment") or {}
        if not isinstance(energy_payload, dict):
            return None

        conversation_data = {
            "user_message": user_message,
            "analysis": cbt_analysis,
            "cbt_response": response,
        }
        return self.energy_model.apply_llm_assessment(conversation_data, energy_payload)

    def _apply_psych_model_patch(
        self,
        patch: Dict[str, Any],
        cbt_analysis: Dict[str, Any],
        crisis_detection: Dict[str, Any],
        count_turn: bool = True,
    ) -> None:
        """将大模型结构化心理模型补丁合并到用户级记忆。"""
        try:
            profile = dict(self.personalization_profile or self._default_personalization_profile())
            self.personalization_profile = {**self._default_personalization_profile(), **profile}
            patch = patch or {}

            preferred_name = self._short_text(patch.get("preferred_name"), 24)
            if preferred_name:
                self.personalization_profile["preferred_name"] = preferred_name

            communication_style = self._short_text(patch.get("communication_style"), 24)
            if communication_style:
                self.personalization_profile["communication_style"] = communication_style

            for key, max_items in [
                ("current_main_concerns", 6),
                ("cognitive_patterns", 8),
                ("effective_strategies", 6),
                ("support_preferences", 6),
                ("risk_notes", 4),
            ]:
                values = self._short_text_list(patch.get(key), limit=80)
                if values:
                    self._merge_unique_list(key, values, max_items=max_items)

            emotional_state = (cbt_analysis or {}).get("emotional_state", {}) or {}
            emotion = emotional_state.get("primary")
            severity = int(emotional_state.get("severity", 0) or 0)
            if emotion:
                self.personalization_profile["last_emotion"] = emotion
                self.personalization_profile["last_severity"] = severity
                recurring = dict(self.personalization_profile.get("recurring_emotions") or {})
                recurring[str(emotion)] = int(recurring.get(str(emotion), 0) or 0) + 1
                self.personalization_profile["recurring_emotions"] = recurring

            technique = (cbt_analysis or {}).get("recommended_technique")
            if technique:
                technique_name = technique.value if hasattr(technique, "value") else str(technique)
                self._merge_unique_list("effective_strategies", [technique_name], max_items=6)

            if (crisis_detection or {}).get("alert", False):
                alert_type = str((crisis_detection or {}).get("alert_type") or "psychological_crisis")
                self._merge_unique_list("risk_notes", [alert_type], max_items=4)

            if count_turn:
                self.personalization_profile["updated_turns"] = int(
                    self.personalization_profile.get("updated_turns", 0) or 0
                ) + 1
            self.personalization_profile["last_updated"] = datetime.now().replace(microsecond=0).isoformat()
            self._save_psych_model()
        except Exception:
            logger.exception("应用 LLM 心理模型补丁失败")

    def _short_text(self, value: Any, limit: int) -> Optional[str]:
        if value is None:
            return None
        text = str(value).strip()
        if not text:
            return None
        return text[:limit]

    def _short_text_list(self, value: Any, limit: int = 80) -> List[str]:
        if value is None:
            return []
        if isinstance(value, str):
            raw_values = [value]
        elif isinstance(value, list):
            raw_values = value
        else:
            return []
        cleaned = []
        for item in raw_values:
            text = str(item).strip()
            if text:
                cleaned.append(text[:limit])
        return cleaned

    def _update_last_assistant_metadata(
        self,
        response: str,
        cbt_analysis: Dict,
        crisis_detection: Dict,
        response_type: Optional[str] = None,
    ) -> None:
        """同步更新最后一条匹配回复的 assistant 元数据。"""
        for message in reversed(self.conversation_history):
            if message.get("role") != "assistant" or message.get("content") != response:
                continue

            metadata = message.setdefault("metadata", {})
            if response_type:
                metadata["response_type"] = response_type
            metadata["cbt_analysis"] = self._serialize_analysis_data(cbt_analysis)
            metadata["crisis_detection"] = self._serialize_analysis_data(crisis_detection)
            metadata["user_state"] = dict(self.user_state)
            return

    def save_history(self, filename: str = "chat_history.json"):
        """保存对话历史到当前存储后端。"""
        if database_storage_enabled():
            repo = get_database_repository()
            session_id = getattr(self, "storage_session_id", None)
            if session_id:
                repo.save_session_messages(str(session_id), list(self.conversation_history or []))
                return
            if self.user_id and getattr(self, "storage_source", None):
                source = str(getattr(self, "storage_source", None) or "cli")
                conversation_id = str(getattr(self, "storage_conversation_id", None) or source)
                visible_history = [message for message in list(self.conversation_history or []) if message.get("role") != "system"]
                now = datetime.now().replace(microsecond=0).isoformat()
                entry = {
                    "userId": self.user_id,
                    "safeUserId": self._safe_storage_user_id(),
                    "source": source,
                    "conversationId": conversation_id,
                    "safeConversationId": conversation_id,
                    "sessionId": conversation_id if source == "api" else None,
                    "title": f"{source.upper()} 会话 - {self.user_id}",
                    "createdAt": now,
                    "updatedAt": now,
                    "lastMessageAt": None,
                    "messageCount": len([message for message in visible_history if message.get("role") == "user"]),
                    "historyCount": len(visible_history),
                    "dataDir": self.data_dir,
                    "snapshotPath": None,
                }
                repo.save_conversation_snapshot(
                    user_id=str(self.user_id),
                    safe_user_id=self._safe_storage_user_id(),
                    conversation_id=conversation_id,
                    safe_conversation_id=conversation_id,
                    source=source,
                    history=list(self.conversation_history or []),
                    metadata={"sessionId": conversation_id, "title": entry["title"], "dataDir": self.data_dir},
                    entry=entry,
                )
                return
            return
        filepath = self._get_filepath(filename)
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(self.conversation_history, f, ensure_ascii=False, indent=2)

    def _generate_cbt_response(self, user_message: str, analysis: Dict) -> str:
        """生成回复（含CBT引导）——主回复与CBT建议合并为一次LLM调用"""
        try:
            if getattr(Config, "AGENT_MODEL_TOOL_CALLING_ENABLED", True):
                return self._generate_response_with_model_tools(user_message, analysis)
            api_response = self._create_response_stream(user_message, analysis, stream=False)
            return api_response.choices[0].message.content
        except Exception as e:
            logger.exception("普通回复生成失败")
            return USER_FACING_ERROR_MESSAGE

    def _build_response_messages(
        self,
        user_message: str,
        analysis: Dict,
        response_context: Optional[Dict[str, Any]] = None,
    ) -> Tuple[List[Dict[str, Any]], int]:
        """构造主回复模型消息，并返回建议 max_tokens。"""
        messages_for_api = self._get_messages_for_api(user_message)
        response_context = response_context or {}
        prompt_runtime = self._resolve_prompt_runtime()
        need_cbt = self._should_add_cbt_guidance(analysis)

        extra_system_messages = [{
            "role": "system",
            "content": prompt_runtime.realtime_instruction,
        }]
        template = response_context.get("template")
        scenario = response_context.get("scenario")
        if template and scenario:
            scenario_name = scenario.value if hasattr(scenario, "value") else str(scenario)
            template_hint = self._compact_template_hint(template)
            extra_system_messages.append({
                "role": "system",
                "content": (
                    f"[移植情境提示] 当前识别到的情境是：{scenario_name}。"
                    f"下面是病房陪伴话术素材，只作为参考。"
                    f"请把其中的意象或安抚方向自然融入回复，必须回应用户原话，"
                    f"不要整段照抄，不要覆盖正常对话，不要说你在引用模板。"
                    f"素材摘要：{template_hint}"
                )
            })
        mcp_context = response_context.get("mcp_context")
        if mcp_context:
            extra_system_messages.append({
                "role": "system",
                "content": (
                    "[MCP服务结果] 以下内容来自确定性服务，不是模型猜测。"
                    "如果用户询问当前时间、日期或星期，必须严格按这里的服务结果回答，"
                    "不要使用“快到”“大概”“应该”等模糊说法，也不要编造与服务结果矛盾的时间。\n"
                    "如果用户同时询问其它事情，必须继续回应其它问题；不要只输出时间。\n"
                    f"{mcp_context}"
                )
            })
        harbor_context = response_context.get("harbor_context")
        if harbor_context:
            extra_system_messages.append({
                "role": "system",
                "content": (
                    "[心之港湾工具结果] 以下内容来自本地确定性心理调节工具库。"
                    "如果用户请求放松、呼吸、正念、冥想、音乐放松、肌肉放松或54321接地，"
                    "请优先按这里的练习脚本给出床旁可完成的简短语音引导。"
                    "不要要求复杂动作；如果出现明显身体红旗，仍优先建议联系医护。\n"
                    f"{harbor_context}"
                )
            })
        knowledge_context = response_context.get("knowledge_context")
        if knowledge_context:
            knowledge_backend = str(response_context.get("knowledge_backend") or "dify").lower()
            source_label = "Dify Knowledge Base" if knowledge_backend == "dify" else "configured knowledge backend"
            extra_system_messages.append({
                "role": "system",
                "content": (
                    f"[Dify知识库检索结果] 以下内容来自{source_label}，只作为回答参考。"
                    "如果资料与用户当前问题相关，必须优先按资料回答用户问到的定义、用途或事实，不要把资料中的项目术语自由解释成心理象征。"
                    "如果资料只提到某个词但没有给出明确定义，要直接说明“资料中只提到该词，未给出更多定义”。"
                    "如果资料只说某个词是“特殊测试词”，只能说明资料如此标注，不能推断它是用来确认用户是否认真看资料，也不能补充资料中没有的测试目的。"
                    "同时要简要补充同一资料片段中明确存在的其它相关事实，帮助用户知道资料主要讲了什么；不要只截取一个孤立词。"
                    "如果资料不相关，不要生硬引用。不要编造资料中没有的护理或医疗结论；涉及具体医疗处置时仍建议联系医护确认。\n"
                    f"{knowledge_context}"
                )
            })
            knowledge_context = ""
        else:
            knowledge_reason = str(response_context.get("knowledge_reason") or "").strip()
            if knowledge_reason and knowledge_reason not in {"ok", "auto_skipped_for_speed"}:
                extra_system_messages.append({
                    "role": "system",
                    "content": (
                        "[Dify知识库检索状态] 本轮已经尝试检索 Dify 知识库，但没有拿到可用于回答的资料片段。"
                        f"检索状态：{knowledge_reason}。"
                        "如果用户询问资料中的定义、项目术语、测试词或事实，不要编造；"
                        "请直接说明暂时没有从知识库查到这个词的明确资料，"
                        "可以邀请用户确认资料是否已保存、索引完成，或稍后重试。"
                    )
                })
        if knowledge_context:
            extra_system_messages.append({
                "role": "system",
                "content": (
                    "[知识库检索结果] 以下内容来自配置的知识库后端，只作为回答参考。"
                    "如果资料与用户当前问题相关，必须优先按资料回答用户问到的定义、用途或事实，不要把资料中的项目术语自由解释成心理象征。"
                    "如果资料只提到某个词但没有给出明确定义，要直接说明“资料中只提到该词，未给出更多定义”。"
                    "如果资料只说某个词是“特殊测试词”，只能说明资料如此标注，不能推断它是用来确认用户是否认真看资料，也不能补充资料中没有的测试目的。"
                    "同时要简要补充同一资料片段中明确存在的其它相关事实，帮助用户知道资料主要讲了什么；不要只截取一个孤立词。"
                    "如果资料不相关，不要生硬引用。"
                    "不要编造资料中没有的护理或医疗结论；涉及具体医疗处置时仍建议联系医护确认。\n"
                    f"{knowledge_context}"
                )
            })

        if need_cbt and analysis.get("recommended_technique"):
            technique = analysis["recommended_technique"]
            technique_name = technique.value if hasattr(technique, "value") else str(technique)
            emotional = analysis.get("emotional_state", {})
            severity = emotional.get("severity", 5)
            emotion = emotional.get("primary", "neutral")
            distortions = analysis.get("cognitive_distortions", [])
            distortions_str = "、".join(distortions) if distortions else "无"

            cbt_instruction = (
                f"[本转CBT引导指令]当前用户情绪：{emotion}（强度{severity}/10），"
                f"认知性扰曲：{distortions_str}。"
                f"请先自然共情，再只引入一个{technique_name}方向的微小引导。"
                f"不要使用编号/列表，不要生硬分段，不要重复同一层意思。"
                f"语气积极、稳住希望，总字数控制在180字以内。"
                f"只输出普通纯文本，不要Markdown格式。"
            )

            user_msg = messages_for_api[-1]
            messages = messages_for_api[:-1] + extra_system_messages + [
                {"role": "system", "content": cbt_instruction},
                user_msg
            ]
            max_tokens = int(getattr(Config, "RESPONSE_MAX_TOKENS_CBT", 240) or 240)
        else:
            user_msg = messages_for_api[-1]
            messages = messages_for_api[:-1] + extra_system_messages + [user_msg]
            max_tokens = int(getattr(Config, "RESPONSE_MAX_TOKENS_NORMAL", 200) or 200)

        return messages, max_tokens

    def _create_response_stream(
        self,
        user_message: str,
        analysis: Dict,
        stream: bool = True,
        response_context: Optional[Dict[str, Any]] = None,
    ):
        """创建回复请求，支持普通返回和流式返回。"""
        messages, max_tokens = self._build_response_messages(
            user_message=user_message,
            analysis=analysis,
            response_context=response_context,
        )
        return self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=self.temperature,
            max_tokens=max_tokens,
            stream=stream,
        )

    def _model_tool_calling_enabled(self) -> bool:
        return bool(
            getattr(Config, "AGENT_TOOLS_ENABLED", True)
            and getattr(Config, "AGENT_MODEL_TOOL_CALLING_ENABLED", True)
        )

    def _should_plan_model_tools_for_stream(
        self,
        user_message: str,
        analysis: Dict,
        response_context: Optional[Dict[str, Any]] = None,
    ) -> bool:
        """Keep ordinary streaming direct; only plan tools when a tool is likely needed."""
        if not self._model_tool_calling_enabled():
            return False

        response_context = response_context or {}
        if any(
            response_context.get(key)
            for key in (
                "knowledge_context",
                "knowledge_reason",
                "mcp_context",
                "harbor_context",
                "template",
            )
        ):
            return False

        if should_use_mcp_services(user_message):
            return True
        if self._should_plan_harbor_tool_for_stream(user_message):
            return True

        return False

    def _should_plan_harbor_tool_for_stream(self, user_message: str) -> bool:
        """Only plan harbor tools for explicit exercise requests, not every anxious turn."""
        normalized = str(user_message or "").strip().lower()
        if not normalized:
            return False
        explicit_terms = [
            "心之港湾",
            "港湾",
            "放松练习",
            "带我放松",
            "呼吸练习",
            "呼吸调节",
            "54321",
            "接地练习",
            "正念",
            "冥想",
            "音乐放松",
            "肌肉放松",
        ]
        if any(term in normalized for term in explicit_terms):
            return True

        action_terms = ["带我", "做一个", "做一次", "来一个", "练习一下"]
        need_terms = ["放松", "呼吸", "睡不着", "失眠", "疼痛", "害怕", "焦虑", "崩溃", "撑不住"]
        return any(term in normalized for term in action_terms) and any(
            term in normalized for term in need_terms
        )

    def _with_tool_choice_instruction(self, messages: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """在模型工具选择阶段补充工具使用要求。"""
        if not messages:
            return messages
        instruction = {
            "role": "system",
            "content": (
                "[工具选择要求] 你可以根据用户原话决定是否调用工具。"
                "如果用户询问当前时间/日期/星期，必须调用 mcp_service_router，不能自己猜。"
                "如果用户询问资料、知识库、项目术语或需要外部资料支撑的事实，优先调用 knowledge_retrieval。"
                "如果用户明确想做心之港湾、呼吸、正念、冥想、音乐放松、肌肉放松、54321接地，"
                "或表达焦虑/恐惧/失眠/疼痛/情绪崩溃且希望马上缓一缓，可调用 harbor_regulation_tool。"
                "如果用户涉及骨髓移植具体阶段或治疗场景，可调用 transplant_context_lookup 获取分期和话术素材。"
                "如果不需要工具，就不要调用工具。"
            ),
        }
        return messages[:-1] + [instruction, messages[-1]]

    def _prepare_stream_messages_with_model_tools(
        self,
        user_message: str,
        analysis: Dict,
        response_context: Optional[Dict[str, Any]] = None,
    ) -> Tuple[List[Dict[str, Any]], int, Optional[Dict[str, Any]], Optional[str]]:
        """让模型先决定是否调用工具，再返回最终流式回复所需消息。"""
        messages, max_tokens = self._build_response_messages(
            user_message=user_message,
            analysis=analysis,
            response_context=response_context,
        )
        if not self._should_plan_model_tools_for_stream(
            user_message=user_message,
            analysis=analysis,
            response_context=response_context,
        ):
            return messages, max_tokens, None, None

        tools = get_model_tool_definitions()
        if not tools:
            return messages, max_tokens, None, None

        planning_messages = self._with_tool_choice_instruction(messages)
        try:
            first_response = self.client.chat.completions.create(
                model=self.model,
                messages=planning_messages,
                temperature=self.temperature,
                max_tokens=min(max_tokens, 256),
                stream=False,
                tools=tools,
                tool_choice="auto",
            )
        except Exception:
            logger.exception("模型工具选择失败，降级为不调用工具的流式回复")
            return messages, max_tokens, {
                "source": "model_tool_calling",
                "toolCount": 0,
                "requestedTools": [],
                "tools": [],
                "error": "tool_planning_failed",
            }, None

        message = first_response.choices[0].message
        tool_calls = list(getattr(message, "tool_calls", None) or [])
        if not tool_calls:
            return messages, max_tokens, {
                "source": "model_tool_calling",
                "toolCount": 0,
                "requestedTools": [],
                "tools": [],
            }, None

        planning_messages.append({
            "role": "assistant",
            "content": getattr(message, "content", None) or "",
            "tool_calls": [
                self._serialize_tool_call_for_api(tool_call)
                for tool_call in tool_calls
            ],
        })

        max_calls = max(1, int(getattr(Config, "AGENT_MODEL_TOOL_CALL_MAX_CALLS", 2) or 2))
        executed_tools: List[Dict[str, Any]] = []
        for tool_call in tool_calls[:max_calls]:
            function = getattr(tool_call, "function", None)
            tool_name = getattr(function, "name", "")
            arguments = parse_tool_arguments(getattr(function, "arguments", "{}"))
            tool_result = execute_model_tool_call(
                tool_name=tool_name,
                arguments=arguments,
                agent=self,
                user_message=user_message,
            )
            executed_tools.append(summarize_tool_output(
                tool_name=tool_name,
                result=tool_result,
                source="model_tool_calling",
                arguments=arguments,
                tool_call_id=getattr(tool_call, "id", None),
            ))
            planning_messages.append({
                "role": "tool",
                "tool_call_id": getattr(tool_call, "id", ""),
                "name": tool_name,
                "content": json.dumps(tool_result, ensure_ascii=False),
            })

        planning_messages.append({
            "role": "system",
            "content": (
                "[工具结果使用要求] 请结合工具返回结果和用户完整原话回答。"
                "如果工具只回答了用户问题的一部分，必须继续回应其它部分。"
                "如果工具结果不足以确定医疗安排、手术时间或诊疗结论，要明确建议以医生、护士或院内通知为准，不能编造。"
            ),
        })
        return planning_messages, max_tokens, {
            "source": "model_tool_calling",
            "toolCount": len(executed_tools),
            "requestedTools": [
                getattr(getattr(tool_call, "function", None), "name", "")
                for tool_call in tool_calls
            ],
            "tools": executed_tools,
            "maxCalls": max_calls,
            "truncated": len(tool_calls) > max_calls,
        }, None

    def _static_chat_completion_stream(self, text: str, chunk_size: int = 36) -> Iterator[Any]:
        """把已有文本包装成与 OpenAI 流式 chunk 形状相同的迭代器。"""
        class _Delta:
            def __init__(self, content: str):
                self.content = content

        class _Choice:
            def __init__(self, content: str):
                self.delta = _Delta(content)

        class _Chunk:
            def __init__(self, content: str):
                self.choices = [_Choice(content)]

        for start in range(0, len(text), chunk_size):
            yield _Chunk(text[start:start + chunk_size])

    def _create_response_stream_with_model_tools(
        self,
        user_message: str,
        analysis: Dict,
        response_context: Optional[Dict[str, Any]] = None,
    ) -> Tuple[Any, Optional[Dict[str, Any]]]:
        messages, max_tokens, tool_trace, precomputed_response = self._prepare_stream_messages_with_model_tools(
            user_message=user_message,
            analysis=analysis,
            response_context=response_context,
        )
        if precomputed_response is not None:
            return self._static_chat_completion_stream(precomputed_response), tool_trace
        return self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=self.temperature,
            max_tokens=max_tokens,
            stream=True,
        ), tool_trace

    def _serialize_tool_call_for_api(self, tool_call: Any) -> Dict[str, Any]:
        function = getattr(tool_call, "function", None)
        return {
            "id": getattr(tool_call, "id", None),
            "type": getattr(tool_call, "type", "function"),
            "function": {
                "name": getattr(function, "name", ""),
                "arguments": getattr(function, "arguments", "{}"),
            },
        }

    def _generate_response_with_model_tools(
        self,
        user_message: str,
        analysis: Dict,
        response_context: Optional[Dict[str, Any]] = None,
    ) -> str:
        """非流式路径：让模型可选择调用本地工具，再生成最终回复。"""
        messages, max_tokens = self._build_response_messages(
            user_message=user_message,
            analysis=analysis,
            response_context=response_context,
        )
        tools = get_model_tool_definitions()

        try:
            first_response = self.client.chat.completions.create(
                model=self.model,
                messages=messages,
                temperature=self.temperature,
                max_tokens=max_tokens,
                stream=False,
                tools=tools,
                tool_choice="auto",
            )
        except Exception:
            logger.exception("模型工具调用初始化失败，降级为普通回复")
            api_response = self._create_response_stream(
                user_message,
                analysis,
                stream=False,
                response_context=response_context,
            )
            return api_response.choices[0].message.content

        message = first_response.choices[0].message
        tool_calls = list(getattr(message, "tool_calls", None) or [])
        if not tool_calls:
            self.last_tool_trace = {
                "source": "model_tool_calling",
                "toolCount": 0,
                "requestedTools": [],
                "tools": [],
            }
            return getattr(message, "content", None) or ""

        messages.append({
            "role": "assistant",
            "content": getattr(message, "content", None) or "",
            "tool_calls": [
                self._serialize_tool_call_for_api(tool_call)
                for tool_call in tool_calls
            ],
        })

        max_calls = max(1, int(getattr(Config, "AGENT_MODEL_TOOL_CALL_MAX_CALLS", 2) or 2))
        executed_tools: List[Dict[str, Any]] = []
        for tool_call in tool_calls[:max_calls]:
            function = getattr(tool_call, "function", None)
            tool_name = getattr(function, "name", "")
            arguments = parse_tool_arguments(getattr(function, "arguments", "{}"))
            tool_result = execute_model_tool_call(
                tool_name=tool_name,
                arguments=arguments,
                agent=self,
                user_message=user_message,
            )
            executed_tools.append(summarize_tool_output(
                tool_name=tool_name,
                result=tool_result,
                source="model_tool_calling",
                arguments=arguments,
                tool_call_id=getattr(tool_call, "id", None),
            ))
            messages.append({
                "role": "tool",
                "tool_call_id": getattr(tool_call, "id", ""),
                "name": tool_name,
                "content": json.dumps(tool_result, ensure_ascii=False),
            })

        self.last_tool_trace = {
            "source": "model_tool_calling",
            "toolCount": len(executed_tools),
            "requestedTools": [
                getattr(getattr(tool_call, "function", None), "name", "")
                for tool_call in tool_calls
            ],
            "tools": executed_tools,
            "maxCalls": max_calls,
            "truncated": len(tool_calls) > max_calls,
        }

        final_response = self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=self.temperature,
            max_tokens=max_tokens,
            stream=False,
        )
        return final_response.choices[0].message.content

    def _build_safety_alert(
        self,
        user_message: str,
        analysis: Dict,
        crisis_detection: Dict,
    ) -> Optional[Dict[str, Any]]:
        """严重心理/身体信号优先给安全警告，不继续做 CBT。"""
        if self._has_medical_red_flag(user_message):
            return {
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

        if (crisis_detection or {}).get("alert", False):
            alarm = build_crisis_alarm(crisis_detection)
            if (crisis_detection or {}).get("alert_type") == "medical_red_flag":
                return {
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
            return {
                "alert_type": "psychological_crisis",
                "crisis_level": alarm["level"],
                "response_type": "crisis_alert",
                "notify": True,
                "response": (
                    "我必须先提醒你：现在最重要的是你的安全。"
                    "请马上按呼叫铃或告诉护士、家人，让一个人陪在你身边；"
                    "如果有伤害自己的冲动，请把危险物品放远。你不是一个人，这一刻先一起撑过去。"
                ),
            }

        if self._has_severe_emotional_state(user_message, analysis, crisis_detection):
            return {
                "alert_type": "severe_emotional_distress",
                "crisis_level": "warning",
                "response_type": "severe_distress_alert",
                "notify": True,
                "response": (
                    "我需要先提醒你：你现在的痛苦已经比较重，不适合继续做练习。"
                    "请先按呼叫铃，或告诉身边的护士、家人，让他们陪你一下。"
                    "我们先把安全和支持稳住，后面还有办法一步步缓下来。"
                ),
            }

        return None

    def _assess_crisis_for_stream(self, user_message: str, analysis: Dict) -> Dict[str, Any]:
        """流式入口默认做语义安全预检，不使用心理危机关键词规则。"""
        if not getattr(Config, "CRISIS_DETECTION_ENABLED", True):
            return {"alert": False, "source": "disabled"}

        emotional_state = (analysis or {}).get("emotional_state", {})

        if getattr(Config, "CRISIS_LLM_STREAM_BLOCKING_ENABLED", False):
            return self.crisis_module.assess_crisis_semantic_only(
                user_message,
                emotional_state,
                psych_model_context=self._build_crisis_assessment_context(),
                fast_precheck=True,
            )

        return {
            "alert": False,
            "alert_type": None,
            "source": "semantic_background_pending",
            "severity_score": 0,
            "crisis_level": "none",
            "alarm_level": "none",
            "crisis_types": [],
            "reason": "流式回复不使用心理危机关键词规则；危机状态由后台语义分析补充。",
        }

    def _has_medical_red_flag(self, user_message: str) -> bool:
        """移植病房身体红旗：先转医护，不继续心理引导。"""
        if not getattr(Config, "MEDICAL_RED_FLAG_RULE_ENABLED", False):
            return False
        return contains_any(user_message, MEDICAL_RED_FLAG_KEYWORDS)

    def _has_severe_emotional_state(
        self,
        user_message: str,
        analysis: Dict,
        crisis_detection: Optional[Dict[str, Any]] = None,
    ) -> bool:
        crisis_detection = crisis_detection or {}
        if crisis_detection.get("source") == "semantic_background_pending":
            return False

        if str(crisis_detection.get("source", "")).startswith("llm_semantic"):
            severity_score = int(crisis_detection.get("severity_score", 0) or 0)
            crisis_types = crisis_detection.get("crisis_types") or []
            warning_threshold = max(7, getattr(Config, "CRISIS_ALERT_THRESHOLD", 10) - 2)
            return bool(crisis_types) and severity_score >= warning_threshold

        emotional = (analysis or {}).get("emotional_state", {}) or {}
        severity = int(emotional.get("severity", 0) or 0)
        problem_severity = int((analysis or {}).get("problem_severity", 0) or 0)
        return (
            severity >= 8
            or problem_severity >= 8
        )

    def _compact_template_hint(self, template: str, limit: int = 120) -> str:
        """把较长移植话术压缩成首 token 友好的短提示。"""
        hint = " ".join((template or "").split())
        if len(hint) <= limit:
            return hint
        return hint[:limit].rstrip("，。；、 ") + "..."

    def _analysis_features_enabled(self) -> bool:
        return bool(
            getattr(Config, "CBT_LLM_ENABLED", True)
            or getattr(Config, "CRISIS_LLM_DETECTION_ENABLED", True)
            or getattr(Config, "TRANSPLANT_LLM_SCENARIO_ENABLED", True)
        )

    def _should_start_background_analysis(self, user_message: str) -> bool:
        """判断是否需要后台综合分析；不参与首 token 决策。"""
        if not self._analysis_features_enabled():
            return False

        normalized = (user_message or "").strip().lower()
        return not (len(normalized) <= 8 and normalized in CASUAL_INPUTS)

    def _background_analysis_start_mode(self) -> str:
        mode = str(getattr(Config, "BACKGROUND_ANALYSIS_START_MODE", "after_stream") or "after_stream").lower()
        allowed = {"before_stream", "parallel", "immediate", "after_first_delta", "after_stream", "disabled"}
        return mode if mode in allowed else "after_stream"

    def _start_unified_analysis_task(
        self,
        user_message: str,
        current_phase: TransplantPhase,
    ) -> Dict[str, Any]:
        """并行启动综合分析，结果只在完成后被消费，不阻塞流式回复。"""
        task: Dict[str, Any] = {
            "event": threading.Event(),
            "result": None,
            "lock": threading.Lock(),
            "consumed": False,
        }

        def run_analysis() -> None:
            try:
                crisis_first = self._background_crisis_first_result(user_message)
                if crisis_first is not None:
                    task["result"] = crisis_first
                    return
                task["result"] = self._llm_unified_analyze(user_message, current_phase)
            except Exception:
                logger.exception("后台综合分析失败")
                task["result"] = None
            finally:
                task["event"].set()

        threading.Thread(target=run_analysis, daemon=True).start()
        return task

    def _background_crisis_first_result(self, user_message: str) -> Optional[Dict[str, Any]]:
        """后台分析先做危机判断；一旦报警就停止后续分析链路。"""
        if not getattr(Config, "BACKGROUND_CRISIS_FIRST_ENABLED", True):
            return None
        if not getattr(Config, "CRISIS_DETECTION_ENABLED", True):
            return None
        if not getattr(Config, "CRISIS_LLM_DETECTION_ENABLED", True):
            return None

        crisis_detection = self.crisis_module.assess_crisis_semantic_only(
            user_message,
            {},
            psych_model_context=self._build_crisis_assessment_context(),
            fast_precheck=False,
        )
        if not crisis_detection.get("alert", False):
            return None

        return {
            "_analysis_kind": "crisis_first_alert",
            "crisis": {
                "has_crisis": True,
                "crisis_types": crisis_detection.get("crisis_types") or [],
                "severity_score": crisis_detection.get("severity_score", 0),
                "reason": crisis_detection.get("reason", ""),
            },
            "_crisis_detection": crisis_detection,
        }

    def _consume_completed_unified_analysis(
        self,
        task: Dict[str, Any],
        user_message: str,
        cbt_analysis: Dict,
        crisis_detection: Dict,
        current_phase: Optional[TransplantPhase],
    ) -> Tuple[Dict, Dict]:
        """如果后台分析已完成，就用它增强本轮元数据；否则立即返回规则快筛结果。"""
        event = task.get("event")
        if event is None or not event.is_set():
            return cbt_analysis, crisis_detection

        unified = task.get("result")
        if not unified:
            return cbt_analysis, crisis_detection

        return self._apply_unified_analysis_result(
            unified=unified,
            user_message=user_message,
            fallback_analysis=cbt_analysis,
            fallback_crisis=crisis_detection,
            current_phase=current_phase,
        )

    def _apply_unified_analysis_result(
        self,
        unified: Dict,
        user_message: str,
        fallback_analysis: Dict,
        fallback_crisis: Dict,
        current_phase: Optional[TransplantPhase],
    ) -> Tuple[Dict, Dict]:
        """应用后台综合分析结果，同时保留硬危机告警能力。"""
        if current_phase is None:
            current_phase = self.get_transplant_phase()

        cbt_analysis = self._analysis_from_unified(unified) or fallback_analysis
        crisis_detection = self._crisis_detection_from_unified(unified) or fallback_crisis
        hard_alert_types = {"medical_red_flag", "severe_emotional_distress"}
        if (
            (fallback_crisis or {}).get("alert", False)
            and (fallback_crisis or {}).get("alert_type") in hard_alert_types
        ):
            crisis_detection = fallback_crisis

        if crisis_detection.get("alert", False) and not (fallback_crisis or {}).get("alert", False):
            self.crisis_module._record_crisis_event(
                user_message,
                crisis_detection.get("severity_score"),
                crisis_detection,
            )
            self.crisis_module._trigger_alert(crisis_detection)

        tp = unified.get("transplant") or {}
        phase = tp.get("phase")
        if isinstance(phase, TransplantPhase) and phase != self.get_transplant_phase():
            self.set_transplant_phase(phase)

        return cbt_analysis, crisis_detection

    def _is_crisis_first_result(self, unified: Dict[str, Any]) -> bool:
        return (unified or {}).get("_analysis_kind") == "crisis_first_alert"

    def _finish_background_analysis_task(
        self,
        task: Dict[str, Any],
        user_message: str,
        response: str,
        current_phase: Optional[TransplantPhase],
        timeout_seconds: Optional[float] = None,
    ) -> None:
        """回复结束后继续等待后台分析，供后续轮次和 last_result 使用。"""
        try:
            task_lock = task.get("lock")
            if task_lock is None:
                task_lock = threading.Lock()
                task["lock"] = task_lock

            with task_lock:
                if task.get("consumed", False):
                    return

                event = task.get("event")
                if event is None:
                    return

                if timeout_seconds is None:
                    timeout_seconds = float(getattr(Config, "BACKGROUND_ANALYSIS_TIMEOUT_SECONDS", 8) or 8)
                if not event.wait(timeout=timeout_seconds):
                    return

                unified = task.get("result")
                if not unified:
                    task["consumed"] = True
                    return

                fallback = self.last_result or {}
                cbt_analysis, crisis_detection = self._apply_unified_analysis_result(
                    unified=unified,
                    user_message=user_message,
                    fallback_analysis=fallback.get("cbt_analysis", {}),
                    fallback_crisis=fallback.get("crisis_detection", {}),
                    current_phase=current_phase,
                )

                if self.last_result and self.last_result.get("response") == response:
                    crisis_first_only = self._is_crisis_first_result(unified)
                    previous_analysis = self.last_result.get("cbt_analysis", {}) or {}
                    self.last_result["cbt_analysis"] = cbt_analysis
                    self.last_result["crisis_detection"] = crisis_detection
                    if crisis_detection.get("alert", False):
                        self.last_result["response_type"] = "crisis_alert"
                        self.last_result["energy_assessment"] = None
                        self.last_result["energy_report"] = None
                    else:
                        energy_assessment = self._apply_llm_energy_assessment(
                            unified=unified,
                            user_message=user_message,
                            response=response,
                            cbt_analysis=cbt_analysis,
                        )
                        if energy_assessment:
                            self.last_result["energy_assessment"] = energy_assessment
                            self.last_result["energy_report"] = self.energy_model.get_energy_report()
                    self._update_last_assistant_metadata(
                        response=response,
                        cbt_analysis=cbt_analysis,
                        crisis_detection=crisis_detection,
                        response_type=self.last_result.get("response_type"),
                    )
                    if not crisis_first_only and previous_analysis.get("source") == "semantic_background_pending":
                        self._record_cbt_profile_signal(cbt_analysis, count_session=False)
                    if not crisis_first_only:
                        self._apply_psych_model_patch(
                            patch=unified.get("psych_model_patch") or {},
                            cbt_analysis=cbt_analysis,
                            crisis_detection=crisis_detection,
                            count_turn=True,
                        )
                    if Config.AUTO_SAVE_PROGRESS:
                        self.energy_model.save_progress()
                        if not crisis_first_only:
                            self._save_psych_model()
                task["consumed"] = True
                if self._pending_analysis_task and self._pending_analysis_task.get("task") is task:
                    self._pending_analysis_task = None
        except Exception:
            logger.exception("消费后台综合分析结果失败")

    def wait_for_background_analysis(self, timeout_seconds: Optional[float] = None) -> bool:
        """短暂等待本轮后台分析完成；用于 API done 前合并已完成的语义结果。"""
        pending = self._pending_analysis_task
        if not pending:
            return False

        task = pending.get("task")
        if not task:
            return False

        self._finish_background_analysis_task(
            task=task,
            user_message=pending.get("user_message", ""),
            response=pending.get("response", ""),
            current_phase=pending.get("current_phase"),
            timeout_seconds=timeout_seconds,
        )
        return bool(task.get("consumed", False))

    def _analysis_from_unified(self, unified: Dict) -> Dict[str, Any]:
        """将综合分析结果转换为当前链路使用的 CBT analysis 结构。"""
        if self._is_crisis_first_result(unified):
            return {}
        return {
            "emotional_state": unified.get("emotional_state", {"primary": "neutral", "severity": 1, "details": {}}),
            "cognitive_distortions": unified.get("cognitive_distortions", []),
            "problem_severity": unified.get("problem_severity", 1),
            "intervention_needed": unified.get("intervention_needed", False),
            "recommended_technique": unified.get("recommended_technique"),
        }

    def _crisis_detection_from_unified(self, unified: Dict) -> Dict[str, Any]:
        """将综合分析里的危机分数转换为当前链路的报警标记。"""
        direct_detection = (unified or {}).get("_crisis_detection")
        if isinstance(direct_detection, dict):
            return direct_detection

        crisis_info = unified.get("crisis") or {}
        severity_score = int(crisis_info.get("severity_score", 0) or 0)
        threshold = getattr(Config, "CRISIS_ALERT_THRESHOLD", 10)
        crisis_types = crisis_info.get("crisis_types") or []
        high_risk_type = any(item in {"自杀危机", "自伤危机", "身体红旗"} for item in crisis_types)
        alert = bool(crisis_info.get("has_crisis", False)) and (severity_score >= threshold or high_risk_type)
        result = {
            "alert": alert,
            "alert_type": "medical_red_flag" if alert and "身体红旗" in crisis_types else "psychological_crisis" if alert else None,
            "source": "llm_semantic_background",
            "severity_score": severity_score,
            "crisis_types": crisis_types,
            "reason": crisis_info.get("reason", ""),
        }
        alarm = build_crisis_alarm(result)
        return {
            **result,
            "crisis_level": alarm["level"],
            "alarm_level": alarm["level"],
            "alarm_label": alarm["label"],
            "alarm_action": alarm["action"],
            "alarm_message": alarm["message"],
            "should_notify": alarm["shouldNotify"],
            "requires_immediate_action": alarm["requiresImmediateAction"],
        }

    def _build_stream_response_context(
        self,
        user_message: str,
        current_phase: TransplantPhase,
        analysis: Dict,
    ) -> Dict[str, Any]:
        """为模型主导流式回复构造轻量提示上下文。"""
        context: Dict[str, Any] = {
            "phase": current_phase,
            "scenario": None,
            "template": None,
        }
        if not getattr(Config, "TRANSPLANT_SUPPORT_ENABLED", True):
            return context

        trigger = self._detect_transplant_trigger_fast(
            user_message=user_message,
            current_phase=current_phase,
            emotional_severity=analysis.get("emotional_state", {}).get("severity", 0),
        )
        if not trigger.should_trigger or not trigger.scenario:
            return context

        template = get_template(trigger.phase, trigger.scenario)
        if not template:
            return context

        context["phase"] = trigger.phase
        context["scenario"] = trigger.scenario
        context["template"] = template
        return context

    def _detect_transplant_trigger_fast(
        self,
        user_message: str,
        current_phase: TransplantPhase,
        emotional_severity: int = 0,
    ) -> TriggerResult:
        """轻量移植情境判断：只用于给模型追加提示，不直接替代回复。"""
        inferred_phase = detect_phase_from_text(user_message)
        phase = inferred_phase or current_phase
        scenario = detect_scenario(user_message, phase)
        if not scenario:
            return TriggerResult(False, phase, None, 0.0, "未命中情境关键词")

        base_conf = 0.65
        if emotional_severity >= 6:
            base_conf += 0.1

        return TriggerResult(True, phase, scenario, min(base_conf, 0.95), "命中情境关键词")

    def _should_add_cbt_guidance(self, analysis: Dict) -> bool:
        """判断是否需要追加CBT引导（危机由 crisis_module 处理）"""
        if not Config.CBT_ENABLED or not Config.AUTO_CBT_INTERVENTION:
            return False
        if (analysis or {}).get("source") == "semantic_background_pending":
            return False

        emotional = (analysis or {}).get("emotional_state", {}) or {}
        severity = int(emotional.get("severity", 0) or 0)
        distortions = (analysis or {}).get("cognitive_distortions", []) or []

        # 情绪强度达到阈值：认为“需要干预”
        if severity >= getattr(Config, "CBT_INTERVENTION_SEVERITY_THRESHOLD", 6):
            return True

        # 认知扭曲明显：允许在情绪不高时也轻量触发
        if getattr(Config, "CBT_DISTORTION_TRIGGER_ENABLED", True) and len(distortions) > 0:
            return True

        # 兼容旧字段
        if bool((analysis or {}).get("intervention_needed", False)):
            return True

        return False

    def _crisis_alert_callback(self, crisis_data: Dict):
        """危机报警回调"""
        alarm = crisis_data.get("alarm") or build_crisis_alarm(crisis_data)
        logger.debug("危机报警已记录：level=%s title=%s action=%s", alarm["level"], alarm["title"], alarm["action"])

    def _llm_unified_analyze(self, user_message: str, current_phase: TransplantPhase) -> Optional[Dict]:
        """
        一次 LLM 调用同时完成：
        - CBT 分析（情绪、认知扭曲、推荐技术）
        - 危机检测（has_crisis、severity_score）
        - 移植情境识别（should_trigger、phase、scenario）
        - 心理能量语义评估（五个维度）
        - 用户心理模型补丁（称呼、关注点、偏好、风险提示等）

        返回结构：
        {
          "emotional_state": {"primary": str, "severity": int},
          "cognitive_distortions": [...],
          "problem_severity": int,
          "intervention_needed": bool,
          "recommended_technique": str | null,
          "crisis": {
            "has_crisis": bool,
            "severity_score": int,
            "crisis_types": [...]
          },
          "transplant": {
            "should_trigger": bool,
            "phase": str,
            "scenario": str | null,
            "confidence": float
          },
          "energy_assessment": {
            "cognitive_growth": int,
            "emotion_regulation": int,
            "behavior_change": int,
            "social_connection": int,
            "self_efficacy": int
          },
          "psych_model_patch": {
            "preferred_name": str | null,
            "current_main_concerns": [...],
            "support_preferences": [...]
          }
        }
        失败时返回 None，由调用方降级到各模块独立调用。
        """
        try:
            system_prompt = (
                '你是「小芽」系统的综合分析助手，负责对骨髄移植患者的表述做五项并行分析：'
                '①CBT情绪与认知分析 ②语义安全/危机筛查 ③移植分期情境识别 '
                '④心理能量语义评估 ⑤用户心理模型补丁。'
                '只做结构化分析，不输出安慰话语或治疗方案；所有判断必须结合语义和用户心理模型，不要做关键词匹配。'
            )
            psych_context = self._build_crisis_assessment_context()

            user_prompt = (
                "请阅读下面这段患者的话，同时完成以下五项分析，严格以 JSON 输出：\n"
                "{\n"
                '  "emotional_state": {\n'
                '    "primary": 主要情绪英文代号（sadness/anxiety/anger/hopelessness/guilt/joy/calm/hope/neutral 之一）,\n'
                '    "severity": 1到10的整数\n'
                "  },\n"
                '  "cognitive_distortions": 数组，元素为 all_or_nothing/catastrophizing/negative_filter/overgeneralization/mind_reading，可为空数组,\n'
                '  "problem_severity": 1到10的整数,\n'
                '  "intervention_needed": true或false,\n'
                '  "recommended_technique": COGNITIVE_RESTRUCTURING/BEHAVIORAL_ACTIVATION/PROBLEM_SOLVING/RELAXATION_TRAINING/EXPOSURE_TECHNIQUE/MINDFULNESS/THOUGHT_RECORDING/ACTIVITY_SCHEDULING 之一，或 null,\n'
                '  "crisis": {\n'
                '    "has_crisis": true或false,\n'
                '    "severity_score": 0到20的整数（越高越严重）,\n'
                '    "crisis_types": 数组，元素为 自杀危机/自伤危机/重度抑郁/惊恐发作/急性焦虑/情绪崩溃/身体红旗，可为空数组\n'
                "  },\n"
                '  "transplant": {\n'
                f'    "phase": 当前分期（{current_phase.value} 或根据患者话语推断为 移植前准备期/移植中关键期/移植后恢复期 之一）,\n'
                '    "should_trigger": true或false（是否触发预设心理引导话术；注意：打招呼/说你好/初次见面应触发FIRST_MEET为true，化疗不适触发CHEMO_PREP，仅纯粹无关闲聊且完全匹配不到任何情境时才为false）,\n'
                '    "scenario": 情境key或null，可选值：' + json.dumps(list(TRANSPLANT_SCENARIO_GUIDES.keys()), ensure_ascii=False) + ',\n'
                '    "confidence": 0到1的小数\n'
                "  },\n"
                '  "energy_assessment": {\n'
                '    "cognitive_growth": 0到20的整数（用户是否出现新的理解、重评、反思或认知松动）,\n'
                '    "emotion_regulation": 0到25的整数（用户是否表达情绪稳定、被安抚、愿意调节或情绪强度下降）,\n'
                '    "behavior_change": 0到20的整数（用户是否表达具体行动、计划、练习或照护配合）,\n'
                '    "social_connection": 0到15的整数（用户是否表达求助、连接家属/医护/他人或愿意被陪伴）,\n'
                '    "self_efficacy": 0到20的整数（用户是否表达掌控感、希望感、能做到一点点或自我效能）,\n'
                '    "assessment_note": 简短说明本轮为什么这样加分,\n'
                '    "achievement_signals": {\n'
                '      "cognitive_restructure": true或false,\n'
                '      "mindfulness_practice": true或false,\n'
                '      "behavioral_activation": true或false,\n'
                '      "positive_emotion": true或false,\n'
                '      "high_quality_session": true或false\n'
                "    }\n"
                "  },\n"
                '  "psych_model_patch": {\n'
                '    "preferred_name": 用户明确要求的称呼或null,\n'
                '    "current_main_concerns": 从本轮语义中可稳定记录的近期关注，最多3条,\n'
                '    "cognitive_patterns": 可稳定记录的认知模式，最多3条,\n'
                '    "effective_strategies": 本轮显示可能有效的支持方式或CBT技术，最多3条,\n'
                '    "support_preferences": 用户明确表达或语义强烈暗示的支持偏好，最多3条,\n'
                '    "risk_notes": 需要后续安全关注的风险摘要，最多2条；没有则空数组,\n'
                '    "communication_style": brief/normal/gentle/structured之一或null,\n'
                '    "evidence": 以上补丁对应的用户原话证据短句，最多3条\n'
                "  }\n"
                "}\n\n"
                f"当前系统分期：{current_phase.value}\n"
                f"患者原话：{user_message}\n\n"
                f"用户长期心理模型（可为空，只作风险校准参考，当前原话优先）：\n{psych_context or '无'}\n\n"
                "危机判断要求：不要因单个词机械触发；明确自杀/自伤意图、无法保证安全通常为高危或紧急；"
                "胸痛、喘不过气、呼吸困难、持续高热、严重出血、意识异常等移植患者现场医疗风险使用“身体红旗”。\n\n"
                "心理能量评估要求：必须基于本轮完整语义，不要做关键词加分；如果只是普通闲聊或事实提问，各维度应接近0。"
                "不要为了鼓励而虚高加分；危机报警轮次可给低分或0分。\n\n"
                "心理模型补丁要求：只记录对后续个性化明显有用、且有用户原话证据的内容；不要凭空推断长期特征。"
                "如果当前原话与既有心理模型冲突，以当前原话为准；风险字段宁可短而具体。\n\n"
                f"情境key参考：{json.dumps(TRANSPLANT_SCENARIO_GUIDES, ensure_ascii=False)}\n\n"
                "只输出 JSON，不要添加任何解释或其他文字。"
            )

            resp = create_chat_completion_json(
                self.client,
                schema_model=UnifiedAnalysisPayload,
                schema_name="xiaoya_unified_analysis",
                model=Config.LLM_DETECTION_MODEL,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                temperature=Config.LLM_DETECTION_TEMPERATURE,
                max_tokens=max(900, int(getattr(Config, "LLM_DETECTION_MAX_TOKENS", 256) or 256)),
            )
            content = resp.choices[0].message.content.strip()
            data = parse_structured_json(content, UnifiedAnalysisPayload)
            if not isinstance(data, dict):
                return None

            # --- 规范化 CBT 部分 ---
            emo = data.get("emotional_state") or {}
            data["emotional_state"] = {
                "primary": emo.get("primary", "neutral"),
                "severity": int(emo.get("severity", 1) or 1),
                "details": {},
            }
            if not isinstance(data.get("cognitive_distortions"), list):
                data["cognitive_distortions"] = []
            data["problem_severity"] = int(data.get("problem_severity", 1) or 1)
            data["intervention_needed"] = bool(data.get("intervention_needed", False))

            # recommended_technique 字段转枚举
            rec_raw = data.get("recommended_technique")
            rec_enum = None
            if isinstance(rec_raw, str):
                try:
                    rec_enum = CBTTechnique[rec_raw]
                except KeyError:
                    for t in CBTTechnique:
                        if t.value == rec_raw:
                            rec_enum = t
                            break
            data["recommended_technique"] = rec_enum

            # --- 规范化危机部分 ---
            crisis = data.get("crisis") or {}
            data["crisis"] = {
                "has_crisis": bool(crisis.get("has_crisis", False)),
                "severity_score": int(crisis.get("severity_score", 0) or 0),
                "crisis_types": crisis.get("crisis_types") or [],
            }

            # --- 规范化移植情境部分 ---
            tp = data.get("transplant") or {}
            phase_str = tp.get("phase") or current_phase.value
            try:
                tp_phase = TransplantPhase(phase_str)
            except Exception:
                tp_phase = current_phase

            scenario_enum = None
            scenario_key = tp.get("scenario")
            if isinstance(scenario_key, str):
                try:
                    scenario_enum = Scenario[scenario_key]
                except KeyError:
                    scenario_enum = None

            data["transplant"] = {
                "should_trigger": bool(tp.get("should_trigger", False)),
                "phase": tp_phase,
                "scenario": scenario_enum,
                "confidence": float(tp.get("confidence", 0.0) or 0.0),
            }
            data["energy_assessment"] = data.get("energy_assessment") or {}
            data["psych_model_patch"] = data.get("psych_model_patch") or {}

            return data

        except Exception as e:
            logger.exception("综合分析 LLM 调用失败，将降级到各模块独立调用")
            return None

    def _get_messages_for_api(self, user_message: str) -> List[Dict[str, str]]:
        """
        获取用于API调用的消息列表
        如果启用增量摘要，只传入记忆中枢 + 当前问题
        否则传入完整历史
        """
        system_msg = {
            "role": "system",
            "content": self._resolve_prompt_runtime().system_prompt,
        }
        personalization_context = self._build_personalization_context()
        personalization_msg = (
            {"role": "system", "content": personalization_context}
            if personalization_context else None
        )
        if not Config.HISTORY_COMPRESSION_ENABLED:
            # 未启用压缩，返回完整历史
            history_without_system = [
                msg for msg in self.conversation_history
                if msg.get("role") != "system"
            ]
            messages = [system_msg]
            if personalization_msg:
                messages.append(personalization_msg)
            return messages + history_without_system + [{"role": "user", "content": user_message}]

        # 增量摘要模式：只传入记忆中枢 + 当前问题
        messages = [system_msg]
        if personalization_msg:
            messages.append(personalization_msg)

        # 如果有记忆中枢，添加到消息中
        if self.memory_core:
            messages.append({
                "role": "system",
                "content": f"【记忆中枢】以下是之前对话的核心信息：\n{self.memory_core}"
            })

        # 添加当前用户输入
        messages.append({"role": "user", "content": user_message})

        return messages

    def _build_personalization_context(self) -> str:
        """根据持久化心理模型构建紧凑的提示词上下文。"""
        profile = self.personalization_profile or {}
        lines = []
        context_blocks = []

        name = profile.get("preferred_name")
        if name:
            lines.append(f"- 用户称呼：{name}")

        concerns = profile.get("current_main_concerns") or []
        if concerns:
            lines.append(f"- 近期主要关注：{'；'.join(str(item) for item in concerns[-3:])}")

        recurring = profile.get("recurring_emotions") or {}
        if recurring:
            top_emotions = sorted(recurring.items(), key=lambda item: item[1], reverse=True)[:3]
            lines.append("- 常见情绪：" + "；".join(f"{emotion}({count})" for emotion, count in top_emotions))

        cognitive_patterns = profile.get("cognitive_patterns") or []
        if cognitive_patterns:
            lines.append(f"- 常见认知模式：{'；'.join(str(item) for item in cognitive_patterns[-4:])}")

        strategies = profile.get("effective_strategies") or []
        if strategies:
            lines.append(f"- 已尝试/可能有效的支持方式：{'；'.join(str(item) for item in strategies[-4:])}")

        preferences = profile.get("support_preferences") or []
        if preferences:
            lines.append(f"- 用户偏好的回应方式：{'；'.join(str(item) for item in preferences[-3:])}")

        last_emotion = profile.get("last_emotion")
        if last_emotion:
            lines.append(f"- 上次主要情绪：{last_emotion}，强度 {profile.get('last_severity', 0)}/10")

        if lines or self.memory_core:
            instruction = (
                "[用户心理模型] 以下信息来自该用户过往对话的长期心理模型，只用于提升个性化陪伴质量。"
                "回应时要自然使用这些线索：优先承接用户当前原话；可以记住称呼、近期关注、偏好和已尝试方式；"
                "不要直接说“根据你的心理模型/档案”；不要把模型内容机械复述给用户；如果当前用户原话与模型冲突，以当前原话为准。"
            )
            context_blocks.append(instruction + "\n" + "\n".join(lines))

        cohort_context = get_cohort_learning_context(current_user_id=self.user_id)
        if cohort_context:
            context_blocks.append(cohort_context)

        if not context_blocks:
            return ""

        return "\n\n".join(context_blocks)

    def _build_crisis_assessment_context(self) -> str:
        """为语义安全评估构建紧凑的用户个性化上下文。"""
        profile = self.personalization_profile or {}
        lines = []
        if self.memory_core:
            lines.append(f"- 长期记忆摘要：{self.memory_core[:500]}")

        for label, key, limit in [
            ("近期主要关注", "current_main_concerns", 4),
            ("常见认知模式", "cognitive_patterns", 4),
            ("风险提示", "risk_notes", 4),
            ("偏好支持方式", "support_preferences", 3),
        ]:
            values = profile.get(key) or []
            if values:
                lines.append(f"- {label}：{'；'.join(str(item) for item in values[-limit:])}")

        recurring = profile.get("recurring_emotions") or {}
        if recurring:
            top_emotions = sorted(recurring.items(), key=lambda item: item[1], reverse=True)[:3]
            lines.append("- 常见情绪：" + "；".join(f"{emotion}({count})" for emotion, count in top_emotions))

        last_emotion = profile.get("last_emotion")
        if last_emotion:
            lines.append(f"- 最近情绪：{last_emotion}，强度 {profile.get('last_severity', 0)}/10")

        return "\n".join(lines)

    def _update_memory_core(self, user_message: str, response: str, cbt_analysis: Dict, crisis_detection: Dict):
        """
        更新记忆中枢：每轮对话后生成增量摘要
        将本轮对话的核心信息融合到记忆中枢中
        """
        try:
            # 提取本轮关键信息
            emotional_state = cbt_analysis.get("emotional_state", {})
            emotion = emotional_state.get("primary", "未知")
            severity = emotional_state.get("severity", 0)
            distortions = cbt_analysis.get("cognitive_distortions", [])
            crisis_level = 10 if crisis_detection.get("alert", False) else 0

            # 构建本轮摘要提示
            if self.memory_core:
                # 已有记忆中枢，进行增量更新
                prompt = (
                    f"你是记忆中枢管理器。请将本轮对话的核心信息融合到现有记忆中，生成更新后的记忆摘要。\n\n"
                    f"【现有记忆】\n{self.memory_core}\n\n"
                    f"【本轮对话】\n"
                    f"用户: {user_message}\n"
                    f"小芽: {response}\n\n"
                    f"【本轮分析】\n"
                    f"- 主要情绪: {emotion} (强度: {severity}/10)\n"
                    f"- 认知扭曲: {', '.join(distortions) if distortions else '无'}\n"
                    f"- 危机等级: {crisis_level}/10\n\n"
                    f"请生成更新后的记忆摘要，要求：\n"
                    f"1. 保留用户的核心问题、情绪特征、重要进展\n"
                    f"2. 融合本轮新信息（如有重要变化）\n"
                    f"3. 删除过时或不重要的信息\n"
                    f"4. 控制在{Config.INCREMENTAL_SUMMARY_MAX_WORDS}字以内\n"
                    f"5. 用第三人称客观描述\n\n"
                    f"只输出更新后的记忆摘要，不要添加其他内容："
                )
            else:
                # 首次生成记忆中枢
                prompt = (
                    f"你是记忆中枢管理器。请为首轮对话生成核心信息摘要。\n\n"
                    f"【本轮对话】\n"
                    f"用户: {user_message}\n"
                    f"小芽: {response}\n\n"
                    f"【本轮分析】\n"
                    f"- 主要情绪: {emotion} (强度: {severity}/10)\n"
                    f"- 认知扭曲: {', '.join(distortions) if distortions else '无'}\n"
                    f"- 危机等级: {crisis_level}/10\n\n"
                    f"请生成记忆摘要，要求：\n"
                    f"1. 提取用户的核心问题和关注点\n"
                    f"2. 记录用户的情绪状态和心理特征\n"
                    f"3. 控制在{Config.INCREMENTAL_SUMMARY_MAX_WORDS}字以内\n"
                    f"4. 用第三人称客观描述\n\n"
                    f"只输出记忆摘要，不要添加其他内容："
                )

            # 调用 LLM 生成/更新记忆中枢
            api_response = self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": "你是一个专业的记忆中枢管理器，擅长提取和融合对话中的核心信息。"},
                    {"role": "user", "content": prompt}
                ],
                temperature=0.3,  # 使用较低温度以获得更稳定的摘要
                max_tokens=600
            )

            new_memory = api_response.choices[0].message.content.strip()
            self.memory_core = new_memory
            self._save_psych_model()

            # 可选：打印记忆更新日志（调试用）
            if os.getenv("DEBUG_MEMORY_CORE", "false").lower() == "true":
                logger.debug("记忆中枢已更新: %s...", new_memory[:100])

        except Exception as e:
            logger.exception("更新记忆中枢失败")
            # 失败时使用简单的记录
            if not self.memory_core:
                self.memory_core = f"用户表达了关于心理健康的问题，情绪状态为{emotion}。"
                self._save_psych_model()

    def _load_persistent_data(self):
        """加载持久化数据"""
        try:
            # 加载能量进度
            self.energy_model.load_progress("energy_progress.json")
            # 加载危机历史
            self.crisis_module.load_crisis_history("crisis_history.json")
            # 加载用户状态和长期心理模型
            self._load_user_state()
            if self.psych_model_enabled:
                self._load_psych_model()
        except Exception as e:
            logger.exception("加载持久化数据失败")

    def _load_user_state(self, filename: str = "user_state.json"):
        """加载用户状态（如骨髓移植分期）"""
        try:
            if database_storage_enabled() and self.user_id:
                data = get_database_repository().load_user_state(str(self.user_id))
                if isinstance(data, dict):
                    self.user_state.update(data)
                return
            if database_storage_enabled():
                return
            filepath = self._get_psych_filepath(filename)
            if not os.path.exists(filepath):
                return
            with open(filepath, "r", encoding="utf-8") as f:
                data = json.load(f)
            if isinstance(data, dict):
                self.user_state.update(data)
        except Exception as e:
            logger.exception("加载用户状态失败")

    def _save_user_state(self, filename: str = "user_state.json"):
        """保存用户状态（如骨髓移植分期）"""
        try:
            if database_storage_enabled() and self.user_id:
                get_database_repository().save_user_state(
                    user_id=str(self.user_id),
                    safe_user_id=self._safe_storage_user_id(),
                    state=dict(self.user_state or {}),
                    psych_model_dir=self.psych_model_dir,
                )
                return
            self._write_json_atomic(self._get_psych_filepath(filename), self.user_state)
        except Exception as e:
            logger.exception("保存用户状态失败")

    def _load_psych_model(self, filename: str = PSYCH_MODEL_FILENAME):
        """加载用户级长期心理模型。"""
        try:
            if not self.psych_model_enabled:
                return
            if database_storage_enabled() and self.user_id:
                data = get_database_repository().load_psych_model(str(self.user_id))
                if isinstance(data, dict):
                    profile = data.get("cbt_user_profile")
                    if isinstance(profile, dict):
                        self.cbt_module.user_profile.update(profile)

                    personalization = data.get("personalization_profile")
                    if isinstance(personalization, dict):
                        self.personalization_profile = {
                            **self._default_personalization_profile(),
                            **personalization,
                        }

                    user_state = data.get("user_state")
                    if isinstance(user_state, dict):
                        self.user_state.update(user_state)

                    memory_core = data.get("memory_core")
                    if memory_core is None or isinstance(memory_core, str):
                        self.memory_core = memory_core
                    return
            if database_storage_enabled():
                return
            filepath = self._get_psych_filepath(filename)
            if not os.path.exists(filepath):
                return
            data = None
            for attempt in range(3):
                try:
                    with open(filepath, "r", encoding="utf-8") as f:
                        data = json.load(f)
                    break
                except PermissionError:
                    if attempt == 2:
                        raise
                    time.sleep(0.05)
            if not isinstance(data, dict):
                return

            profile = data.get("cbt_user_profile")
            if isinstance(profile, dict):
                self.cbt_module.user_profile.update(profile)

            personalization = data.get("personalization_profile")
            if isinstance(personalization, dict):
                self.personalization_profile = {
                    **self._default_personalization_profile(),
                    **personalization,
                }

            user_state = data.get("user_state")
            if isinstance(user_state, dict):
                self.user_state.update(user_state)

            memory_core = data.get("memory_core")
            if memory_core is None or isinstance(memory_core, str):
                self.memory_core = memory_core
        except Exception:
            logger.exception("加载用户心理模型失败")

    def _save_psych_model(self, filename: str = PSYCH_MODEL_FILENAME):
        """保存用户级长期心理模型。"""
        if not self.psych_model_enabled:
            return
        data = {
            "modelVersion": 1,
            "userId": self.user_id,
            "updatedAt": datetime.now().replace(microsecond=0).isoformat(),
            "memory_core": self.memory_core,
            "user_state": dict(self.user_state or {}),
            "cbt_user_profile": dict(getattr(self.cbt_module, "user_profile", {}) or {}),
            "personalization_profile": dict(self.personalization_profile or {}),
        }
        try:
            with self._psych_model_lock:
                if database_storage_enabled() and self.user_id:
                    get_database_repository().save_psych_model(
                        user_id=str(self.user_id),
                        safe_user_id=self._safe_storage_user_id(),
                        psych_model=data,
                        psych_model_dir=self.psych_model_dir,
                    )
                else:
                    self._write_json_atomic(self._get_psych_filepath(filename), data)
            if filename == self.PSYCH_MODEL_FILENAME:
                mark_cohort_learning_dirty()
        except Exception:
            logger.exception("保存用户心理模型失败")

    def save_all_progress(self):
        """保存所有进度数据"""
        try:
            # 保存对话历史
            self.save_history()
            # 保存能量进度
            self.energy_model.save_progress()
            # 保存危机历史
            self.crisis_module.save_crisis_history()
            # 保存用户状态
            self._save_user_state()
            # 保存用户级心理模型
            self._save_psych_model()
            logger.info("所有进度已保存")
        except Exception as e:
            logger.exception("保存进度失败")

    def get_comprehensive_report(self) -> Dict[str, any]:
        """获取综合报告"""
        return {
            "cbt_progress": self.cbt_module.get_progress_report(),
            "energy_report": self.energy_model.get_energy_report(),
            "crisis_report": self.crisis_module.get_crisis_history_report(),
            "session_count": len([msg for msg in self.conversation_history if msg["role"] == "user"])
        }

    def get_grounding_exercise(self) -> str:
        """获取正念接地练习"""
        return self.crisis_module.get_grounding_exercise()

    def load_history(self, filename: str = "chat_history.json"):
        """从当前存储后端加载对话历史。"""
        try:
            if database_storage_enabled():
                history = None
                session_id = getattr(self, "storage_session_id", None)
                if session_id:
                    history = get_database_repository().load_session_history(str(session_id))
                elif self.user_id and getattr(self, "storage_source", None):
                    payload = get_database_repository().load_user_conversations(
                        str(self.user_id),
                        include_history=True,
                    )
                    source = str(getattr(self, "storage_source", None) or "cli")
                    conversation_id = str(getattr(self, "storage_conversation_id", None) or source)
                    for item in payload.get("conversations") or []:
                        if item.get("source") == source and str(item.get("conversationId")) == conversation_id:
                            history = item.get("history")
                            break
                if isinstance(history, list):
                    self.conversation_history = history
                    return
                return
            filepath = self._get_filepath(filename)
            with open(filepath, 'r', encoding='utf-8') as f:
                self.conversation_history = json.load(f)
        except FileNotFoundError:
            logger.warning("历史文件 %s 不存在", filename)
        except Exception as e:
            logger.exception("加载历史文件失败")

    def reset(self):
        """
        重置所有数据，恢复到初始状态
        清除对话历史、用户状态、CBT档案、能量进度、危机历史，并删除所有持久化文件
        """
        # 1. 重置对话历史（只保留系统提示词）
        self.conversation_history = [
            {"role": "system", "content": self.system_prompt}
        ]

        # 2. 重置记忆中枢
        self.memory_core = None
        self.personalization_profile = self._default_personalization_profile()

        # 3. 重置用户状态
        self.user_state = {
            "transplant_phase": TransplantPhase.PREP.value
        }

        # 4. 重新初始化 CBT 模块（重置用户档案）
        self.cbt_module = CBTModule()

        # 5. 重新初始化能量模型（重置所有进度）
        self.energy_model = PsychologicalEnergyModel(data_dir=self.psych_model_dir)
        self.energy_model.user_id = self.user_id
        self.energy_model.safe_user_id = self._safe_storage_user_id()
        self.energy_model.psych_model_dir = self.psych_model_dir

        # 6. 重新初始化危机干预模块（重置危机历史）
        self.crisis_module = CrisisInterventionModule(
            alert_callback=self._crisis_alert_callback,
            data_dir=self.psych_model_dir,
        )
        self.crisis_module.user_id = self.user_id
        self.crisis_module.safe_user_id = self._safe_storage_user_id()
        self.crisis_module.psych_model_dir = self.psych_model_dir

        if database_storage_enabled():
            repo = get_database_repository()
            session_id = getattr(self, "storage_session_id", None)
            if session_id:
                repo.clear_session_runtime(str(session_id))
            if self.user_id:
                repo.clear_user_runtime(str(self.user_id))
            return {
                "success": True,
                "deleted_files": [],
                "message": "所有数据已在数据库中重置，系统已恢复到初始状态"
            }

        # 7. 删除所有持久化文件
        files_to_delete = [
            ("chat_history.json", self._get_filepath("chat_history.json")),
            ("agent_state.json", self._get_filepath("agent_state.json")),
            ("user_state.json", self._get_psych_filepath("user_state.json")),
            ("energy_progress.json", self._get_psych_filepath("energy_progress.json")),
            ("crisis_history.json", self._get_psych_filepath("crisis_history.json")),
            (self.PSYCH_MODEL_FILENAME, self._get_psych_filepath(self.PSYCH_MODEL_FILENAME)),
        ]

        deleted_files = []
        seen_paths = set()
        for filename, filepath in files_to_delete:
            try:
                normalized_path = os.path.abspath(filepath)
                if normalized_path in seen_paths:
                    continue
                seen_paths.add(normalized_path)
                if os.path.exists(filepath):
                    os.remove(filepath)
                    deleted_files.append(filename)
            except Exception as e:
                logger.exception("删除文件 %s 失败", filename)

        return {
            "success": True,
            "deleted_files": deleted_files,
            "message": "所有数据已重置，系统已恢复到初始状态"
        }
    def _get_filepath(self, filename: str) -> str:
        """获取文件的完整路径（统一放在当前 agent 的数据目录下）"""
        if os.path.isabs(filename):
            return filename
        if not database_storage_enabled():
            os.makedirs(self.data_dir, exist_ok=True)
        return os.path.join(self.data_dir, filename)

    def _get_psych_filepath(self, filename: str) -> str:
        """获取用户级心理模型文件路径。"""
        if os.path.isabs(filename):
            return filename
        if not database_storage_enabled():
            os.makedirs(self.psych_model_dir, exist_ok=True)
        return os.path.join(self.psych_model_dir, filename)

    def _write_json_atomic(self, filepath: str, data: Any) -> None:
        if database_storage_enabled():
            return
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        temp_path = f"{filepath}.{os.getpid()}.{threading.get_ident()}.tmp"
        with open(temp_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        os.replace(temp_path, filepath)
