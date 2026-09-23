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

-- 11) 业务表结构：列存在 + 类型 + 可空性 + 主键 + 索引（不只验列名）
DROP TEMPORARY TABLE IF EXISTS tmp_a2_expected_schema;
CREATE TEMPORARY TABLE tmp_a2_expected_schema (
  table_name  VARCHAR(64) NOT NULL,
  column_name VARCHAR(64) NOT NULL,
  data_type   VARCHAR(32) NOT NULL COMMENT 'information_schema DATA_TYPE',
  min_len     INT NULL COMMENT 'varchar 最小长度；其他类型忽略',
  is_nullable VARCHAR(3) NOT NULL COMMENT 'YES/NO',
  is_pk       VARCHAR(3) NOT NULL COMMENT 'PRI=须在主键中',
  PRIMARY KEY (table_name, column_name)
) ENGINE=MEMORY;

INSERT INTO tmp_a2_expected_schema
(table_name, column_name, data_type, min_len, is_nullable, is_pk) VALUES
 ('app_sms_code','id','bigint',NULL,'NO','PRI'),
 ('app_sms_code','phone','varchar',20,'NO','NO'),
 ('app_sms_code','scene','varchar',32,'NO','NO'),
 ('app_sms_code','code_hash','varchar',64,'NO','NO'),
 ('app_sms_code','expires_at','datetime',NULL,'NO','NO'),
 ('app_sms_code','create_time','datetime',NULL,'NO','NO'),
 ('app_refresh_session','id','bigint',NULL,'NO','PRI'),
 ('app_refresh_session','user_id','bigint',NULL,'NO','NO'),
 ('app_refresh_session','token_hash','varchar',64,'NO','NO'),
 ('app_refresh_session','family_id','varchar',64,'NO','NO'),
 ('app_refresh_session','expires_at','datetime',NULL,'NO','NO'),
 ('app_user_consent','id','bigint',NULL,'NO','PRI'),
 ('app_user_consent','user_id','bigint',NULL,'NO','NO'),
 ('app_user_consent','agreement_type','varchar',32,'NO','NO'),
 ('app_user_consent','agreement_version','varchar',32,'NO','NO'),
 ('app_user_consent','accepted_at','datetime',NULL,'NO','NO'),
 ('app_user_oauth','id','bigint',NULL,'NO','PRI'),
 ('app_user_oauth','user_id','bigint',NULL,'NO','NO'),
 ('app_user_oauth','provider','varchar',16,'NO','NO'),
 ('app_user_oauth','open_id','varchar',64,'NO','NO'),
 ('user_real_name_auth','id','bigint',NULL,'NO','PRI'),
 ('user_real_name_auth','user_id','bigint',NULL,'NO','NO'),
 ('user_real_name_auth','status','varchar',32,'NO','NO'),
 ('user_real_name_auth','auditor_id','bigint',NULL,'YES','NO'),
 ('user_real_name_auth','reject_reason','varchar',500,'YES','NO'),
 ('user_phone_change_log','id','bigint',NULL,'NO','PRI'),
 ('user_phone_change_log','user_id','bigint',NULL,'NO','NO'),
 ('user_phone_change_log','old_phone_mask','varchar',32,'YES','NO'),
 ('user_phone_change_log','new_phone_mask','varchar',32,'YES','NO'),
 ('user_phone_change_log','result','varchar',32,'NO','NO'),
 ('user_author_capability','id','bigint',NULL,'NO','PRI'),
 ('user_author_capability','user_id','bigint',NULL,'NO','NO'),
 ('user_author_capability','enabled','tinyint',NULL,'NO','NO'),
 ('user_author_capability','operator_id','bigint',NULL,'YES','NO'),
 ('user_notification','id','bigint',NULL,'NO','PRI'),
 ('user_notification','type','varchar',32,'NO','NO'),
 ('user_notification','title','varchar',200,'NO','NO'),
 ('user_notification','create_time','datetime',NULL,'NO','NO'),
 ('user_notification_receiver','id','bigint',NULL,'NO','PRI'),
 ('user_notification_receiver','notification_id','bigint',NULL,'NO','NO'),
 ('user_notification_receiver','user_id','bigint',NULL,'NO','NO'),
 ('user_notification_receiver','read_at','datetime',NULL,'YES','NO'),
 ('user_notification_preference','id','bigint',NULL,'NO','PRI'),
 ('user_notification_preference','user_id','bigint',NULL,'NO','NO'),
 ('user_notification_preference','channel','varchar',32,'NO','NO'),
 ('user_notification_preference','type','varchar',32,'NO','NO'),
 ('user_notification_preference','enabled','tinyint',NULL,'NO','NO'),
 ('user_feedback','id','bigint',NULL,'NO','PRI'),
 ('user_feedback','user_id','bigint',NULL,'NO','NO'),
 ('user_feedback','category','varchar',32,'NO','NO'),
 ('user_feedback','content','varchar',2000,'NO','NO'),
 ('user_feedback','status','varchar',32,'NO','NO'),
 ('user_feedback','handler_id','bigint',NULL,'YES','NO');

