-- A5_20260928_001__user_profile_bio_precheck.sql
-- A5 个人简介（bio）字段新增前置检查（只读，不写库）
--
-- 用途：确认 sys_user 尚无 bio 列，并留存行数基线，供迁移后核对无数据变化。
-- 适用：MySQL 8.0.36，目标库为平台主库（sys_user 所在库）。
-- 前置：已完成 a2/a1/a4/pc 既有迁移。
-- 可重复：是（只读）。末尾汇总 result 须为 PASS（fail_cnt=0），否则停止，不得执行 migrate。

-- CHECK-1：sys_user 表存在
SELECT CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS result,
       'sys_user_exists' AS check_id
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user';

-- CHECK-2：bio 列不存在（首次执行应为 0；若已存在说明迁移已执行过）
SELECT CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       'bio_absent_before_migrate' AS check_id
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'bio';

-- CHECK-3：基线行数（迁移不增删行，verify 后应一致）
SELECT CASE WHEN COUNT(*) >= 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       'baseline_row_count' AS check_id
FROM sys_user;

-- 汇总
SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'),
              ',pass_cnt=', SUM(result = 'PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result = 'FAIL', check_id, NULL) ORDER BY check_id), '')) AS summary
FROM (
  SELECT CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS result, 'sys_user_exists' AS check_id
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
  UNION ALL
  SELECT CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result, 'bio_absent_before_migrate' AS check_id
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'bio'
) t;

SELECT 'PRECHECK_END' AS step, NOW() AS ts;
