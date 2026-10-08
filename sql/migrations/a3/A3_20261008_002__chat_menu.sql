-- A3 用户沟通菜单注册
-- 查询当前最大 menu_id，选取安全 ID 段

-- 用户沟通目录菜单（一级菜单下的子目录）
-- 使用 5200 段，避免与已有 51xx 冲突
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (5200, '用户沟通', 0, 8, 'chat', NULL, '', '', 1, 0, 'M', '0', '0', '', 'chat', 'admin', NOW(), '', NULL, '用户沟通目录');

-- 会话列表（菜单按钮）
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (5201, '会话列表', 5200, 1, 'chat-sessions', 'chat/ChatSessions', '', '', 1, 0, 'C', '0', '0', 'chat:session:list', 'list', 'admin', NOW(), '', NULL, '用户沟通会话列表');

-- 会话详情（隐藏菜单，通过列表页跳转进入）
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (5202, '会话详情', 5200, 2, 'chat-detail', 'chat/ChatDetail', '', '', 1, 0, 'C', '1', '0', 'chat:session:query', '#', 'admin', NOW(), '', NULL, '用户沟通会话详情');

-- 权限按钮：分配管理员
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (5203, '分配管理员', 5201, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'chat:session:assign', '#', 'admin', NOW(), '', NULL, '');

-- 权限按钮：变更状态
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (5204, '变更状态', 5201, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'chat:session:status', '#', 'admin', NOW(), '', NULL, '');

-- 权限按钮：发送消息
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (5205, '发送消息', 5201, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'chat:message:send', '#', 'admin', NOW(), '', NULL, '');

-- 权限按钮：查看消息
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (5206, '查看消息', 5201, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'chat:message:list', '#', 'admin', NOW(), '', NULL, '');

-- 权限按钮：标记已读
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (5207, '标记已读', 5201, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'chat:session:read', '#', 'admin', NOW(), '', NULL, '');

-- 权限按钮：创建会话
INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES (5208, '创建会话', 5201, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'chat:session:create', '#', 'admin', NOW(), '', NULL, '');

-- 给管理员角色（role_id=1）授予全部 chat 菜单
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 5200), (1, 5201), (1, 5202), (1, 5203), (1, 5204), (1, 5205), (1, 5206), (1, 5207), (1, 5208);
