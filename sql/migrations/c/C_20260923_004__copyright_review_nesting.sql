-- ============================================================
-- 版权审核管理菜单层级调整：AI初审+人工复核 从页面改为二级目录
-- 将「作品审核工作台」和「AI审核规则配置」归入其下
-- ============================================================

-- 1. AI初审+人工复核：C(页面) → M(目录)
UPDATE sys_menu SET menu_type = 'M', component = NULL, path = 'ai-review', order_num = 1 WHERE menu_id = 5125;

-- 2. 作品审核工作台 → 移入 AI初审+人工复核 下
UPDATE sys_menu SET parent_id = 5125, order_num = 1 WHERE menu_id = 5120;

-- 3. AI审核规则配置 → 移入 AI初审+人工复核 下
UPDATE sys_menu SET parent_id = 5125, order_num = 2 WHERE menu_id = 5121;
