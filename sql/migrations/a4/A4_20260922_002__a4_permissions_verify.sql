-- =====================================================================
-- A4_20260922_002__a4_permissions_verify.sql
-- 用途: 校验 A4 菜单、15 个权限标识与角色授权落库正确
-- 适用: MySQL 8.0.36
-- 事务: 只读；最终 SUMMARY 行 result=FAIL 表示权限数据不满足契约
-- 可重复: 是
-- =====================================================================

SET NAMES utf8mb4;
SET @a4_perm_version := 'A4_20260922_002';

SELECT 'PERM_VERIFY' AS step, @a4_perm_version AS version, DATABASE() AS db_name, NOW() AS ts;

DROP TEMPORARY TABLE IF EXISTS tmp_a4_perm_verify;
CREATE TEMPORARY TABLE tmp_a4_perm_verify (
  check_id VARCHAR(48) NOT NULL,
  result   VARCHAR(8)  NOT NULL,
  detail   VARCHAR(255) NOT NULL,
  PRIMARY KEY (check_id)
) ENGINE=MEMORY;

-- 1) 历史登记唯一
INSERT INTO tmp_a4_perm_verify
SELECT 'history_registered',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('history_rows=', COUNT(*))
FROM a4_migration_history WHERE version = @a4_perm_version;

-- 2) 菜单数量与类型构成：1 目录 + 5 页面 + 10 按钮 = 16
INSERT INTO tmp_a4_perm_verify
SELECT 'menu_count',
       IF(COUNT(*) = 16, 'PASS', 'FAIL'),
       CONCAT('a4_menus=', COUNT(*), '/16')
FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015;

INSERT INTO tmp_a4_perm_verify
SELECT 'menu_type_mix',
       IF(SUM(menu_type='M') = 1 AND SUM(menu_type='C') = 5 AND SUM(menu_type='F') = 10,
          'PASS', 'FAIL'),
       CONCAT('M=', SUM(menu_type='M'), ',C=', SUM(menu_type='C'), ',F=', SUM(menu_type='F'))
FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015;

-- 3) 契约 §8 的 15 个权限标识必须齐全且无多余
INSERT INTO tmp_a4_perm_verify
SELECT 'perms_complete',
       IF(COUNT(DISTINCT perms) = 15, 'PASS', 'FAIL'),
       CONCAT('distinct_perms=', COUNT(DISTINCT perms), '/15')
FROM sys_menu
WHERE menu_id BETWEEN 3000 AND 3015
  AND perms IN ('user:app:list','user:app:query','user:app:status','user:app:grant',
                'user:realname:list','user:realname:query','user:realname:audit',
                'user:creator:list','user:creator:update',
                'user:message:list','user:message:add','user:message:query',
                'user:feedback:list','user:feedback:query','user:feedback:handle');

-- 4) 不得出现契约外的 perms
INSERT INTO tmp_a4_perm_verify
SELECT 'perms_no_extra',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('extra_perms=', COUNT(*))
FROM sys_menu
WHERE menu_id BETWEEN 3000 AND 3015
  AND perms IS NOT NULL
  AND perms NOT IN ('user:app:list','user:app:query','user:app:status','user:app:grant',
                    'user:realname:list','user:realname:query','user:realname:audit',
                    'user:creator:list','user:creator:update',
                    'user:message:list','user:message:add','user:message:query',
                    'user:feedback:list','user:feedback:query','user:feedback:handle');

-- 5) 页面菜单必须挂到 A4 目录，且组件路径与前端映射一致
INSERT INTO tmp_a4_perm_verify
SELECT 'pages_under_dir',
       IF(COUNT(*) = 5, 'PASS', 'FAIL'),
       CONCAT('pages_under_3000=', COUNT(*), '/5')
FROM sys_menu WHERE menu_id IN (3001,3005,3008,3010,3013) AND parent_id = 3000;

INSERT INTO tmp_a4_perm_verify
SELECT 'page_components_match',
       IF(COUNT(*) = 5, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*), '/5')
FROM sys_menu
WHERE (menu_id = 3001 AND component = 'user/UserManage')
   OR (menu_id = 3005 AND component = 'user/realname/index')
   OR (menu_id = 3008 AND component = 'user/UserManage')
   OR (menu_id = 3010 AND component = 'user/message/index')
   OR (menu_id = 3013 AND component = 'user/feedback/index');

-- 6) 按钮必须挂到对应页面；不得成为顶级或目录子级
INSERT INTO tmp_a4_perm_verify
SELECT 'buttons_under_pages',
       IF(COUNT(*) = 10, 'PASS', 'FAIL'),
       CONCAT('buttons_under_pages=', COUNT(*), '/10')
FROM sys_menu
WHERE menu_id BETWEEN 3002 AND 3015 AND menu_type = 'F'
  AND parent_id IN (3001, 3005, 3008, 3010, 3013);

-- 7) 菜单状态正常且可见（前端再由权限指令逐按钮控制）
INSERT INTO tmp_a4_perm_verify
SELECT 'menus_enabled',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('abnormal_menus=', COUNT(*))
FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015 AND (status <> '0' OR visible <> '0');

-- 8) 授权：超级管理员拥有全部 16 个菜单，且不扩散到其他角色
INSERT INTO tmp_a4_perm_verify
SELECT 'admin_granted_all',
       IF(COUNT(*) = 16, 'PASS', 'FAIL'),
       CONCAT('admin_grant_rows=', COUNT(*), '/16')
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE rm.menu_id BETWEEN 3000 AND 3015
  AND r.del_flag = '0' AND (r.role_key = 'admin' OR r.role_id = 1);

-- 非超级管理员被授予 A4 菜单即为越权扩散。
-- 例外：role_key 以 'a4' 开头的角色是隔离测试库自建的最小权限验证角色
-- （只读/列表+详情/写权限），用于 USER-02 权限矩阵，不属于产品角色。
INSERT INTO tmp_a4_perm_verify
SELECT 'grants_not_leaked',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('non_admin_grants=', COUNT(*))
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE rm.menu_id BETWEEN 3000 AND 3015
  AND NOT (r.del_flag = '0' AND (r.role_key = 'admin' OR r.role_id = 1))
  AND r.role_key NOT LIKE 'a4%';

-- 9) 不复制若依原生：系统管理下的用户管理（menu_id=100）必须保持原样
INSERT INTO tmp_a4_perm_verify
SELECT 'ruoyi_user_menu_unchanged',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('menu_100_matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 100 AND parent_id = 1 AND path = 'user'
  AND component = 'system/user/index' AND perms = 'system:user:list';

SELECT check_id, result, detail FROM tmp_a4_perm_verify ORDER BY check_id;

SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'),
              ',pass_cnt=', SUM(result = 'PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result='FAIL', check_id, NULL) ORDER BY check_id), '')) AS summary
FROM tmp_a4_perm_verify;

DROP TEMPORARY TABLE IF EXISTS tmp_a4_perm_verify;
