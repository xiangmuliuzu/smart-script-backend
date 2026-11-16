-- 合同管理菜单（使用新的 menu_id 避免冲突）
-- 父菜单按真实路由查询
SET @parent_id = (SELECT menu_id FROM sys_menu WHERE path = 'copyright' AND parent_id = 0 LIMIT 1);

-- 合同管理主菜单
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
(5301, '合同管理', @parent_id, 10, 'contract-manage', 'copyright/ContractManage', 1, 0, 'C', '0', '0', 'smartscript:copyright:contract:list', 'DocumentCopy', 'admin', NOW(), '合同管理页面')
ON DUPLICATE KEY UPDATE menu_name='合同管理', component='copyright/ContractManage', path='contract-manage';

-- 合同操作权限按钮
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
(5302, '查询合同', 5301, 1, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:contract:query', '#', 'admin', NOW(), '合同详情查询'),
(5303, '生成合同', 5301, 2, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:contract:generate', '#', 'admin', NOW(), '生成电子合同'),
(5304, '预览合同', 5301, 3, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:contract:preview', '#', 'admin', NOW(), '预览合同PDF'),
(5305, '下载合同', 5301, 4, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:contract:download', '#', 'admin', NOW(), '下载合同文件'),
(5306, '登记签署', 5301, 5, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:contract:sign', '#', 'admin', NOW(), '登记合同签署状态'),
(5307, '合同归档', 5301, 6, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:contract:archive', '#', 'admin', NOW(), '合同归档'),
(5308, '合同作废', 5301, 7, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:contract:cancel', '#', 'admin', NOW(), '合同作废')
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name);

-- 给超级管理员角色授予权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu
WHERE menu_id BETWEEN 5301 AND 5308;
