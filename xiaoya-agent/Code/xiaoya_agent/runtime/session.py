"""API 会话运行时管理。

这一层把 sessionId、thread_id、患者阶段、历史重建和提示词配置集中管理。
现阶段仍复用 EnhancedChatAgent 的本地 JSON 持久化；thread_id 先与 LangGraph
调用对齐，后续接 checkpointer/store 时不需要再改 API 入口。
"""
import os
import json
import re
import shutil
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Dict, List, Optional

from xiaoya_agent.config import Config
from xiaoya_agent.core.agent import EnhancedChatAgent
from xiaoya_agent.domain.transplant import TransplantPhase
from xiaoya_agent.runtime.state_store import (
    apply_session_state,
    load_session_history,
    load_session_state,
    save_session_state,
)

SESSION_TTL_SECONDS = 21600
SESSION_METADATA_FILENAME = "session_meta.json"
USER_MODEL_METADATA_FILENAME = "psych_model_meta.json"
PSYCH_MODEL_FILENAME = "psych_model.json"
USER_CONVERSATION_INDEX_FILENAME = "conversation_index.json"
USER_CONVERSATIONS_DIRNAME = "conversations"


@dataclass
class SessionManager:
    session_id: str
    user_id: str
    thread_id: str
    data_dir: str
    psych_model_dir: str
    agent: EnhancedChatAgent
    last_access: float = field(default_factory=time.time)
    lock: threading.RLock = field(default_factory=threading.RLock)


agent_sessions: Dict[str, SessionManager] = {}
agent_sessions_lock = threading.Lock()


def _now_iso() -> str:
    return datetime.now().replace(microsecond=0).isoformat()


def sanitize_session_id(session_id: str) -> str:
    safe_session_id = re.sub(r"[^0-9A-Za-z_.-]+", "_", str(session_id)).strip("._")[:80]
    return safe_session_id or "default"


def _session_key(session_id: str) -> str:
    return sanitize_session_id(session_id)


def sanitize_user_id(user_id: str) -> str:
    return sanitize_session_id(user_id)


def _user_key(user_id: str) -> str:
    return sanitize_user_id(user_id)


def _users_root() -> str:
    root = os.path.abspath(os.path.join(Config.DATA_DIR, "users"))
    os.makedirs(root, exist_ok=True)
    return root


def _assert_session_owner(session_id: str, data_dir: str) -> None:
    """拒绝不同外部 sessionId 映射到同一个安全目录。"""
    meta = _read_json_file(_metadata_path(data_dir), None)
    if not isinstance(meta, dict):
        return
    stored_session_id = str(meta.get("sessionId") or "")
    if stored_session_id and stored_session_id != str(session_id):
        safe_session_id = sanitize_session_id(session_id)
        raise ValueError(
            f"sessionId 与已有会话目录冲突：{session_id!r} 和 {stored_session_id!r} "
            f"都会映射到 safeSessionId={safe_session_id!r}"
        )


def _user_model_metadata_path(model_dir: str) -> str:
    return os.path.join(model_dir, USER_MODEL_METADATA_FILENAME)


def _assert_user_model_owner(user_id: str, model_dir: str) -> None:
    meta = _read_json_file(_user_model_metadata_path(model_dir), None)
    if not isinstance(meta, dict):
        return
    stored_user_id = str(meta.get("userId") or "")
    if stored_user_id and stored_user_id != str(user_id):
        safe_user_id = sanitize_user_id(user_id)
        raise ValueError(
            f"userId 与已有心理模型目录冲突：{user_id!r} 和 {stored_user_id!r} "
            f"都会映射到 safeUserId={safe_user_id!r}"
        )


def get_user_model_dir(user_id: str) -> str:
    model_dir = os.path.abspath(os.path.join(_users_root(), _user_key(user_id)))
    if os.path.isdir(model_dir):
        _assert_user_model_owner(user_id, model_dir)
    os.makedirs(model_dir, exist_ok=True)
    _write_user_model_metadata(user_id, model_dir)
    return model_dir


def get_user_model_dir_if_exists(user_id: str) -> Optional[str]:
    model_dir = os.path.abspath(os.path.join(_users_root(), _user_key(user_id)))
    if os.path.isdir(model_dir):
        _assert_user_model_owner(user_id, model_dir)
        return model_dir
    return None


