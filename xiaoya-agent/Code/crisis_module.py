"""
危机干预识别和干预模块
实时识别"无助/绝望"情绪并触发正念引导或报警

说明：
- 原有基于关键词的规则检测逻辑仍然保留，但作为兜底方案；
- 优先使用大模型进行结构化危机评估（可通过配置开关控制）。
"""
from enum import Enum
from typing import Dict, List, Optional, Callable, Any
import time
import json
from datetime import datetime
from openai import OpenAI
from config import Config

class CrisisType(Enum):
    """危机类型"""
    SUICIDAL = "自杀危机"
    SELF_HARM = "自伤危机"
    SEVERE_DEPRESSION = "重度抑郁"
    PANIC_ATTACK = "惊恐发作"
    ACUTE_ANXIETY = "急性焦虑"
    EMOTIONAL_BREAKDOWN = "情绪崩溃"

class CrisisInterventionModule:
    """危机干预模块"""

    def __init__(self, alert_callback: Optional[Callable] = None):
        """
        初始化危机干预模块

        Args:
            alert_callback: 报警回调函数
        """
        self.alert_callback = alert_callback
        self.crisis_history = []

        # 危机关键词库（兜底规则）
        self.crisis_keywords = self._load_crisis_keywords()

        # 连续危机检测
        self.consecutive_crisis_count = 0
        self.last_crisis_time = None

        # LLM 客户端（懒加载）
        self._llm_client: Optional[Any] = None

    def _load_crisis_keywords(self) -> Dict[str, List[str]]:
        """加载危机关键词"""
        return {
            CrisisType.SUICIDAL.value: [
                "自杀", "死", "结束生命", "不想活了", "死了算了",
                "自杀念头", "自杀想法", "轻生", "自我毁灭"
            ],
            CrisisType.SELF_HARM.value: [
                "自残", "割腕", "伤害自己", "自伤", "伤自己"
            ],
            CrisisType.SEVERE_DEPRESSION.value: [
                "绝望", "无助", "崩溃", "撑不住了", "活不下去了",
                "没有意义", "一切都完了", "彻底绝望"
            ],
            CrisisType.PANIC_ATTACK.value: [
                "恐慌发作", "心脏要停了", "喘不过气", "要死了",
                "控制不住", "发疯了", "崩溃了"
            ],
            CrisisType.ACUTE_ANXIETY.value: [
                "极度焦虑", "控制不住", "害怕死了", "心跳加速",
                "呼吸困难", "出不去", "困住了"
            ],
            CrisisType.EMOTIONAL_BREAKDOWN.value: [
                "情绪崩溃", "受不了了", "疯了", "控制不住情绪",
                "爆发了", "忍不了了"
            ]
        }

    def detect_crisis(self, user_message: str, emotional_analysis: Dict) -> Dict[str, bool]:
        """
        危机检测只返回一个状态变量：
        - alert: 是否需要触发报警

        规则：
        - 先尝试 LLM 判定（如开启），失败则回退到关键词规则；
        - 一旦判定为需要报警，立即触发报警回调；
        - 不返回任何危机细节（等级/类型/话术等）。
        """
        if not Config.CRISIS_DETECTION_ENABLED:
            return {"alert": False}

        # 1) LLM 判定（优先）
        if Config.CRISIS_LLM_DETECTION_ENABLED:
            llm_result = self._llm_detect_crisis(user_message, emotional_analysis)
            if isinstance(llm_result, dict) and llm_result.get("has_crisis") is True:
                # 使用配置阈值控制何时报警（更严重时才触发）
                sev = int(llm_result.get("severity_score", 0) or 0)
                threshold = getattr(Config, "CRISIS_ALERT_THRESHOLD", 10)
                if sev >= threshold:
                    self._record_crisis_event(user_message, sev)
                    self._trigger_alert({"alert": True})
                    return {"alert": True}

        # 2) 兜底：关键词规则
        rule = self._rule_based_detect_crisis(user_message, emotional_analysis)
        if rule.get("alert", False):
            self._record_crisis_event(user_message)
            self._trigger_alert({"alert": True})
            return {"alert": True}

        return {"alert": False}

    def _rule_based_detect_crisis(self, user_message: str, emotional_analysis: Dict) -> Dict[str, bool]:
        """兜底：关键词规则，只输出是否需要报警"""
        detected_types = self._analyze_crisis_keywords(user_message)
        emotional_severity = int((emotional_analysis or {}).get("severity", 0) or 0)

        keyword_score = len(detected_types) * 3
        total_score = keyword_score + emotional_severity

        consecutive_factor = self._check_consecutive_crisis(detected_types)
        total_score += consecutive_factor

        # 使用配置阈值控制何时报警（分数越高代表越严重）
        threshold = getattr(Config, "CRISIS_ALERT_THRESHOLD", 10)
        alert = total_score >= threshold
        return {"alert": alert}

    def _get_llm_client(self) -> Optional[Any]:
        """懒加载 LLM 客户端"""
        if self._llm_client is None:
            try:
                self._llm_client = OpenAI(
                    api_key=Config.API_KEY,
                    base_url=Config.API_BASE_URL
                )
            except Exception as e:
                print(f"初始化危机检测 LLM 客户端失败: {e}")
                self._llm_client = None
        return self._llm_client

    def _llm_detect_crisis(self, user_message: str, emotional_analysis: Dict) -> Optional[Dict[str, Any]]:
        """
        使用大模型进行危机检测，返回结构化结果：
        {
          "has_crisis": bool,
          "crisis_level": "NONE|MILD|MODERATE|SEVERE|CRITICAL",
          "crisis_types": ["自杀危机"|"自伤危机"|...],
          "severity_score": int(0-20),
          "reason": str
        }
        """
        client = self._get_llm_client()
        if client is None:
            return None

        try:
            emotion = emotional_analysis or {}
            primary = emotion.get("primary", "")
            severity = emotion.get("severity", 0)

            system_prompt = (
                "你是“小芽”系统中的危机评估助手，专门为骨髓移植患者做文本级别的心理危机筛查。"
                "你只做“是否存在危机及类型”的判断，不进行安慰或治疗，也不提供具体医疗方案。"
            )
            user_prompt = (
                "请阅读下面这段患者的话，结合其情绪分析结果，判断是否存在心理危机，并严格按照 JSON 输出：\n"
                "{\n"
                '  "has_crisis": true 或 false,\n'
                '  "crisis_types": 数组，元素只能是以下之一：'
                '["自杀危机","自伤危机","重度抑郁","惊恐发作","急性焦虑","情绪崩溃"],\n'
                '  "severity_score": 0 到 20 的整数（综合你对风险的判断）, \n'
                '  "reason": 用一两句话简要说明判断依据\n'
                "}\n\n"
                f"患者原话：{user_message}\n"
                f"情绪分析（供参考，可不完全采纳）：primary={primary}, severity={severity}\n\n"
                "只输出 JSON，不要添加任何解释或多余文字。"
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
            content = resp.choices[0].message.content.strip()
            if content.startswith("```"):
                parts = content.split("```")
                if len(parts) >= 2:
                    content = parts[1]
                    if content.lstrip().startswith("json"):
                        content = "\n".join(content.splitlines()[1:])

            data = json.loads(content)
            if isinstance(data, dict):
                return data
            return None
        except Exception as e:
            print(f"LLM 危机检测失败，将使用关键词规则兜底: {e}")
            return None

    def _analyze_crisis_keywords(self, message: str) -> List[str]:
        """分析危机关键词"""
        detected_types = []
        message_lower = message.lower()

        for crisis_type, keywords in self.crisis_keywords.items():
            for keyword in keywords:
                if keyword in message_lower:
                    if crisis_type not in detected_types:
                        detected_types.append(crisis_type)
                    break

        return detected_types

    def _check_consecutive_crisis(self, detected_types: List[str]) -> int:
        """检查连续危机"""
        current_time = time.time()

        if detected_types:
            if (self.last_crisis_time and
                current_time - self.last_crisis_time < 3600):  # 1小时内
                self.consecutive_crisis_count += 1
            else:
                self.consecutive_crisis_count = 1

            self.last_crisis_time = current_time

            # 连续危机加重系数
            return min(self.consecutive_crisis_count * 2, 6)
        else:
            self.consecutive_crisis_count = 0
            return 0

    def _record_crisis_event(self, user_message: str, severity_score: Optional[int] = None):
        """记录危机事件（简化版，只记录触发报警的事件）"""
        event = {
            "timestamp": datetime.now().isoformat(),
            "user_message": user_message,
            "severity_score": severity_score
        }

        self.crisis_history.append(event)

        # 保持最近50次危机记录
        if len(self.crisis_history) > 50:
            self.crisis_history = self.crisis_history[-50:]

    def _trigger_alert(self, crisis_data: Dict):
        """触发报警"""
        if self.alert_callback:
            self.alert_callback(crisis_data)
        else:
            # 默认报警行为：打印报警信息
            print("\n" + "="*60)
            print("危机报警触发！")
            # 危机细节已被简化，不再输出等级/类型
            print("请立即联系专业心理援助！")
            print("="*60 + "\n")

    def get_grounding_exercise(self) -> str:
        """获取正念接地练习"""
        return """
 正念接地练习（给身体留一点空间）

如果你正在病房里、身体不舒服，也没关系。这个练习可以坐着或躺着完成，慢一点就好。

第一步：先把注意力放在“更慢的呼气”上，轻轻呼气两次。

第二步：用感官把自己拉回此刻（不需要很用力，想到什么就说什么）：
1. 看到的 5 样东西（比如：天花板、窗帘、床栏、灯、杯子）
2. 触碰到的 4 处感觉（比如：被子贴着皮肤、枕头的重量、衣服的纹理、脚踩着床）
3. 听到的 3 种声音（比如：空调声、走廊声、自己的呼吸）
4. 闻到的 2 种气味（比如：空气、消毒水、洗发水）
5. 尝到的 1 种味道（比如：水、口腔里的味道）

最后：把手放在胸口或腹部，轻轻对自己说一句：“我在这里，我是安全的。”
        """

    def get_crisis_history_report(self) -> Dict[str, any]:
        """获取危机历史报告"""
        if not self.crisis_history:
            return {"total_crises": 0, "recent_crises": []}

        # 最近7天的危机
        week_ago = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        recent_crises = [
            event for event in self.crisis_history
            if datetime.fromisoformat(event["timestamp"]) > week_ago
        ]

        return {
            "total_crises": len(self.crisis_history),
            "recent_crises_count": len(recent_crises),
            "recent_crises": recent_crises[-5:]  # 最近5次
        }

    def save_crisis_history(self, filename: str = "crisis_history.json"):
        """保存危机历史"""
        filepath = self._get_filepath(filename)
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(self.crisis_history, f, ensure_ascii=False, indent=2)

    def load_crisis_history(self, filename: str = "crisis_history.json"):
        """加载危机历史"""
        try:
            filepath = self._get_filepath(filename)
            with open(filepath, 'r', encoding='utf-8') as f:
                self.crisis_history = json.load(f)
        except FileNotFoundError:
            pass
        except Exception as e:
            print(f"加载危机历史失败: {str(e)}")
    def _get_filepath(self, filename: str) -> str:
        """获取文件的完整路径（统一放在 Code 目录下）"""
        import os
        script_dir = os.path.dirname(os.path.abspath(__file__))
        return os.path.join(script_dir, filename)
