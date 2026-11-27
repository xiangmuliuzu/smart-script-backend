-- =============================================================================
-- C_20261030_003  任务逐轮托管定金改造：
--   sys_demand 增列 托管/定金/失败字段；sys_demand_stage 增列 逐轮时限与截止时间。
--   入账走支付宝沙箱（见 C_20261030_004），出账为记账，仅落状态字段。
-- =============================================================================

-- 1. escrow_amount 托管金额（=预算，元）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'escrow_amount');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN escrow_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT ''托管金额(元)=预算''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. escrow_status 托管状态 none|pending_payment|escrowed|released|refunded_to_publisher
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'escrow_status');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN escrow_status VARCHAR(32) NOT NULL DEFAULT ''none'' COMMENT ''托管状态''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. deposit_amount 定金金额（=floor(预算/10)，元）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'deposit_amount');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN deposit_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT ''定金金额(元)=floor(预算/10)''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. deposit_status 定金状态 none|held|refunded|forfeited
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'deposit_status');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN deposit_status VARCHAR(20) NOT NULL DEFAULT ''none'' COMMENT ''定金状态''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. deposit_paid_at 定金入账时间
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'deposit_paid_at');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN deposit_paid_at DATETIME DEFAULT NULL COMMENT ''定金入账时间''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6. deposit_settled_at 定金结算时间（退还/没收）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'deposit_settled_at');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN deposit_settled_at DATETIME DEFAULT NULL COMMENT ''定金结算时间''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7. fail_reason 失败原因
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'fail_reason');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN fail_reason VARCHAR(500) DEFAULT NULL COMMENT ''失败原因''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 8. fail_at 失败时间
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'fail_at');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN fail_at DATETIME DEFAULT NULL COMMENT ''失败终止时间''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- sys_demand_stage 逐轮时限 ----------

-- 9. duration_days 本轮时限(天)
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'duration_days');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN duration_days INT NOT NULL DEFAULT 0 COMMENT ''本轮时限(天)''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 10. duration_hours 本轮时限(小时)
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'duration_hours');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN duration_hours INT NOT NULL DEFAULT 0 COMMENT ''本轮时限(小时)''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 11. started_at 本轮开始时间（顺序起算）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'started_at');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN started_at DATETIME DEFAULT NULL COMMENT ''本轮开始时间''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 12. due_at 本轮截止时间 = started_at + days + hours
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'due_at');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN due_at DATETIME DEFAULT NULL COMMENT ''本轮截止时间''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 13. is_overdue 本轮逾期标记
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'is_overdue');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN is_overdue TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''本轮是否逾期''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 历史兼容回填（尽力，开发库可接受） ----------
-- 将历史进行中单的整单 duration_hours 回填到其首条未通过轮次，避免逐轮计时上线后旧单无时限。
UPDATE sys_demand_stage s
  JOIN (
    SELECT demand_id, MIN(stage_id) AS first_stage_id
    FROM sys_demand_stage
    WHERE status <> 'approved'
    GROUP BY demand_id
  ) f ON s.stage_id = f.first_stage_id
  JOIN sys_demand d ON d.demand_id = s.demand_id
SET s.duration_hours = d.duration_hours
WHERE d.status = 'in_progress'
  AND d.duration_hours IS NOT NULL
  AND s.duration_days = 0
  AND s.duration_hours = 0;
