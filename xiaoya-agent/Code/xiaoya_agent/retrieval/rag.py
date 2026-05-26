"""仅使用 Dify 的运行时知识检索。

在本项目中，RAG 指 Dify 知识库检索。本地 ``File/`` 目录只作为项目参考资料，
不会被运行时检索索引，也不会作为 Dify 检索失败后的兜底来源。
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple

from xiaoya_agent.config import Config
from xiaoya_agent.integrations.dify import (
    dify_knowledge_configured,
    retrieve_dify_knowledge,
    should_use_dify_knowledge,
)

logger = logging.getLogger(__name__)


@dataclass
class KnowledgeIndex:
    """兼容旧导入路径的空壳对象。

    运行时 RAG 已移除本地 File/ 索引。这里故意保持为空，
    避免调用方误把项目文件当作 RAG 内容使用。
    """

    source_dir: str = ""
    signature: Tuple[Tuple[str, int, int], ...] = field(default_factory=tuple)
    chunks: List[Any] = field(default_factory=list)
    errors: List[str] = field(default_factory=lambda: ["local_file_rag_removed"])
    backend: str = "dify"
    semantic_enabled: bool = False
    embedding_model: Optional[str] = None


def reset_rag_index_cache() -> None:
    """兼容旧接口的空操作；当前已经没有本地 RAG 索引需要清理。"""
    return None


def get_knowledge_index(source_dir: Optional[str] = None) -> KnowledgeIndex:
    """返回一个空的兼容索引对象。

    ``source_dir`` 仅用于兼容旧代码路径，不会被读取、扫描或索引。
    """
    return KnowledgeIndex(source_dir=str(source_dir or ""))


def _base_result() -> Dict[str, Any]:
    return {
        "enabled": bool(getattr(Config, "RAG_ENABLED", True)),
        "matches": [],
        "context": "",
        "retrievalBackend": "dify",
        "fallbackUsed": False,
        "sourceDir": None,
        "indexedChunkCount": None,
        "errors": [],
    }


def retrieve_knowledge(query: str, top_k: Optional[int] = None) -> Dict[str, Any]:
    """只从 Dify 知识库检索参考片段。"""
    result = _base_result()
    if not getattr(Config, "RAG_ENABLED", True):
        return {**result, "reason": "RAG_ENABLED=false"}

    query = (query or "").strip()
    if not query:
        return {**result, "reason": "query_too_short"}

    limit = min(10, max(1, int(top_k or getattr(Config, "RAG_TOP_K", 3) or 3)))
    if not should_use_dify_knowledge():
        return {
            **result,
            "reason": "dify_disabled",
            "errors": [
                "Runtime RAG is Dify-only. Enable RAG_BACKEND=dify or DIFY_KNOWLEDGE_ENABLED=true.",
            ],
        }

    if not dify_knowledge_configured():
        return {
            **result,
            "reason": "dify_not_configured",
            "errors": [
                "Please configure DIFY_KNOWLEDGE_BASE_ID and DIFY_KNOWLEDGE_API_KEY. File/ is not used as a RAG fallback.",
            ],
        }

    try:
        retrieved = retrieve_dify_knowledge(query, top_k=limit)
        retrieved["fallbackUsed"] = False
        return retrieved
    except Exception as exc:
        logger.warning("Dify knowledge retrieval failed: %s", exc)
        return {
            **result,
            "reason": "dify_retrieval_failed",
            "errors": [str(exc)],
        }
