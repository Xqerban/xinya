-- ================================================================
-- 心芽 DTX 数据库初始化脚本
-- 数据库：xinya_dtx
-- 字符集：utf8mb4
-- 执行前请先创建数据库和用户（见脚本末尾注释）
-- ================================================================

-- 确保使用正确的数据库
USE xinya_dtx;

-- ----------------------------------------------------------------
-- 1. 患者表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS patients (
    id           VARCHAR(36)  NOT NULL COMMENT '患者UUID',
    name         VARCHAR(100) NOT NULL COMMENT '姓名',
    stage        VARCHAR(20)  NOT NULL COMMENT '临床阶段: ADMISSION|PRETREATMENT|TRANSPLANT|REBUILD|DISCHARGE',
    psych_energy INT          NOT NULL DEFAULT 50  COMMENT '心理能量(0-100)',
    tree_level   INT          NOT NULL DEFAULT 1   COMMENT '希望之树等级(冗余字段，与hope_tree_progress同步)',
    admission_date DATE        NOT NULL COMMENT '入院日期',
    room_number  VARCHAR(20)           COMMENT '病房号',
    created_at   DATETIME              COMMENT '创建时间',
    updated_at   DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='患者信息表';

-- ----------------------------------------------------------------
-- 2. 对话记录表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversations (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    patient_id        VARCHAR(36)  NOT NULL COMMENT '患者ID',
    agent_type        VARCHAR(10)  NOT NULL COMMENT '智能体类型: psych|nurse',
    session_id        VARCHAR(36)  NOT NULL COMMENT '会话ID',
    message           TEXT                  COMMENT '消息内容',
    is_from_user      TINYINT(1)            COMMENT '是否来自用户: 1=用户 0=AI',
    psych_energy_delta INT         NOT NULL DEFAULT 0 COMMENT '本条消息触发的心理能量变化',
    crisis_alert      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否触发危机预警',
    created_at        DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_patient_session (patient_id, session_id),
    INDEX idx_patient_agent  (patient_id, agent_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话记录表';

-- ----------------------------------------------------------------
-- 3. PRO 打卡记录表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pro_records (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    patient_id     VARCHAR(36)  NOT NULL COMMENT '患者ID',
    record_date    DATE         NOT NULL COMMENT '打卡日期',
    question_id    VARCHAR(50)  NOT NULL COMMENT '题目ID',
    question_title VARCHAR(200)          COMMENT '题目标题',
    answer         VARCHAR(200) NOT NULL COMMENT '答案文本',
    answer_score   INT          NOT NULL DEFAULT 0 COMMENT '答案分值',
    created_at     DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_patient_date (patient_id, record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PRO症状打卡记录表';

-- ----------------------------------------------------------------
-- 4. 希望之树进度表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hope_tree_progress (
    id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    patient_id       VARCHAR(36) NOT NULL COMMENT '患者ID（唯一）',
    current_level    INT         NOT NULL DEFAULT 1   COMMENT '当前等级(1-7)',
    current_exp      INT         NOT NULL DEFAULT 0   COMMENT '当前等级已积累经验值',
    next_level_exp   INT         NOT NULL DEFAULT 100 COMMENT '升到下一级所需经验值(满级为0)',
    total_growth_days INT        NOT NULL DEFAULT 0   COMMENT '累计成长天数',
    last_growth_date DATETIME             COMMENT '最近一次获得经验的时间',
    updated_at       DATETIME             COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_patient_id (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='希望之树成长进度表';

-- ----------------------------------------------------------------
-- 5. 宣教内容表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS education_contents (
    id               VARCHAR(36)  NOT NULL COMMENT '内容UUID',
    title            VARCHAR(200) NOT NULL COMMENT '标题',
    category         VARCHAR(50)  NOT NULL COMMENT '分类',
    description      VARCHAR(500)          COMMENT '简介',
    content_type     VARCHAR(20)  NOT NULL COMMENT '内容类型: video|article',
    duration_seconds INT                   COMMENT '时长（秒，视频类有效）',
    thumbnail_url    VARCHAR(500)          COMMENT '封面图URL',
    media_url        VARCHAR(500)          COMMENT '媒体资源URL',
    tags             VARCHAR(500)          COMMENT '标签（逗号分隔）',
    sort_order       INT          NOT NULL DEFAULT 0 COMMENT '排序权重',
    is_active        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否上架',
    created_at       DATETIME              COMMENT '创建时间',
    updated_at       DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_category    (category),
    INDEX idx_sort_active (sort_order, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='护理宣教内容表';


-- ================================================================
-- 初始测试数据（可选，开发阶段使用）
-- ================================================================

INSERT IGNORE INTO patients (id, name, stage, psych_energy, tree_level, admission_date, room_number, created_at, updated_at) VALUES
('p-001', '张小明', 'ADMISSION',    60, 1, '2026-02-20', 'A101', NOW(), NOW()),
('p-002', '李小红', 'PRETREATMENT', 45, 2, '2026-02-15', 'A102', NOW(), NOW()),
('p-003', '王大力', 'TRANSPLANT',   70, 3, '2026-02-01', 'B201', NOW(), NOW());

INSERT IGNORE INTO hope_tree_progress (patient_id, current_level, current_exp, next_level_exp, total_growth_days, updated_at) VALUES
('p-001', 1,  30,  100, 3,  NOW()),
('p-002', 2,  80,  250, 12, NOW()),
('p-003', 3, 120,  450, 28, NOW());

INSERT IGNORE INTO education_contents (id, title, category, description, content_type, duration_seconds, sort_order, is_active, created_at, updated_at) VALUES
('ec-001', '移植前你需要了解的事', '预处理期', '详细介绍造血干细胞移植前的准备工作和注意事项', 'video', 480, 1, 1, NOW(), NOW()),
('ec-002', '无菌仓生活指南',       '移植期',   '在无菌仓内的日常护理要点和感染预防措施',         'article', NULL, 2, 1, NOW(), NOW()),
('ec-003', '出仓后的饮食管理',     '出仓期',   '移植后恢复期的营养饮食建议',                     'video',   360, 3, 1, NOW(), NOW());


-- ================================================================
-- 参考：首次部署时创建数据库和用户（用 root 执行）
-- ================================================================
-- CREATE DATABASE IF NOT EXISTS xinya_dtx
--   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--
-- CREATE USER IF NOT EXISTS 'xinya'@'localhost' IDENTIFIED BY 'xinya';
-- GRANT ALL PRIVILEGES ON xinya_dtx.* TO 'xinya'@'localhost';
-- FLUSH PRIVILEGES;
