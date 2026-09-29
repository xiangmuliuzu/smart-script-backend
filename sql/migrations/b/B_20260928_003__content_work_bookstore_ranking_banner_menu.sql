-- =====================================================================
-- B_20260928_003__content_work_bookstore_ranking_banner_menu.sql
-- 用途: B 模块批1 菜单写入（作品管理/书城作品管理/排行榜管理/Banner管理）
-- 背景: 承接 B_20260928_002（分类2010/标签2011），继续在内容管理(2000)下挂 4 个 C 页。
-- 菜单 ID: 2013-2016 四个 C 页；2030-2037 八个 F 按钮（2000-2099 段已确认空闲）。
-- 权限码: 与 smartscript-content 后端 @PreAuthorize 一一对应
--         （书城 changeStatus/changeTrade/updateExt 复用 content:bookstore:edit；
--          排行榜 changeRankNo/recompute 复用 content:ranking:edit；
--          Banner changeStatus/changeSort 复用 content:banner:edit）。
-- 幂等: INSERT IGNORE，可重复执行。
-- =====================================================================
SET NAMES utf8mb4;

SELECT 'B_CONTENT_BATCH1_MENU_START' AS step, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 1) 四个 C 页（挂在 2000 内容管理下，order_num 续 3-6）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, remark)
VALUES
  (2013, '作品管理', 2000, 3, 'work', 'content/work/index', NULL, '',
   1, 0, 'C', '0', '0', 'content:work:list', 'Documentation',
   'b-migration', NOW(), 'B模块批1菜单（B_20260928_003）'),
  (2014, '书城作品管理', 2000, 4, 'bookstore', 'content/bookstore/index', NULL, '',
   1, 0, 'C', '0', '0', 'content:bookstore:list', 'Goods',
   'b-migration', NOW(), 'B模块批1菜单（B_20260928_003）'),
  (2015, '排行榜管理', 2000, 5, 'ranking', 'content/ranking/index', NULL, '',
   1, 0, 'C', '0', '0', 'content:ranking:list', 'Chart',
   'b-migration', NOW(), 'B模块批1菜单（B_20260928_003）'),
  (2016, 'Banner管理', 2000, 6, 'banner', 'content/banner/index', NULL, '',
   1, 0, 'C', '0', '0', 'content:banner:list', 'Picture',
   'b-migration', NOW(), 'B模块批1菜单（B_20260928_003）');

-- ---------------------------------------------------------------------
-- 2) 八个 F 按钮（visible='0'，前后端双重校验）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, remark)
VALUES
  (2030, '作品查询', 2013, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:work:query', '#',
   'b-migration', NOW(), 'B模块批1菜单（B_20260928_003）'),
  (2031, '书城查询', 2014, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:bookstore:query', '#',
   'b-migration', NOW(), 'B模块批1菜单（B_20260928_003）'),
  (2032, '书城编辑', 2014, 2, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:bookstore:edit', '#',
   'b-migration', NOW(), 'B模块批1菜单（B_20260928_003）'),
  (2033, '排行榜查询', 2015, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:ranking:query', '#',
   'b-migration', NOW(), 'B模块批1菜单（B_20260928_003）'),
  (2034, '排行榜编辑', 2015, 2, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:ranking:edit', '#',
   'b-migration', NOW(), 'B模块批1菜单（B_20260928_003）'),
  (2035, 'Banner查询', 2016, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:banner:query', '#',
   'b-migration', NOW(), 'B模块批1菜单（B_20260928_003）'),
  (2036, 'Banner新增', 2016, 2, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:banner:add', '#',
   'b-migration', NOW(), 'B模块批1菜单（B_20260928_003）'),
  (2037, 'Banner编辑', 2016, 3, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:banner:edit', '#',
   'b-migration', NOW(), 'B模块批1菜单（B_20260928_003）');

-- ---------------------------------------------------------------------
-- 3) 授权超级管理员角色（云库 admin=role_id 1；超管默认可见全部，授权保持显式）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
  SELECT 2013 AS menu_id UNION ALL SELECT 2014 UNION ALL SELECT 2015 UNION ALL SELECT 2016
  UNION ALL SELECT 2030 UNION ALL SELECT 2031 UNION ALL SELECT 2032 UNION ALL SELECT 2033
  UNION ALL SELECT 2034 UNION ALL SELECT 2035 UNION ALL SELECT 2036 UNION ALL SELECT 2037
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
WHERE menu_id IN (2013, 2014, 2015, 2016, 2030, 2031, 2032, 2033, 2034, 2035, 2036, 2037)
ORDER BY menu_id;

SELECT 'B_CONTENT_BATCH1_MENU_DONE' AS step;
