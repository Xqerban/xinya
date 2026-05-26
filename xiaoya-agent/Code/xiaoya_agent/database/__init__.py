"""数据库存储入口。"""

from xiaoya_agent.database.repository import (
    database_storage_enabled,
    get_database_repository,
)

__all__ = ["database_storage_enabled", "get_database_repository"]
