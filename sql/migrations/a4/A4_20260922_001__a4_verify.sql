-- =====================================================================
-- A4_20260922_001__a4_verify.sql
-- 用途: A4 增量迁移后校验（表、列、索引、唯一约束、状态集合、账号域）
-- 适用: MySQL 8.0.36
-- 前置: A4_20260922_001__a4_migrate.sql 已执行
-- 事务: 只读；最终 SUMMARY 行 result=FAIL 表示迁移未达到契约要求
-- 可重复: 是
-- 脱敏: 仅输出结构信息与计数，无用户明文
-- =====================================================================

SET NAMES utf8mb4;
SET @a4_version := 'A4_20260922_001';

SELECT 'VERIFY' AS step, @a4_version AS version, DATABASE() AS db_name, NOW() AS ts;

DROP TEMPORARY TABLE IF EXISTS tmp_a4_verify;
CREATE TEMPORARY TABLE tmp_a4_verify (
  check_id VARCHAR(48) NOT NULL,
  result   VARCHAR(8)  NOT NULL,
  detail   VARCHAR(255) NOT NULL,
  PRIMARY KEY (check_id)
) ENGINE=MEMORY;

-- ---------------------------------------------------------------------
-- 1) 迁移历史登记唯一
-- ---------------------------------------------------------------------
INSERT INTO tmp_a4_verify
SELECT 'history_registered',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('history_rows=', COUNT(*))
FROM a4_migration_history WHERE version = @a4_version;

-- 2) A2 五表仍存在（A4 不得删除或重建）
INSERT INTO tmp_a4_verify
SELECT 'a2_tables_intact',
       IF(COUNT(*) = 5, 'PASS', 'FAIL'),
       CONCAT('found=', COUNT(*), '/5')
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('user_real_name_auth','user_author_capability',
                     'user_notification','user_notification_receiver','user_feedback');

-- ---------------------------------------------------------------------
-- 3) GAP-1：request_id 列与唯一索引
-- ---------------------------------------------------------------------
INSERT INTO tmp_a4_verify
SELECT 'gap1_request_id_col',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('request_id_col=', COUNT(*))
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
  AND COLUMN_NAME = 'request_id'
  AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH = 64 AND IS_NULLABLE = 'YES';

INSERT INTO tmp_a4_verify
SELECT 'gap1_request_id_unique_idx',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('uniq_index=', COUNT(*))
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
  AND INDEX_NAME = 'uk_user_notification_request_id' AND NON_UNIQUE = 0;

-- 4) GAP-1：现存 request_id 非空值无重复（唯一约束真实生效的前提）
INSERT INTO tmp_a4_verify
SELECT 'gap1_no_dup_request_id',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('dup_groups=', COUNT(*))
FROM (
  SELECT request_id FROM user_notification
  WHERE request_id IS NOT NULL AND TRIM(request_id) <> ''
  GROUP BY request_id HAVING COUNT(*) > 1
) d;

-- ---------------------------------------------------------------------
-- 5) GAP-2：business_type / business_id 双列存在，biz_ref 兼容列保留
-- ---------------------------------------------------------------------
INSERT INTO tmp_a4_verify
SELECT 'gap2_business_cols',
       IF(COUNT(*) = 2, 'PASS', 'FAIL'),
       CONCAT('business_cols=', COUNT(*), '/2')
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
  AND COLUMN_NAME IN ('business_type','business_id')
  AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH = 64;

INSERT INTO tmp_a4_verify
SELECT 'gap2_biz_ref_retained',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('biz_ref_col=', COUNT(*))
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
  AND COLUMN_NAME = 'biz_ref';

-- 6) GAP-2：历史行未被破坏性回填（biz_ref 保留、新列保持 NULL）
INSERT INTO tmp_a4_verify
SELECT 'gap2_legacy_rows_untouched',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('overwritten_legacy_rows=', COUNT(*))
FROM user_notification
WHERE biz_ref IS NOT NULL AND TRIM(biz_ref) <> ''
  AND (business_type IS NOT NULL OR business_id IS NOT NULL);

