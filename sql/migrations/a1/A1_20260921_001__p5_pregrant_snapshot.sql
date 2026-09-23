-- A1_20260921_001__p5_pregrant_snapshot.sql
-- A1 P5 前置快照（已有库升级时须先于种子执行；保存结果用于可恢复回滚）
-- 全新空库初始化无需执行：无既有数据可快照，出错直接丢弃空库重来。
-- 目标库：MySQL 8 + 若依
-- 执行：将本文件内容送入 mysql，并把输出保存到指定证据文件（勿提交到仓库）
SET NAMES utf8mb4;

SELECT 'SNAPSHOT_TIME' AS section, NOW(6) AS snapshot_time;

SELECT 'ROLE_BEFORE' AS section, role_id, role_name, role_key, remark, del_flag
FROM sys_role
WHERE role_key = 'a1_operator';

SELECT 'ROLE_MENU_BEFORE' AS section,
       rm.role_id, rm.menu_id, r.role_key, m.path, m.component, m.remark AS menu_remark
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
LEFT JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE r.role_key = 'a1_operator';

SELECT 'SYSTEM_MONITOR_ROOTS' AS section, menu_id, path, remark
FROM sys_menu
WHERE parent_id = 0 AND menu_type = 'M' AND path IN ('system', 'monitor');

SELECT 'G1_COMPONENTS' AS section, menu_id, parent_id, path, component, remark
FROM sys_menu
WHERE component IN (
  'system/user/index','system/role/index','system/menu/index','system/post/index',
  'system/dict/index','system/config/index',
  'monitor/operlog/index','monitor/logininfor/index'
);
