-- =====================================================================
-- A4_20260922_003__app_grantable_role.sql
-- 用途: 建立 App 可授权角色的唯一权威来源 —— sys_role.app_grantable
-- 适用: MySQL 8.0.36
-- 前置: A4_20260922_001 / 002 已应用
-- 事务: MySQL DDL 隐式提交；失败以 verify/rollback 收敛
-- 可重复: 是（条件 ALTER + 幂等回填 + history UPSERT）
-- 锁表影响: sys_role ALTER；角色表通常较小
-- 回滚: U20260922_003__app_grantable_role_rollback.sql
-- 依据: A4-管理接口契约.md §5.1 / A4-PC管理实施方案.md §6.3 / P1-R1 裁决
--
-- 裁决要点：
--   - 唯一权威来源是数据库标记，不是部署配置、remark、角色名前缀或 role_key 命名约定。
--   - 默认 0（拒绝）：未显式标记的角色一律不可授予 App 用户。
--   - role_id=1 或 role_key='admin' 永久禁止授予 App 用户，无论标记值如何。
--     该约束在 SQL 层（Mapper）强制执行，本迁移不依赖标记值来兜底。
--   - 标记由若依原生角色管理页维护，复用 system:role:edit 权限与原生操作日志。
--
-- 注意：本迁移**不**为普通业务角色设置 app_grantable=1。默认全部为 0，
--       由管理员按业务需要在角色管理页显式开启。
-- =====================================================================

SET NAMES utf8mb4;
SET @a4_role_version := 'A4_20260922_003';

SELECT 'ROLE_MIGRATE_START' AS step, @a4_role_version AS version, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 0) 前置：001 必须已应用
-- ---------------------------------------------------------------------
SET @a4_001_applied := (
  SELECT COUNT(*) FROM a4_migration_history
  WHERE version = 'A4_20260922_001' AND rolled_back = 0
);
SELECT 'A4_ROLE_PRECHECK' AS step,
       @a4_001_applied AS a4_001_applied,
       IF(@a4_001_applied = 1, 'PROCEED', 'REFUSE_001_NOT_APPLIED') AS decision;

-- ---------------------------------------------------------------------
-- 1) 新增标记列：可空性 NOT NULL，默认 0
-- ---------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role'
    AND COLUMN_NAME = 'app_grantable'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sys_role
     ADD COLUMN app_grantable TINYINT(1) NOT NULL DEFAULT 0
     COMMENT ''是否可授予 App 用户（A4 权威来源；1=可授予，0=拒绝）''
     AFTER data_scope',
  'SELECT ''app_grantable_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2) 安全收敛：超级管理员角色必须保持 0
--    即使有人手工把 role_id=1 / role_key='admin' 置为 1，迁移也会纠正。
--    SQL 层的永久拒绝是最终边界，此处是数据层的一致性兜底。
-- ---------------------------------------------------------------------
SET @admin_forced := 0;
UPDATE sys_role
SET app_grantable = 0
WHERE (role_id = 1 OR role_key = 'admin')
  AND app_grantable <> 0;
SET @admin_forced := ROW_COUNT();

-- ---------------------------------------------------------------------
-- 3) 一致性检查：标记只允许 0/1
-- ---------------------------------------------------------------------
SET @bad_flags := (
  SELECT COUNT(*) FROM sys_role WHERE app_grantable NOT IN (0, 1)
);
SELECT 'A4_ROLE_FLAG_CHECK' AS step,
       @bad_flags AS invalid_flag_rows,
       IF(@bad_flags = 0, 'PASS', 'FAIL') AS result;

-- ---------------------------------------------------------------------
-- 4) 登记版本
-- ---------------------------------------------------------------------
INSERT INTO a4_migration_history (version, purpose, remark)
VALUES (
  @a4_role_version,
  'App grantable role marker on sys_role (single authoritative source)',
  'A4 PC admin: role grantability marker'
)
ON DUPLICATE KEY UPDATE
  purpose     = VALUES(purpose),
  applied_at  = NOW(),
  rolled_back = 0,
  rollback_at = NULL,
  remark      = VALUES(remark);

SELECT 'ROLE_MIGRATE_DONE' AS step, @a4_role_version AS version,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role'
          AND COLUMN_NAME = 'app_grantable') AS column_present,
       (SELECT COUNT(*) FROM sys_role WHERE app_grantable = 1) AS flagged_roles,
       (SELECT COUNT(*) FROM sys_role WHERE role_id = 1 OR role_key = 'admin') AS super_admin_roles,
       @admin_forced AS admin_flags_corrected;
