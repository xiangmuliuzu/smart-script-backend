-- =====================================================================
-- A2_20260921_001__a2_precheck.sql
-- 用途: A2 数据库兼容迁移前置检查（只读，不改结构/数据）
-- 适用: MySQL 8.0.36
-- 前置: 当前库已具备若依核心表 sys_user/sys_role/sys_user_role
-- 事务: 无写入；失败时通过 result 集合中 fail_cnt>0 判定
-- 可重复: 是
-- 锁表影响: 无（只读 information_schema / SELECT）
-- 回滚: 不适用（只读）
-- 对应回滚: U20260921_001__a2_rollback.sql（正式迁移后）
-- =====================================================================
-- 使用: mysql -D <target_db> < 本文件
-- 约定: PASS 行表示可执行 migrate；FAIL 行表示必须停止
-- =====================================================================

SELECT 'CHECK' AS step, 'A2_20260921_001_precheck' AS item, DATABASE() AS db_name, NOW() AS ts;

-- 1) MySQL 版本
SELECT IF(VERSION() LIKE '8.0.%', 'PASS', 'FAIL') AS result,
       CONCAT('mysql_version=', VERSION()) AS detail
FROM DUAL;

-- 2) 核心表存在性
SELECT CASE WHEN COUNT(*) = 3 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('core_tables_found=', COUNT(*), '/3 (sys_user,sys_role,sys_user_role)') AS detail
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('sys_user','sys_role','sys_user_role');

-- 3) sys_user 关键列存在性
SELECT CASE WHEN COUNT(*) >= 9 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('sys_user_required_cols=', COUNT(*), '/9') AS detail
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
  AND COLUMN_NAME IN ('user_id','user_name','nick_name','user_type','email','phonenumber','password','status','del_flag');

-- 4) 禁止已存在不可追踪迁移冲突标记（允许重跑：history 表可有可无）
SELECT CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('forbidden_legacy_tables=', COUNT(*), ' (expect 0: final.sql/new.sql 等不在库内核对)') AS detail
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('final','new','fix2');

-- 5) 重复用户名（必须为 0）
SELECT CASE WHEN IFNULL(MAX(c),0) = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('dup_user_name_groups=', IFNULL(MAX(c),0)) AS detail
FROM (
  SELECT COUNT(*) AS c FROM sys_user GROUP BY user_name HAVING COUNT(*) > 1
) t;

-- 6) 重复非空手机号（必须为 0）
SELECT CASE WHEN IFNULL(MAX(c),0) = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('dup_phone_groups=', IFNULL(MAX(c),0)) AS detail
FROM (
  SELECT COUNT(*) AS c
  FROM sys_user
  WHERE phonenumber IS NOT NULL AND TRIM(phonenumber) <> ''
  GROUP BY phonenumber
  HAVING COUNT(*) > 1
) t;

-- 7) 孤儿用户角色关联（必须为 0）
SELECT CASE WHEN IFNULL(MAX(c),0) = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('orphan_user_role_rows=', IFNULL(MAX(c),0)) AS detail
FROM (
  SELECT COUNT(*) AS c FROM sys_user_role ur LEFT JOIN sys_user u ON u.user_id = ur.user_id WHERE u.user_id IS NULL
  UNION ALL
  SELECT COUNT(*) FROM sys_user_role ur LEFT JOIN sys_role r ON r.role_id = ur.role_id WHERE r.role_id IS NULL
) t;

-- 8) 密码格式统计（EMPTY/BCRYPT 可接受；OTHER>0 则 FAIL）
SELECT 'INFO' AS result,
       CONCAT('pwd_empty=', SUM(password IS NULL OR password = ''),
              ',pwd_bcrypt=', SUM(password LIKE '$2a$%' OR password LIKE '$2b$%' OR password LIKE '$2y$%'),
              ',pwd_other=', SUM(password IS NOT NULL AND password <> '' AND password NOT LIKE '$2a$%' AND password NOT LIKE '$2b$%' AND password NOT LIKE '$2y$%')) AS detail
FROM sys_user;

SELECT CASE WHEN SUM(password IS NOT NULL AND password <> ''
                 AND password NOT LIKE '$2a$%'
                 AND password NOT LIKE '$2b$%'
                 AND password NOT LIKE '$2y$%') = 0
       THEN 'PASS' ELSE 'FAIL' END AS result,
       'password_format_check' AS detail
FROM sys_user;

-- 9) user_id 迁移前指纹（用于迁移后对比）
SELECT 'INFO' AS result,
       CONCAT('user_count=', COUNT(*),
              ',user_id_min=', IFNULL(MIN(user_id), -1),
              ',user_id_max=', IFNULL(MAX(user_id), -1),
              ',user_id_sum=', IFNULL(SUM(user_id), 0),
              ',nonempty_phone=', SUM(phonenumber IS NOT NULL AND TRIM(phonenumber) <> ''),
              ',user_role_rows=', (SELECT COUNT(*) FROM sys_user_role)) AS detail
FROM sys_user;

SELECT user_id, user_name, IFNULL(user_type,'') AS user_type,
       IFNULL(phonenumber,'') AS phonenumber,
       IFNULL(status,'') AS status,
       IFNULL(del_flag,'') AS del_flag,
       CASE WHEN password IS NULL OR password = '' THEN 'EMPTY'
            WHEN password LIKE '$2a$%' OR password LIKE '$2b$%' OR password LIKE '$2y$%' THEN 'BCRYPT'
            ELSE 'OTHER' END AS pwd_format
FROM sys_user
ORDER BY user_id;

-- 10) 汇总
SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'), ',pass_cnt=', SUM(result = 'PASS')) AS summary
FROM (
  SELECT 'FAIL' AS result FROM (
    SELECT COUNT(*) AS c FROM (
      SELECT COUNT(*) AS c2 FROM sys_user GROUP BY user_name HAVING COUNT(*) > 1
    ) a WHERE c2 > 0
  ) x WHERE x.c > 0
  UNION ALL
  SELECT IF(COUNT(*) = 3, 'PASS', 'FAIL') FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('sys_user','sys_role','sys_user_role')
) s;
