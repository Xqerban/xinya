"""
增强版对话智能体 - 集成CBT、心理能量和危机干预
"""
from openai import OpenAI
from typing import List, Dict, Optional, Iterator, Any, Tuple
import json
import os
import threading
import logging
from config import Config
from cbt_module import CBTModule, CBTTechnique
from energy_model import PsychologicalEnergyModel
from crisis_module import CrisisInterventionModule
from transplant_support import (
    TransplantPhase,
    choose_intervention,
    get_template,
    Scenario,
    detect_phase_from_text,
    detect_scenario,
    TriggerResult,
)
from keyword_library import (
    CASUAL_INPUTS,
    MEDICAL_RED_FLAG_KEYWORDS,
    SEVERE_EMOTIONAL_DISTRESS_KEYWORDS,
    TRANSPLANT_SCENARIO_GUIDES,
    contains_any,
)

FAST_OPENINGS = {
    "hopelessness": "我在这里，先陪你把这一刻撑过去。",
    "sadness": "我在听，你现在一定很不好受。",
    "anxiety": "别急，我在，我们先慢一点。",
    "anger": "我听到了你的憋闷和难受。",
    "guilt": "先别急着责怪自己，我在听。",
    "joy": "听到这里，我也替你感到一点开心。",
    "calm": "我在，我们可以慢慢聊。",
    "hope": "我在，这份期待很珍贵。",
    "neutral": "我在，你可以慢慢说。",
}

logger = logging.getLogger(__name__)
USER_FACING_ERROR_MESSAGE = "我刚刚有点卡住了，我们再试一次，好吗？"

