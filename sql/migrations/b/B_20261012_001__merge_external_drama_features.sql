-- =====================================================================
-- B 模块 · 外部视频功能并入「视频内容管理」
--
-- 背景：
--   外部视频管理 (menu_id=2012) 下的「关联剧本」(2027)、「上下架管理」(2028)、
--   「播放数据」(2029) 三个子页面，其功能已全部并入「视频内容管理」(2019)：
--     - 关联剧本  → 详情弹窗「关联剧本」Tab（dramaBind/bind、dramaBind/unbind）
--     - 上下架管理 → 列表「上下架」开关 + 「同步」行操作 + 「批量同步」按钮
--       （dramaStatus/changeStatus、dramaStatus/sync）
--     - 播放数据  → 详情弹窗「播放数据」Tab（dramaStats/{workId}、dramaStats/history）
--   故删除这三个页面菜单，并把仍需的按钮权限改挂到 2019 下。
--
-- 处理（幂等）：
--   1. 将仍需保留的按钮权限改挂到 2019：
--        2047 content:dramabind:edit    （关联/解除）
--        2049 content:dramastatus:edit  （上下架/同步）
--        2050 content:dramastats:query  （播放数据详情）
--   2. 新增按钮权限 2052 content:dramastats:list（dramaStats/history 接口需要），
--      并授权给原持有 2029 的角色及 admin 角色。
--   3. 删除页面菜单 2027/2028/2029 与废弃按钮权限 2046 content:dramabind:query、
--      2048 content:dramastatus:query，并清理 sys_role_menu 关联。
--
-- 说明：后端 /dramaStatus、/dramaBind、/dramaStats 接口全部保留，仅收敛入口，不改后端。
--
-- 回滚：
--   重新执行 smartscript_full_init.sql 第 1119-1122 / 1205-1209 行的 INSERT，
--   并删除 menu_id=2052。
--
-- 执行：
--   $env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev `
--     -e "source sql/migrations/b/B_20261012_001__merge_external_drama_features.sql"
--   verify 闸门：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- ---------------------------------------------------------------------
-- 1. 保留的按钮权限改挂到「视频内容管理」(2019)
-- ---------------------------------------------------------------------
UPDATE sys_menu SET parent_id = 2019, order_num = 4
 WHERE menu_id = 2047 AND perms = 'content:dramabind:edit';
UPDATE sys_menu SET parent_id = 2019, order_num = 5
 WHERE menu_id = 2049 AND perms = 'content:dramastatus:edit';
UPDATE sys_menu SET parent_id = 2019, order_num = 6
 WHERE menu_id = 2050 AND perms = 'content:dramastats:query';

-- ---------------------------------------------------------------------
-- 2. 新增「播放历史查询」按钮权限并授权
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
VALUES (2052, '播放历史查询', 2019, 7, '#', NULL, NULL, '', 1, 0, 'F', '0', '0', 'content:dramastats:list', '#', 'b-migration', '外部视频详情-播放历史')
ON DUPLICATE KEY UPDATE parent_id = 2019, order_num = 7,
  menu_name = '播放历史查询', perms = 'content:dramastats:list';

-- 原持有「播放数据」(2029) 的角色与新按钮权限对齐
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 2052 FROM sys_role_menu rm WHERE rm.menu_id = 2029;

-- admin 角色默认可见
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 2052 FROM sys_role r WHERE r.role_key = 'admin' AND r.del_flag = '0';

-- ---------------------------------------------------------------------
-- 3. 清理被删除页面菜单/废弃权限的角色关联，再删除菜单行
-- ---------------------------------------------------------------------
DELETE FROM sys_role_menu WHERE menu_id IN (2027, 2028, 2029, 2046, 2048);
DELETE FROM sys_menu WHERE menu_id IN (2027, 2028, 2029, 2046, 2048);

COMMIT;

-- ---------------------------------------------------------------------
-- 4. 校验：旧菜单已清除，且 2019 下已具备全部所需按钮权限
-- ---------------------------------------------------------------------
SET @old_remain = (SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (2027, 2028, 2029, 2046, 2048));
SET @keep_cnt = (SELECT COUNT(*) FROM sys_menu
                  WHERE parent_id = 2019
                    AND perms IN ('content:dramabind:edit', 'content:dramastatus:edit',
                                  'content:dramastats:query', 'content:dramastats:list'));
SET @fail_cnt = IF(@old_remain = 0 AND @keep_cnt = 4, 0, 1);

SELECT 'CHECK' AS check_result,
       CONCAT('old_remain=', @old_remain, ', keep_under_2019=', @keep_cnt) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('old_remain=', @old_remain, ', keep_under_2019=', @keep_cnt, '（期望 0 与 4）') AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, 外部视频已并入视频内容管理') AS detail
WHERE @fail_cnt = 0;
