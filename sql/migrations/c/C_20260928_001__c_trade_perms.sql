-- =====================================================================
-- C_20260928_001__c_trade_perms.sql
-- 用途: 修复 C 模块「权限双轨」问题（问题档案 P0-02），并补全 F 级按钮权限。
-- 适用: MySQL 8.0.x
-- 事务: 幂等；可重复执行，不产生重复菜单/授权。
--
-- 背景（根因）：
--   A1 种子（A1_20260921_001）与 C_20260923_002 给交易页面菜单写入的 perms 形如
--   `smartscript:trade:works`，而后端 TradeController 的 @PreAuthorize 强制边界为
--   `trade:works:list` 等。二者不同名 → 一旦把菜单授给非 admin 角色，用户能看到菜单
--   却调用接口 403（admin 因 *:*:* 通配而不受影响，掩盖了该缺陷）。
--   同时 F 级按钮权限（新增/编辑/删除/查询/转订单…）完全缺失，前端 v-hasPermi 无从判定。
--
-- 处置（对齐 A4_20260922_002 范式）：
--   1) 将 8 个交易页面菜单的 perms 校正为与 @PreAuthorize 同名的 `trade:*:list`；
--   2) 在各页面下补 15 个 F 级按钮权限（menu_id 固定段 5160-5174，visible=0）；
--   3) 全部授予超级管理员角色（admin 判定与若依一致）；
--   4) 同 perms 被非本段菜单占用则报出冲突，不猜测、不覆盖。
--   注：5134「合同与结算」属 D 模块，本脚本不动。
--
-- 回滚: 见文件末尾「回滚指引」注释。
-- =====================================================================

SET NAMES utf8mb4;

SELECT 'C_PERM_MIGRATE_START' AS step, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 1) 校正 8 个交易页面菜单 perms：smartscript:trade:* → trade:*:list
--    与 TradeController @PreAuthorize 同名（接口注解为最终强制边界）
-- ---------------------------------------------------------------------
UPDATE sys_menu SET perms = 'trade:works:list',     update_by = 'c-perm-migration', update_time = NOW() WHERE menu_id = 5130 AND parent_id = 5004;
UPDATE sys_menu SET perms = 'trade:orders:list',    update_by = 'c-perm-migration', update_time = NOW() WHERE menu_id = 5131 AND parent_id = 5004;
UPDATE sys_menu SET perms = 'trade:partners:list',  update_by = 'c-perm-migration', update_time = NOW() WHERE menu_id = 5132 AND parent_id = 5004;
UPDATE sys_menu SET perms = 'trade:inquiry:list',   update_by = 'c-perm-migration', update_time = NOW() WHERE menu_id = 5133 AND parent_id = 5004;
UPDATE sys_menu SET perms = 'trade:quote:list',     update_by = 'c-perm-migration', update_time = NOW() WHERE menu_id = 5135 AND parent_id = 5004;
UPDATE sys_menu SET perms = 'trade:tags:list',      update_by = 'c-perm-migration', update_time = NOW() WHERE menu_id = 5136 AND parent_id = 5004;
UPDATE sys_menu SET perms = 'trade:followups:list', update_by = 'c-perm-migration', update_time = NOW() WHERE menu_id = 5137 AND parent_id = 5004;
UPDATE sys_menu SET perms = 'trade:demand:list',    update_by = 'c-perm-migration', update_time = NOW() WHERE menu_id = 5138 AND parent_id = 5004;

-- ---------------------------------------------------------------------
-- 2) F 级按钮权限定义（临时表驱动，保证定义与插入一致）
--    menu_id 固定段 5160-5174；F 类型 visible=0（前端 v-hasPermi 控制显隐）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_c_trade_buttons;
CREATE TEMPORARY TABLE tmp_c_trade_buttons (
  menu_id   BIGINT       NOT NULL PRIMARY KEY,
  menu_name VARCHAR(50)  NOT NULL,
  parent_id BIGINT       NOT NULL,
  order_num INT          NOT NULL,
  perms     VARCHAR(100) NOT NULL
) ENGINE=MEMORY;

INSERT INTO tmp_c_trade_buttons (menu_id, menu_name, parent_id, order_num, perms) VALUES
  (5160, '交易作品新增',   5130, 1, 'trade:works:add'),
  (5161, '交易作品编辑',   5130, 2, 'trade:works:edit'),
  (5162, '授权订单查询',   5131, 1, 'trade:orders:query'),
  (5163, '合作方新增',     5132, 1, 'trade:partners:add'),
  (5164, '合作方编辑',     5132, 2, 'trade:partners:edit'),
  (5165, '询盘查询',       5133, 1, 'trade:inquiry:query'),
  (5166, '询盘跟进',       5133, 2, 'trade:inquiry:edit'),
  (5167, '询盘转订单',     5133, 3, 'trade:inquiry:convert'),
  (5168, '报价接受/拒绝',  5135, 1, 'trade:quote:edit'),
  (5169, '需求标签新增',   5136, 1, 'trade:tags:add'),
  (5170, '需求标签编辑',   5136, 2, 'trade:tags:edit'),
  (5171, '需求标签删除',   5136, 3, 'trade:tags:remove'),
  (5172, '商务跟进新增',   5137, 1, 'trade:followups:add'),
  (5173, '征集投稿查询',   5138, 1, 'trade:demand:query'),
  (5174, '征集令发布',     5138, 2, 'trade:demand:add');

