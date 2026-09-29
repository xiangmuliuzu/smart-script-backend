-- U20260928_001__h12_sys_user_type_delflag_index_rollback.sql
-- H-12 / H6-PERF-01 回滚：删除 sys_user(user_type, del_flag) 覆盖索引。
-- 前置：确认无查询依赖该索引的强制提示（本工程无 FORCE INDEX 使用）；
--       删除后目标 COUNT 查询恢复全表扫描形态（第 21 批隔离库实测）。
-- 幂等：索引不存在时跳过。
-- 第 22 批自检更正：复合索引在 information_schema.STATISTICS 中占多行，
--       判据必须用 COUNT(*) > 0，不能写 = 1（原写法在双列索引下永不触发删除）。

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND INDEX_NAME = 'idx_user_type_del_flag'
);

SET @ddl := IF(@idx_exists > 0,
  'ALTER TABLE sys_user DROP INDEX idx_user_type_del_flag',
  'SELECT ''index idx_user_type_del_flag absent - skip'' AS note');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