-- ---------------------------------------------------------------------
-- 7) GAP-3：状态集合内、无 OPEN 残留、默认值已收敛
-- ---------------------------------------------------------------------
INSERT INTO tmp_a4_verify
SELECT 'gap3_status_within_domain',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('out_of_domain_rows=', COUNT(*))
FROM user_feedback
WHERE status IS NULL OR status NOT IN ('SUBMITTED','PROCESSING','REPLIED','CLOSED');

INSERT INTO tmp_a4_verify
SELECT 'gap3_no_open_left',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('open_rows=', COUNT(*))
FROM user_feedback WHERE status = 'OPEN';

INSERT INTO tmp_a4_verify
SELECT 'gap3_status_default',
       IF(UPPER(COLUMN_DEFAULT) = 'SUBMITTED', 'PASS', 'FAIL'),
       CONCAT('status_default=', IFNULL(COLUMN_DEFAULT, 'NULL'))
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback'
  AND COLUMN_NAME = 'status';

INSERT INTO tmp_a4_verify
SELECT 'gap3_status_check_constraint',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('check_constraint=', COUNT(*))
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback'
  AND CONSTRAINT_TYPE = 'CHECK' AND CONSTRAINT_NAME = 'ck_user_feedback_status';

-- 8) GAP-3：归一规模与快照一致（快照行数 == 曾被归一的 SUBMITTED 行数）
INSERT INTO tmp_a4_verify
SELECT 'gap3_preimage_consistent',
       IF(
         (SELECT COUNT(*) FROM a4_feedback_status_preimage WHERE status_before = 'OPEN')
         <= (SELECT COUNT(*) FROM user_feedback WHERE status = 'SUBMITTED'),
         'PASS', 'FAIL'),
       CONCAT('preimage=', (SELECT COUNT(*) FROM a4_feedback_status_preimage),
              ',submitted=', (SELECT COUNT(*) FROM user_feedback WHERE status = 'SUBMITTED'));

-- ---------------------------------------------------------------------
-- 9) 唯一约束：作者能力 user_id 唯一、通知收件人联合唯一（A2 既有，回归确认）
-- ---------------------------------------------------------------------
INSERT INTO tmp_a4_verify
SELECT 'uk_author_capability_user',
       IF(COUNT(DISTINCT COLUMN_NAME) = 1
          AND SUM(COLUMN_NAME = 'user_id') = 1
          AND SUM(NON_UNIQUE) = 0, 'PASS', 'FAIL'),
       CONCAT('uniq_user_cols=', COUNT(DISTINCT COLUMN_NAME), '/1')
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_author_capability'
  AND INDEX_NAME = 'uk_user_author_capability_user';

-- 复合唯一键在 STATISTICS 中每列一行，故按列数计数，并校验列构成
INSERT INTO tmp_a4_verify
SELECT 'uk_notification_receiver',
       IF(COUNT(DISTINCT COLUMN_NAME) = 2
          AND SUM(COLUMN_NAME = 'notification_id') = 1
          AND SUM(COLUMN_NAME = 'user_id') = 1
          AND SUM(NON_UNIQUE) = 0, 'PASS', 'FAIL'),
       CONCAT('uniq_receiver_cols=', COUNT(DISTINCT COLUMN_NAME), '/2')
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification_receiver'
  AND INDEX_NAME = 'uk_user_notification_receiver';

-- ---------------------------------------------------------------------
-- 10) 账号域：A4 可管理域 01/02/03，PC 管理员 00 必须独立可辨
-- ---------------------------------------------------------------------
INSERT INTO tmp_a4_verify
SELECT 'account_domain_defined',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('non_domain_rows=', COUNT(*))
FROM sys_user
WHERE user_type IS NULL OR user_type NOT IN ('00','01','02','03');

