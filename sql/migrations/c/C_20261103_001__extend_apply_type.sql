-- =====================================================================
-- C_20261103_001：延时历史记录区分"申请延时"与"自主延时"
-- 需求：发布者自主延时（不经申请直接顺延）也要落进 sys_demand_extend_apply，
--       与创作者申请延时同格式展示，仅文案区分来源。
-- 幂等：列存在则跳过。
-- =====================================================================

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_demand_extend_apply'
      AND COLUMN_NAME = 'apply_type') = 0,
  'ALTER TABLE sys_demand_extend_apply ADD COLUMN apply_type VARCHAR(20) NOT NULL DEFAULT ''request'' COMMENT ''延时来源：request=创作者申请延时 / self=发布者自主延时'' AFTER status',
  'SELECT ''apply_type already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量记录全部来自申请流程，显式回填为 request（幂等：仅补空值/未知值）
UPDATE sys_demand_extend_apply
SET apply_type = 'request'
WHERE apply_type IS NULL OR apply_type = '';
