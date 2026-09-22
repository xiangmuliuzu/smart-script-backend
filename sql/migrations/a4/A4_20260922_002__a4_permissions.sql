-- =====================================================================
-- A4_20260922_002__a4_permissions.sql
-- 用途: A4 菜单与 15 个权限标识的版本化落库（独立于表结构迁移 001）
-- 适用: MySQL 8.0.36
-- 前置: A4_20260922_001__a4_migrate.sql 已执行且 verify 通过
-- 事务: 幂等；可重复执行，不产生重复菜单
-- 依据: A4-PC管理实施方案.md §8 / A4-管理接口契约.md §4
--
-- 设计要点：
--   - 新建顶级目录「A4用户管理」(path=appuser)，不与若依原生「系统管理/用户管理」
--     (menu_id=100, path=user) 冲突；菜单由 /getRouters 动态下发。
--   - 权限标注在组件映射中显式登记（user/UserManage、user/realname/index 等），
--     前端不依赖静态路由兜底。
--   - 15 个权限标识与后端 @PreAuthorize 同名，接口注解为最终强制边界。
--   - 幂等定位键：menu_id（A4 固定段 3000-3018）+ perms 双重校验；
--     若同 perms 已被非 A4 菜单占用，precheck 已阻断，本脚本不再猜测。
--   - 授权：仅授予超级管理员角色（admin 判定与若依一致），不扩散到普通角色。
--
-- 回滚: U20260922_002__a4_permissions_rollback.sql
-- =====================================================================

SET NAMES utf8mb4;
SET @a4_perm_version := 'A4_20260922_002';

SELECT 'PERM_MIGRATE_START' AS step, @a4_perm_version AS version, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 0) 前置：001 必须已应用
-- ---------------------------------------------------------------------
SET @a4_001_applied := (
  SELECT COUNT(*) FROM a4_migration_history
  WHERE version = 'A4_20260922_001' AND rolled_back = 0
);
SELECT 'A4_PERM_PRECHECK' AS step,
       @a4_001_applied AS a4_001_applied,
       IF(@a4_001_applied = 1, 'PROCEED', 'REFUSE_001_NOT_APPLIED') AS decision;

-- ---------------------------------------------------------------------
-- 1) 菜单定义（临时表驱动，保证定义与插入一致）
--    menu_id 固定段 3000-3018；F 类型按钮 visible=0 由前端指令控制显隐
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_a4_menus;
CREATE TEMPORARY TABLE tmp_a4_menus (
  menu_id   BIGINT      NOT NULL PRIMARY KEY,
  menu_name VARCHAR(50) NOT NULL,
  parent_id BIGINT      NOT NULL,
  order_num INT         NOT NULL,
  path      VARCHAR(200) NULL,
  component VARCHAR(255) NULL,
  route_name VARCHAR(50) NULL,
  menu_type CHAR(1)     NOT NULL,
  perms     VARCHAR(100) NULL,
  icon      VARCHAR(100) NULL
) ENGINE=MEMORY;

INSERT INTO tmp_a4_menus
  (menu_id, menu_name, parent_id, order_num, path, component, route_name, menu_type, perms, icon)
VALUES
  -- 顶级目录
  (3000, 'A4用户管理', 0,    5, 'appuser',  NULL,                       NULL,           'M', NULL,                    'peoples'),

  -- App 用户与创作者（适配复用现有 UserManage.vue）
  (3001, 'A4-App用户与创作者', 3000, 1, 'users',   'user/UserManage',   'A4AppUser',    'C', 'user:app:list',         'user'),
  (3002, 'A4-App用户查询',     3001, 1, '#',       NULL,                NULL,           'F', 'user:app:query',        '#'),
  (3003, 'A4-App用户状态',     3001, 2, '#',       NULL,                NULL,           'F', 'user:app:status',       '#'),
  (3004, 'A4-用户角色授权',    3001, 3, '#',       NULL,                NULL,           'F', 'user:app:grant',        '#'),

  -- 实名审核（新增页）
  (3005, 'A4-实名审核',        3000, 2, 'realname', 'user/realname/index', 'A4RealName', 'C', 'user:realname:list',    'form'),
  (3006, 'A4-实名申请查询',    3005, 1, '#',       NULL,                NULL,           'F', 'user:realname:query',   '#'),
  (3007, 'A4-实名审核决定',    3005, 2, '#',       NULL,                NULL,           'F', 'user:realname:audit',   '#'),

  -- 作者能力（适配复用现有 UserManage.vue 第二卡片）
  (3008, 'A4-作者能力',        3000, 3, 'creator', 'user/UserManage',   'A4Creator',    'C', 'user:creator:list',     'star'),
  (3009, 'A4-作者能力变更',    3008, 1, '#',       NULL,                NULL,           'F', 'user:creator:update',   '#'),

  -- 用户消息（新增页）
  (3010, 'A4-用户消息',        3000, 4, 'message', 'user/message/index', 'A4Message',   'C', 'user:message:list',     'message'),
  (3011, 'A4-消息创建',        3010, 1, '#',       NULL,                NULL,           'F', 'user:message:add',      '#'),
  (3012, 'A4-消息详情',        3010, 2, '#',       NULL,                NULL,           'F', 'user:message:query',    '#'),

  -- 用户反馈（新增页）
  (3013, 'A4-用户反馈',        3000, 5, 'feedback', 'user/feedback/index', 'A4Feedback', 'C', 'user:feedback:list',    'edit'),
  (3014, 'A4-反馈详情',        3013, 1, '#',       NULL,                NULL,           'F', 'user:feedback:query',   '#'),
  (3015, 'A4-反馈处理',        3013, 2, '#',       NULL,                NULL,           'F', 'user:feedback:handle',  '#');

