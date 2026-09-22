-- =====================================================================
-- U20260922_002__a4_permissions_rollback.sql
-- 用途: 回滚 A4_20260922_002 菜单与权限标识
-- 适用: MySQL 8.0.36
-- 可重复: 是
--
-- 安全原则：
--   1) 只删除 A4 固定菜单段 3000-3015 及其授权行，绝不触碰若依原生菜单。
--   2) 若 A4 菜单已被**非超级管理员角色**额外授权（说明已被业务实际使用），
--      默认拒绝执行并安全停止；需显式 SET @a4_force_rollback = 1 才继续。
--   3) 不删除 a4_migration_history 行，只标记 rolled_back，保留审计轨迹。
-- =====================================================================

SET NAMES utf8mb4;
SET @a4_perm_version := 'A4_20260922_002';
SET @a4_force_rollback := IFNULL(@a4_force_rollback, 0);

SELECT 'PERM_ROLLBACK_START' AS step, @a4_perm_version AS version,
       @a4_force_rollback AS force_flag, NOW() AS ts;

-- 破坏性前置检查：是否有非超管角色被额外授权
SET @extra_grants := (
  SELECT COUNT(*) FROM sys_role_menu rm
  JOIN sys_role r ON r.role_id = rm.role_id
  WHERE rm.menu_id BETWEEN 3000 AND 3015
    AND NOT (r.del_flag = '0' AND (r.role_key = 'admin' OR r.role_id = 1))
);
SET @a4_perm_rows := (
  SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015
);

SELECT 'PERM_ROLLBACK_PRECHECK' AS step,
       @a4_perm_rows AS a4_menu_rows,
       @extra_grants AS non_admin_grants,
       IF(@extra_grants = 0 OR @a4_force_rollback = 1,
          'PROCEED', 'REFUSE_IN_USE') AS decision;

SET @a4_perm_proceed := IF(@extra_grants > 0 AND @a4_force_rollback = 0, 0, 1);

-- 1) 删除授权行
SET @sql := IF(@a4_perm_proceed = 1,
  'DELETE FROM sys_role_menu WHERE menu_id BETWEEN 3000 AND 3015',
  'SELECT ''skip_delete_grants'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 删除按钮（F）
SET @sql := IF(@a4_perm_proceed = 1,
  'DELETE FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015 AND menu_type = ''F''',
  'SELECT ''skip_delete_buttons'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 删除页面菜单（C）——必须先于目录，避免悬挂父级
SET @sql := IF(@a4_perm_proceed = 1,
  'DELETE FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015 AND menu_type = ''C''',
  'SELECT ''skip_delete_pages'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 删除目录（M）
SET @sql := IF(@a4_perm_proceed = 1,
  'DELETE FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015 AND menu_type = ''M''',
  'SELECT ''skip_delete_dir'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5) 标记历史（保留审计行）
SET @sql := IF(@a4_perm_proceed = 1,
  'UPDATE a4_migration_history
     SET rolled_back = 1, rollback_at = NOW(),
         remark = CONCAT(IFNULL(remark,''''), '' | permissions rolled back'')
   WHERE version = @a4_perm_version',
  'SELECT ''skip_history_mark'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6) 回滚后状态：A4 菜单应为 0，若依原生 menu_id=100 必须仍存在
SELECT 'PERM_ROLLBACK_DONE' AS step, @a4_perm_version AS version,
       @a4_perm_proceed AS proceeded,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015) AS remaining_a4_menus,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id BETWEEN 3000 AND 3015) AS remaining_grants,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 100 AND perms = 'system:user:list') AS ruoyi_user_menu_intact,
       (SELECT COUNT(*) FROM sys_menu) AS total_menus;