-- ---------------------------------------------------------------------
-- 3) 冲突守卫：同 perms 已被「非本段」菜单占用则报出（不覆盖、不猜测）
-- ---------------------------------------------------------------------
SET @perms_taken := (
  SELECT COUNT(*) FROM sys_menu m
  JOIN tmp_c_trade_buttons t ON m.perms = t.perms
  WHERE m.menu_id <> t.menu_id
);
SELECT 'C_PERM_CONFLICT' AS step,
       @perms_taken AS conflicting_perms,
       IF(@perms_taken = 0, 'PROCEED', 'REFUSE_PERMS_CONFLICT') AS decision;

-- 幂等插入：menu_id 已存在则跳过
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT t.menu_id, t.menu_name, t.parent_id, t.order_num, '#', NULL, '', NULL,
       1, 0, 'F', '0', '0', t.perms, '#', 'c-perm-migration', NOW(),
       'C 模块交易按钮权限（C_20260928_001）'
FROM tmp_c_trade_buttons t
LEFT JOIN sys_menu m ON m.menu_id = t.menu_id
WHERE m.menu_id IS NULL;

-- ---------------------------------------------------------------------
-- 4) 授权：仅超级管理员角色（admin 判定与若依一致）
--    覆盖 8 个页面菜单 + 15 个按钮，确保菜单与接口权限一并下发
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_c_admin_roles;
CREATE TEMPORARY TABLE tmp_c_admin_roles (
  role_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO tmp_c_admin_roles (role_id)
SELECT role_id FROM sys_role
WHERE del_flag = '0' AND (role_key = 'admin' OR role_id = 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM tmp_c_admin_roles r
CROSS JOIN (
  SELECT menu_id FROM sys_menu WHERE menu_id IN (5130,5131,5132,5133,5135,5136,5137,5138)
  UNION
  SELECT menu_id FROM tmp_c_trade_buttons
) m
LEFT JOIN sys_role_menu rm ON rm.role_id = r.role_id AND rm.menu_id = m.menu_id
WHERE rm.role_id IS NULL;

-- ---------------------------------------------------------------------
-- 5) 校验
-- ---------------------------------------------------------------------
SELECT 'C_PERM_SUMMARY' AS step,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (5130,5131,5132,5133,5135,5136,5137,5138)
          AND perms LIKE 'trade:%:list') AS pages_aligned,
       (SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 5160 AND 5174 AND menu_type = 'F') AS buttons_inserted,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id BETWEEN 5160 AND 5174) AS button_grants,
       (SELECT COUNT(*) FROM sys_menu m JOIN tmp_c_trade_buttons t ON t.menu_id = m.menu_id
          WHERE COALESCE(m.perms,'') <> t.perms) AS perms_mismatch;

-- 明细：列出交易菜单最终 perms，人工比对 TradeController @PreAuthorize
SELECT menu_id, menu_name, parent_id, menu_type, perms
  FROM sys_menu
 WHERE menu_id IN (5130,5131,5132,5133,5135,5136,5137,5138)
    OR menu_id BETWEEN 5160 AND 5174
 ORDER BY parent_id, order_num, menu_id;

DROP TEMPORARY TABLE IF EXISTS tmp_c_trade_buttons;
DROP TEMPORARY TABLE IF EXISTS tmp_c_admin_roles;

SELECT 'C_PERM_MIGRATE_DONE' AS step;

-- ---------------------------------------------------------------------
-- 回滚指引（如需撤销本迁移，按序执行）：
--   DELETE FROM sys_role_menu WHERE menu_id BETWEEN 5160 AND 5174;
--   DELETE FROM sys_menu      WHERE menu_id BETWEEN 5160 AND 5174 AND create_by = 'c-perm-migration';
--   -- 页面 perms 复原为 A1/C_002 原值：
--   UPDATE sys_menu SET perms='smartscript:trade:works'       WHERE menu_id=5130;
--   UPDATE sys_menu SET perms='smartscript:trade:orders'      WHERE menu_id=5131;
--   UPDATE sys_menu SET perms='smartscript:trade:partners'    WHERE menu_id=5132;
--   UPDATE sys_menu SET perms='smartscript:trade:inquiry'     WHERE menu_id=5133;
--   UPDATE sys_menu SET perms='smartscript:trade:quote'       WHERE menu_id=5135;
--   UPDATE sys_menu SET perms='smartscript:trade:demand-tags' WHERE menu_id=5136;
--   UPDATE sys_menu SET perms='smartscript:trade:follow-up'   WHERE menu_id=5137;
--   UPDATE sys_menu SET perms='smartscript:trade:demand'      WHERE menu_id=5138;
-- ---------------------------------------------------------------------
