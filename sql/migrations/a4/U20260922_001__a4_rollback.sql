-- =====================================================================
-- U20260922_001__a4_rollback.sql
-- 用途: 回滚 A4_20260922_001 增量迁移
-- 适用: MySQL 8.0.36
-- 前置: 已确认 A4 PC 管理功能下线，且业务数据处置方案已批准
-- 事务: MySQL DDL 隐式提交；失败以 verify 收敛
-- 可重复: 是（条件 DROP/ALTER；history 标记 UPSERT）
--
-- 安全原则（对应测试矩阵 DB-05：非空业务数据无确认时拒绝破坏性回滚）：
--   1) user_feedback 状态按快照精确还原，**不删除已产生的有效业务数据**；
--      A4 之后由真实业务写入的行（已受理/已回复/已关闭）不还原为 OPEN。
--   2) 若 user_notification 存在 A4 新列的非空业务值，DROP COLUMN 会造成不可逆数据丢失。
--      此时默认**拒绝执行并安全停止**，只有显式设置 @a4_force_rollback = 1 才继续。
--   3) 不重建、不删除 A2 五张业务表；不删除 biz_ref 兼容列。
--   4) 不修改 sys_user 任何数据。
--
-- 用法:
--   默认（安全）: mysql <db> < U20260922_001__a4_rollback.sql
--   显式接受丢失: 先执行 SET @a4_force_rollback = 1; 或在本脚本第 1 行前注入该设置
-- =====================================================================

SET NAMES utf8mb4;
SET @a4_version := 'A4_20260922_001';
SET @a4_force_rollback := IFNULL(@a4_force_rollback, 0);

SELECT 'ROLLBACK_START' AS step, @a4_version AS version, DATABASE() AS db_name,
       @a4_force_rollback AS force_flag, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 0) 破坏性前置检查
-- ---------------------------------------------------------------------
SET @rows_with_business := (
  SELECT COUNT(*) FROM user_notification
  WHERE (business_type IS NOT NULL AND TRIM(business_type) <> '')
     OR (business_id   IS NOT NULL AND TRIM(business_id)   <> '')
);
SET @rows_with_request_id := (
  SELECT COUNT(*) FROM user_notification
  WHERE request_id IS NOT NULL AND TRIM(request_id) <> ''
);

SELECT 'ROLLBACK_PRECHECK' AS step,
       @rows_with_business AS rows_with_business_values,
       @rows_with_request_id AS rows_with_request_id,
       IF(@rows_with_business = 0 OR @a4_force_rollback = 1,
          'PROCEED', 'REFUSE_WOULD_LOSE_DATA') AS decision;

-- 存在业务数据且未显式放行时，整脚本停止（不执行任何 DDL）
SET @sql := IF(@rows_with_business > 0 AND @a4_force_rollback = 0,
  'SELECT ''ROLLBACK_ABORTED'' AS step,
          ''user_notification 存在 A4 新列业务值；DROP 会造成不可逆丢失。'' AS reason,
          ''请先导出数据或显式 SET @a4_force_rollback = 1 后重跑'' AS action',
  'SELECT ''ROLLBACK_PROCEED'' AS step, ''guard_passed'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 用会话变量守卫后续 DDL：未放行时全部跳过
SET @a4_proceed := IF(@rows_with_business > 0 AND @a4_force_rollback = 0, 0, 1);

-- ---------------------------------------------------------------------
-- 1) user_feedback：按快照精确还原 OPEN，保留真实业务流转结果
--    顺序要求：必须先解除 A4 状态集合约束（OPEN 不在其集合内），再还原状态，
--    最后恢复默认值；否则还原 UPDATE 会被 CHECK 拒绝。
-- ---------------------------------------------------------------------
SET @sql := IF(@a4_proceed = 1
  AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
       WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback'
         AND CONSTRAINT_TYPE = 'CHECK'
         AND CONSTRAINT_NAME = 'ck_user_feedback_status') = 1,
  'ALTER TABLE user_feedback DROP CHECK ck_user_feedback_status',
  'SELECT ''skip_status_check_drop'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@a4_proceed = 1,
  'UPDATE user_feedback f
     JOIN a4_feedback_status_preimage p ON p.feedback_id = f.id
   SET f.status = p.status_before
   WHERE p.status_before = ''OPEN'' AND f.status = ''SUBMITTED''',
  'SELECT ''skip_feedback_restore'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 默认值恢复为 A2 基线 OPEN（默认值是结构约定，不承载业务数据，可逆）
SET @sql := IF(@a4_proceed = 1
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback'
         AND COLUMN_NAME = 'status' AND UPPER(COLUMN_DEFAULT) = 'SUBMITTED') = 1,
  'ALTER TABLE user_feedback
     MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT ''OPEN''',
  'SELECT ''skip_status_default_restore'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2) user_notification：移除 A4 新增列（先删唯一索引再删列）
--    不触碰 biz_ref 兼容列
-- ---------------------------------------------------------------------
SET @sql := IF(@a4_proceed = 1
  AND (SELECT COUNT(*) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
         AND INDEX_NAME = 'uk_user_notification_request_id') > 0,
  'ALTER TABLE user_notification DROP INDEX uk_user_notification_request_id',
  'SELECT ''skip_drop_request_id_index'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@a4_proceed = 1
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
         AND COLUMN_NAME = 'request_id') = 1,
  'ALTER TABLE user_notification DROP COLUMN request_id',
  'SELECT ''skip_drop_request_id_col'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@a4_proceed = 1
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
         AND COLUMN_NAME = 'business_id') = 1,
  'ALTER TABLE user_notification DROP COLUMN business_id',
  'SELECT ''skip_drop_business_id'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@a4_proceed = 1
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
         AND COLUMN_NAME = 'business_type') = 1,
  'ALTER TABLE user_notification DROP COLUMN business_type',
  'SELECT ''skip_drop_business_type'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3) 标记历史（快照表刻意保留：为「migrate→rollback→migrate」重跑保留原始前像）
-- ---------------------------------------------------------------------
SET @sql := IF(@a4_proceed = 1,
  'UPDATE a4_migration_history
     SET rolled_back = 1, rollback_at = NOW(),
         remark = CONCAT(IFNULL(remark,''''), '' | rolled back'')
   WHERE version = @a4_version',
  'SELECT ''skip_history_mark'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4) 回滚后状态输出
-- ---------------------------------------------------------------------
SELECT 'ROLLBACK_DONE' AS step, @a4_version AS version,
       @a4_proceed AS proceeded,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
          AND COLUMN_NAME IN ('request_id','business_type','business_id')) AS remaining_a4_cols,
       (SELECT COUNT(*) FROM user_feedback WHERE status = 'OPEN') AS open_rows,
       (SELECT COUNT(*) FROM a4_feedback_status_preimage) AS preimage_retained;

SELECT 'A2_TABLES_STILL_INTACT' AS metric,
       (SELECT COUNT(*) FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME IN ('user_real_name_auth','user_author_capability',
                             'user_notification','user_notification_receiver',
                             'user_feedback')) AS a2_tables;
