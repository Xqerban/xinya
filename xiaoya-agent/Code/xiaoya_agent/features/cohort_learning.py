"""跨用户匿名群体学习。

该模块只读取每个用户已经结构化保存的心理模型字段，聚合骨髓移植患者群体中的
常见关注、情绪、认知模式和有效支持方式。它不读取完整聊天原文，也不会把某个
用户的 ID 写入群体模型。
"""
from __future__ import annotations

import json
import os
import threading
import time
from collections import Counter
from datetime import datetime
from typing import Any, Dict, Iterable, List, Optional

from xiaoya_agent.config import Config
from xiaoya_agent.database import database_storage_enabled, get_database_repository


COHORT_DIRNAME = "cohort_learning"
COHORT_MODEL_FILENAME = "cohort_model.json"
COHORT_DIRTY_FILENAME = "dirty.flag"
PSYCH_MODEL_FILENAME = "psych_model.json"
USER_MODEL_METADATA_FILENAME = "psych_model_meta.json"

_refresh_lock = threading.Lock()
_refresh_in_progress = False


def _now_iso() -> str:
    return datetime.now().replace(microsecond=0).isoformat()


def _data_dir() -> str:
    return os.path.abspath(Config.DATA_DIR)


def _users_root() -> str:
    return os.path.join(_data_dir(), "users")


def _cohort_root() -> str:
    path = os.path.join(_data_dir(), COHORT_DIRNAME)
    if not database_storage_enabled():
        os.makedirs(path, exist_ok=True)
    return path


def _cohort_model_path() -> str:
    return os.path.join(_cohort_root(), COHORT_MODEL_FILENAME)


def _cohort_dirty_path() -> str:
    return os.path.join(_cohort_root(), COHORT_DIRTY_FILENAME)


def _read_json(path: str, default: Any) -> Any:
    try:
        if not os.path.exists(path):
            return default
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


