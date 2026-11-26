-- =====================================================================
-- C_20261029_001  任务广场（需求悬赏）：sys_demand 增加来源字段
-- 用于区分「自主发布(manual)」与「从作品衍生(auto)」的征集需求。
-- 现有投稿/选标/转询盘链路不依赖该列，仅作为来源标记补充；默认 manual。
-- 幂等：仅当列不存在时执行 ADD COLUMN。
-- =====================================================================

SET @db := DATABASE();

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'source'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_demand ADD COLUMN source VARCHAR(32) DEFAULT ''manual'' COMMENT ''来源(manual=自主发布/auto=从作品衍生)''',
    'SELECT ''sys_demand.source already exists'' AS msg');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 校验
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'source';
