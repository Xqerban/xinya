"""Dify 集成辅助函数。

核心智能体仍由 LangGraph 编排。在部署环境提供 Dify API Key 和数据集 ID 后，
Dify 可以接管外层工作流编排以及托管知识库检索。
"""
from __future__ import annotations

import logging
from typing import Any, Dict, List, Optional

import requests

from xiaoya_agent.config import Config

logger = logging.getLogger(__name__)


def dify_knowledge_configured() -> bool:
    backend = str(getattr(Config, "RAG_BACKEND", "auto") or "auto").lower()
    api_key = getattr(Config, "DIFY_KNOWLEDGE_API_KEY", "") or getattr(Config, "DIFY_API_KEY", "")
    return bool(
        getattr(Config, "DIFY_KNOWLEDGE_ENABLED", False)
        or backend == "dify"
    ) and bool(
        getattr(Config, "DIFY_KNOWLEDGE_BASE_ID", "")
        and api_key
    )


def should_use_dify_knowledge() -> bool:
    backend = str(getattr(Config, "RAG_BACKEND", "auto") or "auto").lower()
    return backend == "dify" or bool(getattr(Config, "DIFY_KNOWLEDGE_ENABLED", False))


def dify_replacement_status() -> Dict[str, Any]:
    """返回当前运行时已启用的 Dify 替换路径。"""
    backend = str(getattr(Config, "RAG_BACKEND", "auto") or "auto").lower()
    knowledge_requested = should_use_dify_knowledge()
    knowledge_configured = dify_knowledge_configured()
    if knowledge_configured:
        knowledge_status = "active"
    elif knowledge_requested:
        knowledge_status = "missing_config"
    else:
        knowledge_status = "disabled"

    return {
        "outerChatflow": {
            "status": "ready",
            "replacement": "Dify Chatflow/Workflow can replace the external chat UI and outer flow.",
            "xiaoyaEndpoint": "/v1/dify/chat",
            "openapiSchema": "/v1/dify/openapi.yaml",
        },
        "knowledgeBase": {
            "status": knowledge_status,
            "replacement": "Dify Knowledge Base is the only runtime RAG backend. File/ is reference material and is not indexed locally.",
            "backend": backend,
            "enabled": knowledge_requested,
            "configured": knowledge_configured,
            "datasetId": getattr(Config, "DIFY_KNOWLEDGE_BASE_ID", ""),
            "fallbackToLocal": False,
            "localFallbackSupported": False,
            "searchMethod": getattr(Config, "DIFY_KNOWLEDGE_SEARCH_METHOD", "keyword_search"),
        },
        "promptVariables": {
            "status": "ready",
            "replacement": "Dify can provide prompt variables and simple branch outputs through inputs/promptConfig.",
            "acceptedInputs": [
                "stage",
                "psychEnergy",
                "promptProfile",
                "outputMode",
                "extraInstructions",
                "systemPrompt",
            ],
        },
        "keptInPythonLangGraph": [
            "crisis_leveling_and_alerts",
            "user_psychological_model",
            "energy_accumulation_and_achievements",
            "session_and_user_data_integrity",
            "structured_output_validation",
            "LangGraph turn orchestration",
        ],
    }


class DifyKnowledgeClient:
    """用于调用 Dify 知识库检索的小型客户端。"""

    def __init__(
        self,
        base_url: Optional[str] = None,
        api_key: Optional[str] = None,
        dataset_id: Optional[str] = None,
        timeout: Optional[float] = None,
    ) -> None:
        self.base_url = (base_url or getattr(Config, "DIFY_API_BASE_URL", "") or "").rstrip("/")
        self.api_key = api_key or getattr(Config, "DIFY_KNOWLEDGE_API_KEY", "") or getattr(Config, "DIFY_API_KEY", "")
        self.dataset_id = dataset_id or getattr(Config, "DIFY_KNOWLEDGE_BASE_ID", "")
        self.timeout = float(timeout or getattr(Config, "DIFY_KNOWLEDGE_TIMEOUT_SECONDS", 8) or 8)

    @staticmethod
    def _response_status(response: requests.Response) -> int:
        try:
            return int(getattr(response, "status_code", 200) or 200)
        except (TypeError, ValueError):
            return 200

    @staticmethod
    def _response_preview(response: requests.Response, limit: int = 300) -> str:
        text = str(getattr(response, "text", "") or "").strip()
        if len(text) > limit:
            return text[:limit].rstrip() + "..."
        return text

    def _post_retrieve(self, url: str, payload: Dict[str, Any]) -> requests.Response:
        return requests.post(
            url,
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
            },
            json=payload,
            timeout=self.timeout,
        )

    def _raise_for_status_with_body(self, response: requests.Response) -> None:
        try:
            response.raise_for_status()
        except requests.HTTPError as exc:
            body = self._response_preview(response)
            if body:
                raise requests.HTTPError(
                    f"{exc}; Dify response: {body}",
                    response=response,
                ) from exc
            raise

    @staticmethod
    def _configured_search_method() -> str:
        return str(
            getattr(Config, "DIFY_KNOWLEDGE_SEARCH_METHOD", "keyword_search")
            or "keyword_search"
        ).strip().lower()

    def _retrieve_payloads(self, query: str, top_k: int) -> List[Dict[str, Any]]:
        configured_method = self._configured_search_method()
        if configured_method in {"", "auto"}:
            methods = ["keyword_search", "hybrid_search", "semantic_search"]
        else:
            methods = [configured_method]
            if configured_method != "keyword_search":
                methods.append("keyword_search")

        payloads: List[Dict[str, Any]] = []
        seen_methods = set()
        for method in methods:
            if method in seen_methods:
                continue
            seen_methods.add(method)
            payloads.append({
                "query": query,
                "retrieval_model": {
                    "search_method": method,
                    "reranking_enable": False,
                    "top_k": top_k,
                    "score_threshold_enabled": False,
                },
                "_xiaoya_retrieval_method": method,
            })
        payloads.append({
            "query": query,
            "_xiaoya_retrieval_method": "default",
        })
        return payloads

    def retrieve(self, query: str, top_k: int) -> Dict[str, Any]:
        if not self.base_url:
            raise ValueError("DIFY_API_BASE_URL 不能为空")
        if not self.api_key:
            raise ValueError("DIFY_KNOWLEDGE_API_KEY 不能为空")
        if not self.dataset_id:
            raise ValueError("DIFY_KNOWLEDGE_BASE_ID 不能为空")

        url = f"{self.base_url}/datasets/{self.dataset_id}/retrieve"
        top_k = int(top_k)
        payloads = self._retrieve_payloads(query, top_k=top_k)

        last_response: Optional[requests.Response] = None
        last_method = ""
        for index, payload in enumerate(payloads):
            method = str(payload.get("_xiaoya_retrieval_method") or "")
            request_payload = {
                key: value
                for key, value in payload.items()
                if not key.startswith("_xiaoya_")
            }
            response = self._post_retrieve(url, request_payload)
            last_response = response
            last_method = method
            if self._response_status(response) == 400 and index < len(payloads) - 1:
                logger.info(
                    "Dify knowledge retrieval payload rejected with 400; retrying next method. method=%s response=%s",
                    method,
                    self._response_preview(response),
                )
                continue
            break

        response = last_response
        if response is None:
            raise RuntimeError("Dify retrieve request was not sent")
        self._raise_for_status_with_body(response)
        data = response.json()
        if not isinstance(data, dict):
            data = {}
        data["_xiaoya_retrieval_method"] = last_method or self._configured_search_method()
        return data


