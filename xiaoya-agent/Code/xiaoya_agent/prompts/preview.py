"""基于 LangChain 提示词模板的提示词预览工具。"""
from __future__ import annotations

from typing import Any, Dict, List

try:
    from langchain_core.prompts import ChatPromptTemplate
except Exception:  # pragma: no cover - langchain-core 是预期依赖，但仍允许缺失。
    ChatPromptTemplate = None  # type: ignore


def render_prompt_messages(runtime: Dict[str, Any], message: str) -> List[Dict[str, str]]:
    """渲染提示词 A/B 预览使用的主提示词结构。"""
    system_prompt = str(runtime.get("system_prompt", "") or "")
    realtime_instruction = str(runtime.get("realtime_instruction", "") or "")
    user_message = str(message or "")

    if ChatPromptTemplate is not None:
        template = ChatPromptTemplate.from_messages([
            ("system", "{system_prompt}"),
            ("system", "{realtime_instruction}"),
            ("user", "{message}"),
        ])
        rendered = template.invoke({
            "system_prompt": system_prompt,
            "realtime_instruction": realtime_instruction,
            "message": user_message,
        }).to_messages()
        return [
            {"role": getattr(item, "type", "user").replace("human", "user"), "content": item.content}
            for item in rendered
        ]

    return [
        {"role": "system", "content": system_prompt},
        {"role": "system", "content": realtime_instruction},
        {"role": "user", "content": user_message},
    ]