-- 11a 列名存在
INSERT INTO tmp_a2_verify
SELECT 'domain_columns_present',
       IF(SUM(c.COLUMN_NAME IS NULL)=0,'PASS','FAIL'),
       CONCAT('missing_columns=', SUM(c.COLUMN_NAME IS NULL), ',expected=', COUNT(*))
FROM tmp_a2_expected_schema e
LEFT JOIN information_schema.COLUMNS c
  ON c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME=e.table_name AND c.COLUMN_NAME=e.column_name;

-- 11b 数据类型 / varchar 最小长度
INSERT INTO tmp_a2_verify
SELECT 'domain_column_types',
       IF(SUM(bad)=0,'PASS','FAIL'),
       CONCAT('type_mismatches=', SUM(bad), ',checked=', COUNT(*))
FROM (
  SELECT e.table_name, e.column_name,
         CASE
           WHEN c.COLUMN_NAME IS NULL THEN 1
           WHEN LOWER(c.DATA_TYPE) <> LOWER(e.data_type) THEN 1
           WHEN e.min_len IS NOT NULL AND IFNULL(c.CHARACTER_MAXIMUM_LENGTH,0) < e.min_len THEN 1
           ELSE 0
         END AS bad
  FROM tmp_a2_expected_schema e
  LEFT JOIN information_schema.COLUMNS c
    ON c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME=e.table_name AND c.COLUMN_NAME=e.column_name
) x;

-- 11c 可空性
INSERT INTO tmp_a2_verify
SELECT 'domain_column_nullability',
       IF(SUM(bad)=0,'PASS','FAIL'),
       CONCAT('nullability_mismatches=', SUM(bad), ',checked=', COUNT(*))
FROM (
  SELECT e.table_name, e.column_name,
         CASE WHEN c.COLUMN_NAME IS NULL THEN 1
              WHEN UPPER(IFNULL(c.IS_NULLABLE,'')) <> UPPER(e.is_nullable) THEN 1
              ELSE 0 END AS bad
  FROM tmp_a2_expected_schema e
  LEFT JOIN information_schema.COLUMNS c
    ON c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME=e.table_name AND c.COLUMN_NAME=e.column_name
) x;

-- 11d 主键覆盖
INSERT INTO tmp_a2_verify
SELECT 'domain_primary_keys',
       IF(SUM(bad)=0,'PASS','FAIL'),
       CONCAT('pk_mismatches=', SUM(bad), ',pk_cols_expected=', COUNT(*))
FROM (
  SELECT e.table_name, e.column_name,
         CASE WHEN e.is_pk<>'PRI' THEN 0
              WHEN c.COLUMN_NAME IS NULL THEN 1
              WHEN IFNULL(c.COLUMN_KEY,'') <> 'PRI' THEN 1
              ELSE 0 END AS bad
  FROM tmp_a2_expected_schema e
  LEFT JOIN information_schema.COLUMNS c
    ON c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME=e.table_name AND c.COLUMN_NAME=e.column_name
  WHERE e.is_pk='PRI'
) x;

-- 每个业务表必须存在 PRIMARY 索引
DROP TEMPORARY TABLE IF EXISTS tmp_a2_pk_tables;
CREATE TEMPORARY TABLE tmp_a2_pk_tables (
  table_name VARCHAR(64) PRIMARY KEY
) ENGINE=MEMORY;
INSERT INTO tmp_a2_pk_tables (table_name)
SELECT DISTINCT table_name FROM tmp_a2_expected_schema;

INSERT INTO tmp_a2_verify
SELECT 'domain_table_has_primary_index',
       IF(SUM(missing)=0,'PASS','FAIL'),
       CONCAT('tables_without_pk=', SUM(missing), ',tables=', COUNT(*))
FROM (
  SELECT t.table_name,
         IF(SUM(s.INDEX_NAME='PRIMARY')>0, 0, 1) AS missing
  FROM tmp_a2_pk_tables t
  LEFT JOIN information_schema.STATISTICS s
    ON s.TABLE_SCHEMA=DATABASE() AND s.TABLE_NAME=t.table_name
  GROUP BY t.table_name
) x;

