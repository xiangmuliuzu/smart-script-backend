-- =====================================================================
-- PC_20260923_001__sidebar_ia_verify.sql
-- 用途: PC 侧边栏信息架构迁移后的最终校验（初始化流程与已有库升级共用的收尾关卡）
-- 适用: MySQL 8.0.36
-- 前置: PC_20260923_001__sidebar_ia.sql 已执行（且 A1/A4 各阶段已应用）
-- 事务: 只读；最终 SUMMARY 行 result=FAIL 表示侧边栏层级不满足契约
-- 可重复: 是
-- 脱敏: 只输出菜单结构与计数，无个人信息
-- =====================================================================

SET NAMES utf8mb4;

SELECT 'VERIFY' AS step, 'PC_20260923_001' AS version, DATABASE() AS db_name, NOW() AS ts;

DROP TEMPORARY TABLE IF EXISTS tmp_pc_sidebar_verify;
CREATE TEMPORARY TABLE tmp_pc_sidebar_verify (
  check_id VARCHAR(48) NOT NULL,
  result   VARCHAR(8)  NOT NULL,
  detail   VARCHAR(255) NOT NULL,
  PRIMARY KEY (check_id)
) ENGINE=MEMORY;

-- ---------------------------------------------------------------------
-- 1) 原根级散叶子必须全部归位（迁移第 1、2 步的结果）
-- ---------------------------------------------------------------------
INSERT INTO tmp_pc_sidebar_verify
SELECT 'root_leaves_gone',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('root_leaves=', COUNT(*))
FROM sys_menu
WHERE menu_id IN (5100,5142,5143,5150,5151,5152) AND parent_id = 0;

-- 2) 全部 25 个 A1 产品叶子都不得再挂在根上
INSERT INTO tmp_pc_sidebar_verify
SELECT 'seed_leaves_no_root',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('seed_leaves_at_root=', COUNT(*))
FROM sys_menu
WHERE menu_id IN (5100,5101,5102,5103,5104,5110,5111,5120,5121,5122,5123,5124,5125,
                  5130,5131,5132,5133,5134,5140,5141,5142,5143,5150,5151,5152)
  AND parent_id = 0;

-- 3) 工作台 → 数据总览（绝对路径 /dashboard，Dashboard.vue 快捷入口依赖）
INSERT INTO tmp_pc_sidebar_verify
SELECT 'dashboard_nested_under_workspace',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*))
FROM sys_menu WHERE menu_id = 5100 AND parent_id = 5000 AND path = '/dashboard';

-- 4) 平台运维管理 → 用户与创作者管理 / 全局风控管理
INSERT INTO tmp_pc_sidebar_verify
SELECT 'ops_group_nested',
       IF(COUNT(*) = 2, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*), '/2')
FROM sys_menu
WHERE menu_id IN (5142,5143) AND parent_id = 5005 AND path IN ('/user', '/risk');

-- 5) AI 创作与福利 → AI 创作与次数 / 福利与积分配置 / 消息与公告
INSERT INTO tmp_pc_sidebar_verify
SELECT 'ai_group_nested',
       IF(COUNT(*) = 3, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*), '/3')
FROM sys_menu
WHERE menu_id IN (5150,5151,5152) AND parent_id = 5006
  AND path IN ('/ai/operations', '/support/welfare', '/support/messages');

-- 6) 顶级分组顺序与原型一致（含 A4「用户中心」排在末位）
INSERT INTO tmp_pc_sidebar_verify
SELECT 'top_dir_order',
       IF(COUNT(*) = 9, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*), '/9')
FROM sys_menu
WHERE (menu_id = 5000 AND order_num = 1)
   OR (menu_id = 5001 AND order_num = 2)
   OR (menu_id = 5003 AND order_num = 3)
   OR (menu_id = 5004 AND order_num = 4)
   OR (menu_id = 5005 AND order_num = 5)
   OR (menu_id = 5006 AND order_num = 6)
   OR (menu_id = 5002 AND order_num = 7)
   OR (menu_id = 1    AND order_num = 8)
   OR (menu_id = 3000 AND order_num = 9);

-- ---------------------------------------------------------------------
-- 7) 名称对齐原型；A4 迭代前缀必须清零
-- ---------------------------------------------------------------------
INSERT INTO tmp_pc_sidebar_verify
SELECT 'menu_names_aligned',
       IF(COUNT(*) = 9, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*), '/9')
FROM sys_menu
WHERE (menu_id = 5123 AND menu_name = '版权资产库管理')
   OR (menu_id = 5006 AND menu_name = 'AI 创作与福利')
   OR (menu_id = 5150 AND menu_name = 'AI 创作与次数')
   OR (menu_id = 3000 AND menu_name = '用户中心')
   OR (menu_id = 3001 AND menu_name = 'App用户与创作者')
   OR (menu_id = 3005 AND menu_name = '实名审核')
   OR (menu_id = 3008 AND menu_name = '作者能力')
   OR (menu_id = 3010 AND menu_name = '用户消息')
   OR (menu_id = 3013 AND menu_name = '用户反馈');

INSERT INTO tmp_pc_sidebar_verify
SELECT 'a4_named_menus_gone',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('a4_named_menus=', COUNT(*))
FROM sys_menu WHERE menu_type IN ('M','C') AND menu_name LIKE 'A4%';

