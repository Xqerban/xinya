"""小芽运行时数据的 MySQL 持久化仓储。

本模块集中管理 SQL 表名、连接参数和读写方法。业务层只调用仓储接口，
避免在会话、智能体或功能模块里直接散落数据库连接和 SQL 语句。
"""
from __future__ import annotations

import hashlib
import json
import re
import threading
from contextlib import contextmanager
from datetime import datetime
from typing import Any, Dict, Iterator, List, Optional, Tuple
from urllib.parse import parse_qs, unquote, urlparse

from xiaoya_agent.config import Config


_REPOSITORY = None
_REPOSITORY_LOCK = threading.Lock()


def database_storage_enabled() -> bool:
    backend = str(getattr(Config, "STORAGE_BACKEND", "json") or "json").strip().lower()
    return backend in {"mysql", "database", "db"}


def get_database_repository() -> "MySQLStorageRepository":
    global _REPOSITORY
    with _REPOSITORY_LOCK:
        if _REPOSITORY is None:
            _REPOSITORY = MySQLStorageRepository()
        return _REPOSITORY


def _json_dumps(value: Any) -> Optional[str]:
    if value is None:
        return None
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def _json_loads(value: Any, default: Any = None) -> Any:
    if value in (None, ""):
        return default
    if isinstance(value, (dict, list)):
        return value
    try:
        return json.loads(value)
    except Exception:
        return default


def _stable_key(*parts: Any) -> str:
    raw = "\x1f".join(str(part or "") for part in parts)
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def _safe_identifier(value: str, fallback: str = "xiaoya_") -> str:
    cleaned = re.sub(r"[^0-9A-Za-z_]+", "_", str(value or "")).strip("_")
    if not cleaned:
        cleaned = fallback.strip("_") or "xiaoya"
    if cleaned[0].isdigit():
        cleaned = f"_{cleaned}"
    return cleaned


def _safe_ref_id(value: Any, fallback: str = "default") -> str:
    cleaned = re.sub(r"[^0-9A-Za-z_.-]+", "_", str(value or "")).strip("._")
    return cleaned or fallback


