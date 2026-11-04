-- C 模块：任务广场查询复合索引
-- 服务场景：/trade/demand/list 广场口径 (status='open' AND escrow_status='escrowed') 过滤
-- + ORDER BY created_at、/stats 的 SUM/COUNT 聚合、今日新增（created_at 范围）。
-- 现状：sys_demand 仅有 PK/uk_demand_no/idx_client_id/idx_status，量上来将全表 + filesort。
-- 幂等：已存在则跳过。

SET @exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_demand'
      AND index_name = 'idx_square'
);
SET @sql := IF(@exists = 0,
    'ALTER TABLE sys_demand ADD INDEX idx_square (status, escrow_status, created_at)',
    'SELECT ''idx_square already exists, skipped'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
