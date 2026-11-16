-- =====================================================================
-- B 模块 · 删除「外部漫剧发行」占位菜单（menu_id=2051）
--
-- 背景：
--   menu_id=2051「外部漫剧发行」指向 component=operation/ShortDrama，
--   该页面是批量脚手架提交（a4c736c）引入的纯静态占位页，
--   数据全写死（156/8/99.2%、抖音短剧/快手短剧/微信视频号），
--   按钮只弹 ElMessage，无任何后端调用。
--   真实的外部视频全功能管理已在 menu_id=2012「外部视频管理」目录下
--   （渠道/视频内容/关联剧本/上下架/播放数据 5 个子页），该占位菜单冗余。
--
-- 处理（幂等）：
--   1. 删除 menu_id=2051 及其子按钮（若有）；
--   2. 同步删除 sys_role_menu 中对应的关联行。
--
-- 回滚：
--   重新执行 smartscript_full_init.sql 第 1140 行的 INSERT。
--
-- 执行：
--   $env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev `
--     -e "source sql/migrations/b/B_20261010_004__remove_placeholder_shortdrama_menu.sql"
--   verify 闸门：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 删除菜单 2051 及其子项（role_menu 先删，再删 menu）
-- ---------------------------------------------------------------------
DELETE FROM sys_role_menu WHERE menu_id = 2051;
DELETE FROM sys_role_menu WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE parent_id = 2051);
DELETE FROM sys_menu WHERE parent_id = 2051;
DELETE FROM sys_menu WHERE menu_id = 2051;

-- ---------------------------------------------------------------------
-- 2. 校验：确认 2051 及其子项已不存在
-- ---------------------------------------------------------------------
SET @remain = (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 2051 OR parent_id = 2051);
SET @fail_cnt = IF(@remain > 0, 1, 0);

SELECT 'CHECK' AS check_result,
       CONCAT('remaining_2051=', @remain) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('仍有残留菜单：remaining=', @remain) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, 占位菜单 2051 已清除') AS detail
WHERE @fail_cnt = 0;
