-- =====================================================================
-- A2_20260921_001__a2_verify.sql
-- 用途: A2 迁移后校验（含同名表结构/索引核对，不只是表名计数）
-- 适用: MySQL 8.0.36
-- 前置: 已在目标库执行 A2_20260921_001__a2_migrate.sql
-- 事务: 只读
-- 可重复: 是
-- 脱敏: 手机号掩码输出
-- =====================================================================

SELECT 'VERIFY_START' AS step, 'A2_20260921_001' AS version, DATABASE() AS db_name, NOW() AS ts;

DROP TEMPORARY TABLE IF EXISTS tmp_a2_verify;
CREATE TEMPORARY TABLE tmp_a2_verify (
  check_id VARCHAR(48) NOT NULL,
  result   VARCHAR(8)  NOT NULL,
  detail   VARCHAR(255) NOT NULL,
  PRIMARY KEY (check_id)
) ENGINE=MEMORY;

-- 1) 历史版本
INSERT INTO tmp_a2_verify
SELECT 'history_active',
       IF(COUNT(*)=1,'PASS','FAIL'),
       CONCAT('history_version_rows=', COUNT(*))
FROM a2_migration_history
WHERE version='A2_20260921_001' AND rolled_back=0;

-- 2) sys_user 唯一键
INSERT INTO tmp_a2_verify
SELECT 'sys_user_unique_keys',
       IF(COUNT(DISTINCT INDEX_NAME)=2,'PASS','FAIL'),
       CONCAT('unique_keys=', COUNT(DISTINCT INDEX_NAME), '/2')
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user' AND NON_UNIQUE=0
  AND INDEX_NAME IN ('uk_sys_user_user_name','uk_sys_user_phonenumber');

-- 3) password 长度
INSERT INTO tmp_a2_verify
SELECT 'password_col_len',
       IF(IFNULL(MAX(CHARACTER_MAXIMUM_LENGTH),0)>=255,'PASS','FAIL'),
       CONCAT('password_col_len=', IFNULL(MAX(CHARACTER_MAXIMUM_LENGTH),-1))
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user' AND COLUMN_NAME='password';

-- 4) 重复用户名/手机号
INSERT INTO tmp_a2_verify
SELECT 'dup_user_name',
       IF(IFNULL(MAX(c),0)=0,'PASS','FAIL'),
       CONCAT('dup_user_name_groups=', IFNULL(MAX(c),0))
FROM (SELECT COUNT(*) c FROM sys_user GROUP BY user_name HAVING COUNT(*)>1) t;

INSERT INTO tmp_a2_verify
SELECT 'dup_phone',
       IF(IFNULL(MAX(c),0)=0,'PASS','FAIL'),
       CONCAT('dup_phone_groups=', IFNULL(MAX(c),0))
FROM (
  SELECT COUNT(*) c FROM sys_user
  WHERE phonenumber IS NOT NULL AND TRIM(phonenumber)<>''
  GROUP BY phonenumber HAVING COUNT(*)>1
) t;

-- 5) 空手机号
INSERT INTO tmp_a2_verify
SELECT 'empty_phone_normalized',
       IF(COUNT(*)=0,'PASS','FAIL'),
       CONCAT('empty_string_phonenumber_rows=', COUNT(*))
FROM sys_user WHERE phonenumber IS NOT NULL AND TRIM(phonenumber)='';

-- 6) 孤儿关联
INSERT INTO tmp_a2_verify
SELECT 'orphan_user_role',
       IF(IFNULL(MAX(c),0)=0,'PASS','FAIL'),
       CONCAT('orphan_user_role_rows=', IFNULL(MAX(c),0))
FROM (
  SELECT COUNT(*) c FROM sys_user_role ur LEFT JOIN sys_user u ON u.user_id=ur.user_id WHERE u.user_id IS NULL
  UNION ALL
  SELECT COUNT(*) FROM sys_user_role ur LEFT JOIN sys_role r ON r.role_id=ur.role_id WHERE r.role_id IS NULL
) t;

-- 7) user_type 编码
INSERT INTO tmp_a2_verify
SELECT 'user_type_codes',
       IF(COUNT(*)=0,'PASS','FAIL'),
       CONCAT('invalid_user_type_rows=', COUNT(*))
