-- =====================================================================
-- U20260921_001__a2_rollback.sql
-- 用途: 回滚 A2_20260921_001
--   1) 先删除本版本唯一键（否则恢复空手机号会与 UK 冲突）
--   2) 从 a2_sys_user_preimage 恢复 sys_user 变换字段
--   3) 只删除 a2_table_ownership 中 action=CREATED 的业务表
--      （迁移前已存在的同名表/数据必须保留）
-- 适用: MySQL 8.0.36
-- 前置: 目标库已执行 A2_20260921_001__a2_migrate.sql
-- 事务: 多语句；MySQL DDL 隐式提交
-- 可重复: 是
-- 锁表影响: DROP INDEX / UPDATE sys_user / DROP TABLE 短时元数据锁
-- 保留条件: 共享库/生产库迁移与审计完成前不得删除本脚本与 preimage/ownership
-- =====================================================================

SELECT 'ROLLBACK_START' AS step, 'U20260921_001' AS version, DATABASE() AS db_name, NOW() AS ts;

SET @own_exists := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'a2_table_ownership'
);
SET @pre_exists := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'a2_sys_user_preimage'
);
SET @hist_exists := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'a2_migration_history'
);

SELECT 'CONTROL_TABLES' AS step, @own_exists AS ownership_tbl, @pre_exists AS preimage_tbl, @hist_exists AS history_tbl;

-- ---------------------------------------------------------------------
-- 1) 先删除唯一键
-- ---------------------------------------------------------------------
SET @uk_user := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_user_name'
);
SET @sql := IF(@uk_user > 0,
  'ALTER TABLE sys_user DROP INDEX uk_sys_user_user_name',
  'SELECT ''uk_sys_user_user_name_absent'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @uk_phone := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_sys_user_phonenumber'
);
SET @sql := IF(@uk_phone > 0,
  'ALTER TABLE sys_user DROP INDEX uk_sys_user_phonenumber',
  'SELECT ''uk_sys_user_phonenumber_absent'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2) 逆变换：恢复 sys_user 为迁移前字段值
--    仅恢复 preimage 中存在的 user_id；不删除迁移后新增用户
-- ---------------------------------------------------------------------
SET @sql := IF(@pre_exists = 1,
  'UPDATE sys_user u
   JOIN a2_sys_user_preimage p ON p.user_id = u.user_id
   SET u.phonenumber = p.phonenumber,
       u.user_type   = p.user_type,
       u.status      = p.status,
       u.del_flag    = p.del_flag,
       u.nick_name   = p.nick_name,
       u.email       = p.email,
       u.avatar      = p.avatar,
       u.login_ip    = p.login_ip,
       u.remark      = p.remark,
       u.password    = p.password',
  'SELECT ''preimage_missing_skip_data_restore'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT 'DATA_RESTORE' AS step,
       (SELECT COUNT(*) FROM a2_sys_user_preimage) AS preimage_rows,
       (SELECT COUNT(*) FROM sys_user u JOIN a2_sys_user_preimage p ON p.user_id=u.user_id) AS restored_rows;

SELECT 'RESTORE_DIFF' AS step,
       SUM(CONVERT(u.phonenumber USING utf8mb4) COLLATE utf8mb4_general_ci
           <=> CONVERT(p.phonenumber USING utf8mb4) COLLATE utf8mb4_general_ci) AS phone_match,
       SUM(CONVERT(u.user_type USING utf8mb4) COLLATE utf8mb4_general_ci
           <=> CONVERT(p.user_type USING utf8mb4) COLLATE utf8mb4_general_ci) AS user_type_match,
       SUM(CONVERT(u.status USING utf8mb4) COLLATE utf8mb4_general_ci
           <=> CONVERT(p.status USING utf8mb4) COLLATE utf8mb4_general_ci) AS status_match,
       SUM(CONVERT(u.del_flag USING utf8mb4) COLLATE utf8mb4_general_ci
           <=> CONVERT(p.del_flag USING utf8mb4) COLLATE utf8mb4_general_ci) AS del_flag_match,
       SUM(CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_general_ci
           <=> CONVERT(p.nick_name USING utf8mb4) COLLATE utf8mb4_general_ci) AS nick_match,
       COUNT(*) AS compared_rows,
       SUM(NOT (
            CONVERT(u.phonenumber USING utf8mb4) COLLATE utf8mb4_general_ci
              <=> CONVERT(p.phonenumber USING utf8mb4) COLLATE utf8mb4_general_ci
            AND CONVERT(u.user_type USING utf8mb4) COLLATE utf8mb4_general_ci
              <=> CONVERT(p.user_type USING utf8mb4) COLLATE utf8mb4_general_ci
            AND CONVERT(u.status USING utf8mb4) COLLATE utf8mb4_general_ci
              <=> CONVERT(p.status USING utf8mb4) COLLATE utf8mb4_general_ci
            AND CONVERT(u.del_flag USING utf8mb4) COLLATE utf8mb4_general_ci
              <=> CONVERT(p.del_flag USING utf8mb4) COLLATE utf8mb4_general_ci
            AND CONVERT(u.nick_name USING utf8mb4) COLLATE utf8mb4_general_ci
              <=> CONVERT(p.nick_name USING utf8mb4) COLLATE utf8mb4_general_ci
       )) AS mismatch_rows
FROM sys_user u
JOIN a2_sys_user_preimage p ON p.user_id = u.user_id;

-- ---------------------------------------------------------------------
-- 3) 只删除本版本 CREATED 的业务表
-- ---------------------------------------------------------------------
SELECT 'OWNERSHIP' AS step,
       action,
       GROUP_CONCAT(table_name ORDER BY table_name) AS tables,
       COUNT(*) AS cnt
