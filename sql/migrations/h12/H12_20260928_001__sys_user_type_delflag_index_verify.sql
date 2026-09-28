-- H12_20260928_001__sys_user_type_delflag_index_verify.sql
-- 验证（执行 ADD INDEX 后运行；输出须保留作为变更留证）：
--   期望 1：sys_user 存在索引 idx_user_type_del_flag（列序 user_type, del_flag）；
--   期望 2：目标 COUNT 查询的 EXPLAIN 使用该索引（type=range，Extra 含 Using index）。

-- 1) 索引存在性与列序
SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_user'
  AND INDEX_NAME = 'idx_user_type_del_flag'
ORDER BY SEQ_IN_INDEX;

-- 2) 目标查询执行计划（对照第 21 批隔离库基线：修复前 type=ALL）
EXPLAIN SELECT count(0) FROM sys_user u
WHERE u.user_type IN ('01','02','03') AND u.del_flag = '0';
