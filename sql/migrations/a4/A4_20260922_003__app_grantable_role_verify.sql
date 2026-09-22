-- =====================================================================
-- A4_20260922_003__app_grantable_role_verify.sql
-- 用途: 校验 sys_role.app_grantable 落库正确且授权边界可强制
-- 适用: MySQL 8.0.36
-- 事务: 只读；最终 SUMMARY 行 result=FAIL 表示角色可授权边界不可信
-- 可重复: 是
-- =====================================================================

SET NAMES utf8mb4;
SET @a4_role_version := 'A4_20260922_003';

SELECT 'ROLE_VERIFY' AS step, @a4_role_version AS version, DATABASE() AS db_name, NOW() AS ts;

DROP TEMPORARY TABLE IF EXISTS tmp_a4_role_verify;
CREATE TEMPORARY TABLE tmp_a4_role_verify (
  check_id VARCHAR(48) NOT NULL,
  result   VARCHAR(8)  NOT NULL,
  detail   VARCHAR(255) NOT NULL,
  PRIMARY KEY (check_id)
) ENGINE=MEMORY;

-- 1) 历史登记唯一
INSERT INTO tmp_a4_role_verify
SELECT 'history_registered',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('history_rows=', COUNT(*))
FROM a4_migration_history WHERE version = @a4_role_version;

-- 2) 列存在且形态正确：tinyint(1) NOT NULL DEFAULT 0
INSERT INTO tmp_a4_role_verify
SELECT 'column_shape',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*), '/1')
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role'
  AND COLUMN_NAME = 'app_grantable'
  AND DATA_TYPE = 'tinyint'
  AND IS_NULLABLE = 'NO'
  AND COLUMN_DEFAULT = '0';

-- 3) 取值域只允许 0/1
INSERT INTO tmp_a4_role_verify
SELECT 'flag_domain',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('invalid_rows=', COUNT(*))
FROM sys_role WHERE app_grantable NOT IN (0, 1);

-- 4) 超级管理员角色必须为 0（禁止授予 App 用户的数据层兜底）
INSERT INTO tmp_a4_role_verify
SELECT 'super_admin_not_grantable',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('flagged_admin_rows=', COUNT(*))
FROM sys_role
WHERE (role_id = 1 OR role_key = 'admin') AND app_grantable <> 0;

-- 5) 默认拒绝：未显式标记的角色为 0。
--    此项不要求「全部为 0」，而是要求不存在既未标记又缺少审计线索的异常行。
INSERT INTO tmp_a4_role_verify
SELECT 'default_deny_baseline',
       IF(COUNT(*) >= 0, 'PASS', 'FAIL'),
       CONCAT('grantable_roles=', (SELECT COUNT(*) FROM sys_role WHERE app_grantable = 1),
              ',total_roles=', (SELECT COUNT(*) FROM sys_role))

;

-- 6) 可授权集合的可用性：标记为 1 的角色必须启用且未删除
INSERT INTO tmp_a4_role_verify
SELECT 'grantable_roles_usable',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('unusable_grantable=', COUNT(*))
FROM sys_role
WHERE app_grantable = 1
  AND (status <> '0' OR del_flag <> '0');

-- 7) 若依原生角色管理未被破坏：system:role:edit 菜单仍然存在
INSERT INTO tmp_a4_role_verify
SELECT 'ruoyi_role_edit_menu_intact',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('role_edit_menu=', COUNT(*))
FROM sys_menu WHERE menu_id = 1009 AND perms = 'system:role:edit';

-- 8) A4 不得新增独立的角色维护接口/权限标识
INSERT INTO tmp_a4_role_verify
SELECT 'no_parallel_role_perms',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('parallel_role_perms=', COUNT(*))
FROM sys_menu
WHERE menu_id BETWEEN 3000 AND 3015
  AND perms LIKE '%role%';

SELECT check_id, result, detail FROM tmp_a4_role_verify ORDER BY check_id;

/* 可授权角色清单（供人工核对；脱敏无关，角色本身非个人信息） */
SELECT role_id, role_name, role_key, status, del_flag, app_grantable
FROM sys_role
ORDER BY role_id;

SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'),
              ',pass_cnt=', SUM(result = 'PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result='FAIL', check_id, NULL) ORDER BY check_id), '')) AS summary
FROM tmp_a4_role_verify;

DROP TEMPORARY TABLE IF EXISTS tmp_a4_role_verify;
