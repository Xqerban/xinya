-- ============================================================
-- 手动执行脚本：创建业务专用数据库账号 xinya
-- 需要以 root 或有 CREATE USER 权限的账号执行
-- 执行一次即可，不由 Flyway 自动管理
-- ============================================================

CREATE USER IF NOT EXISTS 'xinya'@'localhost' IDENTIFIED BY 'xinya';
CREATE USER IF NOT EXISTS 'xinya'@'%' IDENTIFIED BY 'xinya';

GRANT ALL PRIVILEGES ON `xinya_dtx`.* TO 'xinya'@'localhost';
GRANT ALL PRIVILEGES ON `xinya_dtx`.* TO 'xinya'@'%';

FLUSH PRIVILEGES;
