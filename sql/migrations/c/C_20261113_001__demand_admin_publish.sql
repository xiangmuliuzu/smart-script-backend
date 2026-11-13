-- =====================================================================
-- C 模块交易 · 任务发布权收归平台运营
--
-- 背景：普通用户不再发布任务（平台在线下与需求方商定并收款后代为发布），
--   用户侧只保留「查看任务 + 开通创作者后接取任务」；发布、逐轮验收、
--   延时审批、编辑与关闭全部移到管理端。托管/押金入账随之退役。
--
-- 内容：
--   1. 恢复「任务发布」按钮权限 trade:demand:add（5194，被 C_20261030_002 删除）
--   2. 新增「任务运营编辑」trade:demand:edit（5197）与「逐轮验收」trade:demand:review（5198）
--   3. 三个按钮权限授予当前已能看到「任务管理」(5138) 的角色，另兜底授予 admin 角色
--
-- 不做的事：
--   - sys_demand.escrow_* / deposit_* 列保留（存量单对账留档），仅停止写入与筛选，故无 DDL；
--   - 存量任务一律保留，不做状态改写：历史 open + pending_payment（从未托管到账）的单，
--     在新口径（广场只看 status=open）下会直接曝光，由运营自行编辑或关闭。
--
-- 幂等：菜单用 INSERT ... SELECT ... WHERE NOT EXISTS；角色关联用 INSERT IGNORE。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/c/C_20261113_001__demand_admin_publish.sql"
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- 1. 任务发布（代需求方建单）：沿用被删除的 5194 号位，恢复后管理端「新增任务」按钮才可见
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
                      is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT 5194, '任务发布', 5138, 2, '#', NULL, NULL, '', 1, 0, 'F', '0', '0',
       'trade:demand:add', '#', 'c-migration', '平台代需求方发布任务（含验收轮次）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) x WHERE x.menu_id = 5194);

-- 2. 任务运营编辑：改单 / 关闭 / 延时审批 / 自主延时
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
                      is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT 5197, '任务运营编辑', 5138, 3, '#', NULL, NULL, '', 1, 0, 'F', '0', '0',
       'trade:demand:edit', '#', 'c-migration', '编辑/关闭任务与延时审批'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) x WHERE x.menu_id = 5197);

-- 3. 逐轮验收：审核本轮交付（通过即记账放款、驳回即恢复计时）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
                      is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT 5198, '任务逐轮验收', 5138, 4, '#', NULL, NULL, '', 1, 0, 'F', '0', '0',
       'trade:demand:review', '#', 'c-migration', '逐轮交付验收与放款触发'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu) x WHERE x.menu_id = 5198);

-- 4. 授权：已能看到「任务管理」(5138) 的角色自动拿到三个操作按钮；admin 角色兜底
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, m.menu_id
FROM sys_role_menu rm
         JOIN (SELECT 5138 AS menu_id) square ON rm.menu_id = square.menu_id
         CROSS JOIN (SELECT 5194 AS menu_id
                     UNION ALL SELECT 5197
                     UNION ALL SELECT 5198) m;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
         CROSS JOIN (SELECT 5194 AS menu_id
                     UNION ALL SELECT 5197
                     UNION ALL SELECT 5198) m
WHERE r.role_key = 'admin'
  AND r.del_flag = '0';

-- 5. 自检：三个按钮权限必须各就各位
SELECT CASE
           WHEN (SELECT COUNT(*) FROM sys_menu
                 WHERE menu_id IN (5194, 5197, 5198)
                   AND perms IN ('trade:demand:add', 'trade:demand:edit', 'trade:demand:review')) = 3
               THEN 'PASS'
           ELSE 'FAIL'
           END AS demand_admin_publish_check,
       (SELECT COUNT(*) FROM sys_role_menu WHERE menu_id IN (5194, 5197, 5198)) AS granted_rows;
