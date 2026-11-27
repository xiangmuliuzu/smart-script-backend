-- =====================================================================
-- C 模块交易 · 报价管理并入询盘管理（管理端菜单收敛）
--
-- 背景：报价不再单独成页。询盘页行内新增「报价记录」按钮，点开即看该询盘
--   的全部报价（只读列表，接口复用 /api/v1/admin/trade/quotes?inquiryId=）；
--   接受报价仍走原逻辑直接生成授权订单。前端 src/views/trade/Quote.vue 已删除，
--   component-map 里的 trade/Quote 映射同时移除，故「报价管理」菜单必须下线。
--
-- 内容：
--   1. 询盘管理(5133) 下新增按钮权限「报价记录」(5424)，权限位沿用原报价管理的
--      trade:quote:list（语义即「能看报价列表」，不新增权限字符串）
--   2. 原挂在报价管理(5135) 下的两个按钮权限 5188 trade:quote:edit、
--      5196 trade:quote:add 重挂到询盘管理(5133) 下，接受/拒绝与议价能力不丢
--   3. 凡能看到「询盘管理」的角色自动获得「报价记录」按钮
--   4. 删除「报价管理」菜单(5135) 及其角色关联
--
-- 后端零改动：listQuotes 已按 inquiryId 过滤，trade:quote:list 权限位保持不变。
--
-- 幂等：菜单用 INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS；授权用 INSERT IGNORE；
--   重挂与删除带条件，可重复执行。
--
-- 回滚：sql/migrations/c/C_20261117_001__merge_quote_menu_into_inquiry_rollback.sql
--
-- 执行：python .local/apply_sql_file.py sql/migrations/c/C_20261117_001__merge_quote_menu_into_inquiry.sql
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- 1. 报价记录按钮（询盘页行内查看该询盘全部报价）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
                      is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT 5424, '报价记录', 5133, 5, '#', NULL, NULL, '', 1, 0, 'F', '0', '0',
       'trade:quote:list', '#', 'c-migration', '报价管理并入询盘管理：行内查看该询盘全部报价'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) x WHERE x.menu_id = 5424);

-- 2. 报价的两个操作按钮重挂到询盘管理下（原父菜单 5135 即将删除）
UPDATE sys_menu
SET parent_id   = 5133,
    order_num   = CASE menu_id WHEN 5188 THEN 6 WHEN 5196 THEN 7 END,
    update_by   = 'c-migration',
    update_time = NOW()
WHERE menu_id IN (5188, 5196)
  AND parent_id = 5135;

-- 3. 授权：能看到询盘管理的角色自动拿到报价记录按钮
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, 5424
FROM sys_role_menu rm
WHERE rm.menu_id = 5133;

-- 4. 下线报价管理菜单及其角色关联
DELETE FROM sys_role_menu WHERE menu_id = 5135;
DELETE FROM sys_menu WHERE menu_id = 5135;

-- 5. 自检：5135 已消失、5424 就位、5188/5196 已归到询盘管理、授权面与询盘管理一致
SELECT CASE
           WHEN (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 5135) = 0
               AND (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 5424 AND parent_id = 5133
                    AND menu_type = 'F' AND perms = 'trade:quote:list') = 1
               AND (SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (5188, 5196) AND parent_id = 5133) = 2
           THEN 'PASS'
           ELSE 'FAIL'
           END AS merge_quote_into_inquiry_check,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = 5133) AS inquiry_grants,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = 5424) AS quote_record_grants,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = 5135) AS quote_menu_grants;