FROM sys_user WHERE user_type IS NOT NULL AND user_type NOT IN ('00','01','02','03');

-- 8) 密码形态
INSERT INTO tmp_a2_verify
SELECT 'password_shape',
       IF(IFNULL(SUM(password IS NOT NULL AND password<>'' AND password NOT LIKE '$2a$%' AND password NOT LIKE '$2b$%' AND password NOT LIKE '$2y$%'),0)=0,'PASS','FAIL'),
       'nonempty_passwords_bcrypt_shaped'
FROM sys_user;

-- 9) 控制表存在
INSERT INTO tmp_a2_verify
SELECT 'control_tables',
       IF(COUNT(*)=3,'PASS','FAIL'),
       CONCAT('control_tables=', COUNT(*), '/3 (history,ownership,preimage)')
FROM information_schema.TABLES
WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN ('a2_migration_history','a2_table_ownership','a2_sys_user_preimage');

-- 10) 表归属登记完整（11 个业务表）
INSERT INTO tmp_a2_verify
SELECT 'ownership_rows',
       IF(COUNT(*)=11,'PASS','FAIL'),
       CONCAT('ownership_rows=', COUNT(*), '/11')
FROM a2_table_ownership WHERE version='A2_20260921_001';

-- 11) 业务表存在且结构关键列齐全（逐表，而非只数表名）
-- 期望列映射
DROP TEMPORARY TABLE IF EXISTS tmp_a2_expected_cols;
CREATE TEMPORARY TABLE tmp_a2_expected_cols (
  table_name  VARCHAR(64) NOT NULL,
  column_name VARCHAR(64) NOT NULL,
  PRIMARY KEY (table_name, column_name)
) ENGINE=MEMORY;

INSERT INTO tmp_a2_expected_cols VALUES
 ('app_sms_code','id'),('app_sms_code','phone'),('app_sms_code','scene'),('app_sms_code','code_hash'),('app_sms_code','expires_at'),('app_sms_code','create_time'),
 ('app_refresh_session','id'),('app_refresh_session','user_id'),('app_refresh_session','token_hash'),('app_refresh_session','family_id'),('app_refresh_session','expires_at'),
 ('app_user_consent','id'),('app_user_consent','user_id'),('app_user_consent','agreement_type'),('app_user_consent','agreement_version'),('app_user_consent','accepted_at'),
 ('app_user_oauth','id'),('app_user_oauth','user_id'),('app_user_oauth','provider'),('app_user_oauth','open_id'),
 ('user_real_name_auth','id'),('user_real_name_auth','user_id'),('user_real_name_auth','status'),('user_real_name_auth','auditor_id'),('user_real_name_auth','reject_reason'),
 ('user_phone_change_log','id'),('user_phone_change_log','user_id'),('user_phone_change_log','old_phone_mask'),('user_phone_change_log','new_phone_mask'),('user_phone_change_log','result'),
 ('user_author_capability','id'),('user_author_capability','user_id'),('user_author_capability','enabled'),('user_author_capability','operator_id'),
 ('user_notification','id'),('user_notification','type'),('user_notification','title'),('user_notification','create_time'),
 ('user_notification_receiver','id'),('user_notification_receiver','notification_id'),('user_notification_receiver','user_id'),('user_notification_receiver','read_at'),
 ('user_notification_preference','id'),('user_notification_preference','user_id'),('user_notification_preference','channel'),('user_notification_preference','type'),('user_notification_preference','enabled'),
 ('user_feedback','id'),('user_feedback','user_id'),('user_feedback','category'),('user_feedback','content'),('user_feedback','status'),('user_feedback','handler_id');

INSERT INTO tmp_a2_verify
SELECT 'domain_table_columns',
       IF(SUM(missing)=0,'PASS','FAIL'),
       CONCAT('missing_columns=', SUM(missing), ',tables_checked=', COUNT(*))
FROM (
  SELECT e.table_name,
         SUM(c.COLUMN_NAME IS NULL) AS missing
  FROM tmp_a2_expected_cols e
  LEFT JOIN information_schema.COLUMNS c
    ON c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME=e.table_name AND c.COLUMN_NAME=e.column_name
  GROUP BY e.table_name
) x;

