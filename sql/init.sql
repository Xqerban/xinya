-- ================================================================
-- 心芽 DTx 数据库初始化脚本
-- 数据库：xinya_dtx
-- 字符集：utf8mb4
-- 版本：v2.0  2026-03-02
-- 执行前请先创建数据库和用户（见脚本末尾注释）
-- ================================================================

USE xinya_dtx;

-- ================================================================
-- 模块一：认证与用户
-- ================================================================

-- ----------------------------------------------------------------
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              VARCHAR(36)  NOT NULL COMMENT '用户UUID',
    username        VARCHAR(100) NOT NULL COMMENT '登录用户名',
    password_hash   VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
    display_name    VARCHAR(100) NOT NULL COMMENT '显示姓名',
    role            VARCHAR(20)  NOT NULL COMMENT '角色: NURSE|DOCTOR|ADMIN',
    phone           VARCHAR(20)           COMMENT '手机号（医护/运维登录用，唯一）',
    refresh_token   VARCHAR(255)          COMMENT '刷新Token',
    refresh_token_expires_at DATETIME     COMMENT '刷新Token过期时间',
    enabled         TINYINT(1)   NOT NULL DEFAULT 1  COMMENT '是否启用',
    last_login_at   DATETIME              COMMENT '最近登录时间',
    created_at      DATETIME              COMMENT '创建时间',
    updated_at      DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表（医护/管理员）';