-- 11e 关键索引：唯一键 + 必要普通索引（含列顺序）
DROP TEMPORARY TABLE IF EXISTS tmp_a2_expected_idx;
CREATE TEMPORARY TABLE tmp_a2_expected_idx (
  table_name  VARCHAR(64) NOT NULL,
  index_name  VARCHAR(64) NOT NULL,
  non_unique  TINYINT NOT NULL COMMENT '0=UNIQUE 1=普通',
  cols_in_order VARCHAR(255) NOT NULL COMMENT '逗号分隔，按 SEQ_IN_INDEX',
  PRIMARY KEY (table_name, index_name)
) ENGINE=MEMORY;

INSERT INTO tmp_a2_expected_idx VALUES
 ('sys_user','uk_sys_user_user_name',0,'user_name'),
 ('sys_user','uk_sys_user_phonenumber',0,'phonenumber'),
 ('app_refresh_session','uk_app_refresh_token_hash',0,'token_hash'),
 ('app_user_oauth','uk_app_oauth_provider_open',0,'provider,open_id'),
 ('user_author_capability','uk_user_author_capability_user',0,'user_id'),
 ('user_notification_receiver','uk_user_notification_receiver',0,'notification_id,user_id'),
 ('user_notification_preference','uk_user_notification_pref',0,'user_id,channel,type'),
 ('a2_migration_history','uk_a2_history_version',0,'version'),
 ('app_sms_code','idx_app_sms_phone_scene_created',1,'phone,scene,create_time'),
 ('app_sms_code','idx_app_sms_ip_created',1,'request_ip,create_time'),
 ('app_refresh_session','idx_app_refresh_user_revoked',1,'user_id,revoked_at'),
 ('user_feedback','idx_user_feedback_user',1,'user_id,create_time'),
 ('user_notification_receiver','idx_user_notification_receiver_user',1,'user_id,deleted_flag,read_at');

INSERT INTO tmp_a2_verify
SELECT 'domain_indexes',
       IF(SUM(bad)=0,'PASS','FAIL'),
       CONCAT('index_mismatches=', SUM(bad), ',expected=', COUNT(*))
FROM (
  SELECT e.table_name, e.index_name,
         CASE
           WHEN s.INDEX_NAME IS NULL THEN 1
           WHEN s.NON_UNIQUE <> e.non_unique THEN 1
           WHEN IFNULL(got.cols,'') <> e.cols_in_order THEN 1
           ELSE 0
         END AS bad
  FROM tmp_a2_expected_idx e
  LEFT JOIN information_schema.STATISTICS s
    ON s.TABLE_SCHEMA=DATABASE() AND s.TABLE_NAME=e.table_name
   AND s.INDEX_NAME=e.index_name AND s.SEQ_IN_INDEX=1
  LEFT JOIN (
    SELECT TABLE_NAME, INDEX_NAME,
           GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE()
    GROUP BY TABLE_NAME, INDEX_NAME
  ) got ON got.TABLE_NAME=e.table_name AND got.INDEX_NAME=e.index_name
) x;

-- 错误结构明细（INFO）
SELECT 'INFO' AS result, e.table_name, e.column_name,
       e.data_type AS expected_type,
       IFNULL(c.DATA_TYPE,'MISSING') AS actual_type,
       e.is_nullable AS expected_null,
       IFNULL(c.IS_NULLABLE,'MISSING') AS actual_null,
       IFNULL(c.COLUMN_KEY,'') AS actual_key
FROM tmp_a2_expected_schema e
LEFT JOIN information_schema.COLUMNS c
  ON c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME=e.table_name AND c.COLUMN_NAME=e.column_name
WHERE c.COLUMN_NAME IS NULL
   OR LOWER(IFNULL(c.DATA_TYPE,'')) <> LOWER(e.data_type)
   OR (e.min_len IS NOT NULL AND IFNULL(c.CHARACTER_MAXIMUM_LENGTH,0) < e.min_len)
   OR UPPER(IFNULL(c.IS_NULLABLE,'')) <> UPPER(e.is_nullable)
   OR (e.is_pk='PRI' AND IFNULL(c.COLUMN_KEY,'')<>'PRI');

-- 12) 关键唯一索引（名称级汇总，与 11e 互补）
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

-- 13) preimage 存在
INSERT INTO tmp_a2_verify
SELECT 'preimage_present',
       IF(COUNT(*)>0,'PASS','FAIL'),
       CONCAT('preimage_rows=', COUNT(*))
FROM a2_sys_user_preimage;

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

DROP TEMPORARY TABLE IF EXISTS tmp_a2_expected_schema;
DROP TEMPORARY TABLE IF EXISTS tmp_a2_expected_idx;
DROP TEMPORARY TABLE IF EXISTS tmp_a2_expected_uk;
DROP TEMPORARY TABLE IF EXISTS tmp_a2_pk_tables;
DROP TEMPORARY TABLE IF EXISTS tmp_a2_verify;

SELECT 'VERIFY_END' AS step, NOW() AS ts;