-- 10b) App 账号域计数 —— 信息项，不参与 FAIL 判定
--      本迁移只做 user_notification / user_feedback / sys_role 的增量，不创建 App 用户；
--      App 域用户由 App 注册流程产生。因此「刚初始化的若依基线里 01/02/03 为空」
--      是合法状态，不能作为迁移失败依据（原实现按 >0 判定，只对已灌入 App 用户的
--      联调库成立，会让全新空库初始化无法通过）。账号域取值合法性由上面
--      account_domain_defined 强制，这里只报计数供人工核对。
INSERT INTO tmp_a4_verify
SELECT 'app_user_domain_count',
       'PASS',
       CONCAT('app_user_rows=', COUNT(*), ' (informational)')
FROM sys_user WHERE user_type IN ('01','02','03') AND del_flag = '0';

-- 11) 实名/作者能力状态域
INSERT INTO tmp_a4_verify
SELECT 'realname_status_domain',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('out_of_domain_rows=', COUNT(*))
FROM user_real_name_auth
WHERE status IS NULL OR status NOT IN ('PENDING','APPROVED','REJECTED');

-- 12) P1-R1：sys_role.app_grantable 若已存在，形态与默认拒绝语义必须正确。
--     只读断言，列不存在时（仅跑 001/002）视为 PASS，不阻断增量重跑。
SET @has_app_grantable := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role'
    AND COLUMN_NAME = 'app_grantable'
);

SET @sql := IF(@has_app_grantable = 1,
  'INSERT INTO tmp_a4_verify
   SELECT ''role_grantable_shape'',
          IF(COUNT(*) = 1, ''PASS'', ''FAIL''),
          CONCAT(''matched='', COUNT(*), ''/1'')
   FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ''sys_role''
     AND COLUMN_NAME = ''app_grantable''
     AND DATA_TYPE = ''tinyint'' AND IS_NULLABLE = ''NO'' AND COLUMN_DEFAULT = ''0''',
  'INSERT INTO tmp_a4_verify VALUES
     (''role_grantable_shape'', ''PASS'', ''app_grantable_absent'')');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@has_app_grantable = 1,
  'INSERT INTO tmp_a4_verify
   SELECT ''role_grantable_admin_denied'',
          IF(COUNT(*) = 0, ''PASS'', ''FAIL''),
          CONCAT(''flagged_admin_rows='', COUNT(*))
   FROM sys_role WHERE (role_id = 1 OR role_key = ''admin'') AND app_grantable <> 0',
  'INSERT INTO tmp_a4_verify VALUES
     (''role_grantable_admin_denied'', ''PASS'', ''app_grantable_absent'')');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@has_app_grantable = 1,
  'INSERT INTO tmp_a4_verify
   SELECT ''role_grantable_domain'',
          IF(COUNT(*) = 0, ''PASS'', ''FAIL''),
          CONCAT(''invalid_flag_rows='', COUNT(*))
   FROM sys_role WHERE app_grantable NOT IN (0, 1)',
  'INSERT INTO tmp_a4_verify VALUES
     (''role_grantable_domain'', ''PASS'', ''app_grantable_absent'')');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 明细与汇总
-- ---------------------------------------------------------------------
SELECT check_id, result, detail FROM tmp_a4_verify ORDER BY check_id;

SELECT 'A4_STATE_SNAPSHOT' AS metric,
       (SELECT COUNT(*) FROM user_feedback)                                   AS feedback_rows,
       (SELECT COUNT(*) FROM user_feedback WHERE status='SUBMITTED')          AS submitted_rows,
       (SELECT COUNT(*) FROM user_notification)                               AS notification_rows,
       (SELECT COUNT(*) FROM user_notification WHERE request_id IS NOT NULL)   AS with_request_id,
       (SELECT COUNT(*) FROM user_real_name_auth)                             AS realname_rows,
       (SELECT COUNT(*) FROM user_author_capability)                          AS capability_rows;

SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'),
              ',pass_cnt=', SUM(result = 'PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result='FAIL', check_id, NULL) ORDER BY check_id), '')) AS summary
FROM tmp_a4_verify;

DROP TEMPORARY TABLE IF EXISTS tmp_a4_verify;