def _write_json_atomic(path: str, data: Any) -> None:
    if database_storage_enabled():
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    temp_path = f"{path}.tmp"
    with open(temp_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.replace(temp_path, path)


def _clean_text(value: Any, limit: int = 80) -> Optional[str]:
    text = str(value or "").strip()
    if not text:
        return None
    return text[:limit]


def _iter_clean_texts(values: Any, limit: int = 80) -> Iterable[str]:
    if isinstance(values, str):
        values = [values]
    if not isinstance(values, list):
        return []
    cleaned = []
    seen = set()
    for value in values:
        text = _clean_text(value, limit=limit)
        if text and text not in seen:
            seen.add(text)
            cleaned.append(text)
    return cleaned


def _add_user_list(counter: Counter, values: Any) -> None:
    for text in _iter_clean_texts(values):
        counter[text] += 1


def _add_user_emotions(counter: Counter, profile: Dict[str, Any]) -> None:
    emotions = set()
    recurring = profile.get("recurring_emotions") or {}
    if isinstance(recurring, dict):
        for emotion, count in recurring.items():
            if int(count or 0) > 0:
                clean = _clean_text(emotion, limit=32)
                if clean:
                    emotions.add(clean)
    last_emotion = _clean_text(profile.get("last_emotion"), limit=32)
    if last_emotion:
        emotions.add(last_emotion)
    for emotion in emotions:
        counter[emotion] += 1


def _counter_items(counter: Counter, min_count: int, limit: int) -> List[Dict[str, Any]]:
    return [
        {"text": text, "userCount": count}
        for text, count in counter.most_common()
        if count >= min_count
    ][:limit]


def _read_user_models(max_users: int = 500) -> List[Dict[str, Any]]:
    if database_storage_enabled():
        return get_database_repository().list_psych_models(max_users=max_users)
    root = _users_root()
    if not os.path.isdir(root):
        return []

    models: List[Dict[str, Any]] = []
    for item in os.scandir(root):
        if not item.is_dir():
            continue
        model_path = os.path.join(item.path, PSYCH_MODEL_FILENAME)
        model = _read_json(model_path, None)
        if not isinstance(model, dict):
            continue
        profile = model.get("personalization_profile")
        if not isinstance(profile, dict):
            continue
        metadata = _read_json(os.path.join(item.path, USER_MODEL_METADATA_FILENAME), {})
        models.append({
            "safeUserId": item.name,
            "updatedAt": model.get("updatedAt") or (metadata or {}).get("updatedAt"),
            "model": model,
            "profile": profile,
        })
        if len(models) >= max_users:
            break
    return models


def rebuild_cohort_learning_model(force: bool = True) -> Dict[str, Any]:
    """重建匿名群体学习模型。"""
    if not getattr(Config, "COHORT_LEARNING_ENABLED", True):
        payload = {
            "modelVersion": 1,
            "enabled": False,
            "eligible": False,
            "updatedAt": _now_iso(),
            "userCount": 0,
            "reason": "COHORT_LEARNING_ENABLED=false",
            "signals": {},
        }
        if database_storage_enabled():
            return get_database_repository().save_cohort_model(payload, dirty=False)
        _write_json_atomic(_cohort_model_path(), payload)
        return payload

    ttl = max(0, int(getattr(Config, "COHORT_LEARNING_REFRESH_SECONDS", 300) or 300))
    if database_storage_enabled():
        repo = get_database_repository()
        cached = repo.load_cohort_model()
        if not force and isinstance(cached, dict) and ttl > 0 and not repo.cohort_model_dirty():
            try:
                updated_at = str(cached.get("updatedAt") or "")
                age = time.time() - datetime.fromisoformat(updated_at).timestamp()
            except Exception:
                age = ttl + 1
            if age < ttl:
                return cached
    else:
        path = _cohort_model_path()
        if not force and os.path.exists(path) and ttl > 0:
            age = time.time() - os.path.getmtime(path)
            if age < ttl:
                cached = _read_json(path, None)
                if isinstance(cached, dict):
                    return cached

    max_users = int(getattr(Config, "COHORT_LEARNING_MAX_USERS", 500) or 500)
    models = _read_user_models(max_users=max_users)
    user_count = len(models)
    min_users = max(1, int(getattr(Config, "COHORT_LEARNING_MIN_USERS", 2) or 2))
    min_signal_users = max(1, int(getattr(Config, "COHORT_LEARNING_MIN_SIGNAL_USERS", 2) or 2))
    max_items = max(1, int(getattr(Config, "COHORT_LEARNING_MAX_CONTEXT_ITEMS", 4) or 4))

    concerns = Counter()
    emotions = Counter()
    cognitive_patterns = Counter()
    strategies = Counter()
    preferences = Counter()
    risk_notes = Counter()
    communication_styles = Counter()
    phases = Counter()

    for item in models:
        model = item["model"]
        profile = item["profile"]
        _add_user_list(concerns, profile.get("current_main_concerns"))
        _add_user_emotions(emotions, profile)
        _add_user_list(cognitive_patterns, profile.get("cognitive_patterns"))
        _add_user_list(strategies, profile.get("effective_strategies"))
        _add_user_list(preferences, profile.get("support_preferences"))
        _add_user_list(risk_notes, profile.get("risk_notes"))

        communication_style = _clean_text(profile.get("communication_style"), limit=32)
        if communication_style:
            communication_styles[communication_style] += 1

        phase = _clean_text((model.get("user_state") or {}).get("transplant_phase"), limit=32)
        if phase:
            phases[phase] += 1

    eligible = user_count >= min_users
    signal_min = min_signal_users if eligible else user_count + 1
    payload = {
        "modelVersion": 1,
        "enabled": True,
        "source": "anonymous_user_psych_models",
        "updatedAt": _now_iso(),
        "userCount": user_count,
        "eligible": eligible,
        "minUsers": min_users,
        "minSignalUsers": min_signal_users,
        "privacy": {
            "storesUserIds": False,
            "readsRawConversationText": False,
            "onlyAggregatesStructuredPsychModels": True,
        },
        "signals": {
            "commonConcerns": _counter_items(concerns, signal_min, max_items),
            "commonEmotions": _counter_items(emotions, signal_min, max_items),
            "commonCognitivePatterns": _counter_items(cognitive_patterns, signal_min, max_items),
            "effectiveStrategies": _counter_items(strategies, signal_min, max_items),
            "supportPreferences": _counter_items(preferences, signal_min, max_items),
            "riskNotes": _counter_items(risk_notes, signal_min, max_items),
            "communicationStyles": _counter_items(communication_styles, signal_min, max_items),
            "transplantPhases": _counter_items(phases, 1 if eligible else user_count + 1, max_items),
        },
        "reason": "ok" if eligible else "not_enough_users",
    }
    if database_storage_enabled():
        return get_database_repository().save_cohort_model(payload, dirty=False)
    _write_json_atomic(path, payload)
    dirty_path = _cohort_dirty_path()
    if os.path.exists(dirty_path):
        try:
            os.remove(dirty_path)
        except Exception:
            pass
    return payload


def get_cohort_learning_model(refresh_if_stale: bool = True) -> Dict[str, Any]:
    """读取群体学习模型，必要时按 TTL 重建。"""
    if database_storage_enabled():
        repo = get_database_repository()
        if refresh_if_stale and repo.cohort_model_dirty():
            return rebuild_cohort_learning_model(force=True)
        if refresh_if_stale:
            return rebuild_cohort_learning_model(force=False)
        model = repo.load_cohort_model()
        if isinstance(model, dict):
            return model
        return rebuild_cohort_learning_model(force=True)

    path = _cohort_model_path()
    dirty_path = _cohort_dirty_path()
    if refresh_if_stale and os.path.exists(dirty_path):
        if not os.path.exists(path) or os.path.getmtime(dirty_path) >= os.path.getmtime(path):
            return rebuild_cohort_learning_model(force=True)

    if refresh_if_stale:
        return rebuild_cohort_learning_model(force=False)
    model = _read_json(path, None)
    if isinstance(model, dict):
        return model
    return rebuild_cohort_learning_model(force=True)


def _format_signal_items(items: List[Dict[str, Any]]) -> str:
    return "；".join(str(item.get("text")) for item in items if item.get("text"))


def get_cohort_learning_context(current_user_id: Optional[str] = None, force_refresh: bool = False) -> str:
    """返回可注入主回复的匿名群体经验提示词片段。"""
    if not getattr(Config, "COHORT_LEARNING_CONTEXT_ENABLED", True):
        return ""

    model = rebuild_cohort_learning_model(force=force_refresh) if force_refresh else get_cohort_learning_model()
    if not model.get("enabled") or not model.get("eligible"):
        return ""

    signals = model.get("signals") or {}
    lines = []
    for label, key in [
        ("群体常见关注", "commonConcerns"),
        ("群体常见情绪", "commonEmotions"),
        ("群体常见认知模式", "commonCognitivePatterns"),
        ("群体中较常有效的支持方式", "effectiveStrategies"),
        ("群体偏好的回应方式", "supportPreferences"),
        ("群体风险提醒", "riskNotes"),
    ]:
        text = _format_signal_items(signals.get(key) or [])
        if text:
            lines.append(f"- {label}：{text}")

    if not lines:
        return ""

    return (
        "[骨髓移植患者群体经验] 以下内容来自多个用户心理模型的匿名聚合，只能作为轻量先验。"
        "不要把它说成某个用户的经历，不要替代当前用户原话，也不要据此做医疗结论；"
        "当当前用户表达不明确时，可以用这些共性来选择更稳妥的共情方向、提问方式和支持策略。\n"
        + "\n".join(lines)
    )


def schedule_cohort_learning_refresh() -> None:
    """异步刷新群体学习模型，避免阻塞当前回复。"""
    if not getattr(Config, "COHORT_LEARNING_ENABLED", True):
        return

    global _refresh_in_progress
    with _refresh_lock:
        if _refresh_in_progress:
            return
        _refresh_in_progress = True

    def worker() -> None:
        global _refresh_in_progress
        try:
            rebuild_cohort_learning_model(force=True)
        finally:
            with _refresh_lock:
                _refresh_in_progress = False

    threading.Thread(target=worker, daemon=True).start()


def mark_cohort_learning_dirty() -> None:
    """标记群体学习模型需要重建，不在保存用户模型时同步扫描所有用户。"""
    if not getattr(Config, "COHORT_LEARNING_ENABLED", True):
        return
    if database_storage_enabled():
        get_database_repository().mark_cohort_model_dirty()
        return
    try:
        with open(_cohort_dirty_path(), "w", encoding="utf-8") as f:
            f.write(_now_iso())
    except Exception:
        pass
