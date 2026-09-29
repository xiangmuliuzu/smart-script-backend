-- =====================================================================
-- B_20260928_005__external_drama_menu.sql
-- 用途: B 模块批3 菜单写入（外部视频管理：渠道/视频内容/关联剧本/上下架/播放数据）
-- 背景: 承接 B_20260928_003（批1）、B_20260928_004（批2），继续在内容管理(2000)下
--       新增 1 个 M 目录(2012) + 5 个 C 页(2018/2019/2027/2028/2029) + 11 个 F 按钮(2040-2050)。
-- 菜单 ID: 2012 M 目录；2018/2019/2027/2028/2029 五个 C 页；2040-2050 十一个 F 按钮。
--          （2017/2020-2026 段由批2占用，已避让；2000-2099 段已确认空闲。）
-- 权限码: 与 smartscript-content 后端 @PreAuthorize 一一对应
--         （渠道 changeStatus 复用 content:channel:edit；
--          视频新增/编辑共用 content:drama:add/edit；
--          关联 bind/unbind 复用 content:dramabind:edit；
--          上下架 changeStatus/sync 复用 content:dramastatus:edit；
--          播放数据仅查询 content:dramastats:query）。
-- 幂等: INSERT IGNORE，可重复执行。
-- =====================================================================
SET NAMES utf8mb4;

SELECT 'B_CONTENT_BATCH3_MENU_START' AS step, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 1) 一个 M 目录（挂在 2000 内容管理下，order_num=8）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, remark)
VALUES
  (2012, '外部视频管理', 2000, 8, 'external-drama', NULL, NULL, '',
   1, 0, 'M', '0', '0', NULL, 'VideoCamera',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）');

-- ---------------------------------------------------------------------
-- 2) 五个 C 页（挂在 2012 外部视频管理下，order_num 续 1-5）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, remark)
VALUES
  (2018, '渠道管理', 2012, 1, 'channel', 'content/external-drama/channel/index', NULL, '',
   1, 0, 'C', '0', '0', 'content:channel:list', 'Connection',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2019, '视频内容管理', 2012, 2, 'drama', 'content/external-drama/drama/index', NULL, '',
   1, 0, 'C', '0', '0', 'content:drama:list', 'Film',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2027, '关联剧本', 2012, 3, 'bind', 'content/external-drama/bind/index', NULL, '',
   1, 0, 'C', '0', '0', 'content:dramabind:list', 'Link',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2028, '上下架管理', 2012, 4, 'status', 'content/external-drama/status/index', NULL, '',
   1, 0, 'C', '0', '0', 'content:dramastatus:list', 'Switch',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2029, '播放数据', 2012, 5, 'stats', 'content/external-drama/stats/index', NULL, '',
   1, 0, 'C', '0', '0', 'content:dramastats:list', 'DataLine',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）');

-- ---------------------------------------------------------------------
-- 3) 十一个 F 按钮（visible='0'，前后端双重校验）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, remark)
VALUES
  (2040, '渠道查询', 2018, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:channel:query', '#',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2041, '渠道新增', 2018, 2, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:channel:add', '#',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2042, '渠道编辑', 2018, 3, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:channel:edit', '#',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2043, '视频查询', 2019, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:drama:query', '#',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2044, '视频新增', 2019, 2, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:drama:add', '#',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2045, '视频编辑', 2019, 3, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:drama:edit', '#',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2046, '关联查询', 2027, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:dramabind:query', '#',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2047, '关联编辑', 2027, 2, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:dramabind:edit', '#',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2048, '上下架查询', 2028, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:dramastatus:query', '#',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2049, '上下架编辑', 2028, 2, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:dramastatus:edit', '#',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）'),
  (2050, '播放数据查询', 2029, 1, '#', NULL, NULL, '',
   1, 0, 'F', '0', '0', 'content:dramastats:query', '#',
   'b-migration', NOW(), 'B模块批3菜单（B_20260928_005）');

-- ---------------------------------------------------------------------
-- 4) 授权超级管理员角色（云库 admin=role_id 1；超管默认可见全部，授权保持显式）
-- 共 17 条：1 M + 5 C + 11 F
-- ---------------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
  SELECT 2012 AS menu_id
  UNION ALL SELECT 2018 UNION ALL SELECT 2019 UNION ALL SELECT 2027 UNION ALL SELECT 2028 UNION ALL SELECT 2029
  UNION ALL SELECT 2040 UNION ALL SELECT 2041 UNION ALL SELECT 2042 UNION ALL SELECT 2043 UNION ALL SELECT 2044
  UNION ALL SELECT 2045 UNION ALL SELECT 2046 UNION ALL SELECT 2047 UNION ALL SELECT 2048 UNION ALL SELECT 2049
  UNION ALL SELECT 2050
) m
LEFT JOIN sys_role_menu rm ON rm.role_id = r.role_id AND rm.menu_id = m.menu_id
WHERE r.del_flag = '0'
  AND (r.role_key = 'admin' OR r.role_id = 1)
  AND rm.role_id IS NULL;

-- ---------------------------------------------------------------------
-- 5) 结果回显
-- ---------------------------------------------------------------------
SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms
FROM sys_menu
WHERE menu_id IN (2012, 2018, 2019, 2027, 2028, 2029,
                  2040, 2041, 2042, 2043, 2044, 2045, 2046, 2047, 2048, 2049, 2050)
ORDER BY menu_id;

SELECT 'B_CONTENT_BATCH3_MENU_DONE' AS step;
