"""
增强版对话智能体 - 集成CBT、心理能量和危机干预
"""
from openai import OpenAI
from typing import List, Dict, Optional, Iterator, Any
import json
import os
import threading
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

class EnhancedChatAgent:
    """增强版对话智能体类 - 集成CBT、心理能量和危机干预"""

    def __init__(self):
        """初始化智能体"""
        Config.validate_config()

        self.client = OpenAI(
            api_key=Config.API_KEY,
            base_url=Config.API_BASE_URL
        )

        self.model = Config.MODEL_NAME
        self.temperature = Config.TEMPERATURE
        self.max_tokens = Config.MAX_TOKENS
        self.system_prompt = Config.SYSTEM_PROMPT
        self.last_result: Optional[Dict[str, Any]] = None

        # 对话历史
        self.conversation_history: List[Dict[str, str]] = [
            {"role": "system", "content": self.system_prompt}
        ]
        
        # 增量摘要：记忆中枢（每轮对话后更新）
        self.memory_core: Optional[str] = None

        # 初始化增强模块
        self.cbt_module = CBTModule()
        self.energy_model = PsychologicalEnergyModel()

        # 危机干预模块（带报警回调）
        self.crisis_module = CrisisInterventionModule(alert_callback=self._crisis_alert_callback)

        # 加载历史数据
        self._load_persistent_data()

        # 用户状态（分期等）
        self.user_state: Dict[str, any] = {
            "transplant_phase": TransplantPhase.PREP.value
        }
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
            crisis_info = unified["crisis"]
            sev = crisis_info["severity_score"]
            threshold = getattr(Config, "CRISIS_ALERT_THRESHOLD", 10)
            alert = crisis_info["has_crisis"] and sev >= threshold
            if alert:
                self.crisis_module._record_crisis_event(user_message, sev)
                self.crisis_module._trigger_alert({"alert": True})
            crisis_detection = {"alert": alert}
        else:
            crisis_detection = self.crisis_module.detect_crisis(
                user_message,
                cbt_analysis.get("emotional_state", {})
            )

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
                "user_state": self.user_state
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
        流式链路采用模型主导：
        - 前台仅做规则危机兜底与轻量模板提示判断；
        - 正式回复立即进入模型流式输出；
        - 结构化分析在回复完成后异步补做。
        """
        current_phase = self.get_transplant_phase()
        cbt_analysis = self.cbt_module._rule_based_analyze_user_input(user_message)
        crisis_detection = self.crisis_module._rule_based_detect_crisis(
            user_message,
            cbt_analysis.get("emotional_state", {})
        )

        if crisis_detection.get("alert", False):
            self.crisis_module._record_crisis_event(user_message)
            self.crisis_module._trigger_alert({"alert": True})

        conversation_data = {
            "user_message": user_message,
            "analysis": cbt_analysis,
            "crisis_detection": crisis_detection
        }

        if crisis_detection.get("alert", False):
            response = ""
            response_type = "crisis_alert"
            self.last_result = self._finalize_chat_turn(
                user_message=user_message,
                response=response,
                response_type=response_type,
                cbt_analysis=cbt_analysis,
                crisis_detection=crisis_detection,
                conversation_data=conversation_data,
                current_phase=current_phase,
                run_post_analysis=False,
            )
            return iter(())

        response_context = self._build_stream_response_context(
            user_message=user_message,
            current_phase=current_phase,
            analysis=cbt_analysis,
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
            run_post_analysis=True,
            response_context=response_context,
        )

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
    ) -> Dict[str, Any]:
        """统一完成历史写入、画像更新、能量评估和记忆更新。"""
        self.conversation_history.append({
            "role": "user",
            "content": user_message
        })

        serialized_cbt_analysis = self._serialize_analysis_data(cbt_analysis)
        serialized_crisis_detection = self._serialize_analysis_data(crisis_detection)

        self.conversation_history.append({
            "role": "assistant",
            "content": response,
            "metadata": {
                "response_type": response_type,
                "cbt_analysis": serialized_cbt_analysis,
                "crisis_detection": serialized_crisis_detection,
                "user_state": self.user_state
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

        if run_post_analysis:
            threading.Thread(
                target=self._run_post_response_analysis,
                args=(user_message, response, current_phase),
                daemon=True,
            ).start()

        return {
            "response": response,
            "response_type": response_type,
            "cbt_analysis": cbt_analysis,
            "crisis_detection": crisis_detection,
            "energy_assessment": energy_assessment,
            "energy_report": self.energy_model.get_energy_report() if energy_assessment else None
        }

    def _stream_static_response(
        self,
        user_message: str,
        response: str,
        response_type: str,
        cbt_analysis: Dict,
        crisis_detection: Dict,
        conversation_data: Dict,
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
            error_msg = f"发生错误: {str(e)}"
            print(error_msg)
            response_parts = [error_msg]
            yield error_msg
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
            error_msg = f"发生错误: {str(e)}"
            print(error_msg)
            return error_msg

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

        extra_system_messages = []
        template = response_context.get("template")
        scenario = response_context.get("scenario")
        if template and scenario:
            scenario_name = scenario.value if hasattr(scenario, "value") else str(scenario)
            extra_system_messages.append({
                "role": "system",
                "content": (
                    f"[移植情境提示] 当前识别到的情境是：{scenario_name}。"
                    f"请参考下面这段陪伴方向，自然融入你的回复，不要照抄，不要说你在引用模板，"
                    f"保持口语化、温暖、连续输出：{template}"
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
                f"请先用自然、简洁、共情的方式回应用户，"
                f"如果合适，再自然引入一段{technique_name}方向的小引导。"
                f"不要使用编号/列表，不要生硬分段，总字数控制在280字以内。"
            )

            user_msg = messages_for_api[-1]
            messages = messages_for_api[:-1] + extra_system_messages + [
                {"role": "system", "content": cbt_instruction},
                user_msg
            ]
            max_tokens = 450
        else:
            user_msg = messages_for_api[-1]
            messages = messages_for_api[:-1] + extra_system_messages + [user_msg]
            max_tokens = 380

        return self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            temperature=self.temperature,
            max_tokens=max_tokens,
            stream=stream,
        )

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
        except Exception as e:
            print(f"流式后置分析失败: {e}")

    def _should_add_cbt_guidance(self, analysis: Dict) -> bool:
        """判断是否需要追加CBT引导（危机由 crisis_module 处理）"""
        if not Config.CBT_ENABLED or not Config.AUTO_CBT_INTERVENTION:
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
        print("\n" + "="*60)
        print(" 紧急危机报警！")
        print("已触发报警标记 alert=true")
        print("建议立即采取以下行动:")
        print("1. 立即联系身边医护/家属获得现实支持")
        print("2. 必要时拨打120或当地紧急救助电话")
        print("3. 可同时联系专业心理援助热线")
        print("="*60 + "\n")

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
            # key -> "场景名称 | 触发条件"
            scenario_map = {
                "FIRST_MEET":        "初次见面/建立连接 | 患者打招呼、说你好、自我介绍、第一次对话、表达想认识小芽",
                "CHEMO_PREP":        "化疗/预处理/重构消极认知 | 患者提到化疗、预处理、放疗、不适反应、恶心呕吐、移植前的痛苦感受",
                "HOPE_TREE":         "希望之树/可视化进步 | 患者提到希望之树、叶子、进步打卡、完成了某项准备",
                "INNER_STRENGTH":    "增强内在力量/唤醒过往资源 | 患者表达撑不住、我不行、没有力量、太难了，或回忆过去克服困难的经历",
                "BREATHING":         "呼吸练习/建立掌控感 | 患者提到呼吸、想放松、紧张、焦虑想做练习，或主动要求引导",
                "INFUSION_DAY":      "细胞回输当日/欢迎仪式 | 患者提到今天回输、干细胞回输、输注、细胞输回来了",
                "SEVERE_DISCOMFORT": "剧烈不适/疼痛恶心/不对抗 | 患者描述疼痛、恶心、呕吐、身体很难受、折磨、受不了",
                "MICRO_LIGHT":       "每日微光记录 | 患者提到今天的小好事、微光、一点点好的感觉、想记录",
                "FUTURE_SCENE":      "未来景象/出院后第一件事 | 患者提到出院后想做什么、未来、康复后的生活、最想做的事",
                "REVIEW_HOPE_TREE":  "回顾希望之树/成长日记 | 患者提到回顾、成长日记、看看以前、希望之树的变化",
                "BLOOD_FLUCTUATION": "血象波动/情绪低落/正常化挫折 | 患者提到血象、白细胞、血小板、指标波动、又掉了、情绪低落",
                "GRATITUDE":         "感恩传递练习 | 患者表达感谢、感恩、谢谢、想感谢某人",
                "SMALL_GOALS":       "设定并完成小目标 | 患者提到完成了某件小事、打卡、坚持、今天做到了、坐起来走了几步",
                "DISCHARGE_LIFE":    "展望出院生活 | 患者提到出院、回家、回到生活、以后的生活、重生",
            }

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
                '    "scenario": 情境key或null，可选值：' + json.dumps(list(scenario_map.keys()), ensure_ascii=False) + ',\n'
                '    "confidence": 0到1的小数\n'
                "  }\n"
                "}\n\n"
                f"当前系统分期：{current_phase.value}\n"
                f"患者原话：{user_message}\n\n"
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
            print(f"综合分析 LLM 调用失败，将降级到各模块独立调用: {e}")
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
            emotion = emotional_state.get("primary_emotion", "未知")
            severity = emotional_state.get("severity", 0)
            distortions = cbt_analysis.get("cognitive_distortions", [])
            crisis_level = crisis_detection.get("risk_level", 0)

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
                print(f"\n[记忆中枢已更新] {new_memory[:100]}...\n")

        except Exception as e:
            print(f"更新记忆中枢失败: {str(e)}")
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
            print(f"加载持久化数据失败: {str(e)}")

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
            print(f"加载用户状态失败: {str(e)}")

    def _save_user_state(self, filename: str = "user_state.json"):
        """保存用户状态（如骨髓移植分期）"""
        try:
            filepath = self._get_filepath(filename)
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(self.user_state, f, ensure_ascii=False, indent=2)
        except Exception as e:
            print(f"保存用户状态失败: {str(e)}")

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
            print("所有进度已保存")
        except Exception as e:
            print(f"保存进度失败: {str(e)}")

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
            print(f"历史文件 {filename} 不存在")
        except Exception as e:
            print(f"加载历史文件失败: {str(e)}")

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
                print(f"删除文件 {filename} 失败: {str(e)}")

        return {
            "success": True,
            "deleted_files": deleted_files,
            "message": "所有数据已重置，系统已恢复到初始状态"
        }
    def _get_filepath(self, filename: str) -> str:
        """获取文件的完整路径（统一放在 Code 目录下）"""
        script_dir = os.path.dirname(os.path.abspath(__file__))
        return os.path.join(script_dir, filename)
