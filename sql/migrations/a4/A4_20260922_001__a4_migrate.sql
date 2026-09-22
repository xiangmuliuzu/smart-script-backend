-- =====================================================================
-- A4_20260922_001__a4_migrate.sql
-- 用途: A4 PC 管理能力增量迁移 —— 复用 A2 五表，仅增量对齐契约 v1
-- 适用: MySQL 8.0.36
-- 前置:
--   1) A2_20260921_001 已应用（五张业务表存在）
--   2) A4_20260922_001__a4_precheck.sql 最终 SUMMARY 为 PASS
--   3) 目标库为隔离测试库，或已批准维护窗口
-- 事务: MySQL DDL 隐式提交；失败以 verify/rollback 收敛
-- 可重复: 是（条件 ALTER + 幂等 index/约束探测 + history UPSERT）
-- 锁表影响: user_notification/user_feedback ALTER + UPDATE；大库需窗口
-- 回滚: U20260922_001__a4_rollback.sql
-- 依据: A4-PC管理实施方案.md §6.2 / A4-管理接口契约.md / P0 裁决 GAP-1～GAP-6
--
-- 本脚本只做裁决范围内的三件事：
--   GAP-1 user_notification 增加可空 request_id + 唯一索引
--   GAP-2 user_notification 增加 business_type/business_id，保留 biz_ref 兼容列
--   GAP-3 user_feedback 状态 OPEN -> SUBMITTED 归一 + 状态集合约束
-- GAP-4/5/6 为服务层与 DTO 映射，不产生 DDL；本脚本不改实名表列名、不解析分隔符。
--
-- 禁止: 重建 A2 五表；删除或改写 biz_ref；破坏性回填历史行；对未知状态自动猜测转换
-- =====================================================================

SET NAMES utf8mb4;
SET @a4_version := 'A4_20260922_001';

