-- V2: 条件性为业务表添加常用索引
-- 若业务表尚未创建（全新数据库），索引操作自动跳过，不报错
-- 使用 ALTER TABLE prepared statement，兼容 Flyway（无需 DELIMITER 切换）

-- ① patients.stage
SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'patients')
    AND NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'patients'
                     AND INDEX_NAME = 'idx_patients_stage'),
    'ALTER TABLE patients ADD INDEX idx_patients_stage (stage)',
    'SELECT 1'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ② alerts(patient_id, resolved)
SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alerts')
    AND NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alerts'
                     AND INDEX_NAME = 'idx_alerts_patient_resolved'),
    'ALTER TABLE alerts ADD INDEX idx_alerts_patient_resolved (patient_id, resolved)',
    'SELECT 1'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ③ pro_records(patient_id, record_date)
SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pro_records')
    AND NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pro_records'
                     AND INDEX_NAME = 'idx_pro_records_patient_date'),
    'ALTER TABLE pro_records ADD INDEX idx_pro_records_patient_date (patient_id, record_date)',
    'SELECT 1'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ④ hope_tree_growth_history(patient_id, created_at)
SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hope_tree_growth_history')
    AND NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hope_tree_growth_history'
                     AND INDEX_NAME = 'idx_hope_tree_growth_patient'),
    'ALTER TABLE hope_tree_growth_history ADD INDEX idx_hope_tree_growth_patient (patient_id, created_at)',
    'SELECT 1'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ⑤ conversations(patient_id, created_at)
SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'conversations')
    AND NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'conversations'
                     AND INDEX_NAME = 'idx_conversations_patient'),
    'ALTER TABLE conversations ADD INDEX idx_conversations_patient (patient_id, created_at)',
    'SELECT 1'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
