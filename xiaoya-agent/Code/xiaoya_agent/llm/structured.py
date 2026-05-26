"""模型分析调用的结构化输出辅助工具。

项目会调用 OpenAI 兼容服务商。部分服务商支持 ``response_format``，
部分不支持，因此这里会优先尝试结构化响应格式，不支持时再安全地
降级重试。两条路径都会使用 Pydantic schema 校验，从而为智能体
其余部分提供稳定的数据结构。
"""
from __future__ import annotations

import json
import logging
import re
from typing import Any, Dict, List, Optional, Type, TypeVar

from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator

logger = logging.getLogger(__name__)

SchemaT = TypeVar("SchemaT", bound=BaseModel)


def _clamp_int(value: Any, low: int, high: int, default: int) -> int:
    try:
        number = int(value)
    except Exception:
        number = default
    return max(low, min(high, number))


def _clamp_float(value: Any, low: float, high: float, default: float) -> float:
    try:
        number = float(value)
    except Exception:
        number = default
    return max(low, min(high, number))


def _coerce_string_list(value: Any) -> List[str]:
    if value is None:
        return []
    if isinstance(value, str):
        return [value] if value.strip() else []
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item).strip()]


class EmotionalStatePayload(BaseModel):
    model_config = ConfigDict(extra="ignore")

    primary: str = "neutral"
    severity: int = Field(default=1, ge=1, le=10)

    @field_validator("primary", mode="before")
    @classmethod
    def normalize_primary(cls, value: Any) -> str:
        return str(value or "neutral").strip() or "neutral"

    @field_validator("severity", mode="before")
    @classmethod
    def normalize_severity(cls, value: Any) -> int:
        return _clamp_int(value, 1, 10, 1)


class CBTAnalysisPayload(BaseModel):
    model_config = ConfigDict(extra="ignore")

    emotional_state: EmotionalStatePayload = Field(default_factory=EmotionalStatePayload)
    cognitive_distortions: List[str] = Field(default_factory=list)
    problem_severity: int = Field(default=1, ge=1, le=10)
    intervention_needed: bool = False
    recommended_technique: Optional[str] = None

    @field_validator("cognitive_distortions", mode="before")
    @classmethod
    def normalize_distortions(cls, value: Any) -> List[str]:
        return _coerce_string_list(value)

    @field_validator("problem_severity", mode="before")
    @classmethod
    def normalize_problem_severity(cls, value: Any) -> int:
        return _clamp_int(value, 1, 10, 1)

    @field_validator("recommended_technique", mode="before")
    @classmethod
    def normalize_recommended_technique(cls, value: Any) -> Optional[str]:
        if value is None:
            return None
        text = str(value).strip()
        return text or None


class CrisisPayload(BaseModel):
    model_config = ConfigDict(extra="ignore")

    has_crisis: bool = False
    crisis_types: List[str] = Field(default_factory=list)
    severity_score: int = Field(default=0, ge=0, le=20)
    reason: str = ""

    @field_validator("crisis_types", mode="before")
    @classmethod
    def normalize_types(cls, value: Any) -> List[str]:
        return _coerce_string_list(value)

    @field_validator("severity_score", mode="before")
    @classmethod
    def normalize_severity_score(cls, value: Any) -> int:
        return _clamp_int(value, 0, 20, 0)

    @field_validator("reason", mode="before")
    @classmethod
    def normalize_reason(cls, value: Any) -> str:
        return str(value or "").strip()


class TransplantDecisionPayload(BaseModel):
    model_config = ConfigDict(extra="ignore")

    should_trigger: bool = False
    phase: Optional[str] = None
    scenario: Optional[str] = None
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    reason: str = ""

    @field_validator("phase", "scenario", mode="before")
    @classmethod
    def normalize_optional_text(cls, value: Any) -> Optional[str]:
        if value is None:
            return None
        text = str(value).strip()
        return text or None

    @field_validator("confidence", mode="before")
    @classmethod
    def normalize_confidence(cls, value: Any) -> float:
        return _clamp_float(value, 0.0, 1.0, 0.0)

    @field_validator("reason", mode="before")
    @classmethod
    def normalize_reason(cls, value: Any) -> str:
        return str(value or "").strip()


