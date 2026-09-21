-- =====================================================================
-- A2_20260921_001__a2_verify.sql
-- 用途: A2 迁移后校验
-- 适用: MySQL 8.0.36
-- 前置: 已在目标库执行 A2_20260921_001__a2_migrate.sql
-- 事务: 只读
-- 可重复: 是
-- 锁表影响: 无
-- 回滚: 不适用
-- 对应脚本: migrate + U20260921_001__a2_rollback.sql
-- =====================================================================

SELECT 'VERIFY_START' AS step, 'A2_20260921_001' AS version, DATABASE() AS db_name, NOW() AS ts;

-- 1) 历史版本已登记
SELECT CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('history_version_rows=', COUNT(*)) AS detail
FROM a2_migration_history
WHERE version = 'A2_20260921_001' AND rolled_back = 0;

-- 2) 唯一键存在
SELECT CASE WHEN COUNT(*) >= 2 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('unique_keys_on_sys_user=',
              SUM(INDEX_NAME IN ('uk_sys_user_user_name','uk_sys_user_phonenumber')), '/2') AS detail
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
  AND NON_UNIQUE = 0
  AND INDEX_NAME IN ('uk_sys_user_user_name','uk_sys_user_phonenumber');

-- 3) password 列长度
SELECT CASE WHEN CHARACTER_MAXIMUM_LENGTH >= 255 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('password_col_len=', IFNULL(CHARACTER_MAXIMUM_LENGTH,-1)) AS detail
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'password';

-- 4) 无重复用户名/手机号
SELECT CASE WHEN IFNULL(MAX(c),0)=0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('dup_user_name_groups=', IFNULL(MAX(c),0)) AS detail
FROM (SELECT COUNT(*) c FROM sys_user GROUP BY user_name HAVING COUNT(*)>1) t;

SELECT CASE WHEN IFNULL(MAX(c),0)=0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('dup_phone_groups=', IFNULL(MAX(c),0)) AS detail
FROM (
  SELECT COUNT(*) c FROM sys_user
  WHERE phonenumber IS NOT NULL AND TRIM(phonenumber)<>''
  GROUP BY phonenumber HAVING COUNT(*)>1
) t;

-- 5) 空手机号已规范为 NULL
SELECT CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('empty_string_phonenumber_rows=', COUNT(*)) AS detail
FROM sys_user
WHERE phonenumber IS NOT NULL AND TRIM(phonenumber)='';

-- 6) 孤儿外键为 0
SELECT CASE WHEN IFNULL(MAX(c),0)=0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('orphan_user_role_rows=', IFNULL(MAX(c),0)) AS detail
FROM (
  SELECT COUNT(*) c FROM sys_user_role ur LEFT JOIN sys_user u ON u.user_id=ur.user_id WHERE u.user_id IS NULL
  UNION ALL
  SELECT COUNT(*) FROM sys_user_role ur LEFT JOIN sys_role r ON r.role_id=ur.role_id WHERE r.role_id IS NULL
) t;

-- 7) user_id 指纹（与 precheck 对比，夹具库允许新增但不得改号/丢号）
SELECT 'INFO' AS result,
       CONCAT('user_count=', COUNT(*),
              ',user_id_sum=', IFNULL(SUM(user_id),0),
              ',nonempty_phone=', SUM(phonenumber IS NOT NULL AND TRIM(phonenumber)<>''),
              ',user_role_rows=', (SELECT COUNT(*) FROM sys_user_role),
              ',bcrypt=', SUM(password LIKE '$2a$%' OR password LIKE '$2b$%' OR password LIKE '$2y$%'),
              ',empty_pwd=', SUM(password IS NULL OR password=''),
              ',other_pwd=', SUM(password IS NOT NULL AND password<>'' AND password NOT LIKE '$2a$%' AND password NOT LIKE '$2b$%' AND password NOT LIKE '$2y$%')) AS detail
FROM sys_user;

-- 8) App/用户域表存在
SELECT CASE WHEN COUNT(*) = 12 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('a2_domain_tables=', COUNT(*), '/12') AS detail
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'a2_migration_history','app_sms_code','app_refresh_session','app_user_consent','app_user_oauth',
    'user_real_name_auth','user_phone_change_log','user_author_capability',
    'user_notification','user_notification_receiver','user_notification_preference','user_feedback'
  );

-- 9) 关键唯一约束（App 域）
SELECT CASE WHEN COUNT(DISTINCT INDEX_NAME) >= 5 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('app_domain_unique_keys=', COUNT(DISTINCT INDEX_NAME)) AS detail
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND NON_UNIQUE = 0
  AND INDEX_NAME IN (
    'uk_app_refresh_token_hash','uk_app_oauth_provider_open','uk_user_author_capability_user',
    'uk_user_notification_receiver','uk_user_notification_pref','uk_a2_history_version'
  );

-- 10) user_type 编码规范（非 00/01/02/03 的非空值必须为 0）
SELECT CASE WHEN COUNT(*)=0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('invalid_user_type_rows=', COUNT(*)) AS detail
FROM sys_user
WHERE user_type IS NOT NULL AND user_type NOT IN ('00','01','02','03');

-- 11) 密码可登录性（BCrypt 形态即可，不回读明文）
SELECT CASE WHEN SUM(password IS NOT NULL AND password <> '' AND password NOT LIKE '$2a$%' AND password NOT LIKE '$2b$%' AND password NOT LIKE '$2y$%')=0
       THEN 'PASS' ELSE 'FAIL' END AS result,
       'all_nonempty_passwords_are_bcrypt_shaped' AS detail
FROM sys_user;

SELECT 'VERIFY_END' AS step, NOW() AS ts;
