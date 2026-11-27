-- =====================================================================
-- B 模块 · 回滚：撤消「书城作品管理」并入「作品管理」
--
-- 还原：
--   1. 恢复 menu 2014「书城作品管理」为启用（status='0'）；
--   2. 恢复子按钮 2031「书城查询」/ 2032「书城编辑」；
--   3. 角色授权从 2013 下新按钮迁回 2014/2031/2032；
--   4. 删除 2013 下新增的书城权限按钮（content:bookstore:list/query/edit）。
--
-- 执行：同 seed 文件的方式 source 本文件。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 恢复 2014 为启用
-- ---------------------------------------------------------------------
UPDATE sys_menu SET status = '0', update_by = 'admin', update_time = NOW() WHERE menu_id = 2014;

-- ---------------------------------------------------------------------
-- 2. 恢复子按钮 2031/2032（幂等；若已存在则仅确保启用）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES (2031, '书城查询', 2014, 1, '#', NULL, 1, 0, 'F', '0', '0', 'content:bookstore:query', '#', 'admin', NOW(), '书城作品查询')
ON DUPLICATE KEY UPDATE status = '0', menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num), perms = VALUES(perms);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES (2032, '书城编辑', 2014, 2, '#', NULL, 1, 0, 'F', '0', '0', 'content:bookstore:edit', '#', 'admin', NOW(), '书城作品编辑')
ON DUPLICATE KEY UPDATE status = '0', menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), order_num = VALUES(order_num), perms = VALUES(perms);

-- ---------------------------------------------------------------------
-- 3. 角色授权从 2013 下新按钮迁回 2014/2031/2032
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m_old.menu_id
FROM sys_role_menu rm
JOIN sys_menu m_new ON m_new.menu_id = rm.menu_id
JOIN sys_menu m_old ON m_old.perms = m_new.perms
WHERE m_new.parent_id = 2013
  AND m_new.perms IN ('content:bookstore:list', 'content:bookstore:query', 'content:bookstore:edit')
  AND m_old.menu_id IN (2014, 2031, 2032);

-- ---------------------------------------------------------------------
-- 4. 删除 2013 下新增的书城权限按钮（先删角色授权，再删菜单）
-- ---------------------------------------------------------------------
DELETE FROM sys_role_menu
WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE parent_id = 2013 AND perms IN ('content:bookstore:list', 'content:bookstore:query', 'content:bookstore:edit'));
DELETE FROM sys_menu
WHERE parent_id = 2013 AND perms IN ('content:bookstore:list', 'content:bookstore:query', 'content:bookstore:edit');

-- ---------------------------------------------------------------------
-- 5. 校验：2014 启用、2031/2032 就位、2013 下新按钮已删除
-- ---------------------------------------------------------------------
SET @b2014 = (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 2014 AND status = '0');
SET @old   = (SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (2031, 2032));
SET @new   = (SELECT COUNT(*) FROM sys_menu WHERE parent_id = 2013 AND perms IN ('content:bookstore:list', 'content:bookstore:query', 'content:bookstore:edit'));
SET @fail_cnt = IF(@b2014 = 1 AND @old = 2 AND @new = 0, 0, 1);

SELECT 'CHECK' AS check_result,
       CONCAT('menu2014_restored=', @b2014, ', old_btns=', @old, ', new_btns_removed=', @new) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('回滚失败：2014启用=', @b2014, ', 旧按钮=', @old, ', 新按钮残留=', @new) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, 书城作品管理独立菜单已还原') AS detail
WHERE @fail_cnt = 0;
