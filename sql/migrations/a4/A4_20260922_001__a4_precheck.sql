-- =====================================================================
-- A4_20260922_001__a4_precheck.sql
-- 用途: A4 PC 管理能力迁移前置检查（只读，不改结构/数据）
-- 适用: MySQL 8.0.36
-- 前置: A2_20260921_001 已应用（A4 复用 A2 五张业务表，不重建）
-- 事务: 无写入；最终 SUMMARY 行 result=FAIL 时禁止执行 migrate
-- 可重复: 是
-- 锁表影响: 无
-- 回滚: 不适用（只读）
-- 脱敏: 仅输出结构信息与手机号掩码，禁止完整号段进入日志
-- 依据: A4-PC管理实施方案.md §6.2 / A4-管理接口契约.md / P0 裁决 GAP-1～GAP-6
-- =====================================================================

SET NAMES utf8mb4;

SELECT 'CHECK' AS step, 'A4_20260922_001_precheck' AS item, DATABASE() AS db_name, NOW() AS ts;

DROP TEMPORARY TABLE IF EXISTS tmp_a4_precheck;
CREATE TEMPORARY TABLE tmp_a4_precheck (
  check_id   VARCHAR(48) NOT NULL,
  result     VARCHAR(8)  NOT NULL,
  detail     VARCHAR(255) NOT NULL,
  PRIMARY KEY (check_id)
) ENGINE=MEMORY;

-- ---------------------------------------------------------------------
-- 1) 环境
-- ---------------------------------------------------------------------
INSERT INTO tmp_a4_precheck VALUES (
  'mysql_version',
  IF(VERSION() LIKE '8.0.%', 'PASS', 'FAIL'),
  CONCAT('mysql_version=', VERSION())
);

-- 2) A4 依赖的 A2 五张业务表必须已存在（A4 只做增量，不重建）
INSERT INTO tmp_a4_precheck
SELECT 'a2_business_tables',
       IF(COUNT(*) = 5, 'PASS', 'FAIL'),
       CONCAT('found=', COUNT(*), '/5')
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('user_real_name_auth','user_author_capability',
                     'user_notification','user_notification_receiver','user_feedback');

-- 3) 若依核心表（PC 管理域依赖）
INSERT INTO tmp_a4_precheck
SELECT 'core_tables',
       IF(COUNT(*) = 4, 'PASS', 'FAIL'),
       CONCAT('found=', COUNT(*), '/4')
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('sys_user','sys_role','sys_menu','sys_user_role');

-- ---------------------------------------------------------------------
-- 4) GAP-1：user_notification.request_id 目标列是否已存在（存在则 migrate 跳过新增）
-- ---------------------------------------------------------------------
INSERT INTO tmp_a4_precheck
SELECT 'gap1_request_id_col',
       IF(COUNT(*) IN (0,1), 'PASS', 'FAIL'),
       CONCAT('request_id_col_count=', COUNT(*))
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
  AND COLUMN_NAME = 'request_id';

-- 5) GAP-2：business_type / business_id 目标列
INSERT INTO tmp_a4_precheck
SELECT 'gap2_business_cols',
       IF(COUNT(*) <= 2, 'PASS', 'FAIL'),
       CONCAT('business_col_count=', COUNT(*), '/2')
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
  AND COLUMN_NAME IN ('business_type','business_id');

-- 6) GAP-2：兼容列 biz_ref 必须保留（裁决要求保留历史数据，不删除）
INSERT INTO tmp_a4_precheck
SELECT 'gap2_biz_ref_kept',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('biz_ref_col_count=', COUNT(*))
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
  AND COLUMN_NAME = 'biz_ref';

-- ---------------------------------------------------------------------
-- 7) GAP-3：反馈状态存量分布 —— 未知值必须阻断，不允许猜测转换
--    只允许 OPEN / SUBMITTED / PROCESSING / REPLIED / CLOSED / NULL
-- ---------------------------------------------------------------------
INSERT INTO tmp_a4_precheck
SELECT 'gap3_feedback_status_known',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('unknown_status_rows=', COUNT(*))
FROM user_feedback
WHERE status IS NOT NULL
  AND status NOT IN ('OPEN','SUBMITTED','PROCESSING','REPLIED','CLOSED');

-- 8) GAP-3：status 列类型必须可容纳目标值
INSERT INTO tmp_a4_precheck
SELECT 'gap3_status_col_type',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('status_col_count=', COUNT(*))
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_feedback'
  AND COLUMN_NAME = 'status' AND DATA_TYPE = 'varchar'
  AND CHARACTER_MAXIMUM_LENGTH >= 32;

