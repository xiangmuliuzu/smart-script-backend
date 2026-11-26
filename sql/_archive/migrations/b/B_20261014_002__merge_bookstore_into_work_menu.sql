-- =====================================================================
-- B 模块 · 合并「书城作品管理」进入「作品管理」（menu 2014 → 2013）
--
-- 背景：
--   产品合并需求：书城作品管理页并入作品管理页（PC 前端已在 content/work/index.vue
--   改为「全部作品 / 书城作品」双 Tab）。「书城作品」Tab 只管理已上架（出现在书城）的作品，
--   且两处均展示封面。书城管理入口从独立菜单收敛到作品管理菜单内。
--
-- 处理（幂等，可重复执行）：
--   1. 在 menu 2013「作品管理」下新增按钮权限：content:bookstore:list/query/edit，
--      承载原书城菜单（2014 自身 perms=content:bookstore:list）及其子按钮（2031/2032）的权限；
--   2. 迁移 sys_role_menu：角色对 2014/2031/2032 的授权平移到 2013 下的新按钮，
--      保证停用独立菜单后角色仍保有书城操作权限；
--   3. 停用 menu 2014（status='1'，菜单树不再展示独立书城入口），物理删除 2031/2032 子按钮；
--   4. 超级管理员（role_id=1）授权新按钮。
--
-- 回滚：
--   执行同名 .rollback.sql。
--
-- 执行：
--   $env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev `
--     -e "source sql/migrations/b/B_20261014_002__merge_bookstore_into_work_menu.sql"
--   verify 闸门：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 2013「作品管理」下新增书城按钮权限（幂等）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '书城列表', 2013, 5, '', NULL, 1, 0, 'F', '0', '0', 'content:bookstore:list', '#', 'admin', NOW(), '书城作品列表（并入作品管理）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 2013 AND perms = 'content:bookstore:list');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '书城查询', 2013, 6, '', NULL, 1, 0, 'F', '0', '0', 'content:bookstore:query', '#', 'admin', NOW(), '书城作品查询（并入作品管理）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 2013 AND perms = 'content:bookstore:query');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '书城编辑', 2013, 7, '', NULL, 1, 0, 'F', '0', '0', 'content:bookstore:edit', '#', 'admin', NOW(), '书城作品编辑（并入作品管理）'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 2013 AND perms = 'content:bookstore:edit');

-- ---------------------------------------------------------------------
-- 2. 迁移角色授权：2014/2031/2032 → 2013 下同 perms 新按钮
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m_new.menu_id
FROM sys_role_menu rm
JOIN sys_menu m_old ON m_old.menu_id = rm.menu_id
JOIN sys_menu m_new ON m_new.parent_id = 2013 AND m_new.perms = m_old.perms
WHERE m_old.menu_id IN (2014, 2031, 2032);

-- ---------------------------------------------------------------------
-- 3. 停用独立书城菜单 2014，物理删除其子按钮 2031/2032
-- ---------------------------------------------------------------------
UPDATE sys_menu SET status = '1', update_by = 'admin', update_time = NOW() WHERE menu_id = 2014;
DELETE FROM sys_role_menu WHERE menu_id IN (2031, 2032);
DELETE FROM sys_menu WHERE menu_id IN (2031, 2032);

-- ---------------------------------------------------------------------
-- 4. 超级管理员授权新按钮
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu
WHERE parent_id = 2013 AND perms IN ('content:bookstore:list', 'content:bookstore:query', 'content:bookstore:edit');

-- ---------------------------------------------------------------------
-- 5. 校验：2014 已停用、2031/2032 已删除、2013 下三个新按钮就位
-- ---------------------------------------------------------------------
SET @b2014 = (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 2014 AND status = '1');
SET @old    = (SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (2031, 2032));
SET @new    = (SELECT COUNT(*) FROM sys_menu WHERE parent_id = 2013 AND perms IN ('content:bookstore:list', 'content:bookstore:query', 'content:bookstore:edit'));
SET @fail_cnt = IF(@b2014 = 1 AND @old = 0 AND @new = 3, 0, 1);

SELECT 'CHECK' AS check_result,
       CONCAT('menu2014_stopped=', @b2014, ', old_btns_removed=', @old, ', new_btns=', @new) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('菜单合并失败：2014停用=', @b2014, ', 旧按钮残留=', @old, ', 新按钮=', @new) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, 书城作品管理已并入作品管理') AS detail
WHERE @fail_cnt = 0;