def read_user_psych_model(user_id: str) -> Optional[Dict[str, Any]]:
    model_dir = get_user_model_dir_if_exists(user_id)
    if not model_dir:
        return None

    metadata = _read_json_file(_user_model_metadata_path(model_dir), {})
    if not isinstance(metadata, dict):
        metadata = {}
    model = _read_json_file(os.path.join(model_dir, PSYCH_MODEL_FILENAME), {})
    if not isinstance(model, dict):
        model = {}

    if not metadata and not model:
        return None

    resolved_user_id = str(metadata.get("userId") or model.get("userId") or user_id)
    return {
        "source": "persisted_user_model",
        "userId": resolved_user_id,
        "safeUserId": sanitize_user_id(resolved_user_id),
        "psychModelDir": model_dir,
        "exists": bool(model),
        "metadata": metadata,
        "psychModel": model,
    }


def build_agent_psych_model_payload(
    agent: EnhancedChatAgent,
    session_id: Optional[str] = None,
    thread_id: Optional[str] = None,
) -> Dict[str, Any]:
    user_id = str(getattr(agent, "user_id", None) or "default")
    phase_value = None
    try:
        phase = agent.get_transplant_phase()
        phase_value = getattr(phase, "value", phase)
    except Exception:
        phase_value = None

    energy_report = None
    energy_model = getattr(agent, "energy_model", None)
    if energy_model is not None and hasattr(energy_model, "get_energy_report"):
        try:
            energy_report = energy_model.get_energy_report()
        except Exception:
            energy_report = None

    crisis_report = None
    crisis_module = getattr(agent, "crisis_module", None)
    if crisis_module is not None and hasattr(crisis_module, "get_crisis_history_report"):
        try:
            crisis_report = crisis_module.get_crisis_history_report()
        except Exception:
            crisis_report = None

    cbt_module = getattr(agent, "cbt_module", None)
    cbt_user_profile = getattr(cbt_module, "user_profile", {}) if cbt_module is not None else {}
    psych_model = {
        "modelVersion": 1,
        "userId": user_id,
        "snapshotAt": _now_iso(),
        "memory_core": getattr(agent, "memory_core", None),
        "user_state": dict(getattr(agent, "user_state", {}) or {}),
        "transplant_phase": phase_value,
        "cbt_user_profile": dict(cbt_user_profile or {}),
        "personalization_profile": dict(getattr(agent, "personalization_profile", {}) or {}),
        "energy_report": energy_report,
        "crisis_report": crisis_report,
    }

    payload = {
        "source": "active_session",
        "userId": user_id,
        "safeUserId": sanitize_user_id(user_id),
        "psychModelDir": getattr(agent, "psych_model_dir", None),
        "psychModel": psych_model,
    }
    if session_id is not None:
        payload["sessionId"] = session_id
    if thread_id is not None:
        payload["threadId"] = thread_id
    return payload


def _write_user_model_metadata(user_id: str, model_dir: str) -> Dict[str, Any]:
    meta = _read_json_file(_user_model_metadata_path(model_dir), None)
    now = _now_iso()
    if not isinstance(meta, dict):
        meta = {
            "userId": user_id,
            "safeUserId": sanitize_user_id(user_id),
            "createdAt": now,
        }
    meta.update({
        "userId": user_id,
        "safeUserId": sanitize_user_id(user_id),
        "updatedAt": now,
        "psychModelDir": model_dir,
    })
    _write_json_file(_user_model_metadata_path(model_dir), meta)
    return meta


def _user_conversation_index_path(model_dir: str) -> str:
    return os.path.join(model_dir, USER_CONVERSATION_INDEX_FILENAME)


def _user_conversations_dir(model_dir: str) -> str:
    path = os.path.join(model_dir, USER_CONVERSATIONS_DIRNAME)
    os.makedirs(path, exist_ok=True)
    return path


def _conversation_snapshot_name(source: str, conversation_id: str) -> str:
    return f"{sanitize_session_id(source)}_{sanitize_session_id(conversation_id)}.json"


def _load_user_conversation_index(user_id: str, model_dir: Optional[str] = None) -> Dict[str, Any]:
    resolved_model_dir = model_dir or get_user_model_dir(user_id)
    index = _read_json_file(_user_conversation_index_path(resolved_model_dir), None)
    if not isinstance(index, dict):
        index = {
            "userId": user_id,
            "safeUserId": sanitize_user_id(user_id),
            "updatedAt": _now_iso(),
            "conversations": [],
        }
    conversations = index.get("conversations")
    if not isinstance(conversations, list):
        index["conversations"] = []
    index["userId"] = str(index.get("userId") or user_id)
    index["safeUserId"] = sanitize_user_id(index["userId"])
    return index


