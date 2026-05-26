"""提示词配置注册表。

这里不绑定具体 Web 后台，只提供一个稳定的运行时配置层。MySQL 存储模式下注册表直接保存在数据库；
旧的 ``data/prompt_registry.json`` 只作为一次性迁移来源。
"""
import copy
import difflib
import json
import os
import threading
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from pydantic import BaseModel, ConfigDict, Field, field_validator

from xiaoya_agent.database import database_storage_enabled, get_database_repository


DEFAULT_REALTIME_INSTRUCTION = (
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


PROMPT_PROFILE_SUFFIXES: Dict[str, str] = {
    "warm_cbt": "",
    "transplant_companion": (
        "你需要特别关注骨髓移植患者的病房处境、治疗不确定性、隔离感和身体不适，"
        "但不要替代医生或护士给出诊疗建议。"
    ),
    "clinical_plain": (
        "回复应更克制、清晰、偏临床沟通风格，少用抒情表达，优先给出明确、安全、可执行的小步骤。"
    ),
}


OUTPUT_MODE_INSTRUCTIONS: Dict[str, str] = {
    "brief_support": DEFAULT_REALTIME_INSTRUCTION,
    "cbt_exercise": (
        DEFAULT_REALTIME_INSTRUCTION
        + "当前输出模式是 CBT 小练习：如果适合，请给出一个非常短的可执行练习，"
        "例如改写一个想法、做一次呼吸观察或提出一个证据问题；不要展开教学。"
    ),
    "transplant_support": (
        DEFAULT_REALTIME_INSTRUCTION
        + "当前输出模式是移植陪伴：优先结合移植阶段、病房体验和医护支持，"
        "用患者能立刻做的一小步稳定当下。"
    ),
    "safety_first": (
        DEFAULT_REALTIME_INSTRUCTION
        + "当前输出模式是安全优先：如果存在心理或身体安全风险，先强调联系医护、家属或现场支持，"
        "再给一句简短陪伴，不做复杂 CBT 练习。"
    ),
}


@dataclass
class PromptRuntimeConfig:
    profile: str
    output_mode: str
    system_prompt: str
    realtime_instruction: str
    profile_version: int = 1
    output_mode_version: int = 1


class PromptEntryPayload(BaseModel):
    """经过校验的提示词注册表写入载荷。"""

    model_config = ConfigDict(extra="ignore")

    content: str = ""
    description: str = ""
    change_note: str = ""
    metadata: Dict[str, Any] = Field(default_factory=dict)

    @field_validator("content", "description", "change_note", mode="before")
    @classmethod
    def normalize_text(cls, value: Any) -> str:
        return str(value or "").strip()

    @field_validator("metadata", mode="before")
    @classmethod
    def normalize_metadata(cls, value: Any) -> Dict[str, Any]:
        return dict(value or {}) if isinstance(value, dict) else {}


_registry_lock = threading.RLock()
_registry_cache: Optional[Dict[str, Dict[str, Dict[str, Any]]]] = None
_registry_cache_mtime: Optional[float] = None
_registry_cache_path: Optional[str] = None


def _now_iso() -> str:
    return datetime.now().replace(microsecond=0).isoformat()


def _prompt_registry_path() -> Path:
    from xiaoya_agent.config import Config

    return Path(Config.DATA_DIR) / "prompt_registry.json"


def _record_from_builtin(key: str, content: str, description: str = "") -> Dict[str, Any]:
    now = "builtin"
    return {
        "key": key,
        "content": content,
        "description": description,
        "version": 1,
        "updatedAt": now,
        "builtin": True,
        "history": [{
            "version": 1,
            "content": content,
            "description": description,
            "updatedAt": now,
        }],
    }


def _builtin_registry() -> Dict[str, Dict[str, Dict[str, Any]]]:
    return {
        "profiles": {
            key: _record_from_builtin(key, content)
            for key, content in PROMPT_PROFILE_SUFFIXES.items()
        },
        "outputModes": {
            key: _record_from_builtin(key, content)
            for key, content in OUTPUT_MODE_INSTRUCTIONS.items()
        },
        "settings": {
            "defaultProfile": None,
            "defaultOutputMode": None,
            "updatedAt": "builtin",
        },
    }


def _bucket_for_kind(kind: str) -> str:
    normalized = (kind or "").strip()
    mapping = {
        "profile": "profiles",
        "profiles": "profiles",
        "prompt_profile": "profiles",
        "mode": "outputModes",
        "outputMode": "outputModes",
        "output_mode": "outputModes",
        "outputModes": "outputModes",
    }
    if normalized not in mapping:
        raise ValueError("kind 必须是 profile 或 mode")
    return mapping[normalized]


def _normalize_entry(key: str, value: Any, builtin: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    if isinstance(value, str):
        value = {"content": value}
    value = dict(value or {})
    content = str(value.get("content", ""))
    version = int(value.get("version") or (builtin or {}).get("version") or 1)
    history = value.get("history") if isinstance(value.get("history"), list) else None
    if not history:
        history = [{
            "version": version,
            "content": content,
            "description": value.get("description", ""),
            "updatedAt": value.get("updatedAt") or _now_iso(),
            "changeNote": value.get("changeNote", ""),
            "metadata": value.get("metadata", {}),
        }]
    return {
        "key": key,
        "content": content,
        "description": value.get("description", (builtin or {}).get("description", "")),
        "version": version,
        "updatedAt": value.get("updatedAt") or _now_iso(),
        "builtin": bool((builtin or {}).get("builtin", False)),
        "metadata": value.get("metadata", {}),
        "history": history,
    }


def _load_registry_data(data: Any) -> Dict[str, Dict[str, Dict[str, Any]]]:
    registry = _builtin_registry()
    if not isinstance(data, dict):
        return registry

    for bucket in ("profiles", "outputModes"):
        values = data.get(bucket, {})
        if not isinstance(values, dict):
            continue
        for key, value in values.items():
            builtin = registry[bucket].get(key)
            registry[bucket][key] = _normalize_entry(key, value, builtin=builtin)
    settings = data.get("settings", {})
    if isinstance(settings, dict):
        registry["settings"].update({
            key: settings.get(key)
            for key in ("defaultProfile", "defaultOutputMode", "updatedAt")
            if key in settings
        })
    return registry


def _load_registry_from_disk(path: Path) -> Dict[str, Dict[str, Dict[str, Any]]]:
    if not path.exists():
        return _builtin_registry()

    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    return _load_registry_data(data)


def _get_registry() -> Dict[str, Dict[str, Dict[str, Any]]]:
    global _registry_cache, _registry_cache_mtime, _registry_cache_path

    if database_storage_enabled():
        cache_key = "mysql:prompt_registry"
        with _registry_lock:
            if _registry_cache is not None and _registry_cache_path == cache_key:
                return copy.deepcopy(_registry_cache)

            repo = get_database_repository()
            stored = repo.load_prompt_registry()
            if isinstance(stored, dict):
                registry = _load_registry_data(stored)
            else:
                legacy_path = _prompt_registry_path()
                registry = _load_registry_from_disk(legacy_path) if legacy_path.exists() else _builtin_registry()
                repo.save_prompt_registry(registry)
            _registry_cache = registry
            _registry_cache_mtime = None
            _registry_cache_path = cache_key
            return copy.deepcopy(registry)

    path = _prompt_registry_path()
    path_str = str(path)
    mtime = path.stat().st_mtime if path.exists() else None
    with _registry_lock:
        if (
            _registry_cache is not None
            and _registry_cache_path == path_str
            and _registry_cache_mtime == mtime
        ):
            return copy.deepcopy(_registry_cache)

        registry = _load_registry_from_disk(path)
        _registry_cache = registry
        _registry_cache_mtime = mtime
        _registry_cache_path = path_str
        return copy.deepcopy(registry)


def _write_registry(registry: Dict[str, Dict[str, Dict[str, Any]]]) -> None:
    global _registry_cache, _registry_cache_mtime, _registry_cache_path

    if database_storage_enabled():
        get_database_repository().save_prompt_registry(registry)
        _registry_cache = copy.deepcopy(registry)
        _registry_cache_mtime = None
        _registry_cache_path = "mysql:prompt_registry"
        return

    path = _prompt_registry_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(registry, f, ensure_ascii=False, indent=2)
    _registry_cache = copy.deepcopy(registry)
    _registry_cache_mtime = path.stat().st_mtime
    _registry_cache_path = str(path)


def get_prompt_registry_snapshot(include_history: bool = True) -> Dict[str, Any]:
    registry = _get_registry()
    if include_history:
        return registry
    snapshot = copy.deepcopy(registry)
    for bucket in ("profiles", "outputModes"):
        for entry in snapshot[bucket].values():
            entry.pop("history", None)
    return snapshot


def get_prompt_entry(kind: str, key: str, include_history: bool = True) -> Dict[str, Any]:
    bucket = _bucket_for_kind(kind)
    registry = _get_registry()
    entry = registry[bucket].get(key)
    if not entry:
        raise KeyError("提示词不存在")
    result = copy.deepcopy(entry)
    if not include_history:
        result.pop("history", None)
    return result


def reload_prompt_registry() -> Dict[str, Any]:
    global _registry_cache, _registry_cache_mtime, _registry_cache_path

    with _registry_lock:
        _registry_cache = None
        _registry_cache_mtime = None
        _registry_cache_path = None
    return get_prompt_registry_snapshot(include_history=True)


def update_prompt_entry(
    kind: str,
    key: str,
    content: str,
    description: Optional[str] = None,
    change_note: Optional[str] = None,
    metadata: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    bucket = _bucket_for_kind(kind)
    key = (key or "").strip()
    if not key:
        raise ValueError("key 不能为空")
    payload = PromptEntryPayload(
        content=content,
        description=description,
        change_note=change_note,
        metadata=metadata or {},
    )
    if not payload.content and bucket == "outputModes":
        raise ValueError("输出模式提示词不能为空")

    with _registry_lock:
        registry = _get_registry()
        current = registry[bucket].get(key)
        version = int((current or {}).get("version") or 0) + 1
        now = _now_iso()
        history = list((current or {}).get("history") or [])
        previous_content = str((current or {}).get("content", ""))
        diff_from_previous = list(difflib.unified_diff(
            previous_content.splitlines(),
            payload.content.splitlines(),
            fromfile=f"{key}@v{(current or {}).get('version', 0)}",
            tofile=f"{key}@v{version}",
            lineterm="",
        )) if current else []
        entry_description = (
            payload.description
            if description is not None
            else (current or {}).get("description", "")
        )
        entry = {
            "key": key,
            "content": payload.content,
            "description": entry_description,
            "version": version,
            "updatedAt": now,
            "builtin": bool((current or {}).get("builtin", False)),
            "metadata": payload.metadata,
            "history": history + [{
                "version": version,
                "content": payload.content,
                "description": entry_description,
                "updatedAt": now,
                "changeNote": payload.change_note,
                "metadata": payload.metadata,
                "diffFromPrevious": diff_from_previous,
            }],
        }
        registry[bucket][key] = entry
        _write_registry(registry)
        return copy.deepcopy(entry)


def update_prompt_settings(
    default_profile: Optional[str] = None,
    default_output_mode: Optional[str] = None,
) -> Dict[str, Any]:
    with _registry_lock:
        registry = _get_registry()
        settings = dict(registry.get("settings") or {})
        if default_profile is not None:
            if default_profile and default_profile not in registry["profiles"]:
                raise ValueError("defaultProfile 不存在")
            settings["defaultProfile"] = default_profile or None
        if default_output_mode is not None:
            if default_output_mode and default_output_mode not in registry["outputModes"]:
                raise ValueError("defaultOutputMode 不存在")
            settings["defaultOutputMode"] = default_output_mode or None
        settings["updatedAt"] = _now_iso()
        registry["settings"] = settings
        _write_registry(registry)
        return copy.deepcopy(settings)


def rollback_prompt_entry(kind: str, key: str, version: int) -> Dict[str, Any]:
    bucket = _bucket_for_kind(kind)
    with _registry_lock:
        registry = _get_registry()
        entry = registry[bucket].get(key)
        if not entry:
            raise KeyError("提示词不存在")
        history = _history_versions(entry)
        target = next((item for item in history if int(item.get("version") or 0) == int(version)), None)
        if not target:
            raise ValueError("version 不存在")
        new_version = int(entry.get("version") or 1) + 1
        now = _now_iso()
        new_entry = {
            **entry,
            "content": str(target.get("content", "")),
            "description": target.get("description", entry.get("description", "")),
            "version": new_version,
            "updatedAt": now,
            "history": history + [{
                "version": new_version,
                "content": str(target.get("content", "")),
                "description": target.get("description", entry.get("description", "")),
                "updatedAt": now,
                "rollbackFromVersion": int(version),
            }],
        }
        registry[bucket][key] = new_entry
        _write_registry(registry)
        return copy.deepcopy(new_entry)


def delete_prompt_entry(kind: str, key: str, purge_history: bool = False) -> Dict[str, Any]:
    bucket = _bucket_for_kind(kind)
    with _registry_lock:
        registry = _get_registry()
        entry = registry[bucket].get(key)
        if not entry:
            raise KeyError("提示词不存在")
        builtin = _builtin_registry()[bucket].get(key)
        if builtin:
            if purge_history:
                reset_entry = copy.deepcopy(builtin)
                registry[bucket][key] = reset_entry
                _write_registry(registry)
                return {
                    "deleted": False,
                    "resetToBuiltin": True,
                    "purgedHistory": True,
                    "entry": copy.deepcopy(reset_entry),
                }

            already_builtin_default = (
                str(entry.get("content", "")) == str(builtin.get("content", ""))
                and str(entry.get("description", "")) == str(builtin.get("description", ""))
            )
            if already_builtin_default:
                return {
                    "deleted": False,
                    "resetToBuiltin": False,
                    "alreadyBuiltin": True,
                    "entry": copy.deepcopy(entry),
                }

            new_version = int(entry.get("version") or 1) + 1
            now = _now_iso()
            history = _history_versions(entry) + [{
                "version": new_version,
                "content": builtin["content"],
                "description": builtin.get("description", ""),
                "updatedAt": now,
                "resetToBuiltin": True,
            }]
            reset_entry = {
                **builtin,
                "version": new_version,
                "updatedAt": now,
                "history": history,
            }
            registry[bucket][key] = reset_entry
            _write_registry(registry)
            return {"deleted": False, "resetToBuiltin": True, "entry": copy.deepcopy(reset_entry)}

        registry[bucket].pop(key, None)
        settings = dict(registry.get("settings") or {})
        if bucket == "profiles" and settings.get("defaultProfile") == key:
            settings["defaultProfile"] = None
        if bucket == "outputModes" and settings.get("defaultOutputMode") == key:
            settings["defaultOutputMode"] = None
        settings["updatedAt"] = _now_iso()
        registry["settings"] = settings
        _write_registry(registry)
        return {"deleted": True, "resetToBuiltin": False, "key": key}


def _history_versions(entry: Dict[str, Any]) -> List[Dict[str, Any]]:
    history = list(entry.get("history") or [])
    if not history:
        history = [{
            "version": int(entry.get("version") or 1),
            "content": entry.get("content", ""),
            "description": entry.get("description", ""),
            "updatedAt": entry.get("updatedAt"),
        }]
    return sorted(history, key=lambda item: int(item.get("version") or 0))


def _select_versions(
    history: List[Dict[str, Any]],
    from_version: Optional[int],
    to_version: Optional[int],
) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    by_version = {int(item.get("version") or 0): item for item in history}
    if to_version is None:
        to_record = history[-1]
    else:
        to_record = by_version.get(int(to_version))
    if not to_record:
        raise ValueError("toVersion 不存在")

    if from_version is None:
        to_index = history.index(to_record)
        from_record = history[to_index - 1] if to_index > 0 else to_record
    else:
        from_record = by_version.get(int(from_version))
    if not from_record:
        raise ValueError("fromVersion 不存在")
    return from_record, to_record


def compare_prompt_versions(
    kind: str,
    key: str,
    from_version: Optional[int] = None,
    to_version: Optional[int] = None,
) -> Dict[str, Any]:
    bucket = _bucket_for_kind(kind)
    registry = _get_registry()
    entry = registry[bucket].get(key)
    if not entry:
        raise KeyError("提示词不存在")

    history = _history_versions(entry)
    from_record, to_record = _select_versions(history, from_version, to_version)
    from_text = str(from_record.get("content", ""))
    to_text = str(to_record.get("content", ""))
    diff = list(difflib.unified_diff(
        from_text.splitlines(),
        to_text.splitlines(),
        fromfile=f"{key}@v{from_record.get('version')}",
        tofile=f"{key}@v{to_record.get('version')}",
        lineterm="",
    ))
    return {
        "kind": "profile" if bucket == "profiles" else "mode",
        "key": key,
        "fromVersion": from_record,
        "toVersion": to_record,
        "diff": diff,
        "changed": from_text != to_text,
    }


def _normalize_key(value: Optional[str], default: str, allowed: Dict[str, str]) -> str:
    fallback = default if default in allowed else next(iter(allowed))
    key = (value or fallback or "").strip()
    return key if key in allowed else fallback


def _runtime_from_registry(
    registry: Dict[str, Any],
    *,
    base_system_prompt: str,
    default_profile: str,
    default_output_mode: str,
    prompt_profile: Optional[str] = None,
    output_mode: Optional[str] = None,
    system_prompt_override: Optional[str] = None,
    extra_instructions: Optional[str] = None,
) -> PromptRuntimeConfig:
    profiles = {
        key: value.get("content", "")
        for key, value in registry["profiles"].items()
    }
    output_modes = {
        key: value.get("content", "")
        for key, value in registry["outputModes"].items()
    }
    profile = _normalize_key(
        prompt_profile,
        registry.get("settings", {}).get("defaultProfile") or default_profile or "warm_cbt",
        profiles,
    )
    mode = _normalize_key(
        output_mode,
        registry.get("settings", {}).get("defaultOutputMode") or default_output_mode or "brief_support",
        output_modes,
    )

    system_prompt = (system_prompt_override or base_system_prompt or "").strip()
    profile_entry = registry["profiles"].get(profile, {})
    output_mode_entry = registry["outputModes"].get(mode, {})
    profile_suffix = str(profile_entry.get("content", "")).strip()
    if profile_suffix:
        system_prompt = f"{system_prompt}\n\n[提示词配置:{profile}] {profile_suffix}"

    realtime_instruction = output_modes[mode]
    if extra_instructions:
        realtime_instruction = (
            f"{realtime_instruction}"
            f"[额外运行时要求] {str(extra_instructions).strip()}"
        )

    return PromptRuntimeConfig(
        profile=profile,
        output_mode=mode,
        system_prompt=system_prompt,
        realtime_instruction=realtime_instruction,
        profile_version=int(profile_entry.get("version") or 1),
        output_mode_version=int(output_mode_entry.get("version") or 1),
    )


def resolve_prompt_runtime_config(
    *,
    base_system_prompt: str,
    default_profile: str,
    default_output_mode: str,
    prompt_profile: Optional[str] = None,
    output_mode: Optional[str] = None,
    system_prompt_override: Optional[str] = None,
    extra_instructions: Optional[str] = None,
) -> PromptRuntimeConfig:
    registry = _get_registry()
    return _runtime_from_registry(
        registry,
        base_system_prompt=base_system_prompt,
        default_profile=default_profile,
        default_output_mode=default_output_mode,
        prompt_profile=prompt_profile,
        output_mode=output_mode,
        system_prompt_override=system_prompt_override,
        extra_instructions=extra_instructions,
    )


def preview_prompt_candidate(
    kind: str,
    key: str,
    candidate_content: str,
    *,
    description: Optional[str] = None,
    change_note: Optional[str] = None,
    metadata: Optional[Dict[str, Any]] = None,
    from_version: Optional[int] = None,
    base_system_prompt: str = "",
    default_profile: str = "warm_cbt",
    default_output_mode: str = "brief_support",
    prompt_profile: Optional[str] = None,
    output_mode: Optional[str] = None,
    system_prompt_override: Optional[str] = None,
    extra_instructions: Optional[str] = None,
) -> Dict[str, Any]:
    """预览提示词修改，但不写入注册表。

    调用方可以先比较渲染后的提示词和可选模型输出，
    再通过 ``update_prompt_entry`` 持久化候选版本。
    """
    bucket = _bucket_for_kind(kind)
    key = (key or "").strip()
    if not key:
        raise ValueError("key 不能为空")
    payload = PromptEntryPayload(
        content=candidate_content,
        description=description,
        change_note=change_note,
        metadata=metadata or {},
    )
    if not payload.content and bucket == "outputModes":
        raise ValueError("输出模式提示词不能为空")

    registry = _get_registry()
    current = registry[bucket].get(key)
    builtin = _builtin_registry()[bucket].get(key)
    if not current and not builtin:
        current = _record_from_builtin(key, "", description or "")
    elif not current:
        current = copy.deepcopy(builtin)

    history = _history_versions(current)
    baseline_record = None
    if from_version is not None:
        baseline_record = next(
            (item for item in history if int(item.get("version") or 0) == int(from_version)),
            None,
        )
        if not baseline_record:
            raise ValueError("fromVersion 不存在")
    else:
        baseline_record = history[-1] if history else current

    candidate_version = int((current or {}).get("version") or 0) + 1
    now = _now_iso()
    baseline_content = str(baseline_record.get("content", ""))
    diff = list(difflib.unified_diff(
        baseline_content.splitlines(),
        payload.content.splitlines(),
        fromfile=f"{key}@v{baseline_record.get('version')}",
        tofile=f"{key}@candidate-v{candidate_version}",
        lineterm="",
    ))
    entry_description = (
        payload.description
        if description is not None
        else (current or {}).get("description", "")
    )
    candidate_entry = {
        "key": key,
        "content": payload.content,
        "description": entry_description,
        "version": candidate_version,
        "updatedAt": now,
        "builtin": bool((current or {}).get("builtin", False)),
        "metadata": payload.metadata,
        "historyRecord": {
            "version": candidate_version,
            "content": payload.content,
            "description": entry_description,
            "updatedAt": now,
            "changeNote": payload.change_note,
            "metadata": payload.metadata,
            "diffFromPrevious": diff,
            "candidate": True,
        },
    }

    current_profile = prompt_profile or (key if bucket == "profiles" else default_profile)
    current_mode = output_mode or (key if bucket == "outputModes" else default_output_mode)
    current_runtime = _runtime_from_registry(
        registry,
        base_system_prompt=base_system_prompt,
        default_profile=default_profile,
        default_output_mode=default_output_mode,
        prompt_profile=current_profile,
        output_mode=current_mode,
        system_prompt_override=system_prompt_override,
        extra_instructions=extra_instructions,
    )

    candidate_registry = copy.deepcopy(registry)
    candidate_registry[bucket][key] = {
        **candidate_entry,
        "history": history + [candidate_entry["historyRecord"]],
    }
    candidate_runtime = _runtime_from_registry(
        candidate_registry,
        base_system_prompt=base_system_prompt,
        default_profile=default_profile,
        default_output_mode=default_output_mode,
        prompt_profile=current_profile,
        output_mode=current_mode,
        system_prompt_override=system_prompt_override,
        extra_instructions=extra_instructions,
    )

    return {
        "kind": "profile" if bucket == "profiles" else "mode",
        "key": key,
        "saved": False,
        "hotReloadAfterSave": True,
        "baselineVersion": baseline_record,
        "currentEntry": copy.deepcopy(current),
        "candidateEntry": candidate_entry,
        "diff": diff,
        "changed": baseline_content != payload.content,
        "currentRuntime": asdict(current_runtime),
        "candidateRuntime": asdict(candidate_runtime),
    }
