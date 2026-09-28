-- A5_20260928_001__user_profile_bio_verify.sql
-- A5 个人简介（bio）迁移后校验（只读）
--
-- 输出末尾汇总行 summary 含 fail_cnt=N；fail_cnt=0 且 result=PASS 才视为迁移成功，
-- 否则按根 README 规则停止排查。

-- CHECK-1：列存在且定义为 VARCHAR(200) NOT NULL DEFAULT ''
SELECT CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS result,
       'bio_column_definition' AS check_id
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_user'
  AND COLUMN_NAME = 'bio'
  AND COLUMN_TYPE = 'varchar(200)'
  AND IS_NULLABLE = 'NO'
  AND COLUMN_DEFAULT = '';

-- CHECK-2：存量行均有默认值（空串），无 NULL
SELECT CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       'bio_no_null_rows' AS check_id
FROM sys_user
WHERE bio IS NULL;

-- CHECK-3：无超长残留（新写入由服务端校验 ≤200）
SELECT CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       'bio_no_oversize_rows' AS check_id
FROM sys_user
WHERE CHAR_LENGTH(bio) > 200;

-- 汇总
SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'),
              ',pass_cnt=', SUM(result = 'PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result = 'FAIL', check_id, NULL) ORDER BY check_id), '')) AS summary
FROM (
  SELECT CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS result, 'bio_column_definition' AS check_id
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'bio'
    AND COLUMN_TYPE = 'varchar(200)'
    AND IS_NULLABLE = 'NO'
    AND COLUMN_DEFAULT = ''
  UNION ALL
  SELECT CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result, 'bio_no_null_rows' AS check_id
  FROM sys_user WHERE bio IS NULL
  UNION ALL
  SELECT CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result, 'bio_no_oversize_rows' AS check_id
  FROM sys_user WHERE CHAR_LENGTH(bio) > 200
) t;

SELECT 'VERIFY_END' AS step, NOW() AS ts;