class EnergyAssessmentPayload(BaseModel):
    model_config = ConfigDict(extra="ignore")

    cognitive_growth: int = Field(default=0, ge=0, le=20)
    emotion_regulation: int = Field(default=0, ge=0, le=25)
    behavior_change: int = Field(default=0, ge=0, le=20)
    social_connection: int = Field(default=0, ge=0, le=15)
    self_efficacy: int = Field(default=0, ge=0, le=20)
    assessment_note: str = ""
    achievement_signals: Dict[str, bool] = Field(default_factory=dict)

    @field_validator("cognitive_growth", "behavior_change", "self_efficacy", mode="before")
    @classmethod
    def normalize_twenty_point_gain(cls, value: Any) -> int:
        return _clamp_int(value, 0, 20, 0)

    @field_validator("emotion_regulation", mode="before")
    @classmethod
    def normalize_emotion_gain(cls, value: Any) -> int:
        return _clamp_int(value, 0, 25, 0)

    @field_validator("social_connection", mode="before")
    @classmethod
    def normalize_social_gain(cls, value: Any) -> int:
        return _clamp_int(value, 0, 15, 0)

    @field_validator("assessment_note", mode="before")
    @classmethod
    def normalize_note(cls, value: Any) -> str:
        return str(value or "").strip()

    @field_validator("achievement_signals", mode="before")
    @classmethod
    def normalize_signals(cls, value: Any) -> Dict[str, bool]:
        if not isinstance(value, dict):
            return {}
        return {str(key): bool(val) for key, val in value.items()}


class PsychModelPatchPayload(BaseModel):
    model_config = ConfigDict(extra="ignore")

    preferred_name: Optional[str] = None
    current_main_concerns: List[str] = Field(default_factory=list)
    cognitive_patterns: List[str] = Field(default_factory=list)
    effective_strategies: List[str] = Field(default_factory=list)
    support_preferences: List[str] = Field(default_factory=list)
    risk_notes: List[str] = Field(default_factory=list)
    communication_style: Optional[str] = None
    evidence: List[str] = Field(default_factory=list)

    @field_validator("preferred_name", "communication_style", mode="before")
    @classmethod
    def normalize_optional_text(cls, value: Any) -> Optional[str]:
        if value is None:
            return None
        text = str(value).strip()
        return text or None

    @field_validator(
        "current_main_concerns",
        "cognitive_patterns",
        "effective_strategies",
        "support_preferences",
        "risk_notes",
        "evidence",
        mode="before",
    )
    @classmethod
    def normalize_list(cls, value: Any) -> List[str]:
        return _coerce_string_list(value)


class UnifiedAnalysisPayload(CBTAnalysisPayload):
    crisis: CrisisPayload = Field(default_factory=CrisisPayload)
    transplant: TransplantDecisionPayload = Field(default_factory=TransplantDecisionPayload)
    energy_assessment: EnergyAssessmentPayload = Field(default_factory=EnergyAssessmentPayload)
    psych_model_patch: PsychModelPatchPayload = Field(default_factory=PsychModelPatchPayload)


def extract_json_object(text: str) -> Optional[Dict[str, Any]]:
    """从模型原始文本中提取 JSON 对象。"""
    content = (text or "").strip()
    if not content:
        return None

    if content.startswith("```"):
        parts = content.split("```")
        if len(parts) >= 2:
            content = parts[1].strip()
            if content.lower().startswith("json"):
                content = "\n".join(content.splitlines()[1:]).strip()

    try:
        parsed = json.loads(content)
        return parsed if isinstance(parsed, dict) else None
    except Exception:
        pass

    match = re.search(r"\{.*\}", content, flags=re.DOTALL)
    if not match:
        return None
    try:
        parsed = json.loads(match.group(0))
    except Exception:
        return None
    return parsed if isinstance(parsed, dict) else None


def parse_structured_json(text: str, schema_model: Type[SchemaT]) -> Optional[Dict[str, Any]]:
    payload = extract_json_object(text)
    if payload is None:
        return None
    try:
        return schema_model.model_validate(payload).model_dump()
    except ValidationError:
        logger.exception("Model JSON did not match schema %s", schema_model.__name__)
        return None


def _response_format(schema_model: Type[BaseModel], schema_name: str) -> Optional[Dict[str, Any]]:
    from xiaoya_agent.config import Config

    if not getattr(Config, "STRUCTURED_OUTPUT_ENABLED", True):
        return None
    mode = str(getattr(Config, "STRUCTURED_OUTPUT_MODE", "json_object") or "json_object").lower()
    if mode == "json_schema":
        return {
            "type": "json_schema",
            "json_schema": {
                "name": schema_name,
                "schema": schema_model.model_json_schema(),
                "strict": bool(getattr(Config, "STRUCTURED_OUTPUT_STRICT", False)),
            },
        }
    if mode == "json_object":
        return {"type": "json_object"}
    return None


def create_chat_completion_json(
    client: Any,
    schema_model: Type[BaseModel],
    schema_name: str,
    **kwargs: Any,
) -> Any:
    """创建一次聊天补全，并尽力启用结构化输出。"""
    response_format = _response_format(schema_model, schema_name)
    if not response_format:
        return client.chat.completions.create(**kwargs)

    try:
        return client.chat.completions.create(**kwargs, response_format=response_format)
    except Exception as exc:
        error_name = exc.__class__.__name__.lower()
        error_text = str(exc).lower()
        if "timeout" in error_name or "timed out" in error_text or "timeout" in error_text:
            raise
        logger.warning(
            "Provider rejected response_format=%s; retrying without structured response_format.",
            response_format.get("type"),
            exc_info=True,
        )
        return client.chat.completions.create(**kwargs)
