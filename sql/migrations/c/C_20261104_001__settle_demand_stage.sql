-- =====================================================================
-- C_20261104_001：待结算单支持"征集任务逐轮放款"
-- 需求：任务每轮验收通过后应放出本轮款项，并在作者收益（待结算→确认入账）体现。
-- 障碍：sys_income_settle 以 order_id 为唯一业务键（NOT NULL + uk_order_id），
--       一个订单只能一张单，装不下"一个任务 N 轮 = N 张单"。
-- 改造：增 biz_type/biz_no/demand_id/stage_id/stage_order；order_id 改为可空；
--       加 uk_stage_id 保证同一轮只生成一张待结算单。
--       订单行 stage_id 为 NULL，InnoDB 唯一索引允许多个 NULL，故既有订单结算不受影响。
--       任务行 order_id/order_no 为 NULL，业务标识走 biz_type + biz_no + demand_id/stage_id。
-- 幂等：列/索引存在则跳过；MODIFY 类语句可重复执行。
-- 注意：.local/apply_sql_file.py 按分号裸切语句，本文件所有字符串字面量内禁止出现分号。
-- =====================================================================

-- 1. biz_type 业务类型 order|demand_stage
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_income_settle'
      AND COLUMN_NAME = 'biz_type') = 0,
  'ALTER TABLE sys_income_settle ADD COLUMN biz_type VARCHAR(20) NOT NULL DEFAULT ''order'' COMMENT ''结算来源: order订单/demand_stage任务逐轮'' AFTER user_id',
  'SELECT ''biz_type already exists'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. biz_no 业务单号快照（任务行存征集任务号，订单行为空，订单号仍走 order_no）
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_income_settle'
      AND COLUMN_NAME = 'biz_no') = 0,
  'ALTER TABLE sys_income_settle ADD COLUMN biz_no VARCHAR(64) NULL COMMENT ''业务单号快照(任务行=征集任务号demand_no)'' AFTER order_no',
  'SELECT ''biz_no already exists'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. demand_id 关联征集任务
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_income_settle'
      AND COLUMN_NAME = 'demand_id') = 0,
  'ALTER TABLE sys_income_settle ADD COLUMN demand_id BIGINT NULL COMMENT ''关联征集任务ID(任务轮次结算专用)'' AFTER biz_no',
  'SELECT ''demand_id already exists'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. stage_id 关联验收轮次（逐轮放款幂等键）
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_income_settle'
      AND COLUMN_NAME = 'stage_id') = 0,
  'ALTER TABLE sys_income_settle ADD COLUMN stage_id BIGINT NULL COMMENT ''关联验收轮次ID(任务轮次结算专用, 一轮一单)'' AFTER demand_id',
  'SELECT ''stage_id already exists'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. stage_order 轮次序号快照（展示"第N轮"用，不随后续改动漂移）
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_income_settle'
      AND COLUMN_NAME = 'stage_order') = 0,
  'ALTER TABLE sys_income_settle ADD COLUMN stage_order INT NULL COMMENT ''轮次序号快照(第N轮)'' AFTER stage_id',
  'SELECT ''stage_order already exists'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6. order_id 放开非空约束（任务轮次结算单无订单）
SET @sql = IF(
  (SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_income_settle'
      AND COLUMN_NAME = 'order_id') = 'NO',
  'ALTER TABLE sys_income_settle MODIFY COLUMN order_id BIGINT NULL COMMENT ''关联订单ID(订单结算单专用, 任务轮次为NULL)''',
  'SELECT ''order_id already nullable'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7. work_title 注释补任务语义（任务行存本轮交付作品标题，无交付作品时存任务标题）
ALTER TABLE sys_income_settle
  MODIFY COLUMN work_title VARCHAR(200) NULL COMMENT '作品标题快照(任务行=本轮交付作品标题, 无则任务标题)';

-- 8. uk_stage_id 唯一键：同一轮只可能有一张待结算单（重复验收/并发重放安全）
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_income_settle'
      AND INDEX_NAME = 'uk_stage_id') = 0,
  'ALTER TABLE sys_income_settle ADD UNIQUE KEY uk_stage_id (stage_id)',
  'SELECT ''uk_stage_id already exists'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 9. idx_demand_id：按任务查其各轮结算
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_income_settle'
      AND INDEX_NAME = 'idx_demand_id') = 0,
  'ALTER TABLE sys_income_settle ADD KEY idx_demand_id (demand_id)',
  'SELECT ''idx_demand_id already exists'' AS msg'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 10. 历史行归位：改造前存在的都是订单结算单
UPDATE sys_income_settle SET biz_type = 'order' WHERE biz_type IS NULL OR biz_type = '';
