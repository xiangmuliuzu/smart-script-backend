-- =====================================================================
-- 回滚：C_20261117_001__merge_quote_menu_into_inquiry.sql
--       报价管理菜单(5135) 恢复，报价按钮权限(5188/5196) 挂回其下，
--       询盘页的「报价记录」按钮权限(5424) 下线。
--
-- 注意：前端 src/views/trade/Quote.vue 与 component-map 的 trade/Quote 映射
--   在 smart-script-web 侧已删除，仅执行本脚本只能恢复菜单行，页面仍会因
--   组件缺失而拿不到路由。要真正回到旧形态，需同时回退管理端那次提交。
--
-- 执行：python .local/apply_sql_file.py sql/migrations/c/C_20261117_001__merge_quote_menu_into_inquiry_rollback.sql
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- 1. 恢复报价管理菜单（沿用原 5135 号位与原字段值）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
                      is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT 5135, '报价管理', 5004, 3, 'quote', 'trade/Quote', '', '', 1, 0, 'C', '0', '0',
       'trade:quote:list', 'Money', 'c-migration', 'C模块交易菜单补全'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) x WHERE x.menu_id = 5135);

-- 2. 报价的两个操作按钮挂回报价管理
UPDATE sys_menu
SET parent_id   = 5135,
    order_num   = CASE menu_id WHEN 5188 THEN 1 WHEN 5196 THEN 2 END,
    update_by   = 'c-rollback',
    update_time = NOW()
WHERE menu_id IN (5188, 5196)
  AND parent_id = 5133;

-- 3. 报价管理授权面：与询盘管理(5133) 保持一致（迁移前即同一批角色）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, 5135
FROM sys_role_menu rm
WHERE rm.menu_id = 5133;

-- 4. 下线询盘页的报价记录按钮
DELETE FROM sys_role_menu WHERE menu_id = 5424;
DELETE FROM sys_menu WHERE menu_id = 5424;

-- 5. 自检：5135 复位、5188/5196 回到报价管理下、5424 已清除
SELECT CASE
           WHEN (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 5135 AND component = 'trade/Quote') = 1
               AND (SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (5188, 5196) AND parent_id = 5135) = 2
               AND (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 5424) = 0
           THEN 'PASS'
           ELSE 'FAIL'
           END AS rollback_quote_menu_check,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = 5135) AS quote_menu_grants,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = 5424) AS quote_record_grants;