class EnhancedChatAgent:
    """增强版对话智能体类 - 集成CBT、心理能量和危机干预"""

    def __init__(self, data_dir: Optional[str] = None, load_persistent_data: bool = True):
        """初始化智能体"""
        Config.validate_config()
        self.data_dir = os.path.abspath(data_dir or Config.DATA_DIR)
        os.makedirs(self.data_dir, exist_ok=True)

        self.client = OpenAI(
            api_key=Config.API_KEY,
            base_url=Config.API_BASE_URL
        )

        self.model = Config.MODEL_NAME
        self.temperature = Config.TEMPERATURE
        self.max_tokens = Config.MAX_TOKENS
        self.system_prompt = Config.SYSTEM_PROMPT
        self.last_result: Optional[Dict[str, Any]] = None
        self._pending_analysis_task: Optional[Dict[str, Any]] = None

        # 对话历史
        self.conversation_history: List[Dict[str, str]] = [
            {"role": "system", "content": self.system_prompt}
        ]
        
        # 增量摘要：记忆中枢（每轮对话后更新）
        self.memory_core: Optional[str] = None

        # 初始化增强模块
        self.cbt_module = CBTModule()
        self.energy_model = PsychologicalEnergyModel(data_dir=self.data_dir)

        # 危机干预模块（带报警回调）
        self.crisis_module = CrisisInterventionModule(
            alert_callback=self._crisis_alert_callback,
            data_dir=self.data_dir,
        )

        # 加载历史数据
        if load_persistent_data:
            self._load_persistent_data()

        # 用户状态（分期等）
        self.user_state: Dict[str, any] = {
            "transplant_phase": TransplantPhase.PREP.value
        }
        if load_persistent_data:
            self._load_user_state()

    def build_fast_opening(self, user_message: str) -> str:
        """生成两阶段流式的首句，用于快速首响。"""
        emotional = self.cbt_module._detect_emotion(user_message)
        primary = emotional.get("primary", "neutral")
        opening = FAST_OPENINGS.get(primary, FAST_OPENINGS["neutral"])
        if primary in {"sadness", "hopelessness", "anxiety", "anger", "guilt"}:
            return opening + "\n\n"
        return opening + " "

    def chat(self, user_message: str) -> Dict[str, any]:
        """
        增强版对话 - 集成CBT、能量评估和危机干预

        Args:
            user_message: 用户输入的消息

        Returns:
            包含回复和分析结果的字典
        """
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
                )
                self.crisis_module._trigger_alert({
                    "alert": True,
                    "alert_type": crisis_detection.get("alert_type", "psychological_crisis"),
                    "source": crisis_detection.get("source"),
                })
        else:
            crisis_detection = self.crisis_module.assess_crisis_semantic_only(
                user_message,
                cbt_analysis.get("emotional_state", {})
            )
            if crisis_detection.get("alert", False):
                self.crisis_module._record_crisis_event(
                    user_message,
                    crisis_detection.get("severity_score"),
                )
                self.crisis_module._trigger_alert({
                    "alert": True,
                    "alert_type": crisis_detection.get("alert_type", "psychological_crisis"),
                    "source": crisis_detection.get("source"),
                })

        # 3. 准备对话数据
        conversation_data = {
            "user_message": user_message,
            "analysis": cbt_analysis,
            "crisis_detection": crisis_detection
        }

        # 4. 生成回复
        if crisis_detection.get("alert", False):
            # 对 crisis 直接报警，不进行其他操作
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

        # 序列化分析数据以支持JSON存储
        serialized_cbt_analysis = self._serialize_analysis_data(cbt_analysis)
        serialized_crisis_detection = self._serialize_analysis_data(crisis_detection)

        self.conversation_history.append({
            "role": "assistant",
            "content": response,
            "metadata": {
                "response_type": response_type,
                "cbt_analysis": serialized_cbt_analysis,
                "crisis_detection": serialized_crisis_detection,
                "user_state": dict(self.user_state)
            }
        })

        # 6. 更新CBT用户档案
        self.cbt_module.update_user_profile(cbt_analysis, user_message)

        # 7. 评估心理能量（如果不是危机干预）
        energy_assessment = None
        if not crisis_detection.get("alert", False):
            conversation_data["cbt_response"] = response
            energy_assessment = self.energy_model.assess_conversation_quality(conversation_data)

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
            "energy_report": self.energy_model.get_energy_report() if energy_assessment else None
        }
        self.last_result = result
        return result

    def stream_chat(self, user_message: str) -> Iterator[str]:
        """
        流式链路采用“本地硬安全快筛 + 回复模型先行 + 语义分析后台增强”：
        - 首 token 前不等待危机 LLM，避免一做语义判断就变慢；
        - 危机语义判断在后台并行完成，完成后更新 last_result 和报警状态；
        - 身体红旗仍用本地关键词硬快筛，保证医疗安全提醒足够直接；
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
        if self._should_start_background_analysis(user_message):
            analysis_task = self._start_unified_analysis_task(user_message, current_phase)

        safety_alert = self._build_safety_alert(user_message, cbt_analysis, crisis_detection)
        if safety_alert:
            crisis_detection = {
                **crisis_detection,
                "alert": True,
                "alert_type": safety_alert["alert_type"],
            }
            conversation_data["crisis_detection"] = crisis_detection
            if safety_alert.get("notify", False):
                self.crisis_module._record_crisis_event(user_message)
                self.crisis_module._trigger_alert({
                    "alert": True,
                    "alert_type": safety_alert["alert_type"],
                })
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
                run_post_analysis=False,
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
            run_post_analysis=False,
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
        run_post_analysis: bool = False,
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

        self.conversation_history.append({
            "role": "assistant",
            "content": response,
            "metadata": {
                "response_type": response_type,
                "cbt_analysis": serialized_cbt_analysis,
                "crisis_detection": serialized_crisis_detection,
                "user_state": dict(self.user_state)
            }
        })

        self.cbt_module.update_user_profile(cbt_analysis, user_message)

        energy_assessment = None
        if not crisis_detection.get("alert", False):
            conversation_data["cbt_response"] = response
            energy_assessment = self.energy_model.assess_conversation_quality(conversation_data)

        if Config.HISTORY_COMPRESSION_ENABLED:
            threading.Thread(
                target=self._update_memory_core,
                args=(user_message, response, cbt_analysis, crisis_detection),
                daemon=True
            ).start()

        if run_post_analysis and analysis_task is None:
            threading.Thread(
                target=self._run_post_response_analysis,
                args=(user_message, response, current_phase),
                daemon=True,
            ).start()

        result = {
            "response": response,
            "response_type": response_type,
            "cbt_analysis": cbt_analysis,
            "crisis_detection": crisis_detection,
            "energy_assessment": energy_assessment,
            "energy_report": self.energy_model.get_energy_report() if energy_assessment else None
        }

        if analysis_task is not None and not analysis_task["event"].is_set():
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

    def _stream_static_response(
        self,
        user_message: str,
        response: str,
        response_type: str,
        cbt_analysis: Dict,
        crisis_detection: Dict,
        conversation_data: Dict,
        current_phase: Optional[TransplantPhase] = None,
        run_post_analysis: bool = False,
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
            run_post_analysis=run_post_analysis,
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
        run_post_analysis: bool = False,
        response_context: Optional[Dict[str, Any]] = None,
        analysis_task: Optional[Dict[str, Any]] = None,
    ) -> Iterator[str]:
        """流式输出 CBT 回复，并在结束后补做落库与评估。"""
        response_parts: List[str] = []

        try:
            stream = self._create_response_stream(user_message, analysis, response_context=response_context)
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
        except Exception as e:
            logger.exception("流式回复生成失败")
            response_parts = [USER_FACING_ERROR_MESSAGE]
            yield USER_FACING_ERROR_MESSAGE
        finally:
            full_response = "".join(response_parts)
            self.last_result = self._finalize_chat_turn(
                user_message=user_message,
                response=full_response,
                response_type=response_type,
                cbt_analysis=analysis,
                crisis_detection=crisis_detection,
                conversation_data=conversation_data,
                current_phase=current_phase,
                run_post_analysis=run_post_analysis,
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

    def get_history(self) -> List[Dict[str, str]]:
        """获取对话历史"""
        return self.conversation_history.copy()

    def _serialize_analysis_data(self, data):
        """序列化分析数据，将枚举值转换为字符串"""
        if isinstance(data, dict):
            return {key: self._serialize_analysis_data(value) for key, value in data.items()}
        elif isinstance(data, list):
            return [self._serialize_analysis_data(item) for item in data]
        elif hasattr(data, 'value'):  # 枚举对象
            return data.value
        elif hasattr(data, '__str__'):  # 其他可序列化对象
            return str(data)
        else:
            return data

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
        """保存对话历史到文件"""
        filepath = self._get_filepath(filename)
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(self.conversation_history, f, ensure_ascii=False, indent=2)

    def _generate_cbt_response(self, user_message: str, analysis: Dict) -> str:
        """生成回复（含CBT引导）——主回复与CBT建议合并为一次LLM调用"""
        try:
            api_response = self._create_response_stream(user_message, analysis, stream=False)
            return api_response.choices[0].message.content
        except Exception as e:
            logger.exception("普通回复生成失败")
            return USER_FACING_ERROR_MESSAGE

    def _create_response_stream(
        self,
        user_message: str,
        analysis: Dict,
        stream: bool = True,
        response_context: Optional[Dict[str, Any]] = None,
    ):
        """创建回复请求，支持普通返回和流式返回。"""
        messages_for_api = self._get_messages_for_api(user_message)
        response_context = response_context or {}
        need_cbt = self._should_add_cbt_guidance(analysis)

        extra_system_messages = [{
            "role": "system",
            "content": (
                "[实时回复要求] 直接回应用户原话，第一句自然、具体、共情。"
                "总长度控制在80到180字；不要长篇大论，不要反复表达同一个点。"
                "只给一个核心安慰点和一个很小的下一步或问题。"
                "不要依赖本地关键词标签；直接根据用户原话的语义判断是否需要轻量CBT。"
                "若用户表达焦虑、低落、绝望、愧疚、愤怒、灾难化或全或无思维，"
                "可以自然融入一个很小的CBT方向引导；若只是闲聊或事实问题，不要强行CBT。"
                "保持积极乐观但不空泛，不承诺治疗结果。"
                "若用户出现安全风险或明显身体红旗，优先建议联系护士/医生，不继续CBT。"
                "输出必须是普通纯文本，不使用Markdown，不使用标题、列表、加粗、引用、代码块或链接语法。"
            )
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
            max_tokens = 280
        else:
            user_msg = messages_for_api[-1]
            messages = messages_for_api[:-1] + extra_system_messages + [user_msg]
            max_tokens = 240

        return self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=self.temperature,
            max_tokens=max_tokens,
            stream=stream,
        )

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
                "response_type": "medical_safety_alert",
                "notify": True,
                "response": (
                    "我需要先提醒你：这种身体情况在移植病房里要优先让医护知道。"
                    "请现在按床头呼叫铃，或请身边人马上联系护士/医生。"
                    "先把身体安全稳住，你已经在正确地求助。"
                ),
            }

        if (crisis_detection or {}).get("alert", False):
            return {
                "alert_type": "psychological_crisis",
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
        """流式入口不做心理危机规则判断，交给后台语义判断补充。"""
        if not getattr(Config, "CRISIS_DETECTION_ENABLED", True):
            return {"alert": False, "source": "disabled"}

        emotional_state = (analysis or {}).get("emotional_state", {})

        if getattr(Config, "CRISIS_LLM_STREAM_BLOCKING_ENABLED", False):
            return self.crisis_module.assess_crisis_semantic_only(
                user_message,
                emotional_state,
            )

        return {
            "alert": False,
            "alert_type": None,
            "source": "semantic_background_pending",
            "severity_score": 0,
            "crisis_types": [],
            "reason": "流式回复不使用心理危机关键词规则；危机状态由后台语义分析补充。",
        }

    def _has_medical_red_flag(self, user_message: str) -> bool:
        """移植病房身体红旗：先转医护，不继续心理引导。"""
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

        if crisis_detection.get("source") == "llm_semantic":
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
            or contains_any(user_message, SEVERE_EMOTIONAL_DISTRESS_KEYWORDS)
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
                task["result"] = self._llm_unified_analyze(user_message, current_phase)
            except Exception:
                logger.exception("后台综合分析失败")
                task["result"] = None
            finally:
                task["event"].set()

        threading.Thread(target=run_analysis, daemon=True).start()
        return task

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

        if crisis_detection.get("alert", False) and not (fallback_crisis or {}).get("alert", False):
            self.crisis_module._record_crisis_event(
                user_message,
                crisis_detection.get("severity_score"),
            )
            self.crisis_module._trigger_alert({
                "alert": True,
                "alert_type": crisis_detection.get("alert_type", "psychological_crisis"),
                "source": crisis_detection.get("source"),
            })

        tp = unified.get("transplant") or {}
        phase = tp.get("phase")
        if isinstance(phase, TransplantPhase) and phase != self.get_transplant_phase():
            self.set_transplant_phase(phase)

        return cbt_analysis, crisis_detection

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
                    timeout_seconds = float(os.getenv("BACKGROUND_ANALYSIS_TIMEOUT_SECONDS", "8"))
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
                    self.last_result["cbt_analysis"] = cbt_analysis
                    self.last_result["crisis_detection"] = crisis_detection
                    if crisis_detection.get("alert", False):
                        self.last_result["response_type"] = "crisis_alert"
                        self.last_result["energy_assessment"] = None
                        self.last_result["energy_report"] = None
                    self._update_last_assistant_metadata(
                        response=response,
                        cbt_analysis=cbt_analysis,
                        crisis_detection=crisis_detection,
                        response_type=self.last_result.get("response_type"),
                    )
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

    def _should_run_preflight_analysis(
        self,
        user_message: str,
        cbt_analysis: Dict,
        crisis_detection: Dict,
        response_context: Optional[Dict[str, Any]] = None,
    ) -> bool:
        """规则未命中有效信号时，才前置调用一次轻量综合分析。"""
        if not (getattr(Config, "CBT_LLM_ENABLED", True) or getattr(Config, "CRISIS_LLM_DETECTION_ENABLED", True) or getattr(Config, "TRANSPLANT_LLM_SCENARIO_ENABLED", True)):
            return False

        normalized = (user_message or "").strip().lower()
        if len(normalized) <= 8 and normalized in CASUAL_INPUTS:
            return False

        emotional = (cbt_analysis or {}).get("emotional_state", {}) or {}
        primary = emotional.get("primary", "neutral")
        severity = int(emotional.get("severity", 0) or 0)
        distortions = (cbt_analysis or {}).get("cognitive_distortions", []) or []
        has_cbt_signal = self._should_add_cbt_guidance(cbt_analysis)
        has_crisis_signal = bool((crisis_detection or {}).get("alert", False))
        has_transplant_signal = bool((response_context or {}).get("scenario"))

        return not any([
            primary != "neutral" and severity > 1,
            distortions,
            has_cbt_signal,
            has_crisis_signal,
            has_transplant_signal,
        ])

    def _analysis_from_unified(self, unified: Dict) -> Dict[str, Any]:
        """将综合分析结果转换为当前链路使用的 CBT analysis 结构。"""
        return {
            "emotional_state": unified.get("emotional_state", {"primary": "neutral", "severity": 1, "details": {}}),
            "cognitive_distortions": unified.get("cognitive_distortions", []),
            "problem_severity": unified.get("problem_severity", 1),
            "intervention_needed": unified.get("intervention_needed", False),
            "recommended_technique": unified.get("recommended_technique"),
        }

    def _crisis_detection_from_unified(self, unified: Dict) -> Dict[str, Any]:
        """将综合分析里的危机分数转换为当前链路的报警标记。"""
        crisis_info = unified.get("crisis") or {}
        severity_score = int(crisis_info.get("severity_score", 0) or 0)
        threshold = getattr(Config, "CRISIS_ALERT_THRESHOLD", 10)
        alert = bool(crisis_info.get("has_crisis", False)) and severity_score >= threshold
        return {
            "alert": alert,
            "alert_type": "psychological_crisis" if alert else None,
            "source": "llm_semantic_background",
            "severity_score": severity_score,
            "crisis_types": crisis_info.get("crisis_types") or [],
            "reason": crisis_info.get("reason", ""),
        }

    def _build_stream_response_context_from_unified(
        self,
        unified: Dict,
        current_phase: TransplantPhase,
    ) -> Dict[str, Any]:
        """基于前置综合分析结果构造移植情境提示上下文。"""
        context: Dict[str, Any] = {
            "phase": current_phase,
            "scenario": None,
            "template": None,
        }
        if not getattr(Config, "TRANSPLANT_SUPPORT_ENABLED", True):
            return context

        transplant = unified.get("transplant") or {}
        phase = transplant.get("phase") if isinstance(transplant.get("phase"), TransplantPhase) else current_phase
        scenario = transplant.get("scenario")
        if not transplant.get("should_trigger") or not isinstance(scenario, Scenario):
            context["phase"] = phase
            return context

        template = get_template(phase, scenario)
        if not template:
            context["phase"] = phase
            return context

        context["phase"] = phase
        context["scenario"] = scenario
        context["template"] = template
        return context

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

    def _run_post_response_analysis(
        self,
        user_message: str,
        response: str,
        current_phase: Optional[TransplantPhase],
    ):
        """模型主导流式链路下，回复后异步补做 LLM 分析与状态校正。"""
        if current_phase is None:
            current_phase = self.get_transplant_phase()

        if not (getattr(Config, "CBT_LLM_ENABLED", True) or getattr(Config, "CRISIS_LLM_DETECTION_ENABLED", True)):
            return

        try:
            unified = self._llm_unified_analyze(user_message, current_phase)
            if not unified:
                return

            tp = unified.get("transplant") or {}
            phase = tp.get("phase")
            if isinstance(phase, TransplantPhase) and phase != self.get_transplant_phase():
                self.set_transplant_phase(phase)
        except Exception:
            logger.exception("流式后置分析失败")

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
        logger.critical(
            "紧急危机报警！已触发报警标记 alert=true，建议立即联系医护、家属或紧急救助。"
        )

    def _llm_unified_analyze(self, user_message: str, current_phase: TransplantPhase) -> Optional[Dict]:
        """
        一次 LLM 调用同时完成：
        - CBT 分析（情绪、认知扭曲、推荐技术）
        - 危机检测（has_crisis、severity_score）
        - 移植情境识别（should_trigger、phase、scenario）

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
          }
        }
        失败时返回 None，由调用方降级到各模块独立调用。
        """
        try:
            system_prompt = (
                '你是「小芽」系统的综合分析助手，负责对骨髄移植患者的表述做三项并行分析：'
                '①CBT情绪与认知分析 ②心理危机筛查 ③移植分期情境识别。'
                '只做结构化分析，不输出安慰话语或治疗方案。'
            )

            user_prompt = (
                "请阅读下面这段患者的话，同时完成以下三项分析，严格以 JSON 输出：\n"
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
                '    "crisis_types": 数组，元素为 自杀危机/自伤危机/重度抑郁/惊恐发作/急性焦虑/情绪崩溃，可为空数组\n'
                "  },\n"
                '  "transplant": {\n'
                f'    "phase": 当前分期（{current_phase.value} 或根据患者话语推断为 移植前准备期/移植中关键期/移植后恢复期 之一）,\n'
                '    "should_trigger": true或false（是否触发预设心理引导话术；注意：打招呼/说你好/初次见面应触发FIRST_MEET为true，化疗不适触发CHEMO_PREP，仅纯粹无关闲聊且完全匹配不到任何情境时才为false）,\n'
                '    "scenario": 情境key或null，可选值：' + json.dumps(list(TRANSPLANT_SCENARIO_GUIDES.keys()), ensure_ascii=False) + ',\n'
                '    "confidence": 0到1的小数\n'
                "  }\n"
                "}\n\n"
                f"当前系统分期：{current_phase.value}\n"
                f"患者原话：{user_message}\n\n"
                f"情境key参考：{json.dumps(TRANSPLANT_SCENARIO_GUIDES, ensure_ascii=False)}\n\n"
                "只输出 JSON，不要添加任何解释或其他文字。"
            )

            resp = self.client.chat.completions.create(
                model=Config.LLM_DETECTION_MODEL,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                temperature=Config.LLM_DETECTION_TEMPERATURE,
                max_tokens=512,
            )
            content = resp.choices[0].message.content.strip()
            # 去掉 markdown 代码块包裹
            if content.startswith("```"):
                parts = content.split("```")
                if len(parts) >= 2:
                    content = parts[1]
                    if content.lstrip().startswith("json"):
                        content = "\n".join(content.splitlines()[1:])

            data = json.loads(content)
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

            # recommended_technique 转枚举
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

            # --- 规范化 crisis 部分 ---
            crisis = data.get("crisis") or {}
            data["crisis"] = {
                "has_crisis": bool(crisis.get("has_crisis", False)),
                "severity_score": int(crisis.get("severity_score", 0) or 0),
                "crisis_types": crisis.get("crisis_types") or [],
            }

            # --- 规范化 transplant 部分 ---
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
        if not Config.HISTORY_COMPRESSION_ENABLED:
            # 未启用压缩，返回完整历史
            return self.conversation_history + [{"role": "user", "content": user_message}]

        # 增量摘要模式：只传入记忆中枢 + 当前问题
        system_msg = self.conversation_history[0]
        messages = [system_msg]

        # 如果有记忆中枢，添加到消息中
        if self.memory_core:
            messages.append({
                "role": "system",
                "content": f"【记忆中枢】以下是之前对话的核心信息：\n{self.memory_core}"
            })

        # 添加当前用户输入
        messages.append({"role": "user", "content": user_message})

        return messages

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

            # 调用LLM生成/更新记忆中枢
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

            # 可选：打印记忆更新日志（调试用）
            if os.getenv("DEBUG_MEMORY_CORE", "false").lower() == "true":
                logger.debug("记忆中枢已更新: %s...", new_memory[:100])

        except Exception as e:
            logger.exception("更新记忆中枢失败")
            # 失败时使用简单的记录
            if not self.memory_core:
                self.memory_core = f"用户表达了关于心理健康的问题，情绪状态为{emotion}。"

    def _load_persistent_data(self):
        """加载持久化数据"""
        try:
            # 加载能量进度
            self.energy_model.load_progress("energy_progress.json")
            # 加载危机历史
            self.crisis_module.load_crisis_history("crisis_history.json")
        except Exception as e:
            logger.exception("加载持久化数据失败")

    def _load_user_state(self, filename: str = "user_state.json"):
        """加载用户状态（如骨髓移植分期）"""
        try:
            filepath = self._get_filepath(filename)
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
            filepath = self._get_filepath(filename)
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(self.user_state, f, ensure_ascii=False, indent=2)
        except Exception as e:
            logger.exception("保存用户状态失败")

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
        """从文件加载对话历史"""
        try:
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
        # 1. 重置对话历史（只保留 system prompt）
        self.conversation_history = [
            {"role": "system", "content": self.system_prompt}
        ]

        # 2. 重置记忆中枢
        self.memory_core = None

        # 3. 重置用户状态
        self.user_state = {
            "transplant_phase": TransplantPhase.PREP.value
        }

        # 4. 重新初始化 CBT 模块（重置用户档案）
        self.cbt_module = CBTModule()

        # 5. 重新初始化能量模型（重置所有进度）
        self.energy_model = PsychologicalEnergyModel()

        # 6. 重新初始化危机干预模块（重置危机历史）
        self.crisis_module = CrisisInterventionModule(alert_callback=self._crisis_alert_callback)

        # 7. 删除所有持久化文件
        files_to_delete = [
            "chat_history.json",
            "user_state.json",
            "energy_progress.json",
            "crisis_history.json"
        ]

        deleted_files = []
        for filename in files_to_delete:
            try:
                filepath = self._get_filepath(filename)
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
        os.makedirs(self.data_dir, exist_ok=True)
        return os.path.join(self.data_dir, filename)
