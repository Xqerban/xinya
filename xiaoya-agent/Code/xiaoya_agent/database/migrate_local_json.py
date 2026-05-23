"""把现有本地 JSON 持久化文件导入已配置的 MySQL 存储。"""
from __future__ import annotations

import json
import os
import re
from datetime import datetime
from typing import Any, Dict, List, Optional

from xiaoya_agent.config import Config
from xiaoya_agent.database.repository import database_storage_enabled, get_database_repository


def _now_iso() -> str:
    return datetime.now().replace(microsecond=0).isoformat()


def _read_json(path: str, default: Any) -> Any:
    try:
        if not os.path.exists(path):
            return default
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


def _safe_id(value: Any) -> str:
    safe = re.sub(r"[^0-9A-Za-z_.-]+", "_", str(value or "")).strip("._")[:80]
    return safe or "default"


def _message_count(history: List[Dict[str, Any]]) -> int:
    return len([message for message in history if isinstance(message, dict) and message.get("role") == "user"])


def _first_user_message(history: List[Dict[str, Any]]) -> str:
    for message in history:
        if isinstance(message, dict) and message.get("role") == "user":
            return str(message.get("content", ""))
    return ""


def _title_from_history(history: List[Dict[str, Any]], fallback: str = "新的会话") -> str:
    text = re.sub(r"\s+", " ", _first_user_message(history)).strip()
    text = text.strip("，。！？；：,.!?;: ")
    if not text:
        return fallback
    return text[:18] + ("..." if len(text) > 18 else "")


def _history_from_session_dir(path: str) -> List[Dict[str, Any]]:
    state = _read_json(os.path.join(path, "agent_state.json"), {})
    if isinstance(state, dict) and isinstance(state.get("conversation_history"), list):
        return state["conversation_history"]
    history = _read_json(os.path.join(path, "chat_history.json"), [])
    return history if isinstance(history, list) else []


def _save_conversation(
    repo,
    user_id: str,
    source: str,
    conversation_id: str,
    history: List[Dict[str, Any]],
    metadata: Optional[Dict[str, Any]] = None,
) -> None:
    metadata = dict(metadata or {})
    visible_history = [message for message in list(history or []) if isinstance(message, dict) and message.get("role") != "system"]
    now = _now_iso()
    entry = {
        "userId": user_id,
        "safeUserId": _safe_id(user_id),
        "source": source,
        "conversationId": conversation_id,
        "safeConversationId": _safe_id(conversation_id),
        "sessionId": metadata.get("sessionId", conversation_id if source == "api" else None),
        "title": metadata.get("title") or _title_from_history(visible_history),
        "createdAt": metadata.get("createdAt") or now,
        "updatedAt": metadata.get("updatedAt") or now,
        "lastMessageAt": metadata.get("lastMessageAt"),
        "messageCount": _message_count(visible_history),
        "historyCount": len(visible_history),
        "dataDir": metadata.get("dataDir"),
        "snapshotPath": None,
    }
    repo.save_conversation_snapshot(
        user_id=user_id,
        safe_user_id=_safe_id(user_id),
        conversation_id=conversation_id,
        safe_conversation_id=_safe_id(conversation_id),
        source=source,
        history=list(history or []),
        metadata=metadata,
        entry=entry,
    )


