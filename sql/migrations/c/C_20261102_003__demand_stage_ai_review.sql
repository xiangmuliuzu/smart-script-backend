-- =============================================================================
-- C_20261102_003  轮次交付物的 AI 审核（提交后、人工验收前的自动前置门）：
--   sys_demand_stage 增列 AI 分数、AI 审核结论(JSON)、AI 审核完成时间。
--   提交交付物 → status=ai_reviewing（与 submitted 一样暂停计时）→ 后台 AI 打分：
--     >=60 → status=submitted（进入发布者验收）；<60 → status=rejected（自动打回创作者）。
--   AI 审核耗时不计入本轮时限：完成时把 due_at 顺延（提交→AI完成）的耗时，
--   人工驳回再顺延（AI完成→人工审核）的耗时，故 ai_reviewed_at 作为第二段的起点。
-- =============================================================================

-- 1. ai_score AI 审核得分（0-100，NULL=未审核）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'ai_score');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN ai_score INT DEFAULT NULL COMMENT ''AI审核得分(0-100)''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. ai_result AI 审核结论摘要（JSON：level/keywords/issues/usingMock）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'ai_result');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN ai_result VARCHAR(2000) DEFAULT NULL COMMENT ''AI审核结论(JSON)''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. ai_reviewed_at AI 审核完成时间（同时作为人工验收计时顺延的起点）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand_stage' AND COLUMN_NAME = 'ai_reviewed_at');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand_stage ADD COLUMN ai_reviewed_at DATETIME DEFAULT NULL COMMENT ''AI审核完成时间''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