-- ---------------------------------------------------------------------
-- 8) A4 菜单体系完整（16 菜单 = 1 目录 + 5 页面 + 10 按钮）
-- ---------------------------------------------------------------------
INSERT INTO tmp_pc_sidebar_verify
SELECT 'a4_menu_set_complete',
       IF(COUNT(*) = 16, 'PASS', 'FAIL'),
       CONCAT('a4_menus=', COUNT(*), '/16')
FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015;

INSERT INTO tmp_pc_sidebar_verify
SELECT 'a4_buttons_present',
       IF(COUNT(*) = 10, 'PASS', 'FAIL'),
       CONCAT('a4_buttons=', COUNT(*), '/10')
FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015 AND menu_type = 'F';

INSERT INTO tmp_pc_sidebar_verify
SELECT 'user_center_children',
       IF(COUNT(*) = 5, 'PASS', 'FAIL'),
       CONCAT('children=', COUNT(*), '/5')
FROM sys_menu WHERE parent_id = 3000 AND menu_type = 'C';

-- ---------------------------------------------------------------------
-- 9) 迁移第 0 步的补齐：5142 必须存在且为最终形态
--    （A1 种子在组件重名时会跳过它，PC 迁移负责按最终形态补写）
-- ---------------------------------------------------------------------
INSERT INTO tmp_pc_sidebar_verify
SELECT 'menu_5142_backfilled',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 5142 AND parent_id = 5005 AND path = '/user'
  AND component = 'user/UserManage' AND perms = 'smartscript:ops:creatorUser';

-- ---------------------------------------------------------------------
-- 10) A1 受限运营角色授权未被本次迁移改变
-- ---------------------------------------------------------------------
INSERT INTO tmp_pc_sidebar_verify
SELECT 'operator_grants_unchanged',
       IF(COUNT(*) = 4, 'PASS', 'FAIL'),
       CONCAT('grants=', COUNT(*), '/4')
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE r.role_key = 'a1_operator';

-- ---------------------------------------------------------------------
-- 11) 结构与引用完整性
-- ---------------------------------------------------------------------
INSERT INTO tmp_pc_sidebar_verify
SELECT 'no_orphan_parent',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('orphan_rows=', COUNT(*))
FROM sys_menu m
LEFT JOIN sys_menu p ON p.menu_id = m.parent_id
WHERE m.parent_id <> 0 AND p.menu_id IS NULL;

-- 同级路由路径唯一（目录/菜单非空 path）：若依 getRouters 直接拼接下级 path
INSERT INTO tmp_pc_sidebar_verify
SELECT 'unique_sibling_paths',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('dup_sibling_paths=', COUNT(*))
FROM (
  SELECT parent_id, path FROM sys_menu
  WHERE menu_type IN ('M','C') AND path <> ''
  GROUP BY parent_id, path HAVING COUNT(*) > 1
) d;

-- 路由名称唯一（前端 keep-alive 与标签页按 route_name 标识）
INSERT INTO tmp_pc_sidebar_verify
SELECT 'unique_route_names',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('dup_route_names=', COUNT(*))
FROM (
  SELECT route_name FROM sys_menu
  WHERE route_name IS NOT NULL AND route_name <> ''
  GROUP BY route_name HAVING COUNT(*) > 1
) d;

-- 若依原生体系保持原样
INSERT INTO tmp_pc_sidebar_verify
SELECT 'baseline_menu_100_intact',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('menu_100_matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 100 AND parent_id = 1 AND path = 'user'
  AND component = 'system/user/index' AND perms = 'system:user:list';

SELECT check_id, result, detail FROM tmp_pc_sidebar_verify ORDER BY check_id;

-- ---------------------------------------------------------------------
-- 证据输出：最终侧边栏树（供人工核对）
-- ---------------------------------------------------------------------
SELECT 'PC_SIDEBAR_ROOT' AS section, menu_id, menu_name, order_num, path, menu_type
FROM sys_menu WHERE parent_id = 0
ORDER BY order_num, menu_id;

SELECT 'PC_SIDEBAR_PRODUCT' AS section, m.menu_id, m.parent_id, p.menu_name AS parent_name,
       m.menu_name, m.path, m.component, m.perms
FROM sys_menu m
JOIN sys_menu p ON p.menu_id = m.parent_id
WHERE m.remark LIKE 'A1SEED%' AND m.menu_type = 'C'
ORDER BY p.order_num, m.order_num, m.menu_id;

SELECT 'PC_SIDEBAR_USER_CENTER' AS section,
       (SELECT GROUP_CONCAT(CONCAT(menu_id, ':', menu_name) ORDER BY order_num)
          FROM sys_menu WHERE parent_id = 3000 AND menu_type = 'C') AS children,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015 AND menu_type = 'F') AS buttons;

SELECT 'FINAL_STATE' AS metric,
       (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()) AS tables_cnt,
       (SELECT COUNT(*) FROM sys_user)     AS users,
       (SELECT COUNT(*) FROM sys_role)     AS roles,
       (SELECT COUNT(*) FROM sys_menu)     AS menus,
       (SELECT COUNT(*) FROM sys_role_menu) AS role_menus;

SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'),
              ',pass_cnt=', SUM(result = 'PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result='FAIL', check_id, NULL) ORDER BY check_id), '')) AS summary
FROM tmp_pc_sidebar_verify;

DROP TEMPORARY TABLE IF EXISTS tmp_pc_sidebar_verify;
