-- =====================================================================
-- U20260928_002__cloud_content_menu_rollback.sql
-- 用途: 回滚 B_20260928_002（云库分类/标签菜单）
-- 幂等: 可重复执行
-- =====================================================================
SET NAMES utf8mb4;

SELECT 'B_CLOUD_CONTENT_MENU_ROLLBACK_START' AS step, DATABASE() AS db_name, NOW() AS ts;

DELETE rm FROM sys_role_menu rm
WHERE rm.menu_id IN (2010, 2011, 2020, 2021, 2022, 2023, 2024, 2025, 2026);

DELETE FROM sys_menu
WHERE menu_id IN (2010, 2011, 2020, 2021, 2022, 2023, 2024, 2025, 2026);

SELECT 'B_CLOUD_CONTENT_MENU_ROLLBACK_CHECK' AS step,
       (SELECT COUNT(*) FROM sys_menu
         WHERE menu_id IN (2010, 2011, 2020, 2021, 2022, 2023, 2024, 2025, 2026)) AS menus_left;

SELECT 'B_CLOUD_CONTENT_MENU_ROLLBACK_DONE' AS step;
