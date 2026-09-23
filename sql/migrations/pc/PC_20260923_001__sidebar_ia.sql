-- PC 侧边栏信息架构对齐原型
-- 依据：原型侧边栏 MainLayout 的名称与分组（分组顺序、目录层级、三个改名）
-- 目标库：PC 后端实际连接的开发库（启动后可用 information_schema.PROCESSLIST 核对）
--
-- 前提：产品菜单已由 sql/migrations/a1/A1_20260921_001__p5_menu_seed.sql 写入
--       （menu_id 5000-5152），且 A4 菜单 3000-3015 已由
--       A4_20260922_002__a4_permissions.sql 写入。
-- 本脚本只改 sys_menu 的展示属性，不动 menu_type / perms / visible / status，
-- 也不动任何页面组件，因此不改变权限判定与页面实现。
--
-- 四件事：
--   1) 顶级分组顺序按原型排列
--   2) 产品叶子回到原型的分组层级；路径改写成绝对路径，
--      保证 /dashboard /user /risk /ai/operations /support/* 与改造前完全一致
--      （Dashboard.vue 快捷入口硬编码引用 /copyright/ai-review、/trade/orders、/user、/risk）
--   3) 三个名称对齐原型：版权资产库管理、AI 创作与福利、AI 创作与次数
--   4) A4 迭代菜单去掉 "A4" 前缀，顶级目录「A4用户管理」改名「用户中心」
--
-- 全部按 menu_id 定位并带原值校验，可重复执行；回滚见 U20260923_001__sidebar_ia_rollback.sql
-- 最终校验见 PC_20260923_001__sidebar_ia_verify.sql（SUMMARY 必须 PASS）
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0) 补齐被 A1 种子「组件唯一性」守卫跳过的产品菜单
--    5142 与 3001/3008 共用 user/UserManage，种子据此判为重复而跳过；
--    但它是原型侧边栏的「用户与创作者管理」，路径 /user 还被 Dashboard 快捷入口引用。
--    这里直接按最终形态写入（parent 5005 + 绝对路径），已存在则跳过。
-- ---------------------------------------------------------------------
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 5142, '用户与创作者管理', 5005, 3, '/user', 'user/UserManage', '', 'User',
       1, 0, 'C', '0', '0', 'smartscript:ops:creatorUser', 'User', 'pc_migration', NOW(),
       'A1SEED-product-not-system-user'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 5142);

-- ---------------------------------------------------------------------
-- 1) 顶级分组顺序（原型：工作台 → 内容与作品 → 版权审核管理 → 交易商务管理
--    → 平台运维管理 → AI 创作与福利 → 数据统计 → 系统管理 → 用户中心）
-- ---------------------------------------------------------------------
UPDATE sys_menu SET order_num = 1 WHERE menu_id = 5000 AND menu_type = 'M';
UPDATE sys_menu SET order_num = 2 WHERE menu_id = 5001 AND menu_type = 'M';
UPDATE sys_menu SET order_num = 3 WHERE menu_id = 5003 AND menu_type = 'M';
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 5004 AND menu_type = 'M';
UPDATE sys_menu SET order_num = 5 WHERE menu_id = 5005 AND menu_type = 'M';
UPDATE sys_menu SET order_num = 6 WHERE menu_id = 5006 AND menu_type = 'M';
UPDATE sys_menu SET order_num = 7 WHERE menu_id = 5002 AND menu_type = 'M';
UPDATE sys_menu SET order_num = 8 WHERE menu_id = 1 AND menu_type = 'M';
UPDATE sys_menu SET order_num = 9 WHERE menu_id = 3000 AND menu_type = 'M';

-- ---------------------------------------------------------------------
-- 2) 分组层级 + 绝对路径
-- ---------------------------------------------------------------------
-- 工作台 → 数据总览
UPDATE sys_menu SET parent_id = 5000, path = '/dashboard', order_num = 1
  WHERE menu_id = 5100 AND menu_type = 'C' AND path = 'dashboard';

-- 平台运维管理 → 广告运营配置 / 用户画像与推荐配置 / 用户与创作者管理 / 全局风控管理
UPDATE sys_menu SET parent_id = 5005, path = '/user', order_num = 3
  WHERE menu_id = 5142 AND menu_type = 'C' AND path = 'user';
UPDATE sys_menu SET parent_id = 5005, path = '/risk', order_num = 4
  WHERE menu_id = 5143 AND menu_type = 'C' AND path = 'risk';

-- AI 创作与福利 → AI 创作与次数 / 福利与积分配置 / 消息与公告
UPDATE sys_menu SET parent_id = 5006, path = '/ai/operations', order_num = 1
  WHERE menu_id = 5150 AND menu_type = 'C' AND path = 'ai/operations';
UPDATE sys_menu SET parent_id = 5006, path = '/support/welfare', order_num = 2
  WHERE menu_id = 5151 AND menu_type = 'C' AND path = 'support/welfare';
UPDATE sys_menu SET parent_id = 5006, path = '/support/messages', order_num = 3
  WHERE menu_id = 5152 AND menu_type = 'C' AND path = 'support/messages';

-- ---------------------------------------------------------------------
-- 3) 名称对齐原型
-- ---------------------------------------------------------------------
UPDATE sys_menu SET menu_name = '版权资产库管理' WHERE menu_id = 5123 AND menu_name = '版权资产管理';
UPDATE sys_menu SET menu_name = 'AI 创作与福利'  WHERE menu_id = 5006 AND menu_name = 'AI创作与福利';
UPDATE sys_menu SET menu_name = 'AI 创作与次数'  WHERE menu_id = 5150 AND menu_name = 'AI创作与次数';

-- ---------------------------------------------------------------------
-- 4) A4 迭代菜单去前缀（只改目录与菜单，F 类型按钮名保持不变）
-- ---------------------------------------------------------------------
UPDATE sys_menu SET menu_name = '用户中心'         WHERE menu_id = 3000 AND menu_name = 'A4用户管理';
UPDATE sys_menu SET menu_name = 'App用户与创作者'   WHERE menu_id = 3001 AND menu_name = 'A4-App用户与创作者';
UPDATE sys_menu SET menu_name = '实名审核'         WHERE menu_id = 3005 AND menu_name = 'A4-实名审核';
UPDATE sys_menu SET menu_name = '作者能力'         WHERE menu_id = 3008 AND menu_name = 'A4-作者能力';
UPDATE sys_menu SET menu_name = '用户消息'         WHERE menu_id = 3010 AND menu_name = 'A4-用户消息';
UPDATE sys_menu SET menu_name = '用户反馈'         WHERE menu_id = 3013 AND menu_name = 'A4-用户反馈';

-- ---------------------------------------------------------------------
-- 5) 校验：根级散叶子应为 0，A4 命名的目录/菜单应为 0
-- ---------------------------------------------------------------------
SELECT 'PC_SIDEBAR_IA' AS step,
       (SELECT COUNT(*) FROM sys_menu
         WHERE menu_id IN (5100, 5142, 5143, 5150, 5151, 5152) AND parent_id = 0) AS root_leaves_left,
       (SELECT COUNT(*) FROM sys_menu
         WHERE menu_type IN ('M', 'C') AND menu_name LIKE 'A4%') AS a4_named_menus,
       (SELECT COUNT(*) FROM sys_menu
         WHERE menu_id = 5100 AND parent_id = 5000 AND path = '/dashboard') AS dashboard_nested,
       (SELECT COUNT(*) FROM sys_menu
         WHERE menu_id IN (5142, 5143) AND parent_id = 5005 AND path IN ('/user', '/risk')) AS ops_nested;

SELECT 'PC_SIDEBAR_USER_CENTER' AS step,
       (SELECT GROUP_CONCAT(CONCAT(menu_id, ':', menu_name) ORDER BY order_num)
          FROM sys_menu WHERE parent_id = 3000 AND menu_type = 'C') AS children;