-- 列出缺失明细（INFO，便于失败定位）
SELECT 'INFO' AS result,
       e.table_name, e.column_name
FROM tmp_a2_expected_cols e
LEFT JOIN information_schema.COLUMNS c
  ON c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME=e.table_name AND c.COLUMN_NAME=e.column_name
WHERE c.COLUMN_NAME IS NULL;

-- 12) 关键唯一索引（列级）
DROP TEMPORARY TABLE IF EXISTS tmp_a2_expected_uk;
CREATE TEMPORARY TABLE tmp_a2_expected_uk (
  table_name VARCHAR(64) NOT NULL,
  index_name VARCHAR(64) NOT NULL,
  PRIMARY KEY (index_name)
) ENGINE=MEMORY;
INSERT INTO tmp_a2_expected_uk VALUES
 ('sys_user','uk_sys_user_user_name'),
 ('sys_user','uk_sys_user_phonenumber'),
 ('app_refresh_session','uk_app_refresh_token_hash'),
 ('app_user_oauth','uk_app_oauth_provider_open'),
 ('user_author_capability','uk_user_author_capability_user'),
 ('user_notification_receiver','uk_user_notification_receiver'),
 ('user_notification_preference','uk_user_notification_pref'),
 ('a2_migration_history','uk_a2_history_version');

INSERT INTO tmp_a2_verify
SELECT 'expected_unique_indexes',
       IF(SUM(missing)=0,'PASS','FAIL'),
       CONCAT('missing_uk=', SUM(missing), ',expected=', COUNT(*))
FROM (
  SELECT u.index_name, SUM(s.INDEX_NAME IS NULL) AS missing
  FROM tmp_a2_expected_uk u
  LEFT JOIN information_schema.STATISTICS s
    ON s.TABLE_SCHEMA=DATABASE() AND s.TABLE_NAME=u.table_name
   AND s.INDEX_NAME=u.index_name AND s.NON_UNIQUE=0
  GROUP BY u.index_name
) x;

-- 错误结构探测：若存在 marker 这类非规格列且缺少 content，应 FAIL
INSERT INTO tmp_a2_verify
SELECT 'user_feedback_schema_shape',
       IF(
         SUM(c.COLUMN_NAME='content')>0 AND SUM(c.COLUMN_NAME='marker')=0
         AND SUM(c.COLUMN_NAME='user_id')>0 AND SUM(c.COLUMN_NAME='status')>0,
         'PASS','FAIL'
       ),
       CONCAT('cols=', IFNULL(GROUP_CONCAT(c.COLUMN_NAME ORDER BY c.ORDINAL_POSITION),''))
FROM information_schema.COLUMNS c
WHERE c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME='user_feedback';

-- 13) preimage 与当前已迁移用户对照（INFO：迁移后应与 preimage 不同才有意义；至少 preimage 非空）
INSERT INTO tmp_a2_verify
SELECT 'preimage_present',
       IF(COUNT(*)>0,'PASS','FAIL'),
       CONCAT('preimage_rows=', COUNT(*))
FROM a2_sys_user_preimage;

-- 指纹 INFO
SELECT 'INFO' AS result,
       CONCAT('user_count=', COUNT(*),
              ',user_id_sum=', IFNULL(SUM(user_id),0),
              ',nonempty_phone=', IFNULL(SUM(phonenumber IS NOT NULL AND TRIM(phonenumber)<>''),0),
              ',user_role_rows=', (SELECT COUNT(*) FROM sys_user_role)) AS detail
FROM sys_user;

SELECT check_id, result, detail FROM tmp_a2_verify ORDER BY check_id;

SELECT CASE WHEN SUM(result='FAIL')=0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result='FAIL'),
              ',pass_cnt=', SUM(result='PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result='FAIL',check_id,NULL) ORDER BY check_id),'')) AS summary
FROM tmp_a2_verify;

DROP TEMPORARY TABLE IF EXISTS tmp_a2_expected_cols;
DROP TEMPORARY TABLE IF EXISTS tmp_a2_expected_uk;
DROP TEMPORARY TABLE IF EXISTS tmp_a2_verify;

SELECT 'VERIFY_END' AS step, NOW() AS ts;
