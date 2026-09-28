-- =====================================================================
-- B_20260928_002__cloud_content_menu.sql
-- 用途: B 模块分类管理/标签管理菜单写入【云端共享库 script_platform_dev】
-- 背景: 云库是裸若依库（sys_menu 仅 86 条），无 A1 种子 5001/5102 占位菜单，
--       B_20260928_001 在云库无前置可依。本迁移改为挂在云库已有的
--       「内容管理」目录（menu_id=2000，path=content）下。
-- 菜单 ID: 2010/2011 两个 C 页；2020-2026 七个 F 按钮（2000-2099 段已确认空闲）。
-- 权限码: 与 smartscript-content 后端 @PreAuthorize 一一对应
--         （分类启用/停用/排序复用 content:category:edit）。
-- 幂等: INSERT IGNORE，可重复执行。
-- 回滚: U20260928_002__cloud_content_menu_rollback.sql
-- =====================================================================
SET NAMES utf8mb4;

SELECT 'B_CLOUD_CONTENT_MENU_START' AS step, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 1) 两个 C 页：分类管理 / 标签管理（挂在 2000 内容管理下）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, remark)
VALUES
  (2010, '分类管理', 2000, 1, 'category', 'content/category/index', NULL, '',
   1, 0, 'C', '0', '0', 'content:category:list', 'Collection',
   'b-migration', NOW(), 'B模块内容管理菜单（B_20260928_002，云库适配）'),
  (2011, '标签管理', 2000, 2, 'tag', 'content/tag/index', NULL, '',
   1, 0, 'C', '0', '0', 'content:tag:list', 'Tag',
   'b-migration', NOW(), 'B模块内容管理菜单（B_20260928_002，云库适配）');

-- ---------------------------------------------------------------------
-- 2) 七个 F 按钮（visible='0'，前后端双重校验）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, remark)
VALUES
  (2020, '分类查询', 2010, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:category:query', '#',
   'b-migration', NOW(), 'B模块内容管理菜单（B_20260928_002，云库适配）'),
  (2021, '分类新增', 2010, 2, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:category:add', '#',
   'b-migration', NOW(), 'B模块内容管理菜单（B_20260928_002，云库适配）'),
  (2022, '分类编辑', 2010, 3, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:category:edit', '#',
   'b-migration', NOW(), 'B模块内容管理菜单（B_20260928_002，云库适配）'),
  (2023, '标签查询', 2011, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:tag:query', '#',
   'b-migration', NOW(), 'B模块内容管理菜单（B_20260928_002，云库适配）'),
  (2024, '标签新增', 2011, 2, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:tag:add', '#',
   'b-migration', NOW(), 'B模块内容管理菜单（B_20260928_002，云库适配）'),
  (2025, '标签编辑', 2011, 3, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:tag:edit', '#',
   'b-migration', NOW(), 'B模块内容管理菜单（B_20260928_002，云库适配）'),
  (2026, '标签删除', 2011, 4, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:tag:remove', '#',
   'b-migration', NOW(), 'B模块内容管理菜单（B_20260928_002，云库适配）');

-- ---------------------------------------------------------------------
-- 3) 授权超级管理员角色（云库 admin=role_id 1；超管默认可见全部，授权保持显式）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
  SELECT 2010 AS menu_id UNION ALL SELECT 2011
  UNION ALL SELECT 2020 UNION ALL SELECT 2021 UNION ALL SELECT 2022
  UNION ALL SELECT 2023 UNION ALL SELECT 2024 UNION ALL SELECT 2025 UNION ALL SELECT 2026
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
WHERE menu_id IN (2010, 2011, 2020, 2021, 2022, 2023, 2024, 2025, 2026)
ORDER BY menu_id;

SELECT 'B_CLOUD_CONTENT_MENU_DONE' AS step;
