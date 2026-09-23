-- U20260921_001__p5_menu_rollback.sql
-- A1 P5 自动回滚 v4
-- 前提：已有库升级时先保存 A1_20260921_001__p5_pregrant_snapshot.sql 的输出，
--       用于核对回滚后是否与授权前一致（本脚本只按 remark 前缀精确撤销自身写入）
-- 范围：只撤销本脚本创建的菜单（A1SEED）及其角色授权，以及 remark='A1SEED-CREATED' 的新建角色
-- 注意：PC_20260923_001__sidebar_ia.sql 会改写部分 A1SEED 菜单的展示属性与层级。
--       若已执行 PC 迁移，回滚会连带删除这些菜单，请按 sql/migrations/README.md 的
--       逆序回滚（先 PC，再 A4，最后 A1）。
SET NAMES utf8mb4;

SET @a1_role_id := (
  SELECT role_id FROM sys_role
  WHERE role_key='a1_operator' AND del_flag='0'
  ORDER BY (remark = 'A1SEED-CREATED') DESC, role_id
  LIMIT 1
);

-- 1) 撤销对本脚本创建菜单的授权
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE @a1_role_id IS NOT NULL
  AND rm.role_id = @a1_role_id
  AND m.remark LIKE 'A1SEED%';

-- 2) 删除本脚本创建的菜单
DELETE FROM sys_menu WHERE remark LIKE 'A1SEED%';

-- 3) 仅删除本脚本新建角色
DELETE FROM sys_role WHERE role_key='a1_operator' AND remark='A1SEED-CREATED';

-- 4) 校验
SELECT 'ROOTS_AFTER' AS section,
       (SELECT COUNT(*) FROM sys_menu WHERE path='system' AND parent_id=0 AND menu_type='M') AS system_roots,
       (SELECT COUNT(*) FROM sys_menu WHERE path='monitor' AND parent_id=0 AND menu_type='M') AS monitor_roots;

SELECT 'ROLE_AFTER' AS section, role_id, role_key, remark FROM sys_role WHERE role_key='a1_operator';

SELECT 'A1SEED_MENUS_LEFT' AS section, COUNT(*) AS cnt FROM sys_menu WHERE remark LIKE 'A1SEED%';
