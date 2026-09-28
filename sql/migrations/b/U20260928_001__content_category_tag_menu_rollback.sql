-- =====================================================================
-- U20260928_001__content_category_tag_menu_rollback.sql
-- 用途: 回滚 B_20260928_001 的分类/标签 PC 菜单与权限
-- 适用: MySQL 8.0.36
-- 幂等: 可重复执行
--
-- 回滚内容:
--   1) 删除本迁移新增的 8 个菜单（5105 + 5160-5166）及其角色授权
--   2) 5102 恢复为 A1 种子的占位形态：「分类与标签」/ categories /
--      common/ModuleScaffold / smartscript:content:category，顺序恢复为 2
--   3) 5103/5104 顺序恢复为 3/4
--   4) 5102 既有的角色授权保持不动（授权针对 menu_id，与菜单形态无关）
--
-- 注意: sys_category / sys_tag 业务表及后端代码不在本回滚范围。
-- =====================================================================
SET NAMES utf8mb4;

SELECT 'B_CONTENT_MENU_ROLLBACK_START' AS step, DATABASE() AS db_name, NOW() AS ts;

-- 1) 删除新增菜单的角色授权（先删授权，再删菜单）
DELETE rm
FROM sys_role_menu rm
WHERE rm.menu_id IN (5105, 5160, 5161, 5162, 5163, 5164, 5165, 5166);

-- 2) 删除新增的 1 个 C 页与 7 个 F 按钮
DELETE FROM sys_menu
WHERE menu_id IN (5105, 5160, 5161, 5162, 5163, 5164, 5165, 5166);

-- 3) 5102 恢复 A1 占位形态（仅回退由 b-migration 改写过的行，避免覆盖人工调整）
UPDATE sys_menu
SET menu_name   = '分类与标签',
    path        = 'categories',
    component   = 'common/ModuleScaffold',
    perms       = 'smartscript:content:category',
    icon        = 'Collection',
    order_num   = 2,
    update_by   = 'b-rollback',
    update_time = NOW()
WHERE menu_id = 5102
  AND parent_id = 5001
  AND menu_type = 'C'
  AND update_by = 'b-migration';

-- 4) 恢复 5001 目录下兄弟菜单顺序（A1 种子原值）
UPDATE sys_menu SET order_num = 3 WHERE menu_id = 5103 AND parent_id = 5001 AND menu_type = 'C';
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 5104 AND parent_id = 5001 AND menu_type = 'C';

-- 5) 回滚后核对
SELECT 'B_CONTENT_MENU_ROLLBACK_CHECK' AS step,
       (SELECT COUNT(*) FROM sys_menu
         WHERE menu_id IN (5105, 5160, 5161, 5162, 5163, 5164, 5165, 5166)) AS new_menus_left,
       (SELECT COUNT(*) FROM sys_menu
         WHERE menu_id = 5102 AND component = 'common/ModuleScaffold'
           AND perms = 'smartscript:content:category' AND path = 'categories') AS placeholder_restored;

SELECT 'B_CONTENT_MENU_ROLLBACK_DONE' AS step;
