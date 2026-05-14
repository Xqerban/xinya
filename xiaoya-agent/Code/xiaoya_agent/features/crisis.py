"""
危机干预识别和干预模块
实时识别"无助/绝望"情绪并触发正念引导或报警

说明：
- 默认主流程使用大模型做语义危机评估，并结合用户长期心理模型；
- 关键词规则只保留给旧接口或模型不可用时的降级兜底，不作为默认流式报警依据。
"""
from enum import Enum
from typing import Dict, List, Optional, Callable, Any
import time
import json
import logging
import os
from datetime import datetime
from openai import OpenAI
from xiaoya_agent.config import Config
from xiaoya_agent.keywords.library import CRISIS_KEYWORDS, DIRECT_CRISIS_ALERT_TYPES, contains_any
from xiaoya_agent.llm.structured import (
    CrisisPayload,
    create_chat_completion_json,
    parse_structured_json,
)

logger = logging.getLogger(__name__)


CRISIS_ALARM_PROFILES: Dict[str, Dict[str, Any]] = {
    "none": {
        "label": "无危机",
        "title": "无危机报警",
        "message": "当前未达到危机提醒条件。",
        "action": "none",
        "shouldNotify": False,
        "requiresImmediateAction": False,
    },
    "watch": {
        "label": "关注观察",
        "title": "情绪关注提醒",
        "message": "检测到情绪压力偏高，建议记录状态并提供温和陪伴或放松练习。",
        "action": "mindfulness_guide",
        "shouldNotify": False,
        "requiresImmediateAction": False,
    },
    "warning": {
        "label": "一级预警",
        "title": "危机预警",
        "message": "检测到需要关注的危机信号，建议尽快联系家属、护士或可信任的人陪伴确认安全。",
        "action": "notify_support",
        "shouldNotify": True,
        "requiresImmediateAction": False,
    },
    "high": {
        "label": "二级高危报警",
        "title": "高危危机报警",
        "message": "检测到较高危机风险，请立即联系医护、家属或现场支持人员，并确认用户身边有人陪伴。",
        "action": "alert_and_notify",
        "shouldNotify": True,
        "requiresImmediateAction": True,
    },
    "critical": {
        "label": "三级紧急报警",
        "title": "紧急危机报警",
        "message": "检测到紧急危机风险，请立即联系医护、家属或紧急救助，并移开可能造成伤害的物品。",
        "action": "emergency_intervention",
        "shouldNotify": True,
        "requiresImmediateAction": True,
    },
}

DIRECT_HIGH_RISK_TYPES = {"自杀危机", "自伤危机"}
MEDICAL_RED_FLAG_TYPE = "身体红旗"


def _alert_type_for_crisis(crisis_types: List[str], alert: bool) -> Optional[str]:
    if not alert:
        return None
    if MEDICAL_RED_FLAG_TYPE in crisis_types:
        return "medical_red_flag"
    return "psychological_crisis"


def _should_alert_from_semantic(has_crisis: bool, severity_score: int, crisis_types: List[str]) -> bool:
    if not has_crisis:
        return False
    if MEDICAL_RED_FLAG_TYPE in crisis_types:
        return True
    if any(item in DIRECT_HIGH_RISK_TYPES for item in crisis_types):
        return True
    return severity_score >= getattr(Config, "CRISIS_ALERT_THRESHOLD", 10)


def _coerce_score(value: Any, default: int = 0) -> int:
    try:
        return max(0, min(20, int(value or default)))
    except (TypeError, ValueError):
        return default


