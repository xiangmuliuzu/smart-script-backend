-- =====================================================================
-- U20260921_001__a2_rollback.sql
-- 用途: 回滚 A2_20260921_001（撤销唯一键与 App/用户域表；恢复手机号空串语义可选）
-- 适用: MySQL 8.0.36
-- 前置: 目标库已执行 A2_20260921_001__a2_migrate.sql
-- 事务: 多语句；MySQL DDL 隐式提交
-- 可重复: 是（IF EXISTS）
-- 锁表影响: DROP TABLE / DROP INDEX 短时元数据锁
-- 回滚脚本: 本文件自身是回滚；回滚后应执行回滚后复核 SQL
-- 保留条件: 在共享库/生产库迁移完成并完成审计前不得删除本脚本
-- =====================================================================

SELECT 'ROLLBACK_START' AS step, 'U20260921_001' AS version, DATABASE() AS db_name, NOW() AS ts;

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

-- 密码列保持 varchar(255) 不强制回退（回退长度无收益且可能截断未来散列）。
-- 若必须还原官方种子结构，可手工 MODIFY 为 varchar(100)，前提是确认无更长散列。

DROP TABLE IF EXISTS user_feedback;
DROP TABLE IF EXISTS user_notification_preference;
DROP TABLE IF EXISTS user_notification_receiver;
DROP TABLE IF EXISTS user_notification;
DROP TABLE IF EXISTS user_author_capability;
DROP TABLE IF EXISTS user_phone_change_log;
DROP TABLE IF EXISTS user_real_name_auth;
DROP TABLE IF EXISTS app_user_oauth;
DROP TABLE IF EXISTS app_user_consent;
DROP TABLE IF EXISTS app_refresh_session;
DROP TABLE IF EXISTS app_sms_code;

-- 历史表保留审计记录；将本版本标记为已回滚（若表存在）
SET @hist := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'a2_migration_history'
);
SET @sql := IF(@hist > 0,
  'UPDATE a2_migration_history SET rolled_back = 1, rollback_at = NOW(), remark = CONCAT(IFNULL(remark,''''), '' | rolled-back'') WHERE version = ''A2_20260921_001''',
  'SELECT ''history_table_absent'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT 'ROLLBACK_DONE' AS step, NOW() AS ts;
