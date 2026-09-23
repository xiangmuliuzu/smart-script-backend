-- =====================================================================
-- U20260922_003__app_grantable_role_rollback.sql
-- 用途: 回滚 sys_role.app_grantable 标记列
-- 适用: MySQL 8.0.36
-- 可重复: 是
--
-- 安全原则：
--   1) app_grantable 是角色配置数据，不是用户业务数据；DROP COLUMN 只丢失
--      「哪些角色允许授予 App 用户」这一配置，不涉及任何用户的授权结果。
--   2) 但若**已有 App 用户实际持有**被标记为可授权的角色，说明该标记已投入使用；
--      此时默认拒绝执行并安全停止，避免回滚后授权语义静默改变。
--      需显式 SET @a4_force_rollback = 1 才继续。
--   3) 不删除任何 sys_role 行，不修改角色状态，不触碰若依原生角色管理。
--   4) 不删除 a4_migration_history 行，只标记 rolled_back。
-- =====================================================================

SET NAMES utf8mb4;
SET @a4_role_version := 'A4_20260922_003';
SET @a4_force_rollback := IFNULL(@a4_force_rollback, 0);

SELECT 'ROLE_ROLLBACK_START' AS step, @a4_role_version AS version,
       @a4_force_rollback AS force_flag, NOW() AS ts;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role'
    AND COLUMN_NAME = 'app_grantable'
);

-- 已被投入使用的证据：App 账号域用户实际持有被标记为可授权的角色
SET @in_use := 0;
SET @sql := IF(@col_exists = 1,
  'SELECT COUNT(*) INTO @in_use
   FROM sys_user_role ur
   JOIN sys_user u ON u.user_id = ur.user_id
   JOIN sys_role r ON r.role_id = ur.role_id
   WHERE u.user_type IN (''01'',''02'',''03'')
     AND u.del_flag = ''0''
     AND r.app_grantable = 1',
  'SELECT 0 INTO @in_use');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT 'ROLE_ROLLBACK_PRECHECK' AS step,
       @col_exists AS column_present,
       @in_use AS app_users_holding_grantable_roles,
       IF(@in_use = 0 OR @a4_force_rollback = 1, 'PROCEED', 'REFUSE_IN_USE') AS decision;

SET @a4_role_proceed := IF(@in_use > 0 AND @a4_force_rollback = 0, 0, 1);

SET @sql := IF(@a4_role_proceed = 1,
  'SELECT ''ROLE_ROLLBACK_PROCEED'' AS step, ''guard_passed'' AS info',
  'SELECT ''ROLE_ROLLBACK_ABORTED'' AS step,
          ''存在 App 用户持有标记为可授权的角色；回滚会静默改变授权语义'' AS reason,
          ''请先回收授权或显式 SET @a4_force_rollback = 1 后重跑'' AS action');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1) 移除标记列
SET @sql := IF(@a4_role_proceed = 1 AND @col_exists = 1,
  'ALTER TABLE sys_role DROP COLUMN app_grantable',
  'SELECT ''skip_drop_app_grantable'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 标记历史
SET @sql := IF(@a4_role_proceed = 1,
  'UPDATE a4_migration_history
     SET rolled_back = 1, rollback_at = NOW(),
         remark = CONCAT(IFNULL(remark,''''), '' | app_grantable rolled back'')
   WHERE version = @a4_role_version',
  'SELECT ''skip_history_mark'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 回滚后状态：列应消失，角色数据与若依原生菜单保持完好
SELECT 'ROLE_ROLLBACK_DONE' AS step, @a4_role_version AS version,
       @a4_role_proceed AS proceeded,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role'
          AND COLUMN_NAME = 'app_grantable') AS remaining_col,
       (SELECT COUNT(*) FROM sys_role) AS total_roles,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 1009) AS ruoyi_role_edit_menu;
