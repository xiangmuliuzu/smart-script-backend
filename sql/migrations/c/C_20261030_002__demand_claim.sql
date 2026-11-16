-- =============================================================================
-- C_20261030_002  任务接单制：sys_demand 加接单人/接单时间/完成时限/应完成时间/逾期
--                 + 管理后台菜单更名"任务管理"、移除"征集令发布"按钮
-- =============================================================================

-- 1. accepted_creator_id 接单人用户ID（幂等）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'accepted_creator_id');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN accepted_creator_id BIGINT DEFAULT NULL COMMENT ''接单人用户ID''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. accepted_at 接单时间
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'accepted_at');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN accepted_at DATETIME DEFAULT NULL COMMENT ''接单时间''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. duration_hours 接单后完成时限(小时)
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'duration_hours');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN duration_hours INT DEFAULT NULL COMMENT ''接单后完成时限(小时)''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. due_at 应完成时间 = accepted_at + duration_hours
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'due_at');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN due_at DATETIME DEFAULT NULL COMMENT ''应完成时间=accepted_at+duration_hours''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. is_overdue 是否逾期标记
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'is_overdue');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN is_overdue TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否逾期:0否1是''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6. 管理后台菜单：征集项目(5138) 更名为 任务管理
UPDATE sys_menu SET menu_name = '任务管理' WHERE menu_id = 5138;

-- 7. 移除"征集令发布"(trade:demand:add)按钮权限
DELETE FROM sys_role_menu WHERE menu_id = 5194;
DELETE FROM sys_menu WHERE menu_id = 5194;
