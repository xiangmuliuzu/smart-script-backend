-- ============================================================
-- 菜单整理：参照 pc(1) 分组，修复 parent_id=0 的散落 C 类型页面
-- 根因：C-type 挂在 parent_id=0 时后端不做 Layout 包裹，前端渲染为 "/"
-- 修复：归入对应 M-type 目录；隐藏若依基线默认菜单
-- ============================================================

-- 1. 数据总览 → 归入「工作台」
UPDATE sys_menu SET parent_id = 5000, order_num = 1 WHERE menu_id = 5100;

-- 2. 全局风控管理 → 归入「平台运维管理」
UPDATE sys_menu SET parent_id = 5005, order_num = 3 WHERE menu_id = 5143;

-- 3. AI创作与次数 → 归入「AI创作与福利」
UPDATE sys_menu SET parent_id = 5006, order_num = 1 WHERE menu_id = 5150;

-- 4. 福利与积分配置 → 归入「AI创作与福利」
UPDATE sys_menu SET parent_id = 5006, order_num = 2 WHERE menu_id = 5151;

-- 5. 消息与公告 → 归入「AI创作与福利」
UPDATE sys_menu SET parent_id = 5006, order_num = 3 WHERE menu_id = 5152;

-- 6. 隐藏若依基线默认菜单（不删除，仅 visible=1）
UPDATE sys_menu SET visible = '1' WHERE menu_id IN (1, 2, 3, 4);

-- 6.5 修复路径：5142 path 以斜杠开头导致路由异常
UPDATE sys_menu SET path = 'user' WHERE menu_id = 5142;

-- 7. 重新排列顶层 M-type 目录顺序（对齐 pc(1)）
UPDATE sys_menu SET order_num = 1 WHERE menu_id = 5000; -- 工作台
UPDATE sys_menu SET order_num = 2 WHERE menu_id = 5001; -- 内容与作品
UPDATE sys_menu SET order_num = 3 WHERE menu_id = 5002; -- 数据统计
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 5003; -- 版权审核管理
UPDATE sys_menu SET order_num = 5 WHERE menu_id = 5004; -- 交易商务管理
UPDATE sys_menu SET order_num = 6 WHERE menu_id = 5005; -- 平台运维管理
UPDATE sys_menu SET order_num = 7 WHERE menu_id = 5006; -- AI创作与福利