SELECT 'MIGRATE_START' AS step, @a4_version AS version, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 0) 控制表：A4 迁移历史 + 变更前快照
--    不改写 A2 的 a2_migration_history/a2_table_ownership 语义，
--    沿用同一「先快照后变更」思路，回滚据此精确逆变换。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS a4_migration_history (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  version     VARCHAR(64)  NOT NULL COMMENT '迁移版本',
  purpose     VARCHAR(255) NOT NULL DEFAULT '' COMMENT '用途',
  applied_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  rolled_back TINYINT(1)   NOT NULL DEFAULT 0,
  rollback_at DATETIME     NULL,
  remark      VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_a4_history_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A4 迁移历史';

-- user_feedback 状态归一前快照：只记录被本版本由 OPEN 改为 SUBMITTED 的行，
-- 使 rollback 能精确还原，且不会把 A4 之后新产生的 SUBMITTED 行误改回 OPEN。
-- 注意：只增不删。rollback 不清表，这样「migrate → rollback → migrate」重跑时
-- 既有快照会被复用，不会因二次快照丢掉原始前像。
CREATE TABLE IF NOT EXISTS a4_feedback_status_preimage (
  feedback_id   BIGINT      NOT NULL,
  status_before VARCHAR(32) NOT NULL,
  snapshot_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (feedback_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A4 反馈状态归一前快照';

-- ---------------------------------------------------------------------
-- 0b) 重跑安全守卫
--     「migrate → rollback → migrate」时，rollback 已把快照行还原为 OPEN 并解除
--     状态集合约束；若此处仍直接归一，会在后续 ADD CONSTRAINT 时被 OPEN 行拒绝。
--     先在无约束状态下完成归一，再重建约束。
-- ---------------------------------------------------------------------
SET @chk_pre := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback'
    AND CONSTRAINT_TYPE = 'CHECK' AND CONSTRAINT_NAME = 'ck_user_feedback_status'
);
SET @open_pre := (SELECT COUNT(*) FROM user_feedback WHERE status = 'OPEN');

SELECT 'MIGRATE_RERUN_GUARD' AS step,
       @chk_pre AS status_check_present,
       @open_pre AS open_rows_present,
       IF(@chk_pre = 0 AND @open_pre > 0,
          'REAPPLY_AFTER_ROLLBACK', 'NORMAL') AS mode;

-- 约束在但存在域外值，说明数据被外部改写；停止而不是猜测
SET @sql := IF(@chk_pre = 1 AND @open_pre > 0,
  'SELECT ''MIGRATE_ABORTED'' AS step,
          ''user_feedback 存在 OPEN 行但状态集合约束仍在；''  AS reason,
          ''请先在 precheck 定位写入来源后再迁移'' AS action',
  'SELECT ''guard_ok'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 1) GAP-1：user_notification.request_id（可空，唯一索引，历史行允许 NULL）
-- ---------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
    AND COLUMN_NAME = 'request_id'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE user_notification
     ADD COLUMN request_id VARCHAR(64) NULL COMMENT ''A4 创建幂等键；历史行为 NULL''
     AFTER id',
  'SELECT ''request_id_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
    AND INDEX_NAME = 'uk_user_notification_request_id'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE user_notification
     ADD UNIQUE KEY uk_user_notification_request_id (request_id)',
  'SELECT ''request_id_index_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2) GAP-2：business_type / business_id 双列；biz_ref 作为兼容列保留
--    历史行不回填（businessType 为 null、businessId 由服务层映射 biz_ref）
-- ---------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
    AND COLUMN_NAME = 'business_type'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE user_notification
     ADD COLUMN business_type VARCHAR(64) NULL COMMENT ''A4 业务类型；历史行 NULL''
     AFTER biz_ref',
  'SELECT ''business_type_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
    AND COLUMN_NAME = 'business_id'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE user_notification
     ADD COLUMN business_id VARCHAR(64) NULL COMMENT ''A4 业务标识；历史行 NULL''
     AFTER business_type',
  'SELECT ''business_id_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3) GAP-3：反馈状态归一 OPEN -> SUBMITTED
--    先快照被改动的行，再归一，最后收敛默认值与约束
-- ---------------------------------------------------------------------
INSERT INTO a4_feedback_status_preimage (feedback_id, status_before, snapshot_at)
SELECT f.id, f.status, NOW()
FROM user_feedback f
LEFT JOIN a4_feedback_status_preimage p ON p.feedback_id = f.id
WHERE f.status = 'OPEN' AND p.feedback_id IS NULL;

UPDATE user_feedback
SET status = 'SUBMITTED'
WHERE status = 'OPEN';

-- 默认值由 OPEN 收敛为 SUBMITTED，使 A5 写入端无需显式传值也落在 A4 状态机内
SET @default_now := (
  SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback'
    AND COLUMN_NAME = 'status'
);
SET @sql := IF(@default_now IS NULL OR UPPER(@default_now) = 'OPEN',
  'ALTER TABLE user_feedback
     MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT ''SUBMITTED''
     COMMENT ''SUBMITTED/PROCESSING/REPLIED/CLOSED（A4 状态机）''',
  'SELECT ''status_default_already_ok'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 状态集合约束：未知值在写入层即被拒绝，避免再次出现状态机外取值
SET @chk_exists := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback'
    AND CONSTRAINT_TYPE = 'CHECK'
    AND CONSTRAINT_NAME = 'ck_user_feedback_status'
);
SET @sql := IF(@chk_exists = 0,
  'ALTER TABLE user_feedback
     ADD CONSTRAINT ck_user_feedback_status
     CHECK (status IN (''SUBMITTED'',''PROCESSING'',''REPLIED'',''CLOSED''))',
  'SELECT ''status_check_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4) 登记版本
-- ---------------------------------------------------------------------
INSERT INTO a4_migration_history (version, purpose, remark)
VALUES (
  @a4_version,
  'A4 incremental alignment: notification idempotency key, business refs, feedback status normalization',
  'A4 PC admin migration (reuses A2 tables)'
)
ON DUPLICATE KEY UPDATE
  purpose    = VALUES(purpose),
  applied_at = NOW(),
  rolled_back = 0,
  rollback_at = NULL,
  remark     = VALUES(remark);

SELECT 'MIGRATE_DONE' AS step, @a4_version AS version,
       (SELECT COUNT(*) FROM a4_feedback_status_preimage) AS feedback_preimage_rows,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
          AND COLUMN_NAME IN ('request_id','business_type','business_id')) AS added_notification_cols,
       (SELECT COUNT(*) FROM user_feedback WHERE status = 'OPEN') AS remaining_open_rows;