def build_crisis_alarm(
    crisis_data: Optional[Dict[str, Any]],
    emotional_analysis: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    """根据危机元数据构建稳定的分级报警描述。"""
    data = crisis_data or {}
    emotional = emotional_analysis or {}
    alert_type = data.get("alert_type")
    severity_score = _coerce_score(data.get("severity_score"))
    emotion_severity = _coerce_score(emotional.get("severity"))
    crisis_types = [str(item) for item in (data.get("crisis_types") or [])]
    alert = bool(data.get("alert", False))
    explicit_level = str(data.get("crisis_level") or data.get("alarm_level") or "")

    if alert_type == "medical_red_flag" or MEDICAL_RED_FLAG_TYPE in crisis_types:
        level = "critical"
        profile = {
            **CRISIS_ALARM_PROFILES[level],
            "title": "身体红旗紧急提醒",
            "message": "检测到移植相关身体红旗信号，请优先联系护士、医生或院内急救流程。",
            "action": "contact_medical_staff",
        }
        severity_score = max(severity_score, 20)
    elif explicit_level in CRISIS_ALARM_PROFILES:
        level = explicit_level
        profile = CRISIS_ALARM_PROFILES[level]
    elif alert:
        if severity_score >= 16:
            level = "critical"
        elif severity_score >= 13 or any(item in DIRECT_HIGH_RISK_TYPES for item in crisis_types):
            level = "high"
        else:
            level = "warning"
        if alert_type == "severe_emotional_distress" and level == "high":
            level = "warning"
        profile = CRISIS_ALARM_PROFILES[level]
    elif severity_score >= 7 or emotion_severity >= 7 or crisis_types:
        level = "watch"
        profile = CRISIS_ALARM_PROFILES[level]
    else:
        level = "none"
        profile = CRISIS_ALARM_PROFILES[level]

    return {
        "level": level,
        "crisisLevel": level,
        "label": profile["label"],
        "title": profile["title"],
        "message": profile["message"],
        "action": profile["action"],
        "shouldNotify": bool(profile["shouldNotify"]),
        "requiresImmediateAction": bool(profile["requiresImmediateAction"]),
        "severityScore": severity_score,
        "crisisTypes": crisis_types,
        "alertType": alert_type,
    }


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

    def assess_crisis(
        self,
        user_message: str,
        emotional_analysis: Dict,
        psych_model_context: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        危机语义评估，不产生副作用。

        规则：
        - 如开启 CRISIS_LLM_DETECTION_ENABLED，优先使用 LLM 做语义判断；
        - 只有 LLM 不可用、调用失败或输出异常时，才回退到关键词规则；
        - 返回 alert、source、severity_score 等元数据，供上层决定是否记录和报警。
        """
        if not Config.CRISIS_DETECTION_ENABLED:
            return self._enrich_crisis_detection({"alert": False, "source": "disabled"})

        if Config.CRISIS_LLM_DETECTION_ENABLED:
            llm_result = self._llm_detect_crisis(user_message, emotional_analysis, psych_model_context)
            if isinstance(llm_result, dict):
                sev = int(llm_result.get("severity_score", 0) or 0)
                crisis_types = llm_result.get("crisis_types") or []
                alert = _should_alert_from_semantic(
                    llm_result.get("has_crisis") is True,
                    sev,
                    crisis_types,
                )
                result = {
                    "alert": alert,
                    "alert_type": _alert_type_for_crisis(crisis_types, alert),
                    "source": "llm_semantic",
                    "severity_score": sev,
                    "crisis_types": crisis_types,
                    "reason": llm_result.get("reason", ""),
                }
                return self._enrich_crisis_detection(result, emotional_analysis)

        rule = self._rule_based_detect_crisis(user_message, emotional_analysis)
        if rule.get("alert", False):
            result = {
                **rule,
                "alert_type": "psychological_crisis",
                "source": "keyword_fallback",
            }
            return self._enrich_crisis_detection(result, emotional_analysis)
        return {
            **self._enrich_crisis_detection(rule, emotional_analysis),
            "source": "keyword_fallback",
        }

    def assess_crisis_semantic_only(
        self,
        user_message: str,
        emotional_analysis: Dict,
        psych_model_context: Optional[str] = None,
        fast_precheck: bool = False,
    ) -> Dict[str, Any]:
        """仅使用 LLM 语义判断危机，不回退关键词规则。"""
        if not Config.CRISIS_DETECTION_ENABLED:
            return self._enrich_crisis_detection({"alert": False, "source": "disabled"})

        if not Config.CRISIS_LLM_DETECTION_ENABLED:
            return self._enrich_crisis_detection({
                "alert": False,
                "alert_type": None,
                "source": "llm_semantic_disabled",
                "severity_score": 0,
                "crisis_types": [],
                "reason": "危机语义判断已关闭，未启用关键词规则兜底。",
            })

        llm_result = self._llm_detect_crisis(
            user_message,
            emotional_analysis,
            psych_model_context,
            fast_precheck=fast_precheck,
        )
        if isinstance(llm_result, dict):
            sev = int(llm_result.get("severity_score", 0) or 0)
            crisis_types = llm_result.get("crisis_types") or []
            alert = _should_alert_from_semantic(
                llm_result.get("has_crisis") is True,
                sev,
                crisis_types,
            )
            result = {
                "alert": alert,
                "alert_type": _alert_type_for_crisis(crisis_types, alert),
                "source": "llm_semantic",
                "severity_score": sev,
                "crisis_types": crisis_types,
                "reason": llm_result.get("reason", ""),
                "precheck_fast": fast_precheck,
                "llm_latency_ms": llm_result.get("llm_latency_ms"),
            }
            return self._enrich_crisis_detection(result, emotional_analysis)

        return self._enrich_crisis_detection({
            "alert": False,
            "alert_type": None,
            "source": "llm_semantic_unavailable",
            "severity_score": 0,
            "crisis_types": [],
            "reason": "危机语义判断暂不可用，未启用关键词规则兜底。",
        })

    def detect_crisis(self, user_message: str, emotional_analysis: Dict) -> Dict[str, bool]:
        """
        危机检测兼容旧接口，只返回 alert。

        新逻辑已经改为语义判断优先；关键词只在模型不可用或关闭时兜底。
        """
        result = self.assess_crisis(user_message, emotional_analysis)
        if result.get("alert", False):
            self._record_crisis_event(user_message, result.get("severity_score"), result)
            self._trigger_alert(result)
            return {"alert": True}

        return {"alert": False}

    def _rule_based_detect_crisis(self, user_message: str, emotional_analysis: Dict) -> Dict[str, bool]:
        """兜底：关键词规则，只输出是否需要报警"""
        detected_types = self._analyze_crisis_keywords(user_message)

        if any(crisis_type in detected_types for crisis_type in DIRECT_CRISIS_ALERT_TYPES):
            self._check_consecutive_crisis(detected_types)
            return {
                "alert": True,
                "severity_score": 20,
                "crisis_types": detected_types,
                "reason": "关键词兜底命中直接危机类型。",
            }

        emotional_severity = int((emotional_analysis or {}).get("severity", 0) or 0)
        keyword_score = len(detected_types) * 3
        total_score = keyword_score + emotional_severity

        consecutive_factor = self._check_consecutive_crisis(detected_types)
        total_score += consecutive_factor

        threshold = getattr(Config, "CRISIS_ALERT_THRESHOLD", 10)
        return {
            "alert": total_score >= threshold,
            "severity_score": min(20, total_score),
            "crisis_types": detected_types,
            "reason": "关键词兜底综合情绪强度和危机词命中结果。",
        }

    def _enrich_crisis_detection(
        self,
        result: Dict[str, Any],
        emotional_analysis: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        alarm = build_crisis_alarm(result, emotional_analysis)
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

    def _llm_detect_crisis(
        self,
        user_message: str,
        emotional_analysis: Dict,
        psych_model_context: Optional[str] = None,
        fast_precheck: bool = False,
    ) -> Optional[Dict[str, Any]]:
        client = self._get_llm_client()
        if client is None:
            return None

        try:
            emotion = emotional_analysis or {}
            primary = emotion.get("primary", "")
            severity = emotion.get("severity", 0)

            if fast_precheck:
                psych_context = (psych_model_context or "无").strip()
                if len(psych_context) > 700:
                    psych_context = psych_context[:700] + "..."
                system_prompt = (
                    "你是“小芽”的流式安全预检器。只做文本语义安全判断，不做关键词匹配，"
                    "不安慰，不展开分析，只返回 JSON。"
                )
                user_prompt = (
                    "判断患者这句话是否需要在回复前先进入安全/医护提醒。"
                    "必须结合完整语义、否定、上下文和长期心理模型，当前原话优先。\n"
                    'JSON字段：{"has_crisis":布尔,"crisis_types":数组,'
                    '"severity_score":0到20整数,"reason":"不超过20字"}\n'
                    'crisis_types 只能从 ["自杀危机","自伤危机","重度抑郁","惊恐发作",'
                    '"急性焦虑","情绪崩溃","身体红旗"] 中选择，可为空数组。\n'
                    "明确自杀/自伤意图、计划、冲动或无法保证安全，判为高分；"
                    "移植患者出现需要现场医护优先确认的身体风险，使用“身体红旗”。\n"
                    f"患者原话：{user_message}\n"
                    f"情绪参考：primary={primary}, severity={severity}\n"
                    f"长期心理模型摘要：{psych_context}\n"
                    "只输出 JSON。"
                )
                model = getattr(Config, "CRISIS_PRECHECK_MODEL", Config.LLM_DETECTION_MODEL)
                temperature = getattr(Config, "CRISIS_PRECHECK_TEMPERATURE", 0.0)
                max_tokens = getattr(Config, "CRISIS_PRECHECK_MAX_TOKENS", 96)
                timeout = getattr(Config, "CRISIS_PRECHECK_TIMEOUT_SECONDS", 2.2)
            else:
                system_prompt = (
                    "你是“小芽”系统中的语义安全评估助手，专门为骨髓移植患者做文本级别的危机筛查。"
                    "你必须根据患者原话的语义、上下文和用户长期心理模型综合判断，不要做关键词匹配。"
                    "你只做“是否存在危机及类型”的判断，不进行安慰或治疗，也不提供具体医疗方案。"
                )
                user_prompt = (
                    "请阅读下面这段患者的话，结合其情绪分析结果，判断是否存在心理或身体安全危机，并严格按照 JSON 输出：\n"
                    "{\n"
                    '  "has_crisis": true 或 false,\n'
                    '  "crisis_types": 数组，元素只能是以下之一：'
                    '["自杀危机","自伤危机","重度抑郁","惊恐发作","急性焦虑","情绪崩溃","身体红旗"],\n'
                    '  "severity_score": 0 到 20 的整数（综合你对风险的判断）, \n'
                    '  "reason": 用一两句话简要说明判断依据\n'
                    "}\n\n"
                    "判断要求：\n"
                    "- 不要因为单个词机械触发；必须看整句话意图、否定、解释和上下文。\n"
                    "- 如果表达了明确自杀/自伤意图、计划、冲动、无法保证安全，通常属于高危或紧急危机。\n"
                    "- 如果骨髓移植患者表达胸痛、喘不过气、呼吸困难、持续高热、严重出血、意识异常等需要现场医护优先处理的身体风险，类型使用“身体红旗”。\n"
                    "- 用户长期心理模型只作为风险校准参考；当前原话优先。\n\n"
                    f"患者原话：{user_message}\n"
                    f"情绪分析（供参考，可不完全采纳）：primary={primary}, severity={severity}\n\n"
                    f"用户长期心理模型（可为空）：\n{psych_model_context or '无'}\n\n"
                    "只输出 JSON，不要添加任何解释或多余文字。"
                )
                model = Config.LLM_DETECTION_MODEL
                temperature = Config.LLM_DETECTION_TEMPERATURE
                max_tokens = Config.LLM_DETECTION_MAX_TOKENS
                timeout = None

            start_time = time.perf_counter()
            resp = create_chat_completion_json(
                client,
                schema_model=CrisisPayload,
                schema_name="xiaoya_crisis_detection",
                model=model,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                temperature=temperature,
                max_tokens=max_tokens,
                **({"timeout": timeout} if timeout else {}),
            )
            content = resp.choices[0].message.content.strip()
            data = parse_structured_json(content, CrisisPayload)
            if isinstance(data, dict):
                data["llm_latency_ms"] = int((time.perf_counter() - start_time) * 1000)
            return data if isinstance(data, dict) else None
        except Exception as exc:
            error_name = exc.__class__.__name__.lower()
            error_text = str(exc).lower()
            if fast_precheck and (
                "timeout" in error_name or "timed out" in error_text or "timeout" in error_text
            ):
                logger.warning("流式安全预检超时，继续主回复生成：%s", exc)
            else:
                logger.exception("LLM 危机检测失败%s", "（流式快速预检）" if fast_precheck else "")
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

    def _record_crisis_event(
        self,
        user_message: str,
        severity_score: Optional[int] = None,
        crisis_data: Optional[Dict[str, Any]] = None,
    ):
        alarm = build_crisis_alarm(
            crisis_data or {
                "alert": severity_score is not None,
                "alert_type": "psychological_crisis" if severity_score is not None else None,
                "severity_score": severity_score,
            }
        )
        event = {
            "timestamp": datetime.now().isoformat(),
            "user_message": user_message,
            "severity_score": alarm["severityScore"],
            "crisis_level": alarm["level"],
            "alarm_label": alarm["label"],
            "action": alarm["action"],
            "crisis_types": alarm["crisisTypes"],
        }
        self.crisis_history.append(event)
        if len(self.crisis_history) > 50:
            self.crisis_history = self.crisis_history[-50:]

    def _trigger_alert(self, crisis_data: Dict):
        alarm = build_crisis_alarm(crisis_data)
        enriched = {
            **(crisis_data or {}),
            "crisis_level": alarm["level"],
            "alarm": alarm,
            "alarm_level": alarm["level"],
            "alarm_label": alarm["label"],
            "alarm_action": alarm["action"],
            "alarm_message": alarm["message"],
            "should_notify": alarm["shouldNotify"],
            "requires_immediate_action": alarm["requiresImmediateAction"],
        }
        if self.alert_callback:
            self.alert_callback(enriched)
        else:
            log_level = logging.CRITICAL if alarm["level"] == "critical" else logging.ERROR if alarm["level"] == "high" else logging.WARNING
            logger.log(log_level, "%s：%s", alarm["title"], alarm["message"])

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