-- ---------------------------------------------------------------------
-- 9) GAP-1 幂等：已有 request_id 时不得存在重复非空值（否则唯一索引无法建立）
--    列不存在时（首次迁移）本项自动 PASS；用动态 SQL 避免引用不存在的列
-- ---------------------------------------------------------------------
SET @has_request_id := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
    AND COLUMN_NAME = 'request_id'
);
SET @sql := IF(@has_request_id = 1,
  'INSERT INTO tmp_a4_precheck
   SELECT ''gap1_request_id_unique_safe'',
          IF(COUNT(*) = 0, ''PASS'', ''FAIL''),
          CONCAT(''dup_request_id_groups='', COUNT(*))
   FROM (SELECT request_id FROM user_notification
         WHERE request_id IS NOT NULL AND TRIM(request_id) <> ''''
         GROUP BY request_id HAVING COUNT(*) > 1) d',
  'INSERT INTO tmp_a4_precheck VALUES
     (''gap1_request_id_unique_safe'', ''PASS'', ''request_id_absent_first_run'')');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 10) 权限父菜单唯一性 —— 无法唯一定位时必须 FAIL，禁止猜测 menu_id
--     父菜单 = 顶级目录「用户管理」，path='user'，menu_type='M'
-- ---------------------------------------------------------------------
INSERT INTO tmp_a4_precheck
SELECT 'parent_menu_unique',
       IF(COUNT(*) <= 1, 'PASS', 'FAIL'),
       CONCAT('user_dir_menu_count=', COUNT(*))
FROM sys_menu
WHERE parent_id = 0 AND path = 'user';

-- 11) 现有 A4 权限标识冲突检查：同名 perms 若已存在且不属于 A4，则冲突
INSERT INTO tmp_a4_precheck
SELECT 'perms_conflict_free',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('conflicting_perms=', COUNT(*))
FROM sys_menu
WHERE perms IN (
  'user:app:list','user:app:query','user:app:status','user:app:grant',
  'user:realname:list','user:realname:query','user:realname:audit',
  'user:creator:list','user:creator:update',
  'user:message:list','user:message:add','user:message:query',
  'user:feedback:list','user:feedback:query','user:feedback:handle'
)
AND menu_name NOT LIKE 'A4-%';

-- ---------------------------------------------------------------------
-- 12) 账号域基线：user_type 取值必须已归一到 00/01/02/03
-- ---------------------------------------------------------------------
INSERT INTO tmp_a4_precheck
SELECT 'user_type_domain',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('non_domain_user_rows=', COUNT(*))
FROM sys_user
WHERE user_type IS NULL OR user_type NOT IN ('00','01','02','03');

-- ---------------------------------------------------------------------
-- 13) GAP-3 归一规模（信息项，不参与 FAIL 判定）
-- ---------------------------------------------------------------------
INSERT INTO tmp_a4_precheck
SELECT 'gap3_open_rows_info',
       'PASS',
       CONCAT('open_rows_to_normalize=', COUNT(*))
FROM user_feedback WHERE status = 'OPEN';

-- ---------------------------------------------------------------------
-- 14) P1-R1：sys_role.app_grantable 若已存在，形态必须正确
--     列不存在（首次运行 003 之前）视为 PASS；形态不符则 FAIL，
--     避免 003 静默沿用错误列定义。
-- ---------------------------------------------------------------------
INSERT INTO tmp_a4_precheck
SELECT 'role_grantable_col_shape',
       IF(COUNT(*) = 0, 'PASS',
          IF(SUM(DATA_TYPE = 'tinyint' AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT = '0') = 1,
             'PASS', 'FAIL')),
       CONCAT('present=', COUNT(*))
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role'
  AND COLUMN_NAME = 'app_grantable';

-- 15) P1-R1：超级管理员角色不得被标记为可授权
--     列不存在时（003 之前）自动 PASS；用动态 SQL 避免引用不存在的列
SET @has_app_grantable := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role'
    AND COLUMN_NAME = 'app_grantable'
);
SET @sql := IF(@has_app_grantable = 1,
  'INSERT INTO tmp_a4_precheck
   SELECT ''role_grantable_admin_denied'',
          IF(COUNT(*) = 0, ''PASS'', ''FAIL''),
          CONCAT(''flagged_admin_rows='', COUNT(*))
   FROM sys_role WHERE (role_id = 1 OR role_key = ''admin'') AND app_grantable <> 0',
  'INSERT INTO tmp_a4_precheck VALUES
     (''role_grantable_admin_denied'', ''PASS'', ''app_grantable_absent'')');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 明细输出
-- ---------------------------------------------------------------------
SELECT check_id, result, detail FROM tmp_a4_precheck ORDER BY check_id;

-- 脱敏：仅输出掩码手机号与计数
SELECT user_id, user_name,
       CASE
         WHEN phonenumber IS NULL OR TRIM(phonenumber) = '' THEN 'NULL_OR_EMPTY'
         WHEN CHAR_LENGTH(phonenumber) >= 7
           THEN CONCAT(LEFT(phonenumber,3), '****', RIGHT(phonenumber,4))
         ELSE '***'
       END AS phone_mask,
       user_type, status, del_flag
FROM sys_user
WHERE user_type IN ('01','02','03')
ORDER BY user_id
LIMIT 20;

SELECT 'A4_APP_USER_DOMAIN_COUNT' AS metric,
       SUM(user_type = '01') AS type_01,
       SUM(user_type = '02') AS type_02,
       SUM(user_type = '03') AS type_03,
       SUM(user_type = '00') AS type_00
FROM sys_user;

-- 最终汇总：任一 FAIL 即阻断 migrate
SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'),
              ',pass_cnt=', SUM(result = 'PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result='FAIL', check_id, NULL) ORDER BY check_id), '')) AS summary
FROM tmp_a4_precheck;

DROP TEMPORARY TABLE IF EXISTS tmp_a4_precheck;
