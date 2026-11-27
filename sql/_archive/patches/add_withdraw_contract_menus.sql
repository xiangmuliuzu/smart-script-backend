-- D模块新增菜单：提现审核、合同管理及操作权限
-- 父菜单按真实路由查询，避免依赖环境特定的固定 ID。
SET @parent_id = (SELECT menu_id FROM sys_menu WHERE path = 'copyright' AND parent_id = 0 LIMIT 1);

-- 页面菜单：已存在时保持原记录不变。
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
(5209, '提现审核', @parent_id, 9, 'withdraw-review', 'copyright/WithdrawReview', 1, 0, 'C', '0', '0', 'smartscript:copyright:withdraw:list', 'money', 'admin', NOW(), 'D模块-提现审核页面'),
(5210, '合同管理', @parent_id, 7, 'contract-manage', 'copyright/ContractManage', 1, 0, 'C', '0', '0', 'smartscript:copyright:contract:list', 'DocumentCopy', 'admin', NOW(), 'D模块-合同管理页面');

-- 合同操作权限。
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
(5211, '查询合同', 5210, 1, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:contract:query', '#', 'admin', NOW(), '合同详情查询'),
(5212, '生成合同', 5210, 2, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:contract:generate', '#', 'admin', NOW(), '生成电子合同'),
(5213, '登记签署', 5210, 3, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:contract:sign', '#', 'admin', NOW(), '登记合同签署状态'),
(5214, '合同归档', 5210, 4, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:contract:archive', '#', 'admin', NOW(), '合同归档'),
(5215, '合同作废', 5210, 5, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:contract:cancel', '#', 'admin', NOW(), '合同作废');

-- 提现操作权限。
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
(5216, '查询提现', 5209, 1, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:withdraw:query', '#', 'admin', NOW(), '提现详情查询'),
(5217, '审核通过', 5209, 2, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:withdraw:approve', '#', 'admin', NOW(), '提现审核通过'),
(5218, '审核驳回', 5209, 3, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:withdraw:reject', '#', 'admin', NOW(), '提现审核驳回'),
(5219, '提现冻结', 5209, 4, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:withdraw:freeze', '#', 'admin', NOW(), '冻结已通过提现'),
(5220, '提现解冻', 5209, 5, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:withdraw:unfreeze', '#', 'admin', NOW(), '解冻提现申请'),
(5221, '提现导出', 5209, 6, '', '', 1, 0, 'F', '0', '0', 'smartscript:copyright:withdraw:export', '#', 'admin', NOW(), '导出提现记录');

-- 给超级管理员角色授予本批次菜单与按钮权限；重复执行不会重复授权。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu
WHERE menu_id BETWEEN 5209 AND 5221;