class MySQLStorageRepository:
    def __init__(self) -> None:
        self._schema_lock = threading.RLock()
        self._schema_ready = False
        self._pymysql = None
        self._prefix = _safe_identifier(getattr(Config, "DATABASE_TABLE_PREFIX", "xiaoya_"))
        if self._prefix and not self._prefix.endswith("_"):
            self._prefix += "_"

    def _table_name(self, name: str) -> str:
        return f"{self._prefix}{_safe_identifier(name)}"

    def _table(self, name: str) -> str:
        return f"`{self._table_name(name)}`"

    def _storage_ref(self, table_name: str, identifier: Any) -> str:
        return f"mysql:{self._table_name(table_name)}/{_safe_ref_id(identifier)}"

    def _normalize_session_metadata_refs(self, metadata: Dict[str, Any]) -> Dict[str, Any]:
        normalized = dict(metadata or {})
        session_id = str(normalized.get("sessionId") or "")
        user_id = str(normalized.get("userId") or session_id)
        safe_session_id = str(normalized.get("safeSessionId") or _safe_ref_id(session_id))
        safe_user_id = str(normalized.get("safeUserId") or _safe_ref_id(user_id))
        normalized["storageBackend"] = "mysql"
        normalized["dataDir"] = self._storage_ref("sessions", safe_session_id)
        if user_id:
            normalized["psychModelDir"] = self._storage_ref("users", safe_user_id)
        return normalized

    def _normalize_session_state_refs(self, state: Dict[str, Any]) -> Dict[str, Any]:
        normalized = dict(state or {})
        user_id = str(normalized.get("user_id") or normalized.get("userId") or "")
        if user_id:
            normalized["psych_model_dir"] = self._storage_ref("users", _safe_ref_id(user_id))
        normalized["storage_backend"] = "mysql"
        return normalized

    def _normalize_conversation_refs(
        self,
        source: str,
        safe_conversation_id: str,
        metadata: Dict[str, Any],
        entry: Dict[str, Any],
    ) -> Tuple[Dict[str, Any], Dict[str, Any]]:
        storage_ref = self._storage_ref("conversations", f"{source}_{safe_conversation_id}")
        normalized_metadata = dict(metadata or {})
        normalized_entry = dict(entry or {})
        normalized_metadata["storageBackend"] = "mysql"
        normalized_metadata["storageRef"] = storage_ref
        normalized_metadata.pop("dataDir", None)
        normalized_metadata.pop("snapshotPath", None)
        normalized_entry["storageBackend"] = "mysql"
        normalized_entry["storageRef"] = storage_ref
        normalized_entry["dataDir"] = None
        normalized_entry["snapshotPath"] = None
        return normalized_metadata, normalized_entry

    def _import_driver(self):
        if self._pymysql is None:
            try:
                import pymysql
                from pymysql.cursors import DictCursor
            except ImportError as exc:
                raise RuntimeError(
                    "STORAGE_BACKEND=mysql 需要安装 PyMySQL；请先运行 pip install PyMySQL"
                ) from exc
            self._pymysql = (pymysql, DictCursor)
        return self._pymysql

    def _connection_settings(self) -> Dict[str, Any]:
        url = str(getattr(Config, "DATABASE_URL", "") or "").strip()
        settings: Dict[str, Any] = {}
        if url:
            parsed = urlparse(url)
            if parsed.scheme not in {"mysql", "mysql+pymysql"}:
                raise ValueError("DATABASE_URL 只支持 mysql:// 或 mysql+pymysql://")
            query = parse_qs(parsed.query)
            settings = {
                "host": parsed.hostname or "127.0.0.1",
                "port": int(parsed.port or 3306),
                "user": unquote(parsed.username or ""),
                "password": unquote(parsed.password or ""),
                "database": parsed.path.lstrip("/"),
                "charset": query.get("charset", ["utf8mb4"])[0],
            }
        else:
            settings = {
                "host": getattr(Config, "MYSQL_HOST", "127.0.0.1"),
                "port": int(getattr(Config, "MYSQL_PORT", 3306) or 3306),
                "user": getattr(Config, "MYSQL_USER", "root"),
                "password": getattr(Config, "MYSQL_PASSWORD", ""),
                "database": getattr(Config, "MYSQL_DATABASE", ""),
                "charset": getattr(Config, "MYSQL_CHARSET", "utf8mb4"),
            }

        if not settings.get("database"):
            raise ValueError("MySQL 数据库名不能为空，请配置 MYSQL_DATABASE 或 DATABASE_URL")
        settings.setdefault("charset", "utf8mb4")
        settings["autocommit"] = True
        settings["connect_timeout"] = int(getattr(Config, "MYSQL_CONNECT_TIMEOUT", 5) or 5)
        settings["read_timeout"] = int(getattr(Config, "MYSQL_READ_TIMEOUT", 10) or 10)
        settings["write_timeout"] = int(getattr(Config, "MYSQL_WRITE_TIMEOUT", 10) or 10)
        return settings

    def _connect_raw(self):
        pymysql, DictCursor = self._import_driver()
        settings = self._connection_settings()
        settings["cursorclass"] = DictCursor
        return pymysql.connect(**settings)

    @contextmanager
    def connection(self) -> Iterator[Any]:
        conn = self._connect_raw()
        try:
            if bool(getattr(Config, "DATABASE_AUTO_INIT", True)):
                self.ensure_schema(conn)
            yield conn
        finally:
            conn.close()

    def ensure_schema(self, conn: Any = None) -> None:
        with self._schema_lock:
            if self._schema_ready:
                return
            close_conn = False
            if conn is None:
                conn = self._connect_raw()
                close_conn = True
            try:
                with conn.cursor() as cur:
                    cur.execute(f"""
                        CREATE TABLE IF NOT EXISTS {self._table("users")} (
                            user_key CHAR(64) PRIMARY KEY,
                            user_id TEXT NOT NULL,
                            safe_user_id VARCHAR(191) NOT NULL,
                            psych_model_dir TEXT NULL,
                            metadata_json LONGTEXT NULL,
                            psych_model_json LONGTEXT NULL,
                            user_state_json LONGTEXT NULL,
                            energy_progress_json LONGTEXT NULL,
                            crisis_history_json LONGTEXT NULL,
                            created_at VARCHAR(32) NULL,
                            updated_at VARCHAR(32) NULL,
                            KEY idx_safe_user_id (safe_user_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """)
                    cur.execute(f"""
                        CREATE TABLE IF NOT EXISTS {self._table("sessions")} (
                            session_key CHAR(64) PRIMARY KEY,
                            session_id TEXT NOT NULL,
                            safe_session_id VARCHAR(191) NOT NULL,
                            user_key CHAR(64) NULL,
                            user_id TEXT NULL,
                            safe_user_id VARCHAR(191) NULL,
                            thread_id VARCHAR(191) NULL,
                            title VARCHAR(255) NULL,
                            stage VARCHAR(64) NULL,
                            message_count INT NOT NULL DEFAULT 0,
                            metadata_json LONGTEXT NULL,
                            created_at VARCHAR(32) NULL,
                            updated_at VARCHAR(32) NULL,
                            last_message_at VARCHAR(32) NULL,
                            KEY idx_safe_session_id (safe_session_id),
                            KEY idx_user_key (user_key)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """)
                    cur.execute(f"""
                        CREATE TABLE IF NOT EXISTS {self._table("session_states")} (
                            session_key CHAR(64) PRIMARY KEY,
                            session_id TEXT NOT NULL,
                            thread_id VARCHAR(191) NULL,
                            user_key CHAR(64) NULL,
                            user_id TEXT NULL,
                            state_json LONGTEXT NOT NULL,
                            updated_at VARCHAR(32) NULL,
                            KEY idx_user_key (user_key)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """)
                    cur.execute(f"""
                        CREATE TABLE IF NOT EXISTS {self._table("messages")} (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            session_key CHAR(64) NOT NULL,
                            session_id TEXT NOT NULL,
                            message_index INT NOT NULL,
                            role VARCHAR(32) NULL,
                            content LONGTEXT NULL,
                            metadata_json LONGTEXT NULL,
                            message_json LONGTEXT NOT NULL,
                            created_at VARCHAR(32) NULL,
                            UNIQUE KEY uniq_session_message (session_key, message_index),
                            KEY idx_session_role (session_key, role)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """)
                    cur.execute(f"""
                        CREATE TABLE IF NOT EXISTS {self._table("conversations")} (
                            conversation_key CHAR(64) PRIMARY KEY,
                            user_key CHAR(64) NOT NULL,
                            user_id TEXT NOT NULL,
                            safe_user_id VARCHAR(191) NOT NULL,
                            source VARCHAR(32) NOT NULL,
                            conversation_id TEXT NOT NULL,
                            safe_conversation_id VARCHAR(191) NOT NULL,
                            session_id TEXT NULL,
                            title VARCHAR(255) NULL,
                            message_count INT NOT NULL DEFAULT 0,
                            history_count INT NOT NULL DEFAULT 0,
                            metadata_json LONGTEXT NULL,
                            entry_json LONGTEXT NULL,
                            history_json LONGTEXT NULL,
                            created_at VARCHAR(32) NULL,
                            updated_at VARCHAR(32) NULL,
                            last_message_at VARCHAR(32) NULL,
                            KEY idx_user_updated (user_key, updated_at),
                            KEY idx_source (source)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """)
                    cur.execute(f"""
                        CREATE TABLE IF NOT EXISTS {self._table("cohort_models")} (
                            model_key VARCHAR(64) PRIMARY KEY,
                            payload_json LONGTEXT NULL,
                            dirty TINYINT NOT NULL DEFAULT 0,
                            updated_at VARCHAR(32) NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """)
                    cur.execute(f"""
                        CREATE TABLE IF NOT EXISTS {self._table("prompt_registries")} (
                            registry_key VARCHAR(64) PRIMARY KEY,
                            registry_json LONGTEXT NOT NULL,
                            updated_at VARCHAR(32) NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """)
                    cur.execute(
                        f"""
                        UPDATE {self._table("users")}
                        SET psych_model_dir=CONCAT(%s, safe_user_id)
                        WHERE psych_model_dir IS NULL OR psych_model_dir NOT LIKE 'mysql:%%'
                        """,
                        (f"mysql:{self._table_name('users')}/",),
                    )
                self._schema_ready = True
            finally:
                if close_conn:
                    conn.close()

    def upsert_user(
        self,
        user_id: str,
        safe_user_id: str,
        metadata: Optional[Dict[str, Any]] = None,
        psych_model: Optional[Dict[str, Any]] = None,
        user_state: Optional[Dict[str, Any]] = None,
        energy_progress: Optional[Dict[str, Any]] = None,
        crisis_history: Optional[List[Dict[str, Any]]] = None,
        psych_model_dir: Optional[str] = None,
    ) -> Dict[str, Any]:
        existing = self.load_user_record(user_id) or {}
        merged_metadata = metadata if metadata is not None else existing.get("metadata")
        merged_psych_model = psych_model if psych_model is not None else existing.get("psychModel")
        merged_user_state = user_state if user_state is not None else existing.get("userState")
        merged_energy = energy_progress if energy_progress is not None else existing.get("energyProgress")
        merged_crisis = crisis_history if crisis_history is not None else existing.get("crisisHistory")
        now = str((merged_metadata or {}).get("updatedAt") or (merged_psych_model or {}).get("updatedAt") or "")
        created = str((merged_metadata or {}).get("createdAt") or existing.get("createdAt") or now)
        user_key = _stable_key(user_id)
        storage_ref = self._storage_ref("users", safe_user_id)
        if isinstance(merged_metadata, dict):
            merged_metadata = dict(merged_metadata)
            merged_metadata["storageBackend"] = "mysql"
            merged_metadata["psychModelDir"] = storage_ref
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"""
                    INSERT INTO {self._table("users")} (
                        user_key, user_id, safe_user_id, psych_model_dir,
                        metadata_json, psych_model_json, user_state_json,
                        energy_progress_json, crisis_history_json, created_at, updated_at
                    ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                    ON DUPLICATE KEY UPDATE
                        user_id=VALUES(user_id),
                        safe_user_id=VALUES(safe_user_id),
                        psych_model_dir=VALUES(psych_model_dir),
                        metadata_json=VALUES(metadata_json),
                        psych_model_json=VALUES(psych_model_json),
                        user_state_json=VALUES(user_state_json),
                        energy_progress_json=VALUES(energy_progress_json),
                        crisis_history_json=VALUES(crisis_history_json),
                        updated_at=VALUES(updated_at)
                """, (
                    user_key,
                    str(user_id),
                    str(safe_user_id),
                    storage_ref,
                    _json_dumps(merged_metadata),
                    _json_dumps(merged_psych_model),
                    _json_dumps(merged_user_state),
                    _json_dumps(merged_energy),
                    _json_dumps(merged_crisis),
                    created,
                    now or existing.get("updatedAt"),
                ))
        return self.load_user_record(user_id) or {}

    def load_user_record(self, user_id: str) -> Optional[Dict[str, Any]]:
        user_key = _stable_key(user_id)
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"SELECT * FROM {self._table('users')} WHERE user_key=%s", (user_key,))
                row = cur.fetchone()
        if not row:
            return None
        storage_ref = self._storage_ref("users", row.get("safe_user_id"))
        metadata = _json_loads(row.get("metadata_json"), {})
        if isinstance(metadata, dict):
            metadata = dict(metadata)
            metadata["storageBackend"] = "mysql"
            metadata["psychModelDir"] = storage_ref
        return {
            "userId": row.get("user_id"),
            "safeUserId": row.get("safe_user_id"),
            "psychModelDir": storage_ref,
            "metadata": metadata if isinstance(metadata, dict) else {},
            "psychModel": _json_loads(row.get("psych_model_json"), {}),
            "userState": _json_loads(row.get("user_state_json"), {}),
            "energyProgress": _json_loads(row.get("energy_progress_json"), {}),
            "crisisHistory": _json_loads(row.get("crisis_history_json"), []),
            "createdAt": row.get("created_at"),
            "updatedAt": row.get("updated_at"),
        }

    def list_user_records(self) -> List[Dict[str, Any]]:
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"SELECT * FROM {self._table('users')} ORDER BY updated_at DESC")
                rows = cur.fetchall()
                results = []
                for row in rows:
                    user_id = row.get("user_id")
                    if not user_id:
                        continue
                    cur.execute(
                        f"SELECT COUNT(*) AS count FROM {self._table('conversations')} WHERE user_key=%s",
                        (_stable_key(user_id),),
                    )
                    count_row = cur.fetchone() or {}
                    storage_ref = self._storage_ref("users", row.get("safe_user_id"))
                    metadata = _json_loads(row.get("metadata_json"), {})
                    if isinstance(metadata, dict):
                        metadata = dict(metadata)
                        metadata["storageBackend"] = "mysql"
                        metadata["psychModelDir"] = storage_ref
                    results.append({
                        "userId": user_id,
                        "safeUserId": row.get("safe_user_id"),
                        "psychModelDir": storage_ref,
                        "updatedAt": row.get("updated_at"),
                        "conversationCount": int(count_row.get("count") or 0),
                        "metadata": metadata if isinstance(metadata, dict) else {},
                    })
        return results

    def save_session_metadata(self, metadata: Dict[str, Any]) -> Dict[str, Any]:
        metadata = self._normalize_session_metadata_refs(metadata)
        session_id = str(metadata.get("sessionId") or "")
        if not session_id:
            raise ValueError("sessionId is required")
        user_id = str(metadata.get("userId") or session_id)
        safe_user_id = str(metadata.get("safeUserId") or "")
        if safe_user_id:
            self.upsert_user(
                user_id=user_id,
                safe_user_id=safe_user_id,
                metadata={
                    "userId": user_id,
                    "safeUserId": safe_user_id,
                    "updatedAt": metadata.get("updatedAt"),
                    "psychModelDir": metadata.get("psychModelDir"),
                },
                psych_model_dir=metadata.get("psychModelDir"),
            )
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"""
                    INSERT INTO {self._table("sessions")} (
                        session_key, session_id, safe_session_id, user_key, user_id,
                        safe_user_id, thread_id, title, stage, message_count,
                        metadata_json, created_at, updated_at, last_message_at
                    ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                    ON DUPLICATE KEY UPDATE
                        session_id=VALUES(session_id),
                        safe_session_id=VALUES(safe_session_id),
                        user_key=VALUES(user_key),
                        user_id=VALUES(user_id),
                        safe_user_id=VALUES(safe_user_id),
                        thread_id=VALUES(thread_id),
                        title=VALUES(title),
                        stage=VALUES(stage),
                        message_count=VALUES(message_count),
                        metadata_json=VALUES(metadata_json),
                        updated_at=VALUES(updated_at),
                        last_message_at=VALUES(last_message_at)
                """, (
                    _stable_key(session_id),
                    session_id,
                    str(metadata.get("safeSessionId") or ""),
                    _stable_key(user_id),
                    user_id,
                    safe_user_id,
                    str(metadata.get("threadId") or ""),
                    metadata.get("title"),
                    metadata.get("stage"),
                    int(metadata.get("messageCount") or 0),
                    _json_dumps(metadata),
                    metadata.get("createdAt"),
                    metadata.get("updatedAt"),
                    metadata.get("lastMessageAt"),
                ))
        return metadata

    def load_session_metadata(self, session_id: str) -> Optional[Dict[str, Any]]:
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"SELECT * FROM {self._table('sessions')} WHERE session_key=%s",
                    (_stable_key(session_id),),
                )
                row = cur.fetchone()
        if not row:
            return None
        metadata = _json_loads(row.get("metadata_json"), {}) or {}
        metadata.setdefault("sessionId", row.get("session_id"))
        metadata.setdefault("safeSessionId", row.get("safe_session_id"))
        metadata.setdefault("userId", row.get("user_id"))
        metadata.setdefault("safeUserId", row.get("safe_user_id"))
        metadata.setdefault("threadId", row.get("thread_id"))
        metadata.setdefault("title", row.get("title"))
        metadata.setdefault("stage", row.get("stage"))
        metadata.setdefault("messageCount", int(row.get("message_count") or 0))
        metadata.setdefault("createdAt", row.get("created_at"))
        metadata.setdefault("updatedAt", row.get("updated_at"))
        metadata.setdefault("lastMessageAt", row.get("last_message_at"))
        return self._normalize_session_metadata_refs(metadata)

    def list_session_metadata(self) -> List[Dict[str, Any]]:
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"SELECT * FROM {self._table('sessions')} ORDER BY updated_at DESC")
                rows = cur.fetchall()
        sessions = []
        for row in rows:
            metadata = _json_loads(row.get("metadata_json"), {}) or {}
            metadata.setdefault("sessionId", row.get("session_id"))
            metadata.setdefault("safeSessionId", row.get("safe_session_id"))
            metadata.setdefault("userId", row.get("user_id"))
            metadata.setdefault("safeUserId", row.get("safe_user_id"))
            metadata.setdefault("threadId", row.get("thread_id"))
            metadata.setdefault("title", row.get("title"))
            metadata.setdefault("stage", row.get("stage"))
            metadata.setdefault("messageCount", int(row.get("message_count") or 0))
            metadata.setdefault("updatedAt", row.get("updated_at"))
            sessions.append(self._normalize_session_metadata_refs(metadata))
        return sessions

    def save_session_state(self, state: Dict[str, Any]) -> Dict[str, Any]:
        state = self._normalize_session_state_refs(state)
        session_id = str(state.get("session_id") or state.get("sessionId") or "")
        if not session_id:
            raise ValueError("session_id is required")
        user_id = str(state.get("user_id") or state.get("userId") or "")
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"""
                    INSERT INTO {self._table("session_states")} (
                        session_key, session_id, thread_id, user_key, user_id, state_json, updated_at
                    ) VALUES (%s,%s,%s,%s,%s,%s,%s)
                    ON DUPLICATE KEY UPDATE
                        session_id=VALUES(session_id),
                        thread_id=VALUES(thread_id),
                        user_key=VALUES(user_key),
                        user_id=VALUES(user_id),
                        state_json=VALUES(state_json),
                        updated_at=VALUES(updated_at)
                """, (
                    _stable_key(session_id),
                    session_id,
                    str(state.get("thread_id") or ""),
                    _stable_key(user_id) if user_id else None,
                    user_id or None,
                    _json_dumps(state),
                    state.get("updated_at") or state.get("updatedAt"),
                ))
        history = state.get("conversation_history")
        if isinstance(history, list):
            self.save_session_messages(session_id, history)
        return state

    def load_session_state(self, session_id: str) -> Optional[Dict[str, Any]]:
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"SELECT state_json FROM {self._table('session_states')} WHERE session_key=%s",
                    (_stable_key(session_id),),
                )
                row = cur.fetchone()
        if not row:
            return None
        state = _json_loads(row.get("state_json"), None)
        return self._normalize_session_state_refs(state) if isinstance(state, dict) else None

    def save_session_messages(self, session_id: str, history: List[Dict[str, Any]]) -> None:
        session_key = _stable_key(session_id)
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"DELETE FROM {self._table('messages')} WHERE session_key=%s", (session_key,))
                rows = []
                for index, message in enumerate(list(history or [])):
                    if not isinstance(message, dict):
                        continue
                    rows.append((
                        session_key,
                        str(session_id),
                        index,
                        message.get("role"),
                        message.get("content"),
                        _json_dumps(message.get("metadata") if isinstance(message.get("metadata"), dict) else None),
                        _json_dumps(message),
                        message.get("createdAt") or message.get("timestamp"),
                    ))
                if rows:
                    cur.executemany(f"""
                        INSERT INTO {self._table("messages")} (
                            session_key, session_id, message_index, role, content,
                            metadata_json, message_json, created_at
                        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s)
                    """, rows)

    def load_session_history(self, session_id: str) -> Optional[List[Dict[str, Any]]]:
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"SELECT message_json FROM {self._table('messages')} WHERE session_key=%s ORDER BY message_index ASC",
                    (_stable_key(session_id),),
                )
                rows = cur.fetchall()
        if rows:
            return [
                item for item in (_json_loads(row.get("message_json"), None) for row in rows)
                if isinstance(item, dict)
            ]
        state = self.load_session_state(session_id)
        if isinstance(state, dict) and isinstance(state.get("conversation_history"), list):
            return list(state["conversation_history"])
        return None

    def save_conversation_snapshot(
        self,
        user_id: str,
        safe_user_id: str,
        conversation_id: str,
        safe_conversation_id: str,
        source: str,
        history: List[Dict[str, Any]],
        metadata: Dict[str, Any],
        entry: Dict[str, Any],
    ) -> Dict[str, Any]:
        metadata, entry = self._normalize_conversation_refs(source, safe_conversation_id, metadata, entry)
        self.upsert_user(
            user_id=user_id,
            safe_user_id=safe_user_id,
            metadata={
                "userId": user_id,
                "safeUserId": safe_user_id,
                "updatedAt": entry.get("updatedAt"),
            },
        )
        conversation_key = _stable_key(user_id, source, conversation_id)
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"""
                    INSERT INTO {self._table("conversations")} (
                        conversation_key, user_key, user_id, safe_user_id, source,
                        conversation_id, safe_conversation_id, session_id, title,
                        message_count, history_count, metadata_json, entry_json,
                        history_json, created_at, updated_at, last_message_at
                    ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                    ON DUPLICATE KEY UPDATE
                        user_id=VALUES(user_id),
                        safe_user_id=VALUES(safe_user_id),
                        source=VALUES(source),
                        conversation_id=VALUES(conversation_id),
                        safe_conversation_id=VALUES(safe_conversation_id),
                        session_id=VALUES(session_id),
                        title=VALUES(title),
                        message_count=VALUES(message_count),
                        history_count=VALUES(history_count),
                        metadata_json=VALUES(metadata_json),
                        entry_json=VALUES(entry_json),
                        history_json=VALUES(history_json),
                        updated_at=VALUES(updated_at),
                        last_message_at=VALUES(last_message_at)
                """, (
                    conversation_key,
                    _stable_key(user_id),
                    str(user_id),
                    str(safe_user_id),
                    str(source),
                    str(conversation_id),
                    str(safe_conversation_id),
                    entry.get("sessionId"),
                    entry.get("title"),
                    int(entry.get("messageCount") or 0),
                    int(entry.get("historyCount") or 0),
                    _json_dumps(metadata),
                    _json_dumps(entry),
                    _json_dumps(history),
                    entry.get("createdAt"),
                    entry.get("updatedAt"),
                    entry.get("lastMessageAt"),
                ))
        return entry

    def load_user_conversations(self, user_id: str, include_history: bool = False) -> Dict[str, Any]:
        user = self.load_user_record(user_id)
        if not user:
            return {
                "userId": user_id,
                "safeUserId": "",
                "exists": False,
                "conversations": [],
            }
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"SELECT * FROM {self._table('conversations')} WHERE user_key=%s ORDER BY updated_at DESC",
                    (_stable_key(user_id),),
                )
                rows = cur.fetchall()
        conversations = []
        for row in rows:
            item = _json_loads(row.get("entry_json"), {}) or {}
            item.setdefault("userId", row.get("user_id"))
            item.setdefault("safeUserId", row.get("safe_user_id"))
            item.setdefault("source", row.get("source"))
            item.setdefault("conversationId", row.get("conversation_id"))
            item.setdefault("safeConversationId", row.get("safe_conversation_id"))
            item.setdefault("sessionId", row.get("session_id"))
            item.setdefault("title", row.get("title"))
            item.setdefault("messageCount", int(row.get("message_count") or 0))
            item.setdefault("historyCount", int(row.get("history_count") or 0))
            item.setdefault("createdAt", row.get("created_at"))
            item.setdefault("updatedAt", row.get("updated_at"))
            item.setdefault("lastMessageAt", row.get("last_message_at"))
            _, item = self._normalize_conversation_refs(
                str(item.get("source") or row.get("source") or ""),
                str(item.get("safeConversationId") or row.get("safe_conversation_id") or ""),
                {},
                item,
            )
            if include_history:
                item["history"] = _json_loads(row.get("history_json"), [])
            conversations.append(item)
        return {
            "userId": user.get("userId") or user_id,
            "safeUserId": user.get("safeUserId") or "",
            "exists": True,
            "conversationCount": len(conversations),
            "conversations": conversations,
        }

    def remove_conversation(self, user_id: str, conversation_id: str, source: str) -> bool:
        with self.connection() as conn:
            with conn.cursor() as cur:
                affected = cur.execute(
                    f"DELETE FROM {self._table('conversations')} WHERE conversation_key=%s",
                    (_stable_key(user_id, source, conversation_id),),
                )
        return bool(affected)

    def delete_session(self, session_id: str) -> bool:
        session_key = _stable_key(session_id)
        metadata = self.load_session_metadata(session_id)
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"DELETE FROM {self._table('messages')} WHERE session_key=%s", (session_key,))
                cur.execute(f"DELETE FROM {self._table('session_states')} WHERE session_key=%s", (session_key,))
                affected = cur.execute(f"DELETE FROM {self._table('sessions')} WHERE session_key=%s", (session_key,))
        if metadata and metadata.get("userId"):
            self.remove_conversation(str(metadata["userId"]), str(metadata.get("sessionId") or session_id), "api")
        return bool(affected or metadata)

    def clear_session_runtime(self, session_id: str) -> None:
        session_key = _stable_key(session_id)
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"DELETE FROM {self._table('messages')} WHERE session_key=%s", (session_key,))
                cur.execute(f"DELETE FROM {self._table('session_states')} WHERE session_key=%s", (session_key,))

    def clear_user_runtime(self, user_id: str) -> None:
        record = self.load_user_record(user_id)
        if not record:
            return
        self.upsert_user(
            user_id=user_id,
            safe_user_id=str(record.get("safeUserId") or ""),
            metadata=record.get("metadata"),
            psych_model={},
            user_state={},
            energy_progress={},
            crisis_history=[],
            psych_model_dir=record.get("psychModelDir"),
        )

    def delete_user(self, user_id: str) -> bool:
        user_key = _stable_key(user_id)
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"SELECT session_id FROM {self._table('sessions')} WHERE user_key=%s", (user_key,))
                session_ids = [row.get("session_id") for row in cur.fetchall() if row.get("session_id")]
                for session_id in session_ids:
                    session_key = _stable_key(session_id)
                    cur.execute(f"DELETE FROM {self._table('messages')} WHERE session_key=%s", (session_key,))
                    cur.execute(f"DELETE FROM {self._table('session_states')} WHERE session_key=%s", (session_key,))
                cur.execute(f"DELETE FROM {self._table('sessions')} WHERE user_key=%s", (user_key,))
                cur.execute(f"DELETE FROM {self._table('conversations')} WHERE user_key=%s", (user_key,))
                affected = cur.execute(f"DELETE FROM {self._table('users')} WHERE user_key=%s", (user_key,))
        return bool(affected)

    def save_psych_model(self, user_id: str, safe_user_id: str, psych_model: Dict[str, Any], psych_model_dir: str) -> None:
        self.upsert_user(
            user_id=user_id,
            safe_user_id=safe_user_id,
            psych_model=psych_model,
            user_state=psych_model.get("user_state") if isinstance(psych_model, dict) else None,
            psych_model_dir=psych_model_dir,
        )

    def load_psych_model(self, user_id: str) -> Optional[Dict[str, Any]]:
        record = self.load_user_record(user_id)
        model = record.get("psychModel") if record else None
        return model if isinstance(model, dict) and model else None

    def save_user_state(self, user_id: str, safe_user_id: str, state: Dict[str, Any], psych_model_dir: str) -> None:
        self.upsert_user(
            user_id=user_id,
            safe_user_id=safe_user_id,
            user_state=state,
            psych_model_dir=psych_model_dir,
        )

    def load_user_state(self, user_id: str) -> Optional[Dict[str, Any]]:
        record = self.load_user_record(user_id)
        state = record.get("userState") if record else None
        return state if isinstance(state, dict) and state else None

    def save_energy_progress(self, user_id: str, safe_user_id: str, data: Dict[str, Any], psych_model_dir: str = "") -> None:
        self.upsert_user(
            user_id=user_id,
            safe_user_id=safe_user_id,
            energy_progress=data,
            psych_model_dir=psych_model_dir,
        )

    def load_energy_progress(self, user_id: str) -> Optional[Dict[str, Any]]:
        record = self.load_user_record(user_id)
        data = record.get("energyProgress") if record else None
        return data if isinstance(data, dict) and data else None

    def save_crisis_history(self, user_id: str, safe_user_id: str, history: List[Dict[str, Any]], psych_model_dir: str = "") -> None:
        self.upsert_user(
            user_id=user_id,
            safe_user_id=safe_user_id,
            crisis_history=history,
            psych_model_dir=psych_model_dir,
        )

    def load_crisis_history(self, user_id: str) -> Optional[List[Dict[str, Any]]]:
        record = self.load_user_record(user_id)
        history = record.get("crisisHistory") if record else None
        return history if isinstance(history, list) else None

    def list_psych_models(self, max_users: int = 500) -> List[Dict[str, Any]]:
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"""
                    SELECT user_id, safe_user_id, updated_at, metadata_json, psych_model_json
                    FROM {self._table('users')}
                    WHERE psych_model_json IS NOT NULL AND psych_model_json <> ''
                    ORDER BY updated_at DESC
                    LIMIT %s
                    """,
                    (int(max_users),),
                )
                rows = cur.fetchall()
        models: List[Dict[str, Any]] = []
        for row in rows:
            model = _json_loads(row.get("psych_model_json"), {})
            if not isinstance(model, dict):
                continue
            profile = model.get("personalization_profile")
            if not isinstance(profile, dict):
                continue
            metadata = _json_loads(row.get("metadata_json"), {})
            models.append({
                "safeUserId": row.get("safe_user_id"),
                "updatedAt": model.get("updatedAt") or row.get("updated_at") or (metadata or {}).get("updatedAt"),
                "model": model,
                "profile": profile,
            })
        return models

    def save_cohort_model(self, payload: Dict[str, Any], dirty: bool = False) -> Dict[str, Any]:
        updated_at = str((payload or {}).get("updatedAt") or datetime.now().replace(microsecond=0).isoformat())
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"""
                    INSERT INTO {self._table("cohort_models")} (
                        model_key, payload_json, dirty, updated_at
                    ) VALUES (%s,%s,%s,%s)
                    ON DUPLICATE KEY UPDATE
                        payload_json=VALUES(payload_json),
                        dirty=VALUES(dirty),
                        updated_at=VALUES(updated_at)
                """, (
                    "default",
                    _json_dumps(payload or {}),
                    1 if dirty else 0,
                    updated_at,
                ))
        return payload

    def load_cohort_model(self) -> Optional[Dict[str, Any]]:
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"SELECT payload_json, updated_at FROM {self._table('cohort_models')} WHERE model_key=%s",
                    ("default",),
                )
                row = cur.fetchone()
        if not row:
            return None
        payload = _json_loads(row.get("payload_json"), None)
        if not isinstance(payload, dict):
            return None
        payload.setdefault("updatedAt", row.get("updated_at"))
        return payload

    def mark_cohort_model_dirty(self) -> None:
        now = datetime.now().replace(microsecond=0).isoformat()
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"""
                    INSERT INTO {self._table("cohort_models")} (
                        model_key, payload_json, dirty, updated_at
                    ) VALUES (%s,%s,%s,%s)
                    ON DUPLICATE KEY UPDATE
                        dirty=VALUES(dirty),
                        updated_at=VALUES(updated_at)
                """, ("default", _json_dumps({}), 1, now))

    def cohort_model_dirty(self) -> bool:
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"SELECT dirty FROM {self._table('cohort_models')} WHERE model_key=%s",
                    ("default",),
                )
                row = cur.fetchone()
        return bool(row and int(row.get("dirty") or 0))

    def clear_cohort_model_dirty(self) -> None:
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"UPDATE {self._table('cohort_models')} SET dirty=0 WHERE model_key=%s",
                    ("default",),
                )

    def save_prompt_registry(self, registry: Dict[str, Any]) -> Dict[str, Any]:
        updated_at = str(
            ((registry or {}).get("settings") or {}).get("updatedAt")
            or datetime.now().replace(microsecond=0).isoformat()
        )
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(f"""
                    INSERT INTO {self._table("prompt_registries")} (
                        registry_key, registry_json, updated_at
                    ) VALUES (%s,%s,%s)
                    ON DUPLICATE KEY UPDATE
                        registry_json=VALUES(registry_json),
                        updated_at=VALUES(updated_at)
                """, ("default", _json_dumps(registry or {}), updated_at))
        return registry

    def load_prompt_registry(self) -> Optional[Dict[str, Any]]:
        with self.connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"SELECT registry_json FROM {self._table('prompt_registries')} WHERE registry_key=%s",
                    ("default",),
                )
                row = cur.fetchone()
        if not row:
            return None
        registry = _json_loads(row.get("registry_json"), None)
        return registry if isinstance(registry, dict) else None
