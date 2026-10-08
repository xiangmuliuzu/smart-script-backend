-- A3 聊天功能：sys_chat_session 表结构改造
-- 1. 加业务关联列（business_type / business_id / business_name）
-- 2. 原唯一键 (user1_id, user2_id) 改为四列联合唯一键，支持同一用户就不同业务分别建会话

-- 幂等：先检查列是否已存在
SET @col_exists_bt  = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_chat_session' AND COLUMN_NAME = 'business_type');
SET @col_exists_bid = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_chat_session' AND COLUMN_NAME = 'business_id');
SET @col_exists_bn  = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_chat_session' AND COLUMN_NAME = 'business_name');

SET @sql_add_bt  = IF(@col_exists_bt  = 0, 'ALTER TABLE sys_chat_session ADD COLUMN business_type VARCHAR(20) DEFAULT NULL AFTER inquiry_id',  'SELECT 1');
SET @sql_add_bid = IF(@col_exists_bid = 0, 'ALTER TABLE sys_chat_session ADD COLUMN business_id   BIGINT      DEFAULT NULL AFTER business_type', 'SELECT 1');
SET @sql_add_bn  = IF(@col_exists_bn  = 0, 'ALTER TABLE sys_chat_session ADD COLUMN business_name VARCHAR(200) DEFAULT NULL AFTER business_id',  'SELECT 1');

PREPARE stmt FROM @sql_add_bt;  EXECUTE stmt;  DEALLOCATE PREPARE stmt;
PREPARE stmt FROM @sql_add_bid; EXECUTE stmt;  DEALLOCATE PREPARE stmt;
PREPARE stmt FROM @sql_add_bn;  EXECUTE stmt;  DEALLOCATE PREPARE stmt;

-- 加管理员分配列
SET @col_exists_aid = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_chat_session' AND COLUMN_NAME = 'assigned_admin_id');
SET @sql_add_aid = IF(@col_exists_aid = 0, 'ALTER TABLE sys_chat_session ADD COLUMN assigned_admin_id BIGINT DEFAULT NULL AFTER business_name', 'SELECT 1');
PREPARE stmt FROM @sql_add_aid; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 改唯一键：先删旧的再建新的
-- 幂等：检查旧索引是否存在
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_chat_session' AND INDEX_NAME = 'uk_user1_user2');
SET @sql_drop_idx = IF(@idx_exists > 0, 'ALTER TABLE sys_chat_session DROP INDEX uk_user1_user2', 'SELECT 1');
PREPARE stmt FROM @sql_drop_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @new_idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_chat_session' AND INDEX_NAME = 'uk_session_biz');
SET @sql_add_idx = IF(@new_idx_exists = 0, 'ALTER TABLE sys_chat_session ADD UNIQUE KEY uk_session_biz (user1_id, user2_id, business_type, business_id)', 'SELECT 1');
PREPARE stmt FROM @sql_add_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 验证
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_chat_session' AND COLUMN_NAME IN ('business_type','business_id','business_name','assigned_admin_id') ORDER BY ORDINAL_POSITION;
SELECT INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_chat_session' AND INDEX_NAME = 'uk_session_biz' ORDER BY SEQ_IN_INDEX;
