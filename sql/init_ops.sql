-- ================================================================
-- 心芽 DTx 运维平台数据库初始化脚本
-- 数据库：xinya_ops
-- 字符集：utf8mb4
-- 版本：v1.0  2026-03-02
-- 执行顺序：先执行 init.sql（临床库），再执行本脚本
-- ================================================================

-- 创建数据库（若不存在）
CREATE DATABASE IF NOT EXISTS xinya_ops
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 创建专用账号
CREATE USER IF NOT EXISTS 'xinya_ops'@'localhost' IDENTIFIED BY 'xinya_ops';
GRANT ALL PRIVILEGES ON xinya_ops.* TO 'xinya_ops'@'localhost';
FLUSH PRIVILEGES;

USE xinya_ops;

-- ================================================================
-- 一、运维用户表
-- ================================================================
CREATE TABLE IF NOT EXISTS op_users (
    id                       VARCHAR(36)  NOT NULL COMMENT '用户UUID',
    username                 VARCHAR(100) NOT NULL COMMENT '登录用户名',
    password_hash            VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
    display_name             VARCHAR(100) NOT NULL COMMENT '显示姓名',
    role                     VARCHAR(20)  NOT NULL COMMENT '角色: ADMIN|NURSE|DOCTOR',
    phone                    VARCHAR(20)           COMMENT '手机号（唯一，可为空）',
    refresh_token            VARCHAR(255)          COMMENT '刷新Token',
    refresh_token_expires_at DATETIME              COMMENT '刷新Token过期时间',
    enabled                  TINYINT(1)   NOT NULL DEFAULT 1  COMMENT '是否启用',
    last_login_at            DATETIME              COMMENT '最近登录时间',
    created_at               DATETIME              COMMENT '创建时间',
    updated_at               DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='运维平台用户表（ADMIN/NURSE/DOCTOR）';

-- ================================================================
-- 二、宣教内容主控表（与 xinya_dtx.education_contents 同结构，ops 端为主控）
-- ================================================================
CREATE TABLE IF NOT EXISTS education_contents (
    id                 VARCHAR(36)  NOT NULL COMMENT '内容ID，格式 ec-xxxxxxxx',
    title              VARCHAR(200) NOT NULL COMMENT '标题',
    stage              VARCHAR(20)           COMMENT '适用临床阶段，NULL=全阶段',
    category           VARCHAR(50)  NOT NULL COMMENT '分类（如 移植护理/心理调适）',
    description        VARCHAR(500)          COMMENT '简介',
    content_type       VARCHAR(20)  NOT NULL COMMENT '类型: video|article',
    duration_seconds   INT                   COMMENT '时长（秒）',
    thumbnail_url      VARCHAR(500)          COMMENT '封面图URL',
    media_url          VARCHAR(500)          COMMENT '媒体资源URL',
    tags               VARCHAR(500)          COMMENT '标签（逗号分隔）',
    sort_order         INT          NOT NULL DEFAULT 0   COMMENT '排序权重（升序）',
    is_active          TINYINT(1)   NOT NULL DEFAULT 1   COMMENT '是否上架',
    synced_to_clinical TINYINT(1)   NOT NULL DEFAULT 0   COMMENT '是否已同步到临床端',
    created_by         VARCHAR(36)           COMMENT '创建人ID',
    created_at         DATETIME              COMMENT '创建时间',
    updated_at         DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_stage (stage),
    INDEX idx_category (category),
    INDEX idx_content_type (content_type),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='宣教内容主控表（运维端维护，发布后同步到临床端）';

-- ================================================================
-- 三、危机关键词主控表
-- ================================================================
CREATE TABLE IF NOT EXISTS crisis_keywords (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    keyword      VARCHAR(100) NOT NULL COMMENT '危机关键词',
    crisis_level VARCHAR(20)  NOT NULL COMMENT '危机等级: warning|critical',
    is_active    TINYINT(1)   NOT NULL DEFAULT 1  COMMENT '是否启用',
    created_by   VARCHAR(36)           COMMENT '创建人ID',
    created_at   DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_keyword (keyword),
    INDEX idx_crisis_level (crisis_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='危机关键词主控表（AI危机识别词库，变更后同步到临床端）';

-- ================================================================
-- 四、PRO 问卷题目主控表
-- ================================================================
CREATE TABLE IF NOT EXISTS pro_questions (
    id          VARCHAR(50)  NOT NULL COMMENT '题目ID，如 q_nausea',
    stage       VARCHAR(20)  NOT NULL COMMENT '适用临床阶段，ALL=全阶段',
    title       VARCHAR(200) NOT NULL COMMENT '题目标题',
    type        VARCHAR(30)  NOT NULL COMMENT '题目类型: single_choice|scale|multi_choice',
    options     TEXT                  COMMENT '选项 JSON（选择题用）',
    scale_min   INT                   COMMENT '量表最小值',
    scale_max   INT                   COMMENT '量表最大值',
    min_label   VARCHAR(50)           COMMENT '量表最小值标签',
    max_label   VARCHAR(50)           COMMENT '量表最大值标签',
    symptom_key VARCHAR(50)           COMMENT '症状键名（用于趋势统计）',
    sort_order  INT          NOT NULL DEFAULT 0  COMMENT '排序',
    is_active   TINYINT(1)   NOT NULL DEFAULT 1  COMMENT '是否启用',
    created_at  DATETIME              COMMENT '创建时间',
    updated_at  DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_stage (stage),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='PRO问卷题目主控表（变更后同步到临床端）';

-- ================================================================
-- 五、操作审计日志表
-- ================================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    operator_id   VARCHAR(36)           COMMENT '操作人ID',
    operator_name VARCHAR(100)          COMMENT '操作人姓名',
    action        VARCHAR(50)  NOT NULL COMMENT '操作类型',
    target_type   VARCHAR(50)           COMMENT '目标实体类型: user|content|keyword|question',
    target_id     VARCHAR(100)          COMMENT '目标实体ID',
    detail        TEXT                  COMMENT '操作详情（JSON）',
    ip_address    VARCHAR(50)           COMMENT '操作来源IP',
    created_at    DATETIME              COMMENT '操作时间',
    PRIMARY KEY (id),
    INDEX idx_operator (operator_id),
    INDEX idx_action (action),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='运维操作审计日志';

-- ================================================================
-- 初始数据
-- ================================================================

-- ----------------------------------------------------------------
-- 初始运维人员账号
-- 密码均为 Xinya@2024（BCrypt加密，强度10）
-- 生产环境部署后请立即修改默认密码
-- ----------------------------------------------------------------

-- 超级管理员
INSERT IGNORE INTO op_users (id, username, password_hash, display_name, role, phone, enabled, created_at, updated_at)
VALUES (
    'ops-admin-00000001',
    'admin',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoO7Kh5HxBe9cTrqmRBKA7qHJT/y.JJvKi',
    '超级管理员',
    'ADMIN',
    NULL,
    1,
    NOW(), NOW()
);

-- 运维专员 A
INSERT IGNORE INTO op_users (id, username, password_hash, display_name, role, phone, enabled, created_at, updated_at)
VALUES (
    'ops-admin-00000002',
    'ops_zhang',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoO7Kh5HxBe9cTrqmRBKA7qHJT/y.JJvKi',
    '张运维',
    'ADMIN',
    '13800000001',
    1,
    NOW(), NOW()
);

-- 内容运营专员
INSERT IGNORE INTO op_users (id, username, password_hash, display_name, role, phone, enabled, created_at, updated_at)
VALUES (
    'ops-admin-00000003',
    'content_li',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoO7Kh5HxBe9cTrqmRBKA7qHJT/y.JJvKi',
    '李内容',
    'ADMIN',
    '13800000002',
    1,
    NOW(), NOW()
);

-- ----------------------------------------------------------------
-- 初始危机关键词（与临床端 init.sql 保持一致，运维端为主控）
-- ----------------------------------------------------------------
INSERT IGNORE INTO crisis_keywords (keyword, crisis_level, is_active, created_by, created_at)
VALUES
    ('想死',        'critical', 1, 'ops-admin-00000001', NOW()),
    ('不想活',      'critical', 1, 'ops-admin-00000001', NOW()),
    ('活不下去',    'critical', 1, 'ops-admin-00000001', NOW()),
    ('自杀',        'critical', 1, 'ops-admin-00000001', NOW()),
    ('结束生命',    'critical', 1, 'ops-admin-00000001', NOW()),
    ('无法承受',    'warning',  1, 'ops-admin-00000001', NOW()),
    ('绝望',        'warning',  1, 'ops-admin-00000001', NOW()),
    ('没有希望',    'warning',  1, 'ops-admin-00000001', NOW()),
    ('放弃治疗',    'warning',  1, 'ops-admin-00000001', NOW()),
    ('受不了',      'warning',  1, 'ops-admin-00000001', NOW()),
    ('痛不欲生',    'critical', 1, 'ops-admin-00000001', NOW()),
    ('活着没意思',  'critical', 1, 'ops-admin-00000001', NOW()),
    ('很绝望',      'warning',  1, 'ops-admin-00000001', NOW()),
    ('撑不住了',    'warning',  1, 'ops-admin-00000001', NOW()),
    ('太痛苦了',    'warning',  1, 'ops-admin-00000001', NOW());

-- ----------------------------------------------------------------
-- 初始 PRO 问卷题目（与临床端 init.sql 保持一致，运维端为主控）
-- ----------------------------------------------------------------

-- 全阶段通用题目
INSERT IGNORE INTO pro_questions (id, stage, title, type, scale_min, scale_max, min_label, max_label, symptom_key, sort_order, is_active, created_at, updated_at)
VALUES
('q_anxiety',       'ALL',        '您今天感到焦虑或紧张吗？',   'scale', 0, 10, '完全没有', '极度严重', 'anxiety',      10, 1, NOW(), NOW()),
('q_fatigue',       'ALL',        '您今天感到疲乏或无力吗？',   'scale', 0, 10, '完全没有', '极度严重', 'fatigue',      20, 1, NOW(), NOW()),
('q_pain',          'ALL',        '您今天有疼痛感吗？',         'scale', 0, 10, '完全没有', '极度严重', 'pain',         30, 1, NOW(), NOW()),
('q_insomnia',      'ALL',        '您昨晚睡眠质量如何？',       'scale', 0, 10, '非常好',   '非常差',   'insomnia',     40, 1, NOW(), NOW()),
('q_mood',          'ALL',        '您今天的整体心情如何？',     'scale', 0, 10, '非常差',   '非常好',   NULL,           50, 1, NOW(), NOW());

-- 预处理期题目
INSERT IGNORE INTO pro_questions (id, stage, title, type, scale_min, scale_max, min_label, max_label, symptom_key, sort_order, is_active, created_at, updated_at)
VALUES
('q_nausea',        'PRETREATMENT', '您今天有恶心或呕吐感吗？', 'scale', 0, 10, '完全没有', '极度严重', 'nausea',       10, 1, NOW(), NOW()),
('q_appetite',      'PRETREATMENT', '您今天的食欲情况如何？',   'scale', 0, 10, '完全没有', '食欲很好', 'appetite_loss', 20, 1, NOW(), NOW()),
('q_oral',          'PRETREATMENT', '您口腔有疼痛或溃疡感吗？', 'scale', 0, 10, '完全没有', '极度严重', 'oral_mucositis', 30, 1, NOW(), NOW()),
('q_diarrhea',      'PRETREATMENT', '您今天有腹泻症状吗？',     'scale', 0, 10, '完全没有', '极度严重', 'diarrhea',     40, 1, NOW(), NOW());

-- 移植期题目
INSERT IGNORE INTO pro_questions (id, stage, title, type, scale_min, scale_max, min_label, max_label, symptom_key, sort_order, is_active, created_at, updated_at)
VALUES
('q_fever',         'TRANSPLANT', '您今天有发热感觉吗？',       'scale', 0, 10, '完全没有', '极度严重', 'fever',        10, 1, NOW(), NOW()),
('q_skin',          'TRANSPLANT', '您今天皮肤有瘙痒或皮疹吗？', 'scale', 0, 10, '完全没有', '极度严重', 'skin_rash',    20, 1, NOW(), NOW());

-- 重建期题目
INSERT IGNORE INTO pro_questions (id, stage, title, type, scale_min, scale_max, min_label, max_label, symptom_key, sort_order, is_active, created_at, updated_at)
VALUES
('q_activity',      'REBUILD', '您今天的活动能力如何？',         'scale', 0, 10, '完全卧床', '完全正常', NULL,           10, 1, NOW(), NOW()),
('q_confidence',    'REBUILD', '您对康复的信心程度如何？',       'scale', 0, 10, '毫无信心', '充满信心', NULL,           20, 1, NOW(), NOW());

-- ================================================================
-- 执行完成提示
-- ================================================================
SELECT '✅ xinya_ops 数据库初始化完成' AS status;
SELECT CONCAT('管理员账号数量：', COUNT(*)) AS info FROM op_users WHERE role = 'ADMIN';
SELECT CONCAT('危机关键词数量：', COUNT(*)) AS info FROM crisis_keywords WHERE is_active = 1;
SELECT CONCAT('PRO题目数量：',   COUNT(*)) AS info FROM pro_questions   WHERE is_active = 1;

-- ================================================================
-- 附：密码对照表（生产环境部署完成后请删除此注释）
-- ================================================================
-- 初始密码：Xinya@2024
-- BCrypt hash（强度10）：$2a$10$7EqJtq98hPqEX7fNZaFWoO7Kh5HxBe9cTrqmRBKA7qHJT/y.JJvKi
--
-- 账号清单：
--   admin      / Xinya@2024  → 超级管理员（无手机号）
--   ops_zhang  / Xinya@2024  → 运维专员 A（13800000001）
--   content_li / Xinya@2024  → 内容运营专员（13800000002）
-- ================================================================
