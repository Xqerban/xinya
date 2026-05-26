"""会话级智能体状态快照。

本模块把对话历史、记忆中枢、用户状态和提示词运行时选择，
存进每个会话一个经过校验的状态文档。MySQL 模式写入数据库，
JSON 模式才落本地文件，用于 API 在进程重启后恢复智能体。
"""
from __future__ import annotations

import json
import os
from datetime import datetime
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, ConfigDict, Field

from xiaoya_agent.config import Config
from xiaoya_agent.database import database_storage_enabled, get_database_repository

STATE_FILENAME = "agent_state.json"


class AgentSessionState(BaseModel):
    model_config = ConfigDict(extra="ignore")

    session_id: str
    thread_id: str
    user_id: Optional[str] = None
    psych_model_dir: Optional[str] = None
    conversation_history: List[Dict[str, Any]] = Field(default_factory=list)
    memory_core: Optional[str] = None
    user_state: Dict[str, Any] = Field(default_factory=dict)
    prompt_profile: str = "warm_cbt"
    output_mode: str = "brief_support"
    system_prompt_override: Optional[str] = None
    extra_prompt_instructions: Optional[str] = None
    last_tool_trace: Optional[Dict[str, Any]] = None
    updated_at: str = Field(default_factory=lambda: datetime.now().replace(microsecond=0).isoformat())


def _state_path(data_dir: str) -> str:
    return os.path.join(data_dir, STATE_FILENAME)


def _read_json(path: str) -> Optional[Dict[str, Any]]:
    try:
        if not os.path.exists(path):
            return None
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, dict) else None
    except Exception:
        return None


def _session_id_from_data_dir(data_dir: str) -> Optional[str]:
    if database_storage_enabled():
        return None
    try:
        meta = _read_json(os.path.join(data_dir, "session_meta.json"))
        if isinstance(meta, dict) and meta.get("sessionId"):
            return str(meta["sessionId"])
    except Exception:
        pass
    return None


def load_session_state(data_dir: str, session_id: Optional[str] = None) -> Optional[AgentSessionState]:
    if not getattr(Config, "SESSION_STATE_ENABLED", True):
        return None
    if database_storage_enabled():
        resolved_session_id = session_id or _session_id_from_data_dir(data_dir)
        if resolved_session_id:
            data = get_database_repository().load_session_state(resolved_session_id)
            if data:
                try:
                    return AgentSessionState.model_validate(data)
                except Exception:
                    return None
        return None
    data = _read_json(_state_path(data_dir))
    if not data:
        return None
    try:
        return AgentSessionState.model_validate(data)
    except Exception:
        return None


def load_session_history(data_dir: str, session_id: Optional[str] = None) -> Optional[List[Dict[str, Any]]]:
    if database_storage_enabled():
        resolved_session_id = session_id or _session_id_from_data_dir(data_dir)
        if resolved_session_id:
            history = get_database_repository().load_session_history(resolved_session_id)
            if isinstance(history, list):
                return history
        return None
    state = load_session_state(data_dir, session_id=session_id)
    if not state:
        return None
    return state.conversation_history


def apply_session_state(agent: Any, state: Optional[AgentSessionState], restore_psych_model: bool = True) -> None:
    if not state:
        return
    if state.conversation_history:
        agent.conversation_history = list(state.conversation_history)
    if restore_psych_model:
        agent.memory_core = state.memory_core
        if state.user_state:
            agent.user_state.update(state.user_state)
    agent.prompt_profile = state.prompt_profile or agent.prompt_profile
    agent.output_mode = state.output_mode or agent.output_mode
    agent.system_prompt_override = state.system_prompt_override
    agent.extra_prompt_instructions = state.extra_prompt_instructions
    agent.last_tool_trace = state.last_tool_trace


def snapshot_session_state(session_id: str, thread_id: str, agent: Any) -> AgentSessionState:
    return AgentSessionState(
        session_id=session_id,
        thread_id=thread_id,
        user_id=getattr(agent, "user_id", None),
        psych_model_dir=getattr(agent, "psych_model_dir", None),
        conversation_history=list(getattr(agent, "conversation_history", []) or []),
        memory_core=getattr(agent, "memory_core", None),
        user_state=dict(getattr(agent, "user_state", {}) or {}),
        prompt_profile=str(getattr(agent, "prompt_profile", Config.PROMPT_PROFILE) or Config.PROMPT_PROFILE),
        output_mode=str(getattr(agent, "output_mode", Config.OUTPUT_MODE) or Config.OUTPUT_MODE),
        system_prompt_override=getattr(agent, "system_prompt_override", None),
        extra_prompt_instructions=getattr(agent, "extra_prompt_instructions", None),
        last_tool_trace=getattr(agent, "last_tool_trace", None),
    )


def save_session_state(session_id: str, thread_id: str, data_dir: str, agent: Any) -> AgentSessionState:
    state = snapshot_session_state(session_id, thread_id, agent)
    if database_storage_enabled():
        get_database_repository().save_session_state(state.model_dump())
        return state
    os.makedirs(data_dir, exist_ok=True)
    path = _state_path(data_dir)
    temp_path = f"{path}.tmp"
    with open(temp_path, "w", encoding="utf-8") as f:
        json.dump(state.model_dump(), f, ensure_ascii=False, indent=2)
    os.replace(temp_path, path)
    return state


def delete_session_state(data_dir: str) -> None:
    if database_storage_enabled():
        session_id = _session_id_from_data_dir(data_dir)
        if session_id:
            get_database_repository().clear_session_runtime(session_id)
        return
    path = _state_path(data_dir)
    if os.path.exists(path):
        os.remove(path)
