-- =====================================================================
-- A2_20260921_001__a2_precheck.sql
-- 用途: A2 数据库兼容迁移前置检查（只读，不改结构/数据）
-- 适用: MySQL 8.0.36
-- 前置: 当前库已具备若依核心表 sys_user/sys_role/sys_user_role
-- 事务: 无写入；最终 SUMMARY 行 result=FAIL 时禁止 migrate
-- 可重复: 是
-- 锁表影响: 无
-- 回滚: 不适用（只读）
-- 脱敏: 手机号仅输出掩码，禁止完整号段进入日志
-- =====================================================================

SELECT 'CHECK' AS step, 'A2_20260921_001_precheck' AS item, DATABASE() AS db_name, NOW() AS ts;

-- 收集全部检查结果到临时结果集（用派生表汇总，避免漏项）
DROP TEMPORARY TABLE IF EXISTS tmp_a2_precheck;
CREATE TEMPORARY TABLE tmp_a2_precheck (
  check_id   VARCHAR(32) NOT NULL,
  result     VARCHAR(8)  NOT NULL,
  detail     VARCHAR(255) NOT NULL,
  PRIMARY KEY (check_id)
) ENGINE=MEMORY;

-- 1) MySQL 版本
INSERT INTO tmp_a2_precheck VALUES (
  'mysql_version',
  IF(VERSION() LIKE '8.0.%', 'PASS', 'FAIL'),
  CONCAT('mysql_version=', VERSION())
);

-- 2) 核心表
INSERT INTO tmp_a2_precheck
SELECT 'core_tables',
       IF(COUNT(*) = 3, 'PASS', 'FAIL'),
       CONCAT('core_tables_found=', COUNT(*), '/3')
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('sys_user','sys_role','sys_user_role');

-- 3) sys_user 必需列
INSERT INTO tmp_a2_precheck
SELECT 'sys_user_required_cols',
       IF(COUNT(*) >= 9, 'PASS', 'FAIL'),
       CONCAT('sys_user_required_cols=', COUNT(*), '/9')
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
  AND COLUMN_NAME IN ('user_id','user_name','nick_name','user_type','email','phonenumber','password','status','del_flag');

-- 4) 禁止残留不可追踪表名
INSERT INTO tmp_a2_precheck
SELECT 'forbidden_tables',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('forbidden_legacy_tables=', COUNT(*))
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('final','new','fix2');

-- 5) 重复用户名
INSERT INTO tmp_a2_precheck
SELECT 'dup_user_name',
       IF(IFNULL(MAX(c),0) = 0, 'PASS', 'FAIL'),
       CONCAT('dup_user_name_groups=', IFNULL(MAX(c),0))
FROM (
  SELECT COUNT(*) AS c FROM sys_user GROUP BY user_name HAVING COUNT(*) > 1
) t;

-- 6) 重复非空手机号
INSERT INTO tmp_a2_precheck
SELECT 'dup_phone',
       IF(IFNULL(MAX(c),0) = 0, 'PASS', 'FAIL'),
       CONCAT('dup_phone_groups=', IFNULL(MAX(c),0))
FROM (
  SELECT COUNT(*) AS c
  FROM sys_user
  WHERE phonenumber IS NOT NULL AND TRIM(phonenumber) <> ''
  GROUP BY phonenumber
  HAVING COUNT(*) > 1
) t;

-- 7) 孤儿用户角色关联
INSERT INTO tmp_a2_precheck
SELECT 'orphan_user_role',
       IF(IFNULL(MAX(c),0) = 0, 'PASS', 'FAIL'),
       CONCAT('orphan_user_role_rows=', IFNULL(MAX(c),0))
FROM (
  SELECT COUNT(*) AS c FROM sys_user_role ur LEFT JOIN sys_user u ON u.user_id = ur.user_id WHERE u.user_id IS NULL
  UNION ALL
  SELECT COUNT(*) FROM sys_user_role ur LEFT JOIN sys_role r ON r.role_id = ur.role_id WHERE r.role_id IS NULL
) t;

-- 8) 密码格式
INSERT INTO tmp_a2_precheck
SELECT 'password_format',
       IF(IFNULL(SUM(password IS NOT NULL AND password <> ''
                    AND password NOT LIKE '$2a$%'
                    AND password NOT LIKE '$2b$%'
                    AND password NOT LIKE '$2y$%'),0) = 0, 'PASS', 'FAIL'),
       CONCAT('pwd_empty=', IFNULL(SUM(password IS NULL OR password=''),0),
              ',bcrypt=', IFNULL(SUM(password LIKE '$2a$%' OR password LIKE '$2b$%' OR password LIKE '$2y$%'),0),
              ',other=', IFNULL(SUM(password IS NOT NULL AND password <> ''
                    AND password NOT LIKE '$2a$%'
                    AND password NOT LIKE '$2b$%'
                    AND password NOT LIKE '$2y$%'),0))
FROM sys_user;

-- 明细输出（脱敏）
SELECT check_id, result, detail FROM tmp_a2_precheck ORDER BY check_id;

SELECT 'INFO' AS result,
       CONCAT('user_count=', COUNT(*),
              ',user_id_min=', IFNULL(MIN(user_id), -1),
              ',user_id_max=', IFNULL(MAX(user_id), -1),
              ',user_id_sum=', IFNULL(SUM(user_id), 0),
              ',nonempty_phone=', IFNULL(SUM(phonenumber IS NOT NULL AND TRIM(phonenumber) <> ''),0),
              ',user_role_rows=', (SELECT COUNT(*) FROM sys_user_role)) AS detail
FROM sys_user;

SELECT user_id,
       user_name,
       IFNULL(user_type,'') AS user_type,
       CASE
         WHEN phonenumber IS NULL OR TRIM(phonenumber) = '' THEN 'NULL_OR_EMPTY'
         WHEN CHAR_LENGTH(phonenumber) >= 7
           THEN CONCAT(LEFT(phonenumber,3), '****', RIGHT(phonenumber,4))
         ELSE '***'
       END AS phone_mask,
       IFNULL(status,'') AS status,
       IFNULL(del_flag,'') AS del_flag,
       CASE WHEN password IS NULL OR password = '' THEN 'EMPTY'
            WHEN password LIKE '$2a$%' OR password LIKE '$2b$%' OR password LIKE '$2y$%' THEN 'BCRYPT'
            ELSE 'OTHER' END AS pwd_format
FROM sys_user
ORDER BY user_id;

-- 最终汇总：统计 tmp 内全部 FAIL（必须覆盖上述所有检查项）
SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'),
              ',pass_cnt=', SUM(result = 'PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result='FAIL', check_id, NULL) ORDER BY check_id), '')) AS summary
FROM tmp_a2_precheck;

DROP TEMPORARY TABLE IF EXISTS tmp_a2_precheck;