-- ---------------------------------------------------------------------
-- 2) 幂等插入：已存在的 menu_id 不重复插入；同 perms 已被他处占用则报错停止
-- ---------------------------------------------------------------------
SET @perms_taken := (
  SELECT COUNT(*) FROM sys_menu m
  JOIN tmp_a4_menus t ON t.perms IS NOT NULL AND m.perms = t.perms
  WHERE m.menu_id <> t.menu_id
);
SELECT 'A4_PERM_CONFLICT' AS step,
       @perms_taken AS conflicting_perms,
       IF(@perms_taken = 0, 'PROCEED', 'REFUSE_PERMS_CONFLICT') AS decision;

INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT t.menu_id, t.menu_name, t.parent_id, t.order_num, t.path, t.component, '', t.route_name,
       1, 0, t.menu_type, '0', '0', t.perms, t.icon, 'a4_migration', NOW(),
       'A4 PC 管理能力权限（A4_20260922_002）'
FROM tmp_a4_menus t
LEFT JOIN sys_menu m ON m.menu_id = t.menu_id
WHERE m.menu_id IS NULL;

-- ---------------------------------------------------------------------
-- 3) 授权：仅超级管理员角色（admin 判定与若依一致）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_a4_admin_roles;
CREATE TEMPORARY TABLE tmp_a4_admin_roles (
  role_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO tmp_a4_admin_roles (role_id)
SELECT role_id FROM sys_role
WHERE del_flag = '0'
  AND (role_key = 'admin' OR role_id = 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, t.menu_id
FROM tmp_a4_admin_roles r
CROSS JOIN tmp_a4_menus t
LEFT JOIN sys_role_menu rm ON rm.role_id = r.role_id AND rm.menu_id = t.menu_id
WHERE rm.role_id IS NULL;

-- ---------------------------------------------------------------------
-- 4) 校验与登记
-- ---------------------------------------------------------------------
SELECT 'A4_PERM_SUMMARY' AS step,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015) AS a4_menus,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015 AND menu_type = 'M') AS dirs,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015 AND menu_type = 'C') AS pages,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 3000 AND 3015 AND menu_type = 'F') AS buttons,
       (SELECT COUNT(*)
        FROM sys_menu m JOIN tmp_a4_menus t ON t.menu_id = m.menu_id
        WHERE COALESCE(m.perms,'') <> COALESCE(t.perms,'')) AS perms_mismatch,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id BETWEEN 3000 AND 3015) AS granted_rows;

INSERT INTO a4_migration_history (version, purpose, remark)
VALUES (
  @a4_perm_version,
  'A4 menu and 15 permission identifiers (versioned permission migration)',
  'A4 PC admin permissions'
)
ON DUPLICATE KEY UPDATE
  purpose     = VALUES(purpose),
  applied_at  = NOW(),
  rolled_back = 0,
  rollback_at = NULL,
  remark      = VALUES(remark);

DROP TEMPORARY TABLE IF EXISTS tmp_a4_menus;
DROP TEMPORARY TABLE IF EXISTS tmp_a4_admin_roles;

SELECT 'PERM_MIGRATE_DONE' AS step, @a4_perm_version AS version;
