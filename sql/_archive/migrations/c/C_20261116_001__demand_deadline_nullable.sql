-- =====================================================================
-- C_20261116_001：征集截止日期允许为空（长期征集）
-- 需求：管理端发布/编辑任务的「征集截止」标注为“留空为不限”，但 sys_demand.deadline
--       是 NOT NULL 的 DATE 列，在 STRICT_TRANS_TABLES + NO_ZERO_DATE 下留空发布直接报
--       「Column ''deadline'' cannot be null」，500 且不落库。
--       读侧早已按“空=不设时限”实现（广场 excludeExpired、详情/接单 isRecruitExpired、
--       统计与自动关闭扫描的 deadline is null 分支），因此放开列约束而不是收紧表单。
-- 口径：deadline 只到天，截止日当天整天仍可接单；过完当天由 tradeDemandTask 自动关闭
--       （见 C_20261103_003 注册的定时任务，处置逻辑在 TradeDemandService.closeExpiredRecruits）。
-- 幂等：仅当列仍为 NOT NULL 时才放开，重复执行输出 skip。
-- =====================================================================

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_demand'
      AND COLUMN_NAME = 'deadline'
      AND IS_NULLABLE = 'NO') > 0,
  'ALTER TABLE sys_demand MODIFY COLUMN deadline DATE NULL COMMENT ''征集截止日期（当天整天有效；NULL=不设时限，过期由定时任务关闭）''',
  'SELECT ''deadline already nullable, skip'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 自检：期望 0 行（列仍非空说明迁移未生效）
SELECT IF(COUNT(*) = 0, 'OK: sys_demand.deadline 已可为空', 'FAIL: deadline 仍为 NOT NULL') AS check_result
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_demand'
  AND COLUMN_NAME = 'deadline'
  AND IS_NULLABLE = 'NO';