FROM a2_table_ownership
WHERE version = 'A2_20260921_001'
GROUP BY action;

DROP TEMPORARY TABLE IF EXISTS tmp_a2_drop;
CREATE TEMPORARY TABLE tmp_a2_drop (
  table_name VARCHAR(64) PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO tmp_a2_drop (table_name)
SELECT table_name FROM a2_table_ownership
WHERE version = 'A2_20260921_001' AND action = 'CREATED';

SELECT 'WILL_DROP' AS step, table_name FROM tmp_a2_drop ORDER BY table_name;

SET @c := (SELECT COUNT(*) FROM tmp_a2_drop WHERE table_name='app_sms_code');
SET @sql := IF(@c>0,'DROP TABLE IF EXISTS app_sms_code','SELECT ''preserve app_sms_code'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM tmp_a2_drop WHERE table_name='app_refresh_session');
SET @sql := IF(@c>0,'DROP TABLE IF EXISTS app_refresh_session','SELECT ''preserve app_refresh_session'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM tmp_a2_drop WHERE table_name='app_user_consent');
SET @sql := IF(@c>0,'DROP TABLE IF EXISTS app_user_consent','SELECT ''preserve app_user_consent'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM tmp_a2_drop WHERE table_name='app_user_oauth');
SET @sql := IF(@c>0,'DROP TABLE IF EXISTS app_user_oauth','SELECT ''preserve app_user_oauth'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM tmp_a2_drop WHERE table_name='user_real_name_auth');
SET @sql := IF(@c>0,'DROP TABLE IF EXISTS user_real_name_auth','SELECT ''preserve user_real_name_auth'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM tmp_a2_drop WHERE table_name='user_phone_change_log');
SET @sql := IF(@c>0,'DROP TABLE IF EXISTS user_phone_change_log','SELECT ''preserve user_phone_change_log'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM tmp_a2_drop WHERE table_name='user_author_capability');
SET @sql := IF(@c>0,'DROP TABLE IF EXISTS user_author_capability','SELECT ''preserve user_author_capability'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM tmp_a2_drop WHERE table_name='user_notification');
SET @sql := IF(@c>0,'DROP TABLE IF EXISTS user_notification','SELECT ''preserve user_notification'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM tmp_a2_drop WHERE table_name='user_notification_receiver');
SET @sql := IF(@c>0,'DROP TABLE IF EXISTS user_notification_receiver','SELECT ''preserve user_notification_receiver'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM tmp_a2_drop WHERE table_name='user_notification_preference');
SET @sql := IF(@c>0,'DROP TABLE IF EXISTS user_notification_preference','SELECT ''preserve user_notification_preference'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM tmp_a2_drop WHERE table_name='user_feedback');
SET @sql := IF(@c>0,'DROP TABLE IF EXISTS user_feedback','SELECT ''preserve user_feedback'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT 'DROPPED_CHECK' AS step,
       t.table_name,
       IF(i.TABLE_NAME IS NULL, 'ABSENT_OK', 'STILL_PRESENT') AS after_rollback
FROM tmp_a2_drop t
LEFT JOIN information_schema.TABLES i
  ON i.TABLE_SCHEMA = DATABASE() AND i.TABLE_NAME = t.table_name;

SELECT 'PRESERVE_CHECK' AS step,
       t.table_name,
       IF(i.TABLE_NAME IS NULL, 'MISSING_FAIL', 'PRESENT_OK') AS after_rollback
FROM a2_table_ownership t
LEFT JOIN information_schema.TABLES i
  ON i.TABLE_SCHEMA = DATABASE() AND i.TABLE_NAME = t.table_name
WHERE t.version = 'A2_20260921_001' AND t.action = 'PREEXISTING';

DROP TEMPORARY TABLE IF EXISTS tmp_a2_drop;

-- ---------------------------------------------------------------------
-- 4) 标记历史已回滚
-- ---------------------------------------------------------------------
SET @sql := IF(@hist_exists = 1,
  'UPDATE a2_migration_history SET rolled_back = 1, rollback_at = NOW(),
    remark = CONCAT(IFNULL(remark,''''), '' | rolled-back'') WHERE version = ''A2_20260921_001''',
  'SELECT ''history_table_absent'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT 'ROLLBACK_DONE' AS step, NOW() AS ts;
