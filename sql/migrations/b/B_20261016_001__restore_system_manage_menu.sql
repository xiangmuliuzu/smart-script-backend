-- =====================================================================
-- 运维端「系统管理」菜单恢复（幂等，新库与已有库共用）
--
-- 背景：云端开发库 script_platform_dev 丢失了「系统管理」整组菜单：
--   menu_id 1（系统管理目录）与 100–106（用户/角色/菜单/部门/岗位/字典/参数
--   七个管理页面）整行缺失，导致后台管理端「系统管理」分区不显示、七个页面
--   均无法进入。其下的 F 类按钮权限（1000–1038）仍在，但父页不存在，成为悬空
--   按钮；sys_role_menu 中指向 1/100–106 的授权（本应 8 条）也随之为 0。
--
--   本脚本按仓库既有口径（见 sql/smartscript_full_init.sql 第 13 部分）把这
--   8 行菜单与超级管理员(role_id=1) 的 8 条授权补回，只增不改不删。
--
-- 幂等：INSERT IGNORE，主键已存在则跳过；可重复执行；不触碰任何既有行。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -P<端口> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261016_001__restore_system_manage_menu.sql"
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 恢复系统管理目录与七个管理页面（menu_id 1, 100–106）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1,'系统管理',0,90,'system',NULL,'','',1,0,'M','0','0','','system','admin','2026-10-12 17:25:56','',NULL,'系统管理目录');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (100,'用户管理',1,1,'user','system/user/index','','',1,0,'C','0','0','system:user:list','user','admin','2026-10-12 17:25:56','',NULL,'用户管理菜单');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (101,'角色管理',1,2,'role','system/role/index','','',1,0,'C','0','0','system:role:list','peoples','admin','2026-10-12 17:25:56','',NULL,'角色管理菜单');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (102,'菜单管理',1,3,'menu','system/menu/index','','',1,0,'C','0','0','system:menu:list','tree-table','admin','2026-10-12 17:25:56','',NULL,'菜单管理菜单');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (103,'部门管理',1,4,'dept','system/dept/index','','',1,0,'C','0','0','system:dept:list','tree','admin','2026-10-12 17:25:56','',NULL,'部门管理菜单');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (104,'岗位管理',1,5,'post','system/post/index','','',1,0,'C','0','0','system:post:list','post','admin','2026-10-12 17:25:56','',NULL,'岗位管理菜单');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (105,'字典管理',1,6,'dict','system/dict/index','','',1,0,'C','0','0','system:dict:list','dict','admin','2026-10-12 17:25:56','',NULL,'字典管理菜单');
INSERT IGNORE INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (106,'参数设置',1,7,'config','system/config/index','','',1,0,'C','0','0','system:config:list','edit','admin','2026-10-12 17:25:56','',NULL,'参数设置菜单');

-- ---------------------------------------------------------------------
-- 2. 恢复超级管理员对上述页面的授权（sys_role_menu，role_id=1）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,1);
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,100);
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,101);
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,102);
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,103);
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,104);
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,105);
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,106);

-- ---------------------------------------------------------------------
-- 3. verify 闸门
-- ---------------------------------------------------------------------
-- 系统管理根目录存在（应 1）
SET @root_exists = (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 1);

-- 七个管理页面存在（应 7）
SET @page_cnt = (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 100 AND 106);

-- 超管对根目录 + 七页面的授权（应 8）
SET @bind_cnt = (SELECT COUNT(*) FROM sys_role_menu
                 WHERE role_id = 1 AND menu_id IN (1,100,101,102,103,104,105,106));

SET @fail_cnt = (@root_exists = 1) + (@page_cnt = 7) + (@bind_cnt = 8);
SET @fail_cnt = 3 - @fail_cnt;

SELECT 'CHECK' AS check_result,
       CONCAT('root=', @root_exists,
              ', pages=', @page_cnt,
              ', binds=', @bind_cnt) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('系统管理菜单未达预期：root=', @root_exists,
              ', pages=', @page_cnt,
              ', binds=', @bind_cnt) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, 系统管理目录与七个管理页面及超管授权已就绪' AS detail
WHERE @fail_cnt = 0;
