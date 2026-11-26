-- =============================================================================
-- C_20261030_005  轮次交付作品与延期改造：
--   sys_demand_stage 增列 交付作品ID、延期申请（状态/天数/小时/原因/申请时间）。
--   交付即暂停计时（status=submitted 期间不判逾期，审核驳回后截止时间顺延审核耗时）；
--   延期申请由创作者发起、发布者决定；发布者亦可自主延期（直接顺延 due_at）。
-- =============================================================================

-- 1. work_id 交付作品（作者本人的 sys_work，on_shelf 作品禁止交付，服务层校验）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'work_id');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN work_id BIGINT DEFAULT NULL COMMENT ''交付作品ID(sys_work)''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. extend_status 延期申请状态 pending|approved|rejected（NULL=无申请）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'extend_status');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN extend_status VARCHAR(20) DEFAULT NULL COMMENT ''延期申请状态''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. extend_days 申请延长天数
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'extend_days');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN extend_days INT DEFAULT NULL COMMENT ''申请延长(天)''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. extend_hours 申请延长小时
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'extend_hours');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN extend_hours INT DEFAULT NULL COMMENT ''申请延长(小时)''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. extend_reason 申请理由
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'extend_reason');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN extend_reason VARCHAR(255) DEFAULT NULL COMMENT ''延期申请理由''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6. extend_applied_at 申请时间
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'extend_applied_at');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN extend_applied_at DATETIME DEFAULT NULL COMMENT ''延期申请时间''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
