-- C 模块交易菜单补全迁移
-- 目标：补全 sys_menu 中交易商务（parent 5004）下缺失的 5 个页面菜单，
--       并将"询盘与报价"骨架页修正为 C 模块真实组件 trade/Inquiry。
--
-- 现状（迁移前）：
--   5130 交易作品管理  trade/TradeWorks      ✓
--   5131 授权订单管理  trade/AuthOrders       ✓
--   5132 合作方管理    trade/Partners          ✓
--   5133 询盘与报价    common/ModuleScaffold   ← 骨架，需修正
--   5134 合同与结算    common/ModuleScaffold   ← D 模块预留，不动
--
-- 迁移后顺序（按 PRD 9.x 排列）：
--   1 交易作品管理  5130  trade/TradeWorks
--   2 询盘管理      5133  trade/Inquiry        ← 修正
--   3 报价管理      5135  trade/Quote          ← 新增
--   4 授权订单管理  5131  trade/AuthOrders
--   5 合作方管理    5132  trade/Partners
--   6 需求标签      5136  trade/DemandTags     ← 新增
--   7 商务跟进      5137  trade/FollowUp       ← 新增
--   8 征集项目      5138  trade/Demand         ← 新增
--   9 合同与结算    5134  common/ModuleScaffold  ← D 模块，不动
--
-- 可重复执行；回滚见 U20260923_002__c_trade_menu_rollback.sql
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1) 修正骨架页 5133：询盘与报价 → 询盘管理
-- ---------------------------------------------------------------------
UPDATE sys_menu
SET menu_name   = '询盘管理',
    path        = 'inquiry',
    component   = 'trade/Inquiry',
    perms       = 'smartscript:trade:inquiry',
    icon        = 'Chat',
    update_by   = 'c-migration',
    update_time = NOW()
WHERE menu_id = 5133
  AND parent_id = 5004;

-- ---------------------------------------------------------------------
-- 2) 新增 4 个菜单（INSERT IGNORE 保证幂等）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
VALUES
  (5135, '报价管理', 5004, 3, 'quote', 'trade/Quote', NULL, '',
   1, 0, 'C', '0', '0', 'smartscript:trade:quote', 'Money',
   'c-migration', NOW(), '', NULL, 'C模块交易菜单补全'),

  (5136, '需求标签', 5004, 6, 'demand-tags', 'trade/DemandTags', NULL, '',
   1, 0, 'C', '0', '0', 'smartscript:trade:demand-tags', 'Tag',
   'c-migration', NOW(), '', NULL, 'C模块交易菜单补全'),

  (5137, '商务跟进', 5004, 7, 'follow-up', 'trade/FollowUp', NULL, '',
   1, 0, 'C', '0', '0', 'smartscript:trade:follow-up', 'Phone',
   'c-migration', NOW(), '', NULL, 'C模块交易菜单补全'),

  (5138, '征集项目', 5004, 8, 'demand', 'trade/Demand', NULL, '',
   1, 0, 'C', '0', '0', 'smartscript:trade:demand', 'List',
   'c-migration', NOW(), '', NULL, 'C模块交易菜单补全');

-- ---------------------------------------------------------------------
-- 3) 统一排列 order_num（PRD 顺序）
-- ---------------------------------------------------------------------
UPDATE sys_menu SET order_num = 1 WHERE menu_id = 5130;
UPDATE sys_menu SET order_num = 2 WHERE menu_id = 5133;
UPDATE sys_menu SET order_num = 3 WHERE menu_id = 5135;
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 5131;
UPDATE sys_menu SET order_num = 5 WHERE menu_id = 5132;
UPDATE sys_menu SET order_num = 6 WHERE menu_id = 5136;
UPDATE sys_menu SET order_num = 7 WHERE menu_id = 5137;
UPDATE sys_menu SET order_num = 8 WHERE menu_id = 5138;
UPDATE sys_menu SET order_num = 9 WHERE menu_id = 5134;

-- ---------------------------------------------------------------------
-- 4) 校验
-- ---------------------------------------------------------------------
SELECT menu_id, menu_name, order_num, path, component, perms
  FROM sys_menu
 WHERE parent_id = 5004
 ORDER BY order_num;