def _record_document_name(record: Dict[str, Any], segment: Dict[str, Any]) -> str:
    document = record.get("document")
    if not isinstance(document, dict):
        document = segment.get("document") if isinstance(segment.get("document"), dict) else {}
    return str(
        document.get("name")
        or document.get("filename")
        or document.get("title")
        or segment.get("document_id")
        or record.get("document_id")
        or "dify_knowledge"
    )


def normalize_dify_retrieval(data: Dict[str, Any], query: str, top_k: int, max_chars: int) -> Dict[str, Any]:
    effective_method = str(
        data.get("_xiaoya_retrieval_method")
        or getattr(Config, "DIFY_KNOWLEDGE_SEARCH_METHOD", "keyword_search")
        or "keyword_search"
    )
    records = data.get("records")
    if not isinstance(records, list):
        records = data.get("data") if isinstance(data.get("data"), list) else []

    matches: List[Dict[str, Any]] = []
    for index, record in enumerate(records[:top_k], start=1):
        if not isinstance(record, dict):
            continue
        segment = record.get("segment") if isinstance(record.get("segment"), dict) else record
        text = str(
            segment.get("content")
            or segment.get("text")
            or segment.get("answer")
            or ""
        ).strip()
        if not text:
            continue
        score = record.get("score")
        if score is None:
            score = segment.get("score")
        try:
            numeric_score = round(float(score), 4)
        except (TypeError, ValueError):
            numeric_score = 0.0
        source = _record_document_name(record, segment)
        matches.append({
            "source": source,
            "chunkId": str(segment.get("id") or record.get("id") or f"dify-{index}"),
            "score": numeric_score,
            "text": text,
            "metadata": {
                "provider": "dify",
                "datasetId": getattr(Config, "DIFY_KNOWLEDGE_BASE_ID", ""),
                "documentId": segment.get("document_id") or record.get("document_id"),
                "rawScore": score,
            },
        })

    context = _format_dify_context(matches, max_chars=max_chars)
    semantic_enabled = effective_method in {"semantic_search", "hybrid_search"}
    return {
        "enabled": True,
        "matches": matches,
        "context": context,
        "reason": "ok" if matches else "no_relevant_chunks",
        "retrievalBackend": "dify",
        "scoringMode": effective_method,
        "effectiveSearchMethod": effective_method,
        "semanticEnabled": semantic_enabled,
        "embeddingModel": "managed_by_dify" if semantic_enabled else "keyword_index",
        "difyDatasetId": getattr(Config, "DIFY_KNOWLEDGE_BASE_ID", ""),
        "indexedChunkCount": None,
        "sourceDir": None,
        "fallbackUsed": False,
        "errors": [],
        "query": query,
    }


def _format_dify_context(matches: List[Dict[str, Any]], max_chars: int) -> str:
    lines: List[str] = []
    used = 0
    for idx, match in enumerate(matches, start=1):
        header = f"[source {idx} | {match['source']} | score={match['score']}]"
        text = match["text"]
        remaining = max_chars - used - len(header) - 2
        if remaining <= 80:
            break
        if len(text) > remaining:
            text = text[:remaining].rstrip("，。；,.; ") + "..."
        block = f"{header}\n{text}"
        lines.append(block)
        used += len(block)
    return "\n\n".join(lines)


def retrieve_dify_knowledge(query: str, top_k: int) -> Dict[str, Any]:
    client = DifyKnowledgeClient()
    raw = client.retrieve(query=query, top_k=top_k)
    return normalize_dify_retrieval(
        raw,
        query=query,
        top_k=top_k,
        max_chars=int(getattr(Config, "RAG_MAX_CONTEXT_CHARS", 900) or 900),
    )
