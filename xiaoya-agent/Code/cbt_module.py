"""
CBT (认知行为疗法) 对话策略模块

- 优先使用大模型对用户输入进行结构化分析（情绪、认知扭曲、严重程度、推荐技术）；
- 原有关键词/规则逻辑作为兜底方案，在模型不可用或输出异常时启用。
"""
from enum import Enum
from typing import Dict, List, Optional, Tuple, Any
import re
import json
from openai import OpenAI
from config import Config
from keyword_library import (
    CBT_EMOTION_KEYWORDS,
    CBT_COGNITIVE_DISTORTION_KEYWORDS,
    CBT_SEVERITY_KEYWORDS,
    CBT_TRIGGER_KEYWORDS,
    count_keyword_matches,
    contains_any,
)

class CBTTechnique(Enum):
    """CBT技术类型枚举"""
    COGNITIVE_RESTRUCTURING = "认知重构"
    BEHAVIORAL_ACTIVATION = "行为激活"
    PROBLEM_SOLVING = "问题解决"
    RELAXATION_TRAINING = "放松训练"
    EXPOSURE_TECHNIQUE = "暴露技术"
    MINDFULNESS = "正念练习"
    THOUGHT_RECORDING = "思维记录"
    ACTIVITY_SCHEDULING = "活动安排"

