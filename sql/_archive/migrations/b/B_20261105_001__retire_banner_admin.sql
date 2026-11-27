-- =====================================================================
-- B 模块内容 · 下线旧「Banner管理」独立页面
--
-- 内容：
--   1. 删除组件为 content/banner/index 的菜单（旧 Banner 管理页）；
--   2. 删除 content:banner:* 的按钮权限菜单；
--   3. 清理 sys_role_menu 中的角色关联。
--
-- 说明：轮播位改由「作品推广」统一管理（content/promotion/index），
--       sys_banner 仅保留为 App 端只读兜底数据，不再提供管理入口与写接口。
--
-- 幂等：按 component / perms 定位，不依赖固定菜单 ID，可重复执行；
--       末行 verify 闸门要求 PASS fail_cnt=0。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261105_001__retire_banner_admin.sql"
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 先清角色关联，再删菜单
-- ---------------------------------------------------------------------
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.component = 'content/banner/index'
   OR m.perms LIKE 'content:banner:%';

DELETE FROM sys_menu
WHERE component = 'content/banner/index'
   OR perms LIKE 'content:banner:%';

-- ---------------------------------------------------------------------
-- 2. verify 闸门：任一不符则 fail_cnt > 0
-- ---------------------------------------------------------------------
SET @fail = 0;

SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_menu
    WHERE component = 'content/banner/index'
       OR perms LIKE 'content:banner:%') = 0, 0, 1);

SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_role_menu rm
    JOIN sys_menu m ON m.menu_id = rm.menu_id
    WHERE m.component = 'content/banner/index'
       OR m.perms LIKE 'content:banner:%') = 0, 0, 1);

SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_menu
    WHERE component = 'content/promotion/index') >= 1, 0, 1);

SELECT IF(@fail = 0, 'PASS', 'FAIL') AS result, @fail AS fail_cnt;
