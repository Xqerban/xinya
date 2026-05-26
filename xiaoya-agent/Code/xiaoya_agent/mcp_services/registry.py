"""智能体使用的 MCP 风格确定性服务。

这些服务统一放在一个目录里，目的是让时间、日期等实时事实问题
不再依赖聊天模型猜测。当前实现是项目内部服务，但注册表结构
保留了后续改造成标准 MCP 服务的空间。
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Optional

try:
    from zoneinfo import ZoneInfo
except Exception:  # pragma: no cover - 旧版 Python 兜底
    ZoneInfo = None  # type: ignore

from xiaoya_agent.config import Config


WEEKDAY_NAMES = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]
TIME_DIRECT_TERMS = [
    "现在几点",
    "现在几点了",
    "几点了",
    "几点啦",
    "几点钟",
    "当前几点",
    "此刻几点",
    "现在几时",
    "现在时间",
    "当前时间",
    "此刻时间",
    "现在是什么时间",
    "今天几号",
    "今天多少号",
    "今天日期",
    "今天星期几",
    "现在星期几",
    "星期几",
    "今天几月几日",
]


def _normalize_text(text: str) -> str:
    return (text or "").strip().lower().replace(" ", "")


def _configured_timezone_name() -> str:
    return str(getattr(Config, "MCP_TIMEZONE", "Asia/Shanghai") or "Asia/Shanghai")


def _timezone_from_name(name: Optional[str]) -> timezone:
    timezone_name = (name or _configured_timezone_name()).strip()
    if timezone_name in {"Asia/Shanghai", "Asia/Chongqing", "Asia/Harbin", "Asia/Urumqi", "CST", "UTC+8"}:
        return timezone(timedelta(hours=8), name="Asia/Shanghai")
    if timezone_name.upper() in {"UTC", "Z"}:
        return timezone.utc
    if ZoneInfo is not None:
        try:
            return ZoneInfo(timezone_name)  # type: ignore[return-value]
        except Exception:
            pass
    return datetime.now().astimezone().tzinfo or timezone.utc


def should_use_time_service(text: str) -> bool:
    """判断用户是否在询问当前时间或日期。"""
    normalized = _normalize_text(text)
    if not normalized:
        return False

    if any(term in normalized for term in TIME_DIRECT_TERMS):
        return True

    time_anchors = ["现在", "当前", "此刻", "今天"]
    return (
        ("时间" in normalized or "日期" in normalized)
        and any(anchor in normalized for anchor in time_anchors)
    )


def current_time_service(
    *,
    now: Optional[datetime] = None,
    timezone_name: Optional[str] = None,
) -> Dict[str, Any]:
    """返回适合模型读取的当前本地时间结构化结果。"""
    tz_name = timezone_name or _configured_timezone_name()
    tz = _timezone_from_name(tz_name)
    current = now.astimezone(tz) if now and now.tzinfo else (now.replace(tzinfo=tz) if now else datetime.now(tz))
    weekday = WEEKDAY_NAMES[current.weekday()]
    date_text = f"{current.year}年{current.month}月{current.day}日"
    time_text = f"{current.hour:02d}:{current.minute:02d}"
    second_text = f"{current.hour:02d}:{current.minute:02d}:{current.second:02d}"
    timezone_text = tz_name if tz_name else str(current.tzinfo or "")
    answer = f"现在是{date_text}，{weekday}，{time_text}（{timezone_text}）。"
    return {
        "service": "current_time",
        "ok": True,
        "date": current.date().isoformat(),
        "time": time_text,
        "timeWithSeconds": second_text,
        "weekday": weekday,
        "timezone": timezone_text,
        "timezoneOffset": current.strftime("%z"),
        "iso": current.isoformat(),
        "answer": answer,
    }


def _build_mcp_context(result: Dict[str, Any]) -> str:
    services = result.get("services") or []
    if "current_time" not in services:
        return ""
    time_result = ((result.get("results") or {}).get("current_time") or {})
    if not time_result:
        return ""
    return (
        "[current_time]\n"
        f"date: {time_result.get('date')}\n"
        f"time: {time_result.get('time')}\n"
        f"weekday: {time_result.get('weekday')}\n"
        f"timezone: {time_result.get('timezone')}\n"
        f"iso: {time_result.get('iso')}"
    )


def should_use_mcp_services(text: str) -> bool:
    if not getattr(Config, "MCP_SERVICES_ENABLED", True):
        return False
    return should_use_time_service(text)


def invoke_mcp_services(text: str) -> Dict[str, Any]:
    """调用本轮用户输入相关的所有确定性服务。"""
    if not getattr(Config, "MCP_SERVICES_ENABLED", True):
        return {
            "enabled": False,
            "services": [],
            "results": {},
            "context": "",
            "reason": "MCP_SERVICES_ENABLED=false",
        }

    services = []
    results: Dict[str, Any] = {}
    if should_use_time_service(text):
        time_result = current_time_service()
        services.append("current_time")
        results["current_time"] = time_result

    output = {
        "enabled": True,
        "source": "mcp_services",
        "services": services,
        "results": results,
        "context": "",
        "reason": "ok" if services else "no_matching_service",
    }
    output["context"] = _build_mcp_context(output)
    return output