class CBTModule:
    """CBT对话策略模块"""

    def __init__(self):
        """初始化CBT模块"""
        self.user_profile = {
            "cognitive_patterns": [],  # 认知模式
            "behavioral_patterns": [], # 行为模式
            "emotional_triggers": [],  # 情绪触发点
            "progress_level": 0,       # 进步水平 (0-100)
            "session_count": 0         # 会话次数
        }

        # CBT技术库
        self.technique_prompts = self._load_technique_prompts()

        # LLM 客户端（懒加载）
        self._llm_client: Optional[Any] = None

    def _load_technique_prompts(self) -> Dict[str, str]: # TODO 完善提示词
        """加载CBT技术提示词"""
        return {
            CBTTechnique.COGNITIVE_RESTRUCTURING.value: """
我听见你心里这句话一直在转： “{}”
先不急着逼自己“想开”，我们做个很轻的核对：这句话更像“事实”，还是更像“担心/推测”？
如果你愿意，我们可以一起找两样东西：
- 支持它的证据（哪怕只有一点点）
- 不支持它的证据（同样哪怕只有一点点）
最后，我想陪你把它改写成一句更温柔、更贴近事实的话。你觉得这句话里最刺痛你的部分是哪一段？
            """,

            CBTTechnique.BEHAVIORAL_ACTIVATION.value: """
我能理解那种“什么都不想动”的感觉。我们不需要一下子变好，只要把今天变得“可过一点点”。
我们先选一个非常小、5分钟内能完成的小动作：喝几口水、洗把脸、把窗帘拉开、给家人回一句消息都算。
你现在更像是“没力气”，还是“没心情”？我会按你的状态帮你挑一个最省力的小动作。
            """,

            CBTTechnique.PROBLEM_SOLVING.value: """
我们把这件事拆小一点点，好让它没那么压人。
先帮我补两句话：
1）你最想解决的“具体问题”是什么？（一句话就好）
2）如果这件事能好转10%，对你来说会是什么样子？
我会在你给的答案里，帮你找一个最现实、最省力的下一步。
            """,

            CBTTechnique.RELAXATION_TRAINING.value: """
我们先让身体“降一点点噪音”。如果你愿意，跟我做两轮呼吸就好：
慢慢吸气，让气息到腹部；再更慢地呼气，像把紧绷一点点放下。
你可以一边呼气一边在心里默念： “我现在很难受，但我在照顾自己。”
做完后告诉我：你的胸口/胃/喉咙的紧绷有没有松一点点？
            """,

            CBTTechnique.MINDFULNESS.value: """
我们不跟念头打架，只把注意力放回“此刻”一分钟。
你可以轻轻把注意力放在呼吸上：吸气、呼气。
如果有想法冒出来，就像看见一朵云飘过——你不需要抓住它，也不需要赶走它，只要知道“我注意到了”。
现在此刻，你身体里最明显的一个感觉是什么？（比如紧、热、酸、麻、空）
            """,

            CBTTechnique.THOUGHT_RECORDING.value: """
我们可以用一个很简化的小记录，把脑子里那团线捋顺一点：
情境：{}
脑子蹦出来的一句话：{}
情绪强度：{}（0-10）
如果把这句话换成更贴近事实、更照顾你的说法，会是什么？
你愿意先从“那句最刺耳的话”开始吗？我会陪你一起改写。
            """
        }

    def analyze_user_input(self, user_message: str) -> Dict[str, any]:
        """
        分析用户输入，识别情绪状态和认知模式。

        优先策略：
        - 如开启 CBT_LLM_ENABLED，则先尝试调用大模型输出结构化分析；
        - 如模型不可用/失败/输出异常，则回退到原有关键词/规则分析。
        """
        # 1) LLM 分析
        if getattr(Config, "CBT_LLM_ENABLED", False):
            llm_result = self._llm_analyze_user_input(user_message)
            if isinstance(llm_result, dict):
                return llm_result

        # 2) 兜底：规则分析
        return self._rule_based_analyze_user_input(user_message)

    def _rule_based_analyze_user_input(self, user_message: str) -> Dict[str, any]:
        """原有基于关键词/规则的 CBT 分析逻辑（兜底）"""
        analysis = {
            "emotional_state": self._detect_emotion(user_message),
            "cognitive_distortions": self._detect_cognitive_distortions(user_message),
            "problem_severity": self._assess_problem_severity(user_message),
            "intervention_needed": False,
            "recommended_technique": None
        }

        # 根据分析结果推荐CBT技术
        analysis["recommended_technique"] = self._recommend_technique(analysis)

        # 检查是否需要立即干预
        if analysis["emotional_state"]["severity"] >= 7:
            analysis["intervention_needed"] = True

        return analysis

    def _detect_emotion(self, message: str) -> Dict[str, any]:
        """检测情绪状态"""
        message_lower = message.lower()

        # 计算情绪得分
        emotion_scores = {}
        for emotion, keywords in CBT_EMOTION_KEYWORDS.items():
            score = count_keyword_matches(message_lower, keywords)
            if score > 0:
                emotion_scores[emotion] = score

        # 确定主要情绪和严重程度
        if not emotion_scores:
            return {"primary": "neutral", "severity": 1, "details": emotion_scores}

        primary_emotion = max(emotion_scores, key=emotion_scores.get)
        severity = min(emotion_scores[primary_emotion] * 2, 10)  # 1-10分

        # 特别关注绝望情绪
        if primary_emotion == "hopelessness" and severity >= 5:
            severity = 10  # 标记为危机

        return {
            "primary": primary_emotion,
            "severity": severity,
            "details": emotion_scores
        }

    def _detect_cognitive_distortions(self, message: str) -> List[str]:
        """检测认知扭曲"""
        distortions = []
        for distortion, keywords in CBT_COGNITIVE_DISTORTION_KEYWORDS.items():
            if contains_any(message, keywords):
                distortions.append(distortion)

        return distortions

    def _assess_problem_severity(self, message: str) -> int:
        """评估问题严重程度 (1-10)"""
        severity = 1
        for rule in CBT_SEVERITY_KEYWORDS:
            if contains_any(message, rule["keywords"]):
                severity = max(severity, int(rule["score"]))
                break

        return min(severity, 10)

    def _recommend_technique(self, analysis: Dict[str, any]) -> Optional[CBTTechnique]:
        """根据分析结果推荐CBT技术"""
        emotion = analysis["emotional_state"]["primary"]
        severity = analysis["emotional_state"]["severity"]
        distortions = analysis["cognitive_distortions"]

        # 高危情绪 - 使用正念
        if severity >= 8 or emotion == "hopelessness":
            return CBTTechnique.MINDFULNESS

        # 焦虑情绪 - 使用放松训练
        if emotion == "anxiety":
            return CBTTechnique.RELAXATION_TRAINING

        # 有认知扭曲 - 使用认知重构
        if distortions:
            return CBTTechnique.COGNITIVE_RESTRUCTURING

        # 低落情绪 - 使用行为激活
        if emotion == "sadness":
            return CBTTechnique.BEHAVIORAL_ACTIVATION

        # 默认使用问题解决
        return CBTTechnique.PROBLEM_SOLVING

    def generate_cbt_response(self, user_message: str, analysis: Dict[str, any]) -> str:
        """
        生成CBT干预响应
        
        优先使用大模型动态生成个性化的CBT引导语，失败时回退到固定模板

        Args:
            user_message: 用户输入
            analysis: 分析结果

        Returns:
            CBT干预响应
        """
        technique = analysis.get("recommended_technique")

        if not technique:
            return "我理解你的感受。让我们一起探索一下，你目前面临的主要困扰是什么？"

        # 优先使用大模型生成个性化CBT引导
        if getattr(Config, "CBT_LLM_ENABLED", False):
            llm_response = self._llm_generate_cbt_guidance(user_message, analysis, technique)
            if llm_response:
                return llm_response

        # 兜底：使用固定模板
        return self._template_based_response(user_message, analysis, technique)

    def _llm_generate_cbt_guidance(self, user_message: str, analysis: Dict[str, any], technique: CBTTechnique) -> Optional[str]:
        """
        使用大模型动态生成个性化的CBT引导语
        
        Args:
            user_message: 用户输入
            analysis: CBT分析结果
            technique: 推荐的CBT技术
            
        Returns:
            生成的CBT引导语，失败返回None
        """
        client = self._get_llm_client()
        if client is None:
            return None

        try:
            # 获取情绪和认知扭曲信息
            emotional_state = analysis.get("emotional_state", {})
            emotion = emotional_state.get("primary", "neutral")
            severity = emotional_state.get("severity", 5)
            distortions = analysis.get("cognitive_distortions", [])
            
            # 构建技术说明
            technique_descriptions = {
                CBTTechnique.COGNITIVE_RESTRUCTURING: "认知重构：帮助用户识别和挑战负面思维，寻找证据，改写成更平衡的想法",
                CBTTechnique.BEHAVIORAL_ACTIVATION: "行为激活：鼓励用户做一个非常小的行动，打破'什么都不想做'的循环",
                CBTTechnique.PROBLEM_SOLVING: "问题解决：帮助用户把大问题拆解成小步骤，找到可行的下一步",
                CBTTechnique.RELAXATION_TRAINING: "放松训练：引导用户做简单的呼吸练习，缓解身体紧张",
                CBTTechnique.MINDFULNESS: "正念练习：引导用户把注意力放回当下，观察而不评判自己的想法和感受",
                CBTTechnique.THOUGHT_RECORDING: "思维记录：帮助用户记录和分析自己的想法、情绪和情境",
                CBTTechnique.EXPOSURE_TECHNIQUE: "暴露技术：逐步帮助用户面对恐惧的情境",
                CBTTechnique.ACTIVITY_SCHEDULING: "活动安排：帮助用户规划有意义的日常活动"
            }
            
            technique_desc = technique_descriptions.get(technique, "CBT技术")
            distortions_str = "、".join(distortions) if distortions else "无明显认知扭曲"

            system_prompt = (
                "你是小芽，一个温暖、专业的心理支持伙伴，专门陪伴骨髓移植患者。\n"
                "你的任务是根据CBT技术，生成一段口语化、温暖、个性化的引导语。\n\n"
                "要求：\n"
                "1. 语气温暖、口语化，像朋友聊天一样，用'你'、'咱们'等称呼\n"
                "2. 针对用户的具体情况，不要泛泛而谈\n"
                "3. 引导要具体、可操作，步骤简单清晰\n"
                "4. 长度控制在50-150字\n"
                "5. 不要说教，不要用'您应该'、'必须'等词\n"
                "6. 适合语音播报，避免使用编号、列表符号\n"
                "7. 体现共情和陪伴感\n"
            )

            user_prompt = (
                f"用户说：{user_message}\n\n"
                f"CBT分析结果：\n"
                f"- 主要情绪：{emotion}（强度{severity}/10）\n"
                f"- 认知扭曲：{distortions_str}\n"
                f"- 推荐技术：{technique_desc}\n\n"
                f"请生成一段温暖、口语化的CBT引导语，帮助用户使用{technique.value}技术。\n"
                f"直接输出引导语内容，不要加任何前缀或解释。"
            )

            resp = client.chat.completions.create(
                model=Config.MODEL_NAME,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                temperature=0.8,  # 稍高的温度以获得更自然的表达
                max_tokens=600
            )
            
            guidance = resp.choices[0].message.content.strip()
            
            # 验证生成的内容不为空且长度合理
            if guidance and 50 <= len(guidance) <= 500:
                return guidance
            else:
                return None
                
        except Exception as e:
            print(f"大模型生成CBT引导失败: {e}")
            return None

    def _template_based_response(self, user_message: str, analysis: Dict[str, any], technique: CBTTechnique) -> str:
        """
        使用固定模板生成CBT响应（兜底方案）
        
        Args:
            user_message: 用户输入
            analysis: 分析结果
            technique: CBT技术
            
        Returns:
            基于模板的CBT响应
        """
        # 获取对应的提示词模板
        template = self.technique_prompts.get(technique.value, "")

        if technique == CBTTechnique.COGNITIVE_RESTRUCTURING:
            # 提取用户可能的负面思维
            return template.format(user_message)

        elif technique == CBTTechnique.THOUGHT_RECORDING:
            # 提供思维记录模板
            return template.format("你刚才描述的情况", "用户的想法", "情绪名称", "替代想法")

        else:
            return template

    def _get_llm_client(self) -> Optional[Any]:
        """懒加载 LLM 客户端"""
        if self._llm_client is None:
            try:
                self._llm_client = OpenAI(
                    api_key=Config.API_KEY,
                    base_url=Config.API_BASE_URL
                )
            except Exception as e:
                print(f"初始化 CBT 分析 LLM 客户端失败: {e}")
                self._llm_client = None
        return self._llm_client

    def _llm_analyze_user_input(self, user_message: str) -> Optional[Dict[str, any]]:
        """
        使用大模型对用户输入进行结构化 CBT 分析，预期 JSON 结构示例：
        {
          "emotional_state": {"primary": "sadness", "severity": 6},
          "cognitive_distortions": ["all_or_nothing","catastrophizing"],
          "problem_severity": 6,
          "intervention_needed": true/false,
          "recommended_technique": "COGNITIVE_RESTRUCTURING" | "BEHAVIORAL_ACTIVATION" | ...
        }
        """
        client = self._get_llm_client()
        if client is None:
            return None

        try:
            system_prompt = (
                "你是“小芽”系统中的 CBT 分析助手，负责从骨髓移植患者的表述中，"
                "提炼出情绪、认知扭曲、问题严重程度，并推荐合适的 CBT 技术。"
                "你只做“分析和推荐”，不输出安慰话语或治疗方案。"
            )
            user_prompt = (
                "请阅读下面的患者表述，并以 JSON 形式给出 CBT 分析结果。\n"
                "字段要求：\n"
                "{\n"
                '  "emotional_state": {\n'
                '    "primary": 主要情绪英文代号（如 "sadness" "anxiety" "anger" "hopelessness" "guilt" "joy" "calm" "hope" 等）, \n'
                '    "severity": 1 到 10 的整数（数字越大代表越痛苦）\n'
                "  },\n"
                '  "cognitive_distortions": 数组，元素为以下英文代号之一：\n'
                '    ["all_or_nothing","catastrophizing","negative_filter","overgeneralization","mind_reading"],\n'
                '  "problem_severity": 1 到 10 的整数，表示整体困扰程度,\n'
                '  "intervention_needed": true 或 false,\n'
                '  "recommended_technique": 以下 CBT 技术英文代号之一：\n'
                '    ["COGNITIVE_RESTRUCTURING","BEHAVIORAL_ACTIVATION","PROBLEM_SOLVING",\n'
                '     "RELAXATION_TRAINING","EXPOSURE_TECHNIQUE","MINDFULNESS",\n'
                '     "THOUGHT_RECORDING","ACTIVITY_SCHEDULING"]\n'
                "}\n\n"
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
            content = resp.choices[0].message.content.strip() # 取默认第一个回答
            if content.startswith("```"):
                parts = content.split("```")
                if len(parts) >= 2:
                    content = parts[1]
                    if content.lstrip().startswith("json"):
                        content = "\n".join(content.splitlines()[1:])

            data = json.loads(content)
            if not isinstance(data, dict):
                return None

            # 组装回项目内部使用的 analysis 结构
            emo = data.get("emotional_state") or {}
            emotional_state = {
                "primary": emo.get("primary", "neutral"),
                "severity": int(emo.get("severity", 1) or 1),
                "details": {},
            }

            distortions = data.get("cognitive_distortions") or []
            if not isinstance(distortions, list):
                distortions = []

            problem_severity = int(data.get("problem_severity", emotional_state["severity"]) or 1)
            intervention_needed = bool(data.get("intervention_needed", False))

            rec_raw = data.get("recommended_technique")
            recommended_technique = None
            if isinstance(rec_raw, str):
                try:
                    # 支持直接用英文枚举名
                    recommended_technique = CBTTechnique[rec_raw]
                except KeyError:
                    # 兼容中文 value
                    for t in CBTTechnique:
                        if t.value == rec_raw:
                            recommended_technique = t
                            break

            analysis = {
                "emotional_state": emotional_state,
                "cognitive_distortions": distortions,
                "problem_severity": problem_severity,
                "intervention_needed": intervention_needed,
                "recommended_technique": recommended_technique,
            }
            return analysis
        except Exception as e:
            print(f"CBT LLM 分析失败，将使用关键词规则兜底: {e}")
            return None

    def update_user_profile(self, analysis: Dict[str, any], user_message: str):
        """更新用户档案"""
        self.user_profile["session_count"] += 1

        # 更新认知模式
        distortions = analysis.get("cognitive_distortions", [])
        self.user_profile["cognitive_patterns"].extend(distortions)
        self.user_profile["cognitive_patterns"] = list(set(self.user_profile["cognitive_patterns"]))

        # 更新情绪触发点
        emotion = analysis["emotional_state"]["primary"]
        if emotion != "neutral":
            trigger = self._extract_trigger(user_message)
            if trigger:
                self.user_profile["emotional_triggers"].append(trigger)

        # 更新进步水平 (简化算法)
        progress_change = 0
        if analysis["emotional_state"]["severity"] <= 3:
            progress_change = 2  # 正向情绪加分
        elif analysis["emotional_state"]["severity"] >= 7:
            progress_change = -1  # 负向情绪减分

        self.user_profile["progress_level"] = max(0, min(100,
            self.user_profile["progress_level"] + progress_change))

    def _extract_trigger(self, message: str) -> Optional[str]:
        """提取情绪触发点"""
        for keyword in CBT_TRIGGER_KEYWORDS:
            if keyword in message:
                sentences = message.split("。")
                for sentence in sentences:
                    if keyword in sentence and len(sentence) < 50:
                        return sentence.strip()

        return None

    def get_progress_report(self) -> Dict[str, any]:
        """获取进步报告"""
        return {
            "total_sessions": self.user_profile["session_count"],
            "progress_level": self.user_profile["progress_level"],
            "common_patterns": self.user_profile["cognitive_patterns"][:5],  # 最多显示5个
            "trigger_summary": len(self.user_profile["emotional_triggers"])
        }