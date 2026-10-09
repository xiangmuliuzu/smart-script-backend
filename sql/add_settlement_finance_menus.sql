-- 结算管理和财务异常页面菜单
-- 父菜单按真实路由查询，避免依赖环境特定的固定 ID。
SET @parent_id = (SELECT menu_id FROM sys_menu WHERE path = 'copyright' AND parent_id = 0 LIMIT 1);

-- 页面菜单：已存在时保持原记录不变。
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
(5222, '结算管理', @parent_id, 8, 'settlement-manage', 'copyright/SettlementManage', 1, 0, 'C', '0', '0', 'smartscript:copyright:settlement:list', 'Money', 'admin', NOW(), '版权结算管理页面'),
(5223, '财务异常', @parent_id, 10, 'finance-abnormal', 'copyright/FinanceAbnormal', 1, 0, 'C', '0', '0', 'smartscript:copyright:finance:list', 'Warning', 'admin', NOW(), '财务异常处理页面');

-- 结算管理操作权限
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
(5224, '查询结算', 5222, 1, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:settlement:query', '#', 'admin', NOW(), '结算详情查询'),
(5225, '批量核算', 5222, 2, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:settlement:calculate', '#', 'admin', NOW(), '批量结算核算'),
(5226, '处理异常', 5222, 3, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:settlement:handle', '#', 'admin', NOW(), '处理结算异常');

-- 财务异常操作权限
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
(5227, '查询异常', 5223, 1, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:finance:query', '#', 'admin', NOW(), '财务异常查询'),
(5228, '处理异常', 5223, 2, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:finance:handle', '#', 'admin', NOW(), '处理财务异常');

-- 给超级管理员角色授予本批次菜单与按钮权限；重复执行不会重复授权。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu
WHERE menu_id BETWEEN 5222 AND 5228;
