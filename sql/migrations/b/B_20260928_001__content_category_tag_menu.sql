-- =====================================================================
-- B_20260928_001__content_category_tag_menu.sql
-- 用途: B 模块（内容与作品）分类管理 / 标签管理 PC 菜单与权限标识落库
-- 适用: MySQL 8.0.36
-- 前置: A1 产品菜单种子（a1/A1_20260921_001__p5_menu_seed.sql）已应用：
--       提供 5001「内容与作品」目录及 5102 占位菜单「分类与标签」
--       （common/ModuleScaffold 骨架）。
-- 事务: 幂等；可重复执行，不产生重复菜单/授权
-- 依据: B 模块 PC 功能清单（分类管理：新增/编辑/启用/停用/排序；
--       标签管理：新增/编辑/删除）+ smartscript-content 后端
--       SysCategoryController / SysTagController 的 @PreAuthorize 权限码
--
-- 菜单 ID 规划（避开已用段：A1 产品 5000-5152、A4 3000-3018、若依原生 1-1061）:
--   5102 占位骨架改写为「分类管理」C 页（content/category/index）
--   5105 新增「标签管理」C 页（content/tag/index）
--   5160-5166 七个 F 按钮（query/add/edit + tag 的 remove）
--
-- 回滚: U20260928_001__content_category_tag_menu_rollback.sql
-- =====================================================================
SET NAMES utf8mb4;

SELECT 'B_CONTENT_MENU_START' AS step, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 1) 5102 占位骨架「分类与标签」→ 真实「分类管理」
--    只改展示/路由/权限字段，menu_type 仍为 C；按 menu_id+parent 定位，可重复执行
-- ---------------------------------------------------------------------
UPDATE sys_menu
SET menu_name   = '分类管理',
    path        = 'category',
    component   = 'content/category/index',
    perms       = 'content:category:list',
    icon        = 'Collection',
    order_num   = 2,
    update_by   = 'b-migration',
    update_time = NOW()
WHERE menu_id = 5102
  AND parent_id = 5001
  AND menu_type = 'C';

-- ---------------------------------------------------------------------
-- 2) 新增「标签管理」C 页（INSERT IGNORE 保证幂等）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
VALUES
  (5105, '标签管理', 5001, 3, 'tag', 'content/tag/index', NULL, '',
   1, 0, 'C', '0', '0', 'content:tag:list', 'Tag',
   'b-migration', NOW(), '', NULL, 'B模块内容管理菜单（B_20260928_001）');

-- ---------------------------------------------------------------------
-- 3) F 类型按钮：与后端 @PreAuthorize 权限码一一对应
--    visible='0'；是否可见/可用由前端 v-permission 与后端注解双重控制
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
VALUES
  -- 分类管理按钮（启用/停用/排序复用 content:category:edit）
  (5160, '分类查询', 5102, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:category:query', '#',
   'b-migration', NOW(), '', NULL, 'B模块内容管理菜单（B_20260928_001）'),
  (5161, '分类新增', 5102, 2, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:category:add', '#',
   'b-migration', NOW(), '', NULL, 'B模块内容管理菜单（B_20260928_001）'),
  (5162, '分类编辑', 5102, 3, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:category:edit', '#',
   'b-migration', NOW(), '', NULL, 'B模块内容管理菜单（B_20260928_001）'),
  -- 标签管理按钮
  (5163, '标签查询', 5105, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:tag:query', '#',
   'b-migration', NOW(), '', NULL, 'B模块内容管理菜单（B_20260928_001）'),
  (5164, '标签新增', 5105, 2, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:tag:add', '#',
   'b-migration', NOW(), '', NULL, 'B模块内容管理菜单（B_20260928_001）'),
  (5165, '标签编辑', 5105, 3, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:tag:edit', '#',
   'b-migration', NOW(), '', NULL, 'B模块内容管理菜单（B_20260928_001）'),
  (5166, '标签删除', 5105, 4, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:tag:remove', '#',
   'b-migration', NOW(), '', NULL, 'B模块内容管理菜单（B_20260928_001）');

-- ---------------------------------------------------------------------
-- 4) 内容与作品（5001）子菜单顺序：作品 → 分类 → 标签 → 排行榜 → 外部漫剧
-- ---------------------------------------------------------------------
UPDATE sys_menu SET order_num = 1 WHERE menu_id = 5101 AND parent_id = 5001 AND menu_type = 'C';
UPDATE sys_menu SET order_num = 2 WHERE menu_id = 5102 AND parent_id = 5001 AND menu_type = 'C';
UPDATE sys_menu SET order_num = 3 WHERE menu_id = 5105 AND parent_id = 5001 AND menu_type = 'C';
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 5103 AND parent_id = 5001 AND menu_type = 'C';
UPDATE sys_menu SET order_num = 5 WHERE menu_id = 5104 AND parent_id = 5001 AND menu_type = 'C';

-- ---------------------------------------------------------------------
-- 5) 授权：仅超级管理员角色（admin 判定与若依一致），不扩散到普通角色；
--    普通角色后续通过「角色管理」按需勾选。幂等，已存在不重复插入。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
  SELECT 5102 AS menu_id
  UNION ALL SELECT 5105
  UNION ALL SELECT 5160
  UNION ALL SELECT 5161
  UNION ALL SELECT 5162
  UNION ALL SELECT 5163
  UNION ALL SELECT 5164
  UNION ALL SELECT 5165
  UNION ALL SELECT 5166
) m
LEFT JOIN sys_role_menu rm ON rm.role_id = r.role_id AND rm.menu_id = m.menu_id
WHERE r.del_flag = '0'
  AND (r.role_key = 'admin' OR r.role_id = 1)
  AND rm.role_id IS NULL;

-- ---------------------------------------------------------------------
-- 6) 结果回显（证据）
-- ---------------------------------------------------------------------
SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms
FROM sys_menu
WHERE menu_id IN (5102, 5105, 5160, 5161, 5162, 5163, 5164, 5165, 5166)
ORDER BY menu_id;

SELECT 'B_CONTENT_MENU_DONE' AS step;