def _write_user_conversation_index(user_id: str, model_dir: str, index: Dict[str, Any]) -> Dict[str, Any]:
    index["userId"] = str(index.get("userId") or user_id)
    index["safeUserId"] = sanitize_user_id(index["userId"])
    index["updatedAt"] = _now_iso()
    index["conversations"] = sorted(
        list(index.get("conversations") or []),
        key=lambda item: str(item.get("updatedAt") or item.get("lastMessageAt") or ""),
        reverse=True,
    )
    _write_json_file(_user_conversation_index_path(model_dir), index)
    return index


def sync_user_conversation_history(
    user_id: str,
    conversation_id: str,
    source: str,
    history: List[Dict[str, Any]],
    metadata: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    """将一份会话快照写入用户统一历史索引。"""
    resolved_user_id = str(user_id or "default")
    resolved_source = str(source or "api")
    resolved_conversation_id = str(conversation_id or resolved_source)
    metadata = dict(metadata or {})
    model_dir = get_user_model_dir(resolved_user_id)
    conversations_dir = _user_conversations_dir(model_dir)
    snapshot_name = _conversation_snapshot_name(resolved_source, resolved_conversation_id)
    snapshot_path = os.path.join(conversations_dir, snapshot_name)
    now = _now_iso()
    visible_history = [message for message in list(history or []) if message.get("role") != "system"]

    snapshot = {
        "userId": resolved_user_id,
        "safeUserId": sanitize_user_id(resolved_user_id),
        "source": resolved_source,
        "conversationId": resolved_conversation_id,
        "safeConversationId": sanitize_session_id(resolved_conversation_id),
        "updatedAt": now,
        "metadata": metadata,
        "history": list(history or []),
    }
    _write_json_file(snapshot_path, snapshot)

    entry = {
        "userId": resolved_user_id,
        "safeUserId": sanitize_user_id(resolved_user_id),
        "source": resolved_source,
        "conversationId": resolved_conversation_id,
        "safeConversationId": sanitize_session_id(resolved_conversation_id),
        "sessionId": metadata.get("sessionId", resolved_conversation_id if resolved_source == "api" else None),
        "title": metadata.get("title") or build_auto_session_title(_first_user_message(visible_history)),
        "createdAt": metadata.get("createdAt") or now,
        "updatedAt": metadata.get("updatedAt") or now,
        "lastMessageAt": metadata.get("lastMessageAt"),
        "messageCount": _message_count(visible_history),
        "historyCount": len(visible_history),
        "dataDir": metadata.get("dataDir"),
        "snapshotPath": snapshot_path,
    }

    index = _load_user_conversation_index(resolved_user_id, model_dir)
    conversations = [
        item for item in list(index.get("conversations") or [])
        if not (
            item.get("source") == resolved_source
            and str(item.get("conversationId")) == resolved_conversation_id
        )
    ]
    conversations.append(entry)
    index["conversations"] = conversations
    _write_user_conversation_index(resolved_user_id, model_dir, index)
    return entry


def unregister_user_conversation(user_id: str, conversation_id: str, source: str) -> bool:
    model_dir = get_user_model_dir_if_exists(user_id)
    if not model_dir:
        return False
    resolved_source = str(source or "api")
    resolved_conversation_id = str(conversation_id or resolved_source)
    snapshot_path = os.path.join(
        model_dir,
        USER_CONVERSATIONS_DIRNAME,
        _conversation_snapshot_name(resolved_source, resolved_conversation_id),
    )
    removed = False
    if os.path.exists(snapshot_path):
        os.remove(snapshot_path)
        removed = True
    index = _load_user_conversation_index(user_id, model_dir)
    before = len(index.get("conversations") or [])
    index["conversations"] = [
        item for item in list(index.get("conversations") or [])
        if not (
            item.get("source") == resolved_source
            and str(item.get("conversationId")) == resolved_conversation_id
        )
    ]
    removed = removed or len(index["conversations"]) != before
    if removed:
        _write_user_conversation_index(user_id, model_dir, index)
    return removed


def _scan_api_conversations_for_user(user_id: str) -> List[Dict[str, Any]]:
    safe_user_id = sanitize_user_id(user_id)
    results: List[Dict[str, Any]] = []
    root = _sessions_root()
    for item in os.scandir(root):
        if not item.is_dir():
            continue
        meta = _read_json_file(_metadata_path(item.path), None)
        if not isinstance(meta, dict):
            continue
        if str(meta.get("userId") or "") != str(user_id) and str(meta.get("safeUserId") or "") != safe_user_id:
            continue
        history = _history_from_data_dir(item.path)
        results.append(sync_user_conversation_history(
            user_id=str(meta.get("userId") or user_id),
            conversation_id=str(meta.get("sessionId") or item.name),
            source="api",
            history=history,
            metadata={**meta, "dataDir": item.path},
        ))
    return results


def list_user_conversations(user_id: str, include_history: bool = False, refresh: bool = True) -> Dict[str, Any]:
    model_dir = get_user_model_dir_if_exists(user_id)
    if not model_dir:
        return {
            "userId": user_id,
            "safeUserId": sanitize_user_id(user_id),
            "exists": False,
            "conversations": [],
        }
    if refresh:
        _scan_api_conversations_for_user(user_id)
    index = _load_user_conversation_index(user_id, model_dir)
    conversations = []
    for entry in list(index.get("conversations") or []):
        item = dict(entry)
        if include_history:
            snapshot = _read_json_file(str(item.get("snapshotPath") or ""), {})
            item["history"] = snapshot.get("history", []) if isinstance(snapshot, dict) else []
        conversations.append(item)
    return {
        "userId": index.get("userId") or user_id,
        "safeUserId": index.get("safeUserId") or sanitize_user_id(user_id),
        "exists": True,
        "conversationCount": len(conversations),
        "conversations": conversations,
    }


def list_user_summaries() -> List[Dict[str, Any]]:
    root = _users_root()
    users: List[Dict[str, Any]] = []
    for item in os.scandir(root):
        if not item.is_dir():
            continue
        meta = _read_json_file(_user_model_metadata_path(item.path), {})
        if not isinstance(meta, dict):
            meta = {}
        user_id = str(meta.get("userId") or item.name)
        index = _load_user_conversation_index(user_id, item.path)
        users.append({
            "userId": user_id,
            "safeUserId": item.name,
            "psychModelDir": item.path,
            "updatedAt": meta.get("updatedAt") or index.get("updatedAt"),
            "conversationCount": len(index.get("conversations") or []),
            "metadata": meta,
        })
    users.sort(key=lambda item: str(item.get("updatedAt") or ""), reverse=True)
    return users


def _safe_rmtree(path: str, root: str) -> bool:
    abs_path = os.path.abspath(path)
    abs_root = os.path.abspath(root)
    if not os.path.exists(abs_path):
        return False
    if os.path.commonpath([abs_root, abs_path]) != abs_root:
        raise ValueError(f"拒绝删除目录外的路径：{abs_path}")
    shutil.rmtree(abs_path)
    return True


def get_session_data_dir(session_id: str) -> str:
    data_dir = os.path.abspath(os.path.join(Config.DATA_DIR, "sessions", _session_key(session_id)))
    if os.path.isdir(data_dir):
        _assert_session_owner(session_id, data_dir)
    os.makedirs(data_dir, exist_ok=True)
    return data_dir


def _sessions_root() -> str:
    root = os.path.abspath(os.path.join(Config.DATA_DIR, "sessions"))
    os.makedirs(root, exist_ok=True)
    return root


def _session_data_dir_if_exists(session_id: str) -> Optional[str]:
    data_dir = os.path.abspath(os.path.join(_sessions_root(), _session_key(session_id)))
    if os.path.isdir(data_dir):
        _assert_session_owner(session_id, data_dir)
    return data_dir if os.path.isdir(data_dir) else None


def _metadata_path(data_dir: str) -> str:
    return os.path.join(data_dir, SESSION_METADATA_FILENAME)


def _read_json_file(path: str, default: Any) -> Any:
    try:
        if not os.path.exists(path):
            return default
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


def _write_json_file(path: str, data: Any) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    temp_path = f"{path}.tmp"
    with open(temp_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.replace(temp_path, path)


def build_auto_session_title(message: str, fallback: str = "新的会话") -> str:
    text = re.sub(r"\s+", " ", str(message or "")).strip()
    text = text.strip("，。！？!?；;：:、,. ")
    if not text:
        return fallback
    return text[:18] + ("..." if len(text) > 18 else "")


def _history_from_data_dir(data_dir: str) -> List[Dict[str, Any]]:
    state_history = load_session_history(data_dir)
    if isinstance(state_history, list):
        return state_history
    history = _read_json_file(os.path.join(data_dir, "chat_history.json"), [])
    return history if isinstance(history, list) else []


def _message_count(history: List[Dict[str, Any]]) -> int:
    return len([message for message in history if message.get("role") == "user"])


def _first_user_message(history: List[Dict[str, Any]]) -> str:
    for message in history:
        if message.get("role") == "user":
            return str(message.get("content", ""))
    return ""


def _default_metadata(
    session_id: str,
    data_dir: str,
    title: Optional[str] = None,
    user_id: Optional[str] = None,
    psych_model_dir: Optional[str] = None,
) -> Dict[str, Any]:
    now = _now_iso()
    safe_session_id = sanitize_session_id(session_id)
    resolved_user_id = str(user_id or session_id)
    safe_user_id = sanitize_user_id(resolved_user_id)
    resolved_psych_model_dir = psych_model_dir or os.path.abspath(os.path.join(Config.DATA_DIR, "users", safe_user_id))
    return {
        "sessionId": session_id,
        "safeSessionId": safe_session_id,
        "userId": resolved_user_id,
        "safeUserId": safe_user_id,
        "threadId": build_thread_id(session_id),
        "title": title or "新的会话",
        "autoTitle": title is None,
        "createdAt": now,
        "updatedAt": now,
        "lastMessageAt": None,
        "messageCount": _message_count(_history_from_data_dir(data_dir)),
        "stage": "PRETREATMENT",
        "promptProfile": Config.PROMPT_PROFILE,
        "outputMode": Config.OUTPUT_MODE,
        "promptProfileVersion": 1,
        "outputModeVersion": 1,
        "dataDir": data_dir,
        "psychModelDir": resolved_psych_model_dir,
    }


def read_session_metadata(session_id: str) -> Optional[Dict[str, Any]]:
    data_dir = _session_data_dir_if_exists(session_id)
    if not data_dir:
        return None
    data = _read_json_file(_metadata_path(data_dir), None)
    if isinstance(data, dict):
        if "userId" not in data or "psychModelDir" not in data:
            data = {**_default_metadata(session_id, data_dir), **data}
        return data
    history = _history_from_data_dir(data_dir)
    return {
        **_default_metadata(session_id, data_dir),
        "title": build_auto_session_title(_first_user_message(history)),
        "messageCount": _message_count(history),
    }


def _resolve_session_user_id(session_id: str, requested_user_id: Optional[str] = None) -> str:
    data_dir = _session_data_dir_if_exists(session_id)
    existing = _read_json_file(_metadata_path(data_dir), None) if data_dir else None
    existing_user_id = str(existing.get("userId") or "") if isinstance(existing, dict) else ""
    if requested_user_id and existing_user_id and str(requested_user_id) != existing_user_id:
        raise ValueError(f"sessionId={session_id!r} 已绑定 userId={existing_user_id!r}，不能切换到 {requested_user_id!r}")
    return str(requested_user_id or existing_user_id or session_id)


def write_session_metadata(
    session_id: str,
    updates: Optional[Dict[str, Any]] = None,
    title: Optional[str] = None,
    user_id: Optional[str] = None,
) -> Dict[str, Any]:
    data_dir = get_session_data_dir(session_id)
    resolved_user_id = _resolve_session_user_id(session_id, user_id)
    psych_model_dir = get_user_model_dir(resolved_user_id)
    current = read_session_metadata(session_id) or _default_metadata(
        session_id,
        data_dir,
        title=title,
        user_id=resolved_user_id,
        psych_model_dir=psych_model_dir,
    )
    current.update(updates or {})
    if title is not None:
        current["title"] = title
        current["autoTitle"] = False
    current["sessionId"] = session_id
    current["safeSessionId"] = sanitize_session_id(session_id)
    current["userId"] = resolved_user_id
    current["safeUserId"] = sanitize_user_id(resolved_user_id)
    current["threadId"] = build_thread_id(session_id)
    current["dataDir"] = data_dir
    current["psychModelDir"] = psych_model_dir
    _write_json_file(_metadata_path(data_dir), current)
    sync_user_conversation_history(
        user_id=resolved_user_id,
        conversation_id=session_id,
        source="api",
        history=_history_from_data_dir(data_dir),
        metadata=current,
    )
    return current


def create_session_metadata(session_id: str, title: Optional[str] = None, user_id: Optional[str] = None) -> Dict[str, Any]:
    return write_session_metadata(session_id, title=title, user_id=user_id)


def list_session_summaries() -> List[Dict[str, Any]]:
    root = _sessions_root()
    summaries: List[Dict[str, Any]] = []
    for item in os.scandir(root):
        if not item.is_dir():
            continue
        meta = _read_json_file(_metadata_path(item.path), None)
        if not isinstance(meta, dict):
            history = _history_from_data_dir(item.path)
            session_id = item.name
            meta = {
                **_default_metadata(session_id, item.path),
                "title": build_auto_session_title(_first_user_message(history)),
                "messageCount": _message_count(history),
            }
        summaries.append(meta)
    summaries.sort(key=lambda item: item.get("updatedAt") or "", reverse=True)
    return summaries


def get_session_history(session_id: str, include_system: bool = False) -> Optional[List[Dict[str, Any]]]:
    with agent_sessions_lock:
        active = agent_sessions.get(_session_key(session_id))
        if active:
            if active.session_id != session_id:
                raise ValueError(
                    f"sessionId 与已有内存会话冲突：{session_id!r} 和 {active.session_id!r} "
                    f"都会映射到 safeSessionId={_session_key(session_id)!r}"
                )
            history = active.agent.get_history()
        else:
            data_dir = _session_data_dir_if_exists(session_id)
            if not data_dir:
                return None
            history = _history_from_data_dir(data_dir)

    if include_system:
        return history
    return [message for message in history if message.get("role") != "system"]


def rename_session(session_id: str, title: str) -> Optional[Dict[str, Any]]:
    if not title or not str(title).strip():
        raise ValueError("title 不能为空")
    if not _session_data_dir_if_exists(session_id):
        return None
    return write_session_metadata(
        session_id,
        updates={"updatedAt": _now_iso()},
        title=str(title).strip()[:80],
    )


def auto_name_session(session_id: str, message: Optional[str] = None) -> Optional[Dict[str, Any]]:
    data_dir = _session_data_dir_if_exists(session_id)
    if not data_dir:
        return None
    history = get_session_history(session_id, include_system=False) or []
    source = message or _first_user_message(history)
    title = build_auto_session_title(source)
    meta = read_session_metadata(session_id) or _default_metadata(session_id, data_dir)
    meta.update({
        "title": title,
        "autoTitle": True,
        "updatedAt": _now_iso(),
    })
    return write_session_metadata(session_id, updates=meta)


def delete_session(session_id: str) -> bool:
    data_dir = _session_data_dir_if_exists(session_id)
    if not data_dir:
        return False
    meta = read_session_metadata(session_id) or {}

    root = _sessions_root()
    common = os.path.commonpath([root, data_dir])
    if common != root:
        raise ValueError("拒绝删除 sessions 目录外的路径")

    safe_session_id = _session_key(session_id)
    with agent_sessions_lock:
        for key in list(agent_sessions.keys()):
            if key == safe_session_id or sanitize_session_id(key) == safe_session_id:
                agent_sessions.pop(key, None)
    shutil.rmtree(data_dir)
    user_id = meta.get("userId")
    if user_id:
        unregister_user_conversation(str(user_id), str(meta.get("sessionId") or session_id), "api")
    return True


def _cli_sessions_root() -> str:
    root = os.path.abspath(os.path.join(Config.DATA_DIR, "cli_sessions"))
    os.makedirs(root, exist_ok=True)
    return root


def delete_user(user_id: str, delete_sessions: bool = True, delete_cli_sessions: bool = True) -> Dict[str, Any]:
    """删除用户心理模型，并可同时删除绑定到该用户的 API/CLI 会话。"""
    resolved_user_id = str(user_id or "").strip()
    if not resolved_user_id:
        raise ValueError("userId 不能为空")

    safe_user_id = sanitize_user_id(resolved_user_id)
    deleted_sessions: List[str] = []
    deleted_paths: List[str] = []
    errors: List[str] = []

    api_sessions = [
        item for item in list_session_summaries()
        if str(item.get("userId") or "") == resolved_user_id or str(item.get("safeUserId") or "") == safe_user_id
    ]
    if delete_sessions:
        for item in api_sessions:
            session_id = str(item.get("sessionId") or "")
            if not session_id:
                continue
            try:
                if delete_session(session_id):
                    deleted_sessions.append(session_id)
            except Exception as exc:
                errors.append(f"删除 API 会话 {session_id} 失败：{exc}")

    with agent_sessions_lock:
        for key, session in list(agent_sessions.items()):
            if session.user_id == resolved_user_id or sanitize_user_id(session.user_id) == safe_user_id:
                agent_sessions.pop(key, None)

    if delete_cli_sessions:
        old_cli_dir = os.path.join(_cli_sessions_root(), safe_user_id)
        try:
            if _safe_rmtree(old_cli_dir, _cli_sessions_root()):
                deleted_paths.append(old_cli_dir)
        except Exception as exc:
            errors.append(f"删除旧 CLI 会话目录失败：{exc}")

    model_dir = get_user_model_dir_if_exists(resolved_user_id)
    user_model_deleted = False
    if model_dir:
        try:
            user_model_deleted = _safe_rmtree(model_dir, _users_root())
            if user_model_deleted:
                deleted_paths.append(model_dir)
        except Exception as exc:
            errors.append(f"删除用户心理模型目录失败：{exc}")

    return {
        "deleted": bool(user_model_deleted or deleted_sessions or deleted_paths),
        "userId": resolved_user_id,
        "safeUserId": safe_user_id,
        "deletedSessions": deleted_sessions,
        "deletedSessionCount": len(deleted_sessions),
        "deletedUserModel": user_model_deleted,
        "deletedPaths": deleted_paths,
        "errors": errors,
    }


def build_thread_id(session_id: str) -> str:
    return sanitize_session_id(session_id)


def cleanup_expired_sessions() -> None:
    now = time.time()
    expired_ids = []
    with agent_sessions_lock:
        for session_id, session in agent_sessions.items():
            if now - session.last_access > SESSION_TTL_SECONDS:
                expired_ids.append(session_id)
        for session_id in expired_ids:
            agent_sessions.pop(session_id, None)


def get_or_create_session(session_id: str, user_id: Optional[str] = None) -> SessionManager:
    cleanup_expired_sessions()
    session_key = _session_key(session_id)
    with agent_sessions_lock:
        session = agent_sessions.get(session_key)
        if session is not None and session.session_id != session_id:
            raise ValueError(
                f"sessionId 与已有内存会话冲突：{session_id!r} 和 {session.session_id!r} "
                f"都会映射到 safeSessionId={session_key!r}"
            )
        if session is not None and user_id and session.user_id != user_id:
            raise ValueError(f"sessionId={session_id!r} 已绑定 userId={session.user_id!r}，不能切换到 {user_id!r}")
        if session is None:
            data_dir = get_session_data_dir(session_id)
            resolved_user_id = _resolve_session_user_id(session_id, user_id)
            psych_model_dir = get_user_model_dir(resolved_user_id)
            thread_id = build_thread_id(session_id)
            agent = EnhancedChatAgent(
                data_dir=data_dir,
                user_id=resolved_user_id,
                psych_model_dir=psych_model_dir,
            )
            apply_session_state(agent, load_session_state(data_dir), restore_psych_model=False)
            agent.graph_thread_id = thread_id
            session = SessionManager(
                session_id=session_id,
                user_id=resolved_user_id,
                thread_id=thread_id,
                data_dir=data_dir,
                psych_model_dir=psych_model_dir,
                agent=agent,
            )
            agent_sessions[session_key] = session
            create_session_metadata(session_id, user_id=resolved_user_id)
        session.last_access = time.time()
        return session


def update_session_after_chat(
    session: SessionManager,
    user_message: str,
    stage: str,
    prompt_meta: Dict[str, Any],
    save_state_sync: bool = True,
) -> Dict[str, Any]:
    history = session.agent.get_history()
    save_args = {
        "session_id": session.session_id,
        "thread_id": session.thread_id,
        "data_dir": session.data_dir,
        "agent": session.agent,
    }
    if save_state_sync:
        save_session_state(**save_args)
    else:
        threading.Thread(target=save_session_state, kwargs=save_args, daemon=True).start()
    current = read_session_metadata(session.session_id) or _default_metadata(session.session_id, session.data_dir)
    message_count = _message_count(history)
    title = current.get("title") or "新的会话"
    auto_title = bool(current.get("autoTitle", True))
    if auto_title and (not title or title == "新的会话"):
        title = build_auto_session_title(user_message)

    updates = {
        "title": title,
        "autoTitle": auto_title,
        "updatedAt": _now_iso(),
        "lastMessageAt": _now_iso(),
        "messageCount": message_count,
        "stage": stage,
        "promptProfile": prompt_meta.get("promptProfile", current.get("promptProfile")),
        "outputMode": prompt_meta.get("outputMode", current.get("outputMode")),
        "promptProfileVersion": prompt_meta.get("promptProfileVersion", current.get("promptProfileVersion", 1)),
        "outputModeVersion": prompt_meta.get("outputModeVersion", current.get("outputModeVersion", 1)),
    }
    return write_session_metadata(session.session_id, updates=updates, user_id=session.user_id)


def phase_to_stage(phase: TransplantPhase) -> str:
    stage_mapping = {
        TransplantPhase.PREP: "PRETREATMENT",
        TransplantPhase.KEY: "TRANSPLANT",
        TransplantPhase.RECOVERY: "RECOVERY",
    }
    return stage_mapping.get(phase, "PRETREATMENT")


def map_stage_to_phase(stage: str) -> TransplantPhase:
    normalized_stage = str(stage or "").strip().upper()
    stage_mapping = {
        "PRETREATMENT": TransplantPhase.PREP,
        "PREP": TransplantPhase.PREP,
        "TRANSPLANT": TransplantPhase.KEY,
        "KEY": TransplantPhase.KEY,
        "RECOVERY": TransplantPhase.RECOVERY,
        TransplantPhase.PREP.value: TransplantPhase.PREP,
        TransplantPhase.KEY.value: TransplantPhase.KEY,
        TransplantPhase.RECOVERY.value: TransplantPhase.RECOVERY,
    }
    return stage_mapping.get(normalized_stage, stage_mapping.get(str(stage or "").strip(), TransplantPhase.PREP))


def rebuild_agent_history(agent: EnhancedChatAgent, history: List[Dict[str, str]]) -> None:
    runtime = agent._resolve_prompt_runtime()
    rebuilt_history = [{"role": "system", "content": runtime.system_prompt}]
    for msg in history:
        role = msg.get("role")
        content = msg.get("content", "")
        if role in {"user", "assistant"} and content:
            rebuilt_history.append({"role": role, "content": content})
    agent.conversation_history = rebuilt_history


def _extract_prompt_config(data: Dict[str, Any]) -> Dict[str, Any]:
    prompt_config = data.get("promptConfig") or data.get("agentConfig") or {}
    return prompt_config if isinstance(prompt_config, dict) else {}


def apply_prompt_runtime_config(agent: EnhancedChatAgent, data: Dict[str, Any]) -> Dict[str, Any]:
    prompt_config = _extract_prompt_config(data)
    agent.configure_prompt_runtime(
        prompt_profile=prompt_config.get("promptProfile") or prompt_config.get("profile"),
        output_mode=prompt_config.get("outputMode") or prompt_config.get("mode"),
        system_prompt=prompt_config.get("systemPrompt"),
        extra_instructions=prompt_config.get("extraInstructions"),
    )
    runtime = agent._resolve_prompt_runtime()
    return {
        "promptProfile": runtime.profile,
        "outputMode": runtime.output_mode,
        "promptProfileVersion": runtime.profile_version,
        "outputModeVersion": runtime.output_mode_version,
    }


def prepare_session_for_chat(session: SessionManager, data: Dict[str, Any]) -> Dict[str, Any]:
    agent = session.agent
    patient_context = data.get("patientContext", {}) or {}
    history = data.get("history", []) or []

    requested_stage = patient_context.get("stage")
    if requested_stage:
        phase = map_stage_to_phase(requested_stage)
    else:
        phase = agent.get_transplant_phase()
    stage = phase_to_stage(phase)

    if agent.get_transplant_phase() != phase:
        agent.set_transplant_phase(phase)

    prompt_meta = apply_prompt_runtime_config(agent, data)
    if history:
        rebuild_agent_history(agent, history)

    agent.graph_thread_id = session.thread_id
    return {
        "sessionId": session.session_id,
        "userId": session.user_id,
        "safeUserId": sanitize_user_id(session.user_id),
        "threadId": session.thread_id,
        "psychModelDir": session.psych_model_dir,
        "stage": stage,
        **prompt_meta,
    }