def migrate_local_json_to_database(data_dir: Optional[str] = None) -> Dict[str, Any]:
    if not database_storage_enabled():
        raise RuntimeError("请先配置 STORAGE_BACKEND=mysql 再执行迁移")

    root = os.path.abspath(data_dir or Config.DATA_DIR)
    repo = get_database_repository()
    repo.ensure_schema()

    imported = {
        "users": 0,
        "sessions": 0,
        "sessionStates": 0,
        "conversations": 0,
        "promptRegistry": 0,
        "cohortModel": 0,
        "errors": [],
    }

    users_root = os.path.join(root, "users")
    if os.path.isdir(users_root):
        for item in os.scandir(users_root):
            if not item.is_dir():
                continue
            try:
                metadata = _read_json(os.path.join(item.path, "psych_model_meta.json"), {})
                psych_model = _read_json(os.path.join(item.path, "psych_model.json"), {})
                user_state = _read_json(os.path.join(item.path, "user_state.json"), {})
                energy = _read_json(os.path.join(item.path, "energy_progress.json"), {})
                crisis = _read_json(os.path.join(item.path, "crisis_history.json"), [])
                user_id = str(
                    (metadata or {}).get("userId")
                    or (psych_model or {}).get("userId")
                    or item.name
                )
                repo.upsert_user(
                    user_id=user_id,
                    safe_user_id=_safe_id(user_id),
                    metadata=metadata if isinstance(metadata, dict) else {},
                    psych_model=psych_model if isinstance(psych_model, dict) else {},
                    user_state=user_state if isinstance(user_state, dict) else {},
                    energy_progress=energy if isinstance(energy, dict) else {},
                    crisis_history=crisis if isinstance(crisis, list) else [],
                    psych_model_dir=item.path,
                )
                imported["users"] += 1

                conversations_dir = os.path.join(item.path, "conversations")
                if os.path.isdir(conversations_dir):
                    for snapshot in os.scandir(conversations_dir):
                        if not snapshot.is_file() or not snapshot.name.endswith(".json"):
                            continue
                        payload = _read_json(snapshot.path, {})
                        if not isinstance(payload, dict):
                            continue
                        _save_conversation(
                            repo,
                            user_id=str(payload.get("userId") or user_id),
                            source=str(payload.get("source") or "api"),
                            conversation_id=str(payload.get("conversationId") or snapshot.name[:-5]),
                            history=payload.get("history") if isinstance(payload.get("history"), list) else [],
                            metadata=payload.get("metadata") if isinstance(payload.get("metadata"), dict) else {},
                        )
                        imported["conversations"] += 1

                cli_history = _read_json(os.path.join(item.path, "cli_session", "chat_history.json"), [])
                if isinstance(cli_history, list) and cli_history:
                    _save_conversation(
                        repo,
                        user_id=user_id,
                        source="cli",
                        conversation_id="cli",
                        history=cli_history,
                        metadata={
                            "sessionId": "cli",
                            "title": f"CLI 会话 - {user_id}",
                            "dataDir": os.path.join(item.path, "cli_session"),
                        },
                    )
                    imported["conversations"] += 1
            except Exception as exc:
                imported["errors"].append(f"导入用户目录 {item.path} 失败：{exc}")

    sessions_root = os.path.join(root, "sessions")
    if os.path.isdir(sessions_root):
        for item in os.scandir(sessions_root):
            if not item.is_dir():
                continue
            try:
                metadata = _read_json(os.path.join(item.path, "session_meta.json"), {})
                history = _history_from_session_dir(item.path)
                session_id = str((metadata or {}).get("sessionId") or item.name)
                user_id = str((metadata or {}).get("userId") or session_id)
                if not isinstance(metadata, dict):
                    metadata = {}
                metadata = {
                    "sessionId": session_id,
                    "safeSessionId": _safe_id(session_id),
                    "userId": user_id,
                    "safeUserId": _safe_id(user_id),
                    "threadId": metadata.get("threadId") or _safe_id(session_id),
                    "title": metadata.get("title") or _title_from_history(history),
                    "autoTitle": bool(metadata.get("autoTitle", True)),
                    "createdAt": metadata.get("createdAt") or _now_iso(),
                    "updatedAt": metadata.get("updatedAt") or _now_iso(),
                    "lastMessageAt": metadata.get("lastMessageAt"),
                    "messageCount": metadata.get("messageCount") or _message_count(history),
                    "stage": metadata.get("stage") or "PRETREATMENT",
                    "promptProfile": metadata.get("promptProfile") or Config.PROMPT_PROFILE,
                    "outputMode": metadata.get("outputMode") or Config.OUTPUT_MODE,
                    "promptProfileVersion": metadata.get("promptProfileVersion") or 1,
                    "outputModeVersion": metadata.get("outputModeVersion") or 1,
                    "dataDir": item.path,
                    "psychModelDir": metadata.get("psychModelDir") or os.path.join(users_root, _safe_id(user_id)),
                }
                repo.save_session_metadata(metadata)
                imported["sessions"] += 1

                state = _read_json(os.path.join(item.path, "agent_state.json"), {})
                if isinstance(state, dict):
                    state.setdefault("session_id", session_id)
                    state.setdefault("thread_id", metadata.get("threadId") or _safe_id(session_id))
                    state.setdefault("user_id", user_id)
                    state.setdefault("conversation_history", history)
                    repo.save_session_state(state)
                    imported["sessionStates"] += 1
                elif history:
                    repo.save_session_messages(session_id, history)

                _save_conversation(repo, user_id, "api", session_id, history, metadata)
                imported["conversations"] += 1
            except Exception as exc:
                imported["errors"].append(f"导入会话目录 {item.path} 失败：{exc}")

    prompt_registry = _read_json(os.path.join(root, "prompt_registry.json"), None)
    if isinstance(prompt_registry, dict):
        try:
            repo.save_prompt_registry(prompt_registry)
            imported["promptRegistry"] = 1
        except Exception as exc:
            imported["errors"].append(f"导入提示词注册表失败：{exc}")

    cohort_model = _read_json(os.path.join(root, "cohort_learning", "cohort_model.json"), None)
    if isinstance(cohort_model, dict):
        try:
            repo.save_cohort_model(cohort_model, dirty=False)
            imported["cohortModel"] = 1
        except Exception as exc:
            imported["errors"].append(f"导入群体学习模型失败：{exc}")

    return imported


if __name__ == "__main__":
    result = migrate_local_json_to_database()
    print(json.dumps(result, ensure_ascii=False, indent=2))
