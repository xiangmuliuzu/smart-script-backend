-- 回滚 PC_20260923_001__sidebar_ia.sql
-- 只还原该脚本改动的三处：顶级 order_num、产品叶子的 parent_id/path/order_num、菜单名。
-- 不回滚 shared/sql/a1-p5-menu-seed.sql 建立的产品菜单（那是独立脚本，有自己的回滚文件）。
SET NAMES utf8mb4;

-- 0) 删除本迁移补齐的 5142（仅当它仍是本迁移写入的形态）
DELETE FROM sys_role_menu WHERE menu_id = 5142
  AND EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu WHERE menu_id = 5142 AND create_by = 'pc_migration') x);
DELETE FROM sys_menu WHERE menu_id = 5142 AND create_by = 'pc_migration';

-- 1) 顶级分组顺序还原
UPDATE sys_menu SET order_num = 1 WHERE menu_id = 1    AND menu_type = 'M';
UPDATE sys_menu SET order_num = 3 WHERE menu_id = 5002 AND menu_type = 'M';
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 5003 AND menu_type = 'M';
UPDATE sys_menu SET order_num = 5 WHERE menu_id = 5004 AND menu_type = 'M';
UPDATE sys_menu SET order_num = 6 WHERE menu_id = 5005 AND menu_type = 'M';
UPDATE sys_menu SET order_num = 7 WHERE menu_id = 5006 AND menu_type = 'M';
UPDATE sys_menu SET order_num = 5 WHERE menu_id = 3000 AND menu_type = 'M';

-- 2) 产品叶子还原为根级 + 相对路径
UPDATE sys_menu SET parent_id = 0, path = 'dashboard', order_num = 1
  WHERE menu_id = 5100 AND menu_type = 'C';
UPDATE sys_menu SET parent_id = 0, path = 'user', order_num = 3
  WHERE menu_id = 5142 AND menu_type = 'C';
UPDATE sys_menu SET parent_id = 0, path = 'risk', order_num = 4
  WHERE menu_id = 5143 AND menu_type = 'C';
UPDATE sys_menu SET parent_id = 0, path = 'ai/operations', order_num = 5
  WHERE menu_id = 5150 AND menu_type = 'C';
UPDATE sys_menu SET parent_id = 0, path = 'support/welfare', order_num = 6
  WHERE menu_id = 5151 AND menu_type = 'C';
UPDATE sys_menu SET parent_id = 0, path = 'support/messages', order_num = 7
  WHERE menu_id = 5152 AND menu_type = 'C';

-- 3) 菜单名还原
UPDATE sys_menu SET menu_name = '版权资产管理' WHERE menu_id = 5123;
UPDATE sys_menu SET menu_name = 'AI创作与福利' WHERE menu_id = 5006;
UPDATE sys_menu SET menu_name = 'AI创作与次数' WHERE menu_id = 5150;

-- 4) A4 菜单名还原
UPDATE sys_menu SET menu_name = 'A4用户管理'          WHERE menu_id = 3000;
UPDATE sys_menu SET menu_name = 'A4-App用户与创作者'  WHERE menu_id = 3001;
UPDATE sys_menu SET menu_name = 'A4-实名审核'         WHERE menu_id = 3005;
UPDATE sys_menu SET menu_name = 'A4-作者能力'         WHERE menu_id = 3008;
UPDATE sys_menu SET menu_name = 'A4-用户消息'         WHERE menu_id = 3010;
UPDATE sys_menu SET menu_name = 'A4-用户反馈'         WHERE menu_id = 3013;

SELECT 'PC_SIDEBAR_IA_ROLLBACK' AS step,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (5100, 5142, 5143, 5150, 5151, 5152) AND parent_id = 0) AS root_leaves_restored,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_type IN ('M', 'C') AND menu_name LIKE 'A4%') AS a4_named_menus;
