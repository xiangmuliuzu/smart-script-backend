-- =====================================================================
-- B_20260928_004__content_workfile_menu.sql
-- 用途: B 模块批2 菜单写入（作品上传资料）【云端共享库 script_platform_dev】
-- 背景: 承接 B_20260928_003（作品2013/书城2014/排行榜2015/Banner2016），
--       继续在内容管理(2000)下挂 1 个 C 页。
-- 菜单 ID: 2017 一个 C 页；2039 一个 F 按钮（2000-2099 段已确认空闲）。
-- 权限码: 与 smartscript-content 后端 @PreAuthorize 一一对应
--         （作品上传资料查询复用 content:workfile:query）。
-- 幂等: INSERT IGNORE，可重复执行。
-- =====================================================================
SET NAMES utf8mb4;

SELECT 'B_CONTENT_BATCH2_MENU_START' AS step, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 1) 一个 C 页：作品上传资料（挂在 2000 内容管理下，order_num 续 7）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, remark)
VALUES
  (2017, '作品上传资料', 2000, 7, 'workfile', 'content/workfile/index', NULL, '',
   1, 0, 'C', '0', '0', 'content:workfile:list', 'Upload',
   'b-migration', NOW(), 'B模块批2菜单（B_20260928_004）');

-- ---------------------------------------------------------------------
-- 2) 一个 F 按钮（visible='0'，前后端双重校验）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, remark)
VALUES
  (2039, '上传资料查询', 2017, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:workfile:query', '#',
   'b-migration', NOW(), 'B模块批2菜单（B_20260928_004）');

-- ---------------------------------------------------------------------
-- 3) 授权超级管理员角色（云库 admin=role_id 1；超管默认可见全部，授权保持显式）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
  SELECT 2017 AS menu_id UNION ALL SELECT 2039
) m
LEFT JOIN sys_role_menu rm ON rm.role_id = r.role_id AND rm.menu_id = m.menu_id
WHERE r.del_flag = '0'
  AND (r.role_key = 'admin' OR r.role_id = 1)
  AND rm.role_id IS NULL;

-- ---------------------------------------------------------------------
-- 4) 结果回显
-- ---------------------------------------------------------------------
SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms
FROM sys_menu
WHERE menu_id IN (2017, 2039)
ORDER BY menu_id;

SELECT 'B_CONTENT_BATCH2_MENU_DONE' AS step;
