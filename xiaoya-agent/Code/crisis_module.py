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
import logging
import os
from datetime import datetime
from openai import OpenAI
from config import Config
from keyword_library import CRISIS_KEYWORDS, DIRECT_CRISIS_ALERT_TYPES, contains_any

logger = logging.getLogger(__name__)


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

    def __init__(self, alert_callback: Optional[Callable] = None, data_dir: Optional[str] = None):
        """
        初始化危机干预模块

        Args:
            alert_callback: 报警回调函数
        """
        self.alert_callback = alert_callback
        self.data_dir = data_dir or Config.DATA_DIR
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
        return {crisis_type: keywords.copy() for crisis_type, keywords in CRISIS_KEYWORDS.items()}

    def assess_crisis(self, user_message: str, emotional_analysis: Dict) -> Dict[str, Any]:
        """
        危机语义评估，不产生副作用。

        规则：
        - 如开启 CRISIS_LLM_DETECTION_ENABLED，优先使用 LLM 做语义判断；
        - 只有 LLM 不可用、调用失败或输出异常时，才回退到关键词规则；
        - 返回 alert、source、severity_score 等元数据，供上层决定是否记录和报警。
        """
        if not Config.CRISIS_DETECTION_ENABLED:
            return {"alert": False, "source": "disabled"}

        if Config.CRISIS_LLM_DETECTION_ENABLED:
            llm_result = self._llm_detect_crisis(user_message, emotional_analysis)
            if isinstance(llm_result, dict):
                sev = int(llm_result.get("severity_score", 0) or 0)
                threshold = getattr(Config, "CRISIS_ALERT_THRESHOLD", 10)
                alert = bool(llm_result.get("has_crisis") is True and sev >= threshold)
                return {
                    "alert": alert,
                    "alert_type": "psychological_crisis" if alert else None,
                    "source": "llm_semantic",
                    "severity_score": sev,
                    "crisis_types": llm_result.get("crisis_types") or [],
                    "reason": llm_result.get("reason", ""),
                }

        rule = self._rule_based_detect_crisis(user_message, emotional_analysis)
        if rule.get("alert", False):
            return {
                **rule,
                "alert_type": "psychological_crisis",
                "source": "keyword_fallback",
            }
        return {
            **rule,
            "source": "keyword_fallback",
        }

    def assess_crisis_semantic_only(self, user_message: str, emotional_analysis: Dict) -> Dict[str, Any]:
        """仅使用 LLM 语义判断危机，不回退关键词规则。"""
        if not Config.CRISIS_DETECTION_ENABLED:
            return {"alert": False, "source": "disabled"}

        if not Config.CRISIS_LLM_DETECTION_ENABLED:
            return {
                "alert": False,
                "alert_type": None,
                "source": "llm_semantic_disabled",
                "severity_score": 0,
                "crisis_types": [],
                "reason": "危机语义判断已关闭，未启用关键词规则兜底。",
            }

        llm_result = self._llm_detect_crisis(user_message, emotional_analysis)
        if isinstance(llm_result, dict):
            sev = int(llm_result.get("severity_score", 0) or 0)
            threshold = getattr(Config, "CRISIS_ALERT_THRESHOLD", 10)
            alert = bool(llm_result.get("has_crisis") is True and sev >= threshold)
            return {
                "alert": alert,
                "alert_type": "psychological_crisis" if alert else None,
                "source": "llm_semantic",
                "severity_score": sev,
                "crisis_types": llm_result.get("crisis_types") or [],
                "reason": llm_result.get("reason", ""),
            }

        return {
            "alert": False,
            "alert_type": None,
            "source": "llm_semantic_unavailable",
            "severity_score": 0,
            "crisis_types": [],
            "reason": "危机语义判断暂不可用，未启用关键词规则兜底。",
        }

    def detect_crisis(self, user_message: str, emotional_analysis: Dict) -> Dict[str, bool]:
        """
        危机检测兼容旧接口，只返回 alert。

        新逻辑已经改为语义判断优先；关键词只在模型不可用或关闭时兜底。
        """
        result = self.assess_crisis(user_message, emotional_analysis)
        if result.get("alert", False):
            self._record_crisis_event(user_message, result.get("severity_score"))
            self._trigger_alert({
                "alert": True,
                "alert_type": result.get("alert_type", "psychological_crisis"),
                "source": result.get("source"),
            })
            return {"alert": True}

        return {"alert": False}

    def _rule_based_detect_crisis(self, user_message: str, emotional_analysis: Dict) -> Dict[str, bool]:
        """兜底：关键词规则，只输出是否需要报警"""
        detected_types = self._analyze_crisis_keywords(user_message)

        if any(crisis_type in detected_types for crisis_type in DIRECT_CRISIS_ALERT_TYPES):
            self._check_consecutive_crisis(detected_types)
            return {"alert": True}

        emotional_severity = int((emotional_analysis or {}).get("severity", 0) or 0)
        keyword_score = len(detected_types) * 3
        total_score = keyword_score + emotional_severity

        consecutive_factor = self._check_consecutive_crisis(detected_types)
        total_score += consecutive_factor

        threshold = getattr(Config, "CRISIS_ALERT_THRESHOLD", 10)
        return {"alert": total_score >= threshold}

    def _get_llm_client(self) -> Optional[Any]:
        """懒加载 LLM 客户端"""
        if self._llm_client is None:
            try:
                self._llm_client = OpenAI(
                    api_key=Config.API_KEY,
                    base_url=Config.API_BASE_URL
                )
            except Exception:
                logger.exception("初始化危机检测 LLM 客户端失败")
                self._llm_client = None
        return self._llm_client

    def _llm_detect_crisis(self, user_message: str, emotional_analysis: Dict) -> Optional[Dict[str, Any]]:
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
            return data if isinstance(data, dict) else None
        except Exception:
            logger.exception("LLM 危机检测失败，将使用关键词规则兜底")
            return None

    def _analyze_crisis_keywords(self, message: str) -> List[str]:
        detected_types = []
        for crisis_type, keywords in self.crisis_keywords.items():
            if contains_any(message, keywords):
                detected_types.append(crisis_type)
        return detected_types

    def _check_consecutive_crisis(self, detected_types: List[str]) -> int:
        current_time = time.time()
        if detected_types:
            if self.last_crisis_time and current_time - self.last_crisis_time < 3600:
                self.consecutive_crisis_count += 1
            else:
                self.consecutive_crisis_count = 1
            self.last_crisis_time = current_time
            return min(self.consecutive_crisis_count * 2, 6)
        self.consecutive_crisis_count = 0
        return 0

    def _record_crisis_event(self, user_message: str, severity_score: Optional[int] = None):
        event = {
            "timestamp": datetime.now().isoformat(),
            "user_message": user_message,
            "severity_score": severity_score
        }
        self.crisis_history.append(event)
        if len(self.crisis_history) > 50:
            self.crisis_history = self.crisis_history[-50:]

    def _trigger_alert(self, crisis_data: Dict):
        if self.alert_callback:
            self.alert_callback(crisis_data)
        else:
            logger.critical("危机报警触发，请立即联系专业心理援助")

    def get_grounding_exercise(self) -> str:
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
        if not self.crisis_history:
            return {"total_crises": 0, "recent_crises": []}

        week_ago = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        recent_crises = [
            event for event in self.crisis_history
            if datetime.fromisoformat(event["timestamp"]) > week_ago
        ]

        return {
            "total_crises": len(self.crisis_history),
            "recent_crises_count": len(recent_crises),
            "recent_crises": recent_crises[-5:]
        }

    def save_crisis_history(self, filename: str = "crisis_history.json"):
        filepath = self._get_filepath(filename)
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(self.crisis_history, f, ensure_ascii=False, indent=2)

    def load_crisis_history(self, filename: str = "crisis_history.json"):
        try:
            filepath = self._get_filepath(filename)
            with open(filepath, 'r', encoding='utf-8') as f:
                self.crisis_history = json.load(f)
        except FileNotFoundError:
            pass
        except Exception:
            logger.exception("加载危机历史失败")

    def _get_filepath(self, filename: str) -> str:
        if os.path.isabs(filename):
            return filename
        os.makedirs(self.data_dir, exist_ok=True)
        return os.path.join(self.data_dir, filename)
