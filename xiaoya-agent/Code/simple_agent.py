"""
增强版对话智能体 - 集成CBT、心理能量和危机干预
"""
from openai import OpenAI
from typing import List, Dict, Optional
import json
import os
from config import Config
from cbt_module import CBTModule, CBTTechnique
from energy_model import PsychologicalEnergyModel
from crisis_module import CrisisInterventionModule
from transplant_support import TransplantPhase, choose_intervention, get_template

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

        # 对话历史
        self.conversation_history: List[Dict[str, str]] = [
            {"role": "system", "content": self.system_prompt}
        ]

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

    def chat(self, user_message: str) -> Dict[str, any]:
        """
        增强版对话 - 集成CBT、能量评估和危机干预

        Args:
            user_message: 用户输入的消息

        Returns:
            包含回复和分析结果的字典
        """
        # 1. CBT分析用户输入
        cbt_analysis = self.cbt_module.analyze_user_input(user_message)

        # 2. 危机检测
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
            # 需求：对 crisis 直接报警，不进行其他操作（不输出话术/提示）
            response = ""
            response_type = "crisis_alert"
        else:
            # 骨髓移植分期情境触发（task1）
            response = None
            response_type = None
            transplant_trigger = None

            if getattr(Config, "TRANSPLANT_SUPPORT_ENABLED", True):
                current_phase = self.get_transplant_phase()
                transplant_trigger = choose_intervention(
                    user_message=user_message,
                    current_phase=current_phase,
                    emotional_severity=cbt_analysis.get("emotional_state", {}).get("severity", 0),
                )

                if transplant_trigger.should_trigger and transplant_trigger.scenario:
                    template = get_template(transplant_trigger.phase, transplant_trigger.scenario)
                    if template:
                        # 更新分期（当用户明确语境变化时）
                        self.set_transplant_phase(transplant_trigger.phase)
                        # 轻量改写，避免机械背诵（失败则回退原文）
                        response = self._rewrite_guidance_if_possible(template, user_message)
                        response_type = "transplant_guidance"

            if response is None:
                # 正常CBT对话
                response = self._generate_cbt_response(user_message, cbt_analysis)
                response_type = "cbt_response"
            elif response_type == "transplant_guidance":
                # 移植情境引导已包含足够内容，不再追加CBT建议，避免重复
                pass

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

        # 8. 返回完整结果
        return {
            "response": response,
            "response_type": response_type,
            "cbt_analysis": cbt_analysis,
            "crisis_detection": crisis_detection,
            "energy_assessment": energy_assessment,
            "energy_report": self.energy_model.get_energy_report() if energy_assessment else None
        }

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
        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(self.conversation_history, f, ensure_ascii=False, indent=2)

    def _generate_cbt_response(self, user_message: str, analysis: Dict) -> str:
        """生成CBT干预响应"""
        try:
            # 调用API生成基础回复
            api_response = self.client.chat.completions.create(
                model=self.model,
                messages=self.conversation_history + [{
                    "role": "user",
                    "content": user_message
                }],
                temperature=self.temperature,
                max_tokens=self.max_tokens
            )

            base_response = api_response.choices[0].message.content

            # 仅在“需要时”追加CBT建议，避免打扰日常闲聊
            if self._should_add_cbt_guidance(analysis):
                recommended_technique = analysis.get("recommended_technique")
                if recommended_technique:
                    cbt_guidance = self.cbt_module.generate_cbt_response(user_message, analysis)
                    if cbt_guidance and cbt_guidance.strip():
                        # 用更人性化的引导标题，避免“说教感”
                        return f"{base_response}\n\n如果你愿意，我们可以试一个小练习：\n{cbt_guidance.strip()}"

            return base_response

        except Exception as e:
            error_msg = f"发生错误: {str(e)}"
            print(error_msg)
            return error_msg

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

    def _rewrite_guidance_if_possible(self, template: str, user_message: str) -> str:
        """
        对引导语进行轻量改写，保持核心含义不变，避免机械背诵。
        - 如果API不可用/报错，直接回退原模板。
        """
        try:
            prompt = (
                '你是"小芽"，在不改变核心含义与关怀语气的前提下，'
                '把下面的引导语做轻量改写：整体长度相近，中文自然，不要出现"改写/模板/语料库"等词，'
                '不要夸大疗效，不要提供医疗处方，不要使用编号列表。'
                '重要：只改写给定的引导语，不要添加额外的CBT练习或建议，不要重复用户的话。\n\n'
                f'用户刚刚说：{user_message}\n\n'
                f'引导语：{template}\n\n'
                '请输出改写后的引导语（只输出改写后的内容，不要添加其他内容）：'
            )

            api_response = self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": self.system_prompt},
                    {"role": "user", "content": prompt},
                ],
                temperature=min(max(self.temperature, 0.6), 0.95),
                max_tokens=min(self.max_tokens, 600),
            )
            rewritten = api_response.choices[0].message.content
            if rewritten and rewritten.strip():
                return rewritten.strip()
            return template
        except Exception:
            return template

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
            if not os.path.exists(filename):
                return
            with open(filename, "r", encoding="utf-8") as f:
                data = json.load(f)
            if isinstance(data, dict):
                self.user_state.update(data)
        except Exception as e:
            print(f"加载用户状态失败: {str(e)}")

    def _save_user_state(self, filename: str = "user_state.json"):
        """保存用户状态（如骨髓移植分期）"""
        try:
            with open(filename, "w", encoding="utf-8") as f:
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
            with open(filename, 'r', encoding='utf-8') as f:
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

        # 2. 重置用户状态
        self.user_state = {
            "transplant_phase": TransplantPhase.PREP.value
        }

        # 3. 重新初始化 CBT 模块（重置用户档案）
        self.cbt_module = CBTModule()

        # 4. 重新初始化能量模型（重置所有进度）
        self.energy_model = PsychologicalEnergyModel()

        # 5. 重新初始化危机干预模块（重置危机历史）
        self.crisis_module = CrisisInterventionModule(alert_callback=self._crisis_alert_callback)

        # 6. 删除所有持久化文件
        files_to_delete = [
            "chat_history.json",
            "user_state.json",
            "energy_progress.json",
            "crisis_history.json"
        ]

        deleted_files = []
        for filename in files_to_delete:
            try:
                if os.path.exists(filename):
                    os.remove(filename)
                    deleted_files.append(filename)
            except Exception as e:
                print(f"删除文件 {filename} 失败: {str(e)}")

        return {
            "success": True,
            "deleted_files": deleted_files,
            "message": "所有数据已重置，系统已恢复到初始状态"
        }