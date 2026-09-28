-- H12_20260928_001__sys_user_type_delflag_index.sql
-- H-12 / H6-PERF-01：sys_user(user_type, del_flag) 覆盖索引（消除 PC 管理列表 COUNT 全表扫描）
--
-- 批准出处：A5-A7-H12-01-H12-03-行为方案与影响范围.md §4「H6-PERF-01：批准覆盖索引方案」
--           （负责人 2026-09-28 授权 Codex 代录）。
-- 执行责任：**平台属主**在生产/各环境按 H-10 门禁执行（备份或 dump 留底 → 变更窗口 →
--           执行本脚本 → 运行 verify → 保留输出；回滚用配套 U 脚本）。本文件不构成已执行。
-- 实测依据（第 21 批，隔离库 ruoyi_dev_h2_test，12k 行 sys_user）：
--           EXPLAIN 由 type=ALL(≈10,252 行) 变为 type=range + Using index(≈5,128 行)；
--           计时 ≈5.3ms → ≈4.3ms；DROP INDEX 回滚已实测恢复全表扫描形态。
--
-- 幂等说明：脚本先探测索引是否存在；已存在则跳过（可安全重复执行）。
-- 兼容性：纯新增索引，不改数据、不改 SQL 语义；未命中该谓词的查询不受影响。
--         低选择性谓词（user_type IN 3 值命中约 83% 行）下收益随表增长扩大。

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND INDEX_NAME = 'idx_user_type_del_flag'
);

SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE sys_user ADD INDEX idx_user_type_del_flag (user_type, del_flag)',
  'SELECT ''index idx_user_type_del_flag already exists - skip'' AS note');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 执行后请运行 H12_20260928_001__sys_user_type_delflag_index_verify.sql 并保留输出；
-- 回滚执行 U20260928_001__h12_sys_user_type_delflag_index_rollback.sql。
