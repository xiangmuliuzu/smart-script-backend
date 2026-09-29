-- =====================================================================
-- C_20260928_004__c_trade_perms_ops.sql
-- 用途: 为第二轮补全的 C 模块「操作类」接口补 F 级按钮权限（问题档案 P1-13/P1-15）。
-- 适用: MySQL 8.0.x
-- 事务: 幂等；可重复执行，不产生重复菜单/授权。
--
-- 背景：
--   C_20260928_001 已校正 8 个交易页面菜单 perms 并补齐 15 个 F 级按钮（menu_id 5160-5174）。
--   本轮新增接口引入了两个此前不存在的权限标识：
--     - trade:inquiry:add  → POST /trade/inquiry（发起询盘，分工条目 9）
--     - trade:quote:add    → POST /trade/quote、POST /trade/quote/counter-offer（提交报价/买方议价，条目 17/19）
--   询盘接受/拒绝/关闭复用已存在的 trade:inquiry:edit（5166）；
--   报价修改复用已存在的 trade:quote:edit（5168）。故本脚本只补两个新 perms。
--
-- 处置（对齐 C_20260928_001 / A4_20260922_002 范式）：
--   1) 在询盘页(5133)、报价页(5135)下各补 1 个 F 级按钮权限（menu_id 固定段 5175-5176）；
--   2) 授予超级管理员角色（admin 判定与若依一致）；
--   3) 同 perms 被非本段菜单占用则报出冲突，不猜测、不覆盖。
--
-- 回滚: 见文件末尾「回滚指引」注释。
-- =====================================================================

SET NAMES utf8mb4;

SELECT 'C_PERM_OPS_START' AS step, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 1) F 级按钮权限定义（临时表驱动，保证定义与插入一致）
--    menu_id 固定段 5175-5176；F 类型 visible=0（前端 v-hasPermi 控制显隐）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_c_trade_ops_buttons;
CREATE TEMPORARY TABLE tmp_c_trade_ops_buttons (
  menu_id   BIGINT       NOT NULL PRIMARY KEY,
  menu_name VARCHAR(50)  NOT NULL,
  parent_id BIGINT       NOT NULL,
  order_num INT          NOT NULL,
  perms     VARCHAR(100) NOT NULL
) ENGINE=MEMORY;

INSERT INTO tmp_c_trade_ops_buttons (menu_id, menu_name, parent_id, order_num, perms) VALUES
  (5175, '询盘发起',     5133, 4, 'trade:inquiry:add'),
  (5176, '报价提交/议价', 5135, 2, 'trade:quote:add');

-- ---------------------------------------------------------------------
-- 2) 冲突守卫：同 perms 已被「非本段」菜单占用则报出（不覆盖、不猜测）
-- ---------------------------------------------------------------------
SET @perms_taken := (
  SELECT COUNT(*) FROM sys_menu m
  JOIN tmp_c_trade_ops_buttons t ON m.perms = t.perms
  WHERE m.menu_id <> t.menu_id
);
SELECT 'C_PERM_OPS_CONFLICT' AS step,
       @perms_taken AS conflicting_perms,
       IF(@perms_taken = 0, 'PROCEED', 'REFUSE_PERMS_CONFLICT') AS decision;

-- 幂等插入：menu_id 已存在则跳过
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT t.menu_id, t.menu_name, t.parent_id, t.order_num, '#', NULL, '', NULL,
       1, 0, 'F', '0', '0', t.perms, '#', 'c-perm-ops-migration', NOW(),
       'C 模块交易操作按钮权限（C_20260928_004）'
FROM tmp_c_trade_ops_buttons t
LEFT JOIN sys_menu m ON m.menu_id = t.menu_id
WHERE m.menu_id IS NULL;

-- ---------------------------------------------------------------------
-- 3) 授权：仅超级管理员角色（admin 判定与若依一致）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_c_admin_roles_ops;
CREATE TEMPORARY TABLE tmp_c_admin_roles_ops (
  role_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO tmp_c_admin_roles_ops (role_id)
SELECT role_id FROM sys_role
WHERE del_flag = '0' AND (role_key = 'admin' OR role_id = 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, t.menu_id
FROM tmp_c_admin_roles_ops r
CROSS JOIN tmp_c_trade_ops_buttons t
LEFT JOIN sys_role_menu rm ON rm.role_id = r.role_id AND rm.menu_id = t.menu_id
WHERE rm.role_id IS NULL;

-- ---------------------------------------------------------------------
-- 4) 校验
-- ---------------------------------------------------------------------
SELECT 'C_PERM_OPS_SUMMARY' AS step,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 5175 AND 5176 AND menu_type = 'F') AS buttons_inserted,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id BETWEEN 5175 AND 5176) AS button_grants,
       (SELECT COUNT(*) FROM sys_menu m JOIN tmp_c_trade_ops_buttons t ON t.menu_id = m.menu_id
          WHERE COALESCE(m.perms,'') <> t.perms) AS perms_mismatch;

SELECT menu_id, menu_name, parent_id, menu_type, perms
  FROM sys_menu
 WHERE menu_id BETWEEN 5175 AND 5176
 ORDER BY parent_id, order_num, menu_id;

DROP TEMPORARY TABLE IF EXISTS tmp_c_trade_ops_buttons;
DROP TEMPORARY TABLE IF EXISTS tmp_c_admin_roles_ops;

SELECT 'C_PERM_OPS_DONE' AS step;

-- ---------------------------------------------------------------------
-- 回滚指引（如需撤销本迁移，按序执行）：
--   DELETE FROM sys_role_menu WHERE menu_id BETWEEN 5175 AND 5176;
--   DELETE FROM sys_menu      WHERE menu_id BETWEEN 5175 AND 5176 AND create_by = 'c-perm-ops-migration';
-- ---------------------------------------------------------------------