-- ----------------------------------------------------------------
-- 2. 患者表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS patients (
    id               VARCHAR(36)  NOT NULL COMMENT '患者UUID',
    name             VARCHAR(100) NOT NULL COMMENT '姓名',
    age              INT                   COMMENT '年龄',
    gender           VARCHAR(10)           COMMENT '性别: MALE|FEMALE',
    diagnosis        VARCHAR(200)          COMMENT '诊断信息',
    stage            VARCHAR(20)  NOT NULL DEFAULT 'ADMISSION' COMMENT '当前临床阶段: ADMISSION|PRETREATMENT|TRANSPLANT|REBUILD|DISCHARGE',
    stage_start_date DATE         NOT NULL COMMENT '当前阶段开始日期（计算daysInStage用）',
    psych_energy     INT          NOT NULL DEFAULT 50  COMMENT '心理能量(0-100)',
    tree_level       INT          NOT NULL DEFAULT 1   COMMENT '希望之树等级(冗余,与hope_tree_progress同步)',
    admission_date   DATE         NOT NULL COMMENT '入院日期',
    room_number      VARCHAR(20)           COMMENT '病房号',
    created_at       DATETIME              COMMENT '创建时间',
    updated_at       DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_stage (stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='患者信息表';

-- ----------------------------------------------------------------
-- 3. 机器人设备绑定表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS robot_devices (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    device_id         VARCHAR(100) NOT NULL COMMENT '机器人设备序列号',
    patient_id        VARCHAR(36)  NOT NULL COMMENT '绑定的患者ID',
    device_token_hash VARCHAR(255)          COMMENT '设备Token的hash（用于失效判断）',
    token_expires_at  DATETIME              COMMENT 'Token过期时间',
    online_status     VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE' COMMENT '在线状态: ONLINE|OFFLINE',
    last_heartbeat_at DATETIME              COMMENT '最近一次心跳时间',
    network_status    VARCHAR(20)           COMMENT '网络状态: WIFI|4G|OFFLINE',
    battery_level     INT                   COMMENT '电量百分比',
    app_version       VARCHAR(50)           COMMENT '应用版本',
    created_at        DATETIME              COMMENT '创建时间',
    updated_at        DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_id (device_id),
    INDEX idx_patient_id (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器人设备绑定表';

-- ----------------------------------------------------------------
-- 4. 机器人绑定码表（5分钟有效，护士端生成）
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS robot_bind_codes (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    patient_id  VARCHAR(36) NOT NULL COMMENT '患者ID',
    bind_code   VARCHAR(10) NOT NULL COMMENT '6位绑定码',
    created_by  VARCHAR(36)          COMMENT '生成人（护士用户ID）',
    expires_at  DATETIME    NOT NULL COMMENT '过期时间',
    used        TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否已使用',
    created_at  DATETIME             COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_patient_code (patient_id, bind_code),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器人设备绑定码表';


-- ================================================================
-- 模块三：临床路径
-- ================================================================

-- ----------------------------------------------------------------
-- 5. 临床阶段流转历史表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS clinical_stage_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    patient_id      VARCHAR(36)  NOT NULL COMMENT '患者ID',
    from_stage      VARCHAR(20)           COMMENT '来源阶段（首次入仓时为null）',
    to_stage        VARCHAR(20)  NOT NULL COMMENT '目标阶段',
    transition_date DATE         NOT NULL COMMENT '流转日期',
    days_in_stage   INT          NOT NULL DEFAULT 0 COMMENT '在来源阶段的天数',
    operator_id     VARCHAR(36)           COMMENT '操作人用户ID',
    operator_name   VARCHAR(100)          COMMENT '操作人姓名（冗余）',
    operator_note   VARCHAR(500)          COMMENT '操作备注',
    created_at      DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_patient_id (patient_id),
    INDEX idx_transition_date (patient_id, transition_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='临床阶段流转历史表';


-- ================================================================
-- 模块四：智能体对话
-- ================================================================

-- ----------------------------------------------------------------
-- 6. 对话记录表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversations (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    patient_id          VARCHAR(36)  NOT NULL COMMENT '患者ID',
    agent_type          VARCHAR(10)  NOT NULL COMMENT '智能体类型: psych|nurse',
    session_id          VARCHAR(36)  NOT NULL COMMENT '会话ID',
    message             TEXT                  COMMENT '消息内容',
    is_from_user        TINYINT(1)   NOT NULL COMMENT '是否来自用户: 1=用户 0=AI',
    psych_energy_delta  INT          NOT NULL DEFAULT 0  COMMENT '本条消息触发的心理能量变化',
    hope_tree_exp_delta INT          NOT NULL DEFAULT 0  COMMENT '本条消息触发的希望之树经验值',
    crisis_alert        TINYINT(1)   NOT NULL DEFAULT 0  COMMENT '是否触发危机预警',
    crisis_level        VARCHAR(20)           COMMENT '危机等级: none|watch|warning|critical',
    crisis_keywords     VARCHAR(500)          COMMENT '命中的危机关键词(逗号分隔)',
    emotion_signals     VARCHAR(500)          COMMENT '检测到的情绪信号标签(逗号分隔)',
    client_timestamp    BIGINT                COMMENT '客户端消息时间戳（离线补传）',
    created_at          DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_patient_session (patient_id, session_id),
    INDEX idx_patient_agent   (patient_id, agent_type),
    INDEX idx_created_at      (patient_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话记录表';


-- ================================================================
-- 模块五：PRO 每日打卡
-- ================================================================

-- ----------------------------------------------------------------
-- 7. PRO 问卷题目配置表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pro_questions (
    id          VARCHAR(50)  NOT NULL COMMENT '题目ID，如 q_nausea',
    stage       VARCHAR(20)  NOT NULL COMMENT '适用临床阶段（ALL=全阶段）',
    title       VARCHAR(200) NOT NULL COMMENT '题目标题',
    type        VARCHAR(30)  NOT NULL COMMENT '题目类型: single_choice|scale|multi_choice',
    options     TEXT                  COMMENT '选项JSON（single_choice用）',
    scale_min   INT                   COMMENT '量表最小值（scale用）',
    scale_max   INT                   COMMENT '量表最大值（scale用）',
    min_label   VARCHAR(50)           COMMENT '量表最小值标签',
    max_label   VARCHAR(50)           COMMENT '量表最大值标签',
    symptom_key VARCHAR(50)           COMMENT '关联症状Key（用于症状触发判断）',
    sort_order  INT          NOT NULL DEFAULT 0  COMMENT '排序',
    is_active   TINYINT(1)   NOT NULL DEFAULT 1  COMMENT '是否启用',
    created_at  DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_stage_active (stage, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PRO问卷题目配置表';

-- ----------------------------------------------------------------
-- 8. PRO 打卡记录表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pro_records (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    patient_id       VARCHAR(36)  NOT NULL COMMENT '患者ID',
    record_date      DATE         NOT NULL COMMENT '打卡日期',
    question_id      VARCHAR(50)  NOT NULL COMMENT '题目ID',
    question_title   VARCHAR(200)          COMMENT '题目标题（冗余，防止题目修改影响历史）',
    answer           VARCHAR(200) NOT NULL COMMENT '答案文本',
    answer_score     INT          NOT NULL DEFAULT 0 COMMENT '答案分值',
    symptom_key      VARCHAR(50)           COMMENT '关联症状Key',
    client_timestamp BIGINT                COMMENT '客户端时间戳（离线补传用）',
    created_at       DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_patient_date_question (patient_id, record_date, question_id),
    INDEX idx_patient_date (patient_id, record_date),
    INDEX idx_symptom_key  (patient_id, symptom_key, record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PRO症状打卡记录表';


-- ================================================================
-- 模块六：希望之树
-- ================================================================

-- ----------------------------------------------------------------
-- 9. 希望之树进度表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hope_tree_progress (
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    patient_id        VARCHAR(36) NOT NULL COMMENT '患者ID（唯一）',
    current_level     INT         NOT NULL DEFAULT 1   COMMENT '当前等级(1-7)',
    current_exp       INT         NOT NULL DEFAULT 0   COMMENT '当前等级已积累经验值',
    total_exp         INT         NOT NULL DEFAULT 0   COMMENT '历史累计总经验值',
    next_level_exp    INT         NOT NULL DEFAULT 100 COMMENT '升到下一级所需经验值(满级为0)',
    total_growth_days INT         NOT NULL DEFAULT 0   COMMENT '累计成长天数',
    last_growth_date  DATETIME             COMMENT '最近一次获得经验的时间',
    updated_at        DATETIME             COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_patient_id (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='希望之树成长进度表';

-- ----------------------------------------------------------------
-- 10. 希望之树成长历史表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hope_tree_growth_history (
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    patient_id      VARCHAR(36) NOT NULL COMMENT '患者ID',
    growth_source   VARCHAR(30) NOT NULL COMMENT '来源: check_in|education|conversation|stage_advance|meditation',
    exp_amount      INT         NOT NULL COMMENT '本次获得经验值',
    level_before    INT         NOT NULL COMMENT '成长前等级',
    level_after     INT         NOT NULL COMMENT '成长后等级',
    level_up        TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否升级',
    source_ref_id   VARCHAR(100)         COMMENT '来源记录ID（如pro_record批次、conversation session等）',
    created_at      DATETIME             COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_patient_date (patient_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='希望之树成长历史记录表';


-- ================================================================
-- 模块七：护理宣教
-- ================================================================

-- ----------------------------------------------------------------
-- 11. 宣教内容表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS education_contents (
    id               VARCHAR(36)  NOT NULL COMMENT '内容ID（如 ec-001）',
    title            VARCHAR(200) NOT NULL COMMENT '标题',
    stage            VARCHAR(20)           COMMENT '适用临床阶段（null=全阶段）',
    category         VARCHAR(50)  NOT NULL COMMENT '分类',
    description      VARCHAR(500)          COMMENT '简介',
    content_type     VARCHAR(20)  NOT NULL COMMENT '内容类型: video|article',
    duration_seconds INT                   COMMENT '时长（秒，视频类有效）',
    thumbnail_url    VARCHAR(500)          COMMENT '封面图URL',
    media_url        VARCHAR(500)          COMMENT '媒体资源URL',
    tags             VARCHAR(500)          COMMENT '标签（逗号分隔）',
    sort_order       INT          NOT NULL DEFAULT 0  COMMENT '排序权重',
    is_active        TINYINT(1)   NOT NULL DEFAULT 1  COMMENT '是否上架',
    created_at       DATETIME              COMMENT '创建时间',
    updated_at       DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_stage_active    (stage, is_active),
    INDEX idx_category_active (category, is_active),
    INDEX idx_sort_active     (sort_order, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='护理宣教内容表';

-- ----------------------------------------------------------------
-- 12. 患者宣教观看进度表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS education_progress (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    patient_id       VARCHAR(36)  NOT NULL COMMENT '患者ID',
    content_id       VARCHAR(36)  NOT NULL COMMENT '宣教内容ID',
    watched_seconds  INT          NOT NULL DEFAULT 0 COMMENT '已观看秒数',
    completed        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已完成（完成过即为1，不会回退）',
    reward_given     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已发放完成奖励（防止重复发放）',
    last_watched_at  DATETIME              COMMENT '最近一次观看时间',
    created_at       DATETIME              COMMENT '创建时间',
    updated_at       DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_patient_content (patient_id, content_id),
    INDEX idx_patient_id (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='患者宣教观看进度表';


-- ================================================================
-- 模块八/二：心理能量历史（支持趋势查询）
-- ================================================================

-- ----------------------------------------------------------------
-- 13. 心理能量日志表（每次变化记录一条，支持趋势聚合）
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS psych_energy_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    patient_id   VARCHAR(36)  NOT NULL COMMENT '患者ID',
    log_date     DATE         NOT NULL COMMENT '记录日期',
    psych_energy INT          NOT NULL COMMENT '当前心理能量值（变化后）',
    delta        INT          NOT NULL DEFAULT 0 COMMENT '本次变化量（正/负）',
    trigger_type VARCHAR(30)  NOT NULL COMMENT '触发类型: pro_checkin|conversation|manual',
    source_ref   VARCHAR(100)          COMMENT '来源记录引用ID',
    created_at   DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_patient_date (patient_id, log_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='心理能量变化日志表';


-- ================================================================
-- 血象数据（支持 Agent E 接口的 blood_trend 构建）
-- ================================================================

-- ----------------------------------------------------------------
-- 14. 血象检测记录表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS blood_records (
    id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    patient_id      VARCHAR(36)    NOT NULL COMMENT '患者ID',
    record_date     DATE           NOT NULL COMMENT '检测日期',
    wbc             DECIMAL(5,2)            COMMENT '白细胞 ×10⁹/L',
    neutrophil      DECIMAL(5,2)            COMMENT '中性粒细胞 ×10⁹/L',
    platelet        DECIMAL(7,2)            COMMENT '血小板 ×10⁹/L',
    hemoglobin      DECIMAL(6,2)            COMMENT '血红蛋白 g/L',
    wbc_trend       VARCHAR(10)             COMMENT '白细胞趋势: RISING|FALLING|STABLE',
    neutrophil_trend VARCHAR(10)            COMMENT '中性粒细胞趋势',
    platelet_trend  VARCHAR(10)             COMMENT '血小板趋势',
    hemoglobin_trend VARCHAR(10)            COMMENT '血红蛋白趋势',
    recorded_by     VARCHAR(36)             COMMENT '录入人（用户ID）',
    recorded_by_name VARCHAR(100)           COMMENT '录入人姓名（冗余）',
    created_at      DATETIME                COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_patient_date (patient_id, record_date),
    INDEX idx_patient_date (patient_id, record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='血象检测记录表';


-- ================================================================
-- 模块九：预警与通知
-- ================================================================

-- ----------------------------------------------------------------
-- 15. 预警记录表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alerts (
    id             VARCHAR(36)  NOT NULL COMMENT '预警UUID',
    patient_id     VARCHAR(36)  NOT NULL COMMENT '患者ID',
    patient_name   VARCHAR(100)          COMMENT '患者姓名（冗余）',
    alert_type     VARCHAR(30)  NOT NULL COMMENT '预警类型: crisis|symptom|blood|manual',
    level          VARCHAR(20)  NOT NULL COMMENT '预警级别: info|warning|critical',
    message        VARCHAR(1000) NOT NULL COMMENT '预警描述',
    trigger_message VARCHAR(1000)         COMMENT '触发预警的原始消息内容',
    resolved       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已处理',
    resolved_by    VARCHAR(36)           COMMENT '处理人用户ID',
    resolved_note  VARCHAR(500)          COMMENT '处理备注',
    resolved_at    DATETIME              COMMENT '处理时间',
    created_at     DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_patient_resolved (patient_id, resolved),
    INDEX idx_level_resolved   (level, resolved),
    INDEX idx_created_at       (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警通知记录表';

-- ----------------------------------------------------------------
-- 16. 每日提醒推送计划表（由 Agent E 血象接口生成）
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reminder_plans (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    patient_id      VARCHAR(36)  NOT NULL COMMENT '患者ID',
    plan_date       DATE         NOT NULL COMMENT '计划推送日期',
    reminder_id     VARCHAR(50)  NOT NULL COMMENT 'Agent生成的reminderId（去重用）',
    scheduled_time  VARCHAR(10)  NOT NULL COMMENT '计划推送时间 HH:mm',
    type            VARCHAR(30)  NOT NULL COMMENT '类型: education_push|encouragement|medication_reminder',
    content_id      VARCHAR(36)           COMMENT '关联宣教内容ID（可为null）',
    push_message    TEXT         NOT NULL COMMENT '推送文案',
    priority        INT          NOT NULL DEFAULT 1 COMMENT '优先级，1最高',
    status          VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态: pending|sent|completed|skipped',
    trigger_reason  VARCHAR(500)          COMMENT '触发原因（日志用）',
    sent_at         DATETIME              COMMENT '实际推送时间',
    created_at      DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_patient_plan_reminder (patient_id, plan_date, reminder_id),
    INDEX idx_patient_date_status (patient_id, plan_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日提醒推送计划表';


-- ================================================================
-- 模块十一：离线数据同步
-- ================================================================

-- ----------------------------------------------------------------
-- 17. 离线同步幂等记录表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sync_items (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    client_id      VARCHAR(100) NOT NULL COMMENT '客户端唯一ID（幂等Key）',
    device_id      VARCHAR(100) NOT NULL COMMENT '设备序列号',
    patient_id     VARCHAR(36)  NOT NULL COMMENT '患者ID',
    item_type      VARCHAR(50)  NOT NULL COMMENT '数据类型: pro_submit|agent_chat|hopetree_grow|education_progress|robot_data',
    payload        TEXT                  COMMENT '原始请求体JSON',
    status         VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '处理状态: pending|success|failed',
    server_id      VARCHAR(100)          COMMENT '服务端处理结果ID',
    error_code     INT                   COMMENT '失败时的错误码',
    error_message  VARCHAR(500)          COMMENT '失败时的错误信息',
    retry_count    INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    client_created_at BIGINT             COMMENT '客户端数据生成时间戳（毫秒）',
    created_at     DATETIME              COMMENT '首次提交时间',
    processed_at   DATETIME              COMMENT '处理完成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id),
    INDEX idx_device_patient (device_id, patient_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='离线同步幂等记录表';


-- ================================================================
-- 模块十二：运维配置
-- ================================================================

-- ----------------------------------------------------------------
-- 18. 危机关键词配置表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crisis_keywords (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    keyword      VARCHAR(100) NOT NULL COMMENT '危机关键词',
    crisis_level VARCHAR(20)  NOT NULL COMMENT '对应危机等级: warning|critical',
    is_active    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_by   VARCHAR(36)           COMMENT '创建人用户ID',
    created_at   DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_keyword (keyword),
    INDEX idx_level_active (crisis_level, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='危机干预关键词配置表';

-- ----------------------------------------------------------------
-- 19. 审计日志表
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    operator_id    VARCHAR(36)           COMMENT '操作人用户ID',
    operator_name  VARCHAR(100)          COMMENT '操作人姓名',
    action         VARCHAR(50)  NOT NULL COMMENT '操作类型: STAGE_TRANSITION|CREATE_PATIENT|RESOLVE_ALERT|...',
    target_type    VARCHAR(50)           COMMENT '操作对象类型: patient|user|alert|content',
    target_id      VARCHAR(100)          COMMENT '操作对象ID',
    detail         TEXT                  COMMENT '操作详情JSON（记录变更前后值）',
    ip_address     VARCHAR(50)           COMMENT '操作来源IP',
    created_at     DATETIME              COMMENT '操作时间',
    PRIMARY KEY (id),
    INDEX idx_operator_date (operator_id, created_at),
    INDEX idx_action        (action),
    INDEX idx_target        (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志表';


-- ================================================================
-- 初始化数据
-- ================================================================

-- ----------------------------------------------------------------
-- 初始管理员和医护用户（密码均为 Xinya@2026，BCrypt加密）
-- ----------------------------------------------------------------
INSERT IGNORE INTO users (id, username, password_hash, display_name, role, phone, enabled, created_at, updated_at) VALUES
('u-admin', 'admin',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '系统管理员', 'ADMIN',  '13800000000', 1, NOW(), NOW()),
('u-001',   'nurse_01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '李护士',   'NURSE',  '13800000001', 1, NOW(), NOW()),
('u-002',   'doctor_01','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '王医生',   'DOCTOR', '13800000002', 1, NOW(), NOW());

-- ----------------------------------------------------------------
-- 测试患者数据
-- ----------------------------------------------------------------
INSERT IGNORE INTO patients (id, name, age, gender, diagnosis, stage, stage_start_date, psych_energy, tree_level, admission_date, room_number, created_at, updated_at) VALUES
('p-001', '张小明', 35, 'MALE',   '急性髓系白血病', 'ADMISSION',    '2026-02-20', 60, 1, '2026-02-20', 'A101', NOW(), NOW()),
('p-002', '李小红', 28, 'FEMALE', '急性淋巴细胞白血病', 'PRETREATMENT', '2026-02-22', 45, 2, '2026-02-15', 'A102', NOW(), NOW()),
('p-003', '王大力', 42, 'MALE',   '慢性粒细胞白血病', 'TRANSPLANT',   '2026-02-25', 70, 3, '2026-02-01', 'B201', NOW(), NOW());

-- ----------------------------------------------------------------
-- 希望之树初始进度
-- ----------------------------------------------------------------
INSERT IGNORE INTO hope_tree_progress (patient_id, current_level, current_exp, total_exp, next_level_exp, total_growth_days, updated_at) VALUES
('p-001', 1,  30,   30, 100,  3,  NOW()),
('p-002', 2,  80,  180, 250, 12,  NOW()),
('p-003', 3, 120,  480, 450, 28,  NOW());

-- ----------------------------------------------------------------
-- 心理能量日志示例数据（支持趋势接口 /api/patients/{id}/energy-trend 使用）
-- ----------------------------------------------------------------
INSERT IGNORE INTO psych_energy_log (patient_id, log_date, psych_energy, delta, trigger_type, source_ref, created_at) VALUES
('p-001', '2026-02-24', 55,  -5, 'pro_checkin', 'pro-20260224-p001', '2026-02-24 20:00:00'),
('p-001', '2026-02-25', 60,   5, 'pro_checkin', 'pro-20260225-p001', '2026-02-25 20:00:00'),
('p-001', '2026-02-26', 58,  -2, 'conversation','conv-20260226-p001','2026-02-26 21:00:00'),
('p-002', '2026-02-24', 40,  -5, 'pro_checkin', 'pro-20260224-p002', '2026-02-24 20:30:00'),
('p-002', '2026-02-25', 45,   5, 'pro_checkin', 'pro-20260225-p002', '2026-02-25 20:30:00'),
('p-003', '2026-02-25', 68,  -2, 'pro_checkin', 'pro-20260225-p003', '2026-02-25 19:30:00'),
('p-003', '2026-02-26', 70,   2, 'pro_checkin', 'pro-20260226-p003', '2026-02-26 19:30:00');

-- ----------------------------------------------------------------
-- PRO 问卷题目（全阶段通用）
-- ----------------------------------------------------------------
INSERT IGNORE INTO pro_questions (id, stage, title, type, options, symptom_key, sort_order, is_active, created_at) VALUES
('q_nausea',       'ALL', '今天有没有恶心感？',         'single_choice', '[{"value":"none","label":"没有","score":0},{"value":"mild","label":"轻度","score":1},{"value":"moderate","label":"中度","score":2},{"value":"severe","label":"重度","score":3}]', 'nausea',       1, 1, NOW()),
('q_fatigue',      'ALL', '今天整体乏力程度如何？',     'single_choice', '[{"value":"none","label":"无","score":0},{"value":"mild","label":"轻度","score":1},{"value":"moderate","label":"中度","score":2},{"value":"severe","label":"重度","score":3}]', 'fatigue',      2, 1, NOW()),
('q_mood',         'ALL', '今天整体心情如何？',         'scale',         NULL,                                                                                                                                                                             'anxiety',      3, 1, NOW()),
('q_appetite',     'ALL', '今天食欲情况如何？',         'single_choice', '[{"value":"good","label":"正常","score":0},{"value":"poor","label":"较差","score":1},{"value":"none","label":"无食欲","score":2}]',                                            'appetite_loss',4, 1, NOW()),
('q_oral',         'ALL', '口腔内是否有不适或溃疡？',   'single_choice', '[{"value":"none","label":"无","score":0},{"value":"mild","label":"轻微","score":1},{"value":"severe","label":"明显","score":2}]',                                             'oral_mucositis',5, 1, NOW()),
('q_fever',        'ALL', '今日是否有发热（>37.5°C）？','single_choice', '[{"value":"no","label":"没有","score":0},{"value":"low","label":"低热37.5-38°C","score":1},{"value":"high","label":"高热>38°C","score":2}]',                                  'fever',        6, 1, NOW()),
('q_diarrhea',     'ALL', '今天有腹泻情况吗？',         'single_choice', '[{"value":"none","label":"无","score":0},{"value":"mild","label":"1-2次","score":1},{"value":"moderate","label":"3-5次","score":2},{"value":"severe","label":">5次","score":3}]','diarrhea',     7, 1, NOW());

-- scale 类型补全 min/max
UPDATE pro_questions SET scale_min=1, scale_max=10, min_label='非常糟糕', max_label='非常好' WHERE id='q_mood';

-- ----------------------------------------------------------------
-- 宣教内容示例
-- ----------------------------------------------------------------
INSERT IGNORE INTO education_contents (id, title, stage, category, description, content_type, duration_seconds, sort_order, is_active, created_at, updated_at) VALUES
('ec-001', '移植前你需要了解的事',         'PRETREATMENT', '预处理期', '详细介绍造血干细胞移植前的准备工作和注意事项', 'video',   480, 1, 1, NOW(), NOW()),
('ec-002', '无菌仓生活指南',               'TRANSPLANT',   '移植期',   '在无菌仓内的日常护理要点和感染预防措施',         'article', NULL,2, 1, NOW(), NOW()),
('ec-003', '认识预处理：恶心呕吐应对指南', 'PRETREATMENT', '预处理期', '化疗引起的恶心呕吐原因及应对方法',               'video',   360, 3, 1, NOW(), NOW()),
('ec-004', '出仓后的饮食管理',             'REBUILD',      '重建期',   '移植后恢复期的营养饮食建议',                     'video',   360, 4, 1, NOW(), NOW()),
('ec-005', '希望之树：你的康复能量指南',   NULL,           '通用',     '了解希望之树系统，激发康复动力',                 'article', NULL,5, 1, NOW(), NOW()),
('ec-006', '血小板低时的出血防护要点',     'REBUILD',      '重建期',   '血小板低于正常值时的日常注意事项',               'video',   300, 6, 1, NOW(), NOW()),
('ec-007', '预处理期饮食调整小贴士',       'PRETREATMENT', '预处理期', '化疗期间如何调整饮食以减轻不适',                 'article', NULL,7, 1, NOW(), NOW()),
('ec-008', '移植期感染预防全攻略',         'TRANSPLANT',   '移植期',   '移植仓内如何最大程度预防感染',                   'video',   420, 8, 1, NOW(), NOW()),
('ec-009', '血小板低：出血风险与防护',     'REBUILD',      '重建期',   '了解血小板低时的出血风险及防护措施',             'video',   280, 9, 1, NOW(), NOW()),
('ec-012', '重建期为什么要每天测血象',     'REBUILD',      '重建期',   '血象监测的意义及指标解读',                       'article', NULL,12,1, NOW(), NOW());

-- ----------------------------------------------------------------
-- 危机关键词初始配置
-- ----------------------------------------------------------------
INSERT IGNORE INTO crisis_keywords (keyword, crisis_level, is_active, created_by, created_at) VALUES
('不想活了',   'critical', 1, 'u-admin', NOW()),
('想死',       'critical', 1, 'u-admin', NOW()),
('自杀',       'critical', 1, 'u-admin', NOW()),
('结束生命',   'critical', 1, 'u-admin', NOW()),
('放弃治疗',   'warning',  1, 'u-admin', NOW()),
('不想坚持了', 'warning',  1, 'u-admin', NOW()),
('没有希望了', 'warning',  1, 'u-admin', NOW()),
('太痛苦了',   'warning',  1, 'u-admin', NOW()),
('活不下去',   'critical', 1, 'u-admin', NOW()),
('撑不住了',   'warning',  1, 'u-admin', NOW());


-- ================================================================
-- 参考：首次部署时创建数据库和用户（用 root 执行）
-- ================================================================
-- CREATE DATABASE IF NOT EXISTS xinya_dtx
--   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--
-- CREATE USER IF NOT EXISTS 'xinya'@'localhost' IDENTIFIED BY 'xinya123';
-- GRANT ALL PRIVILEGES ON xinya_dtx.* TO 'xinya'@'localhost';
-- FLUSH PRIVILEGES;
