-- ============================================================
-- B_20260928_006 内容管理菜单图标修正
-- 依据：前端 menu-icon.js 白名单 MENU_ICONS 不含 Documentation/Goods/Connection/Film,
--       导致 4 个菜单图标解析为空不渲染。改为白名单内贴切图标名。
-- 库：script_platform_dev (云端)
-- ============================================================

SELECT 'B_CONTENT_MENU_ICON_FIX_START' AS step, NOW() AS ts;

-- 4 个菜单 icon 改为前端白名单内名称
UPDATE sys_menu SET icon = 'Document'    WHERE menu_id = 2013;  -- 作品管理
UPDATE sys_menu SET icon = 'ShoppingCart' WHERE menu_id = 2014;  -- 书城作品管理
UPDATE sys_menu SET icon = 'Guide'        WHERE menu_id = 2018;  -- 渠道管理
UPDATE sys_menu SET icon = 'VideoPlay'    WHERE menu_id = 2019;  -- 视频内容管理

SELECT 'B_CONTENT_MENU_ICON_FIX_DONE' AS step;

-- 校验：4 个菜单 icon 均已更新
SELECT menu_id, menu_name, icon,
       CASE WHEN icon IN ('Document','ShoppingCart','Guide','VideoPlay') THEN 'PASS' ELSE 'FAIL' END AS check_result
FROM sys_menu WHERE menu_id IN (2013,2014,2018,2019);
