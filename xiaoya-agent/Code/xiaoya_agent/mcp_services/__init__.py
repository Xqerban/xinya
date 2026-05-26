"""MCP 风格确定性服务的统一入口。"""

from xiaoya_agent.mcp_services.registry import (
    current_time_service,
    invoke_mcp_services,
    should_use_mcp_services,
    should_use_time_service,
)

__all__ = [
    "current_time_service",
    "invoke_mcp_services",
    "should_use_mcp_services",
    "should_use_time_service",
]
