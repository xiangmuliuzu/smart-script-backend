-- =====================================================================
-- C_20261026_001  订单状态机：资金托管改为合同签署驱动
-- 新状态机：confirmed(签合同) →双方签署完成自动→ paying →买方付款→ delivering →买方确认收货→ completed
-- 删除资金托管(escrow)与取消订单流程；sys_escrow 表与历史数据保留只读，不 DROP。
-- 本脚本仅做数据重映射（幂等），不改表结构：status 仍为 VARCHAR，新增枚举值 paying 无需 DDL。
-- =====================================================================

-- 1. 遗留「已托管(已付款待交割)」订单 → 归入新流程的「交割中」
--    （原 escrow_pending 语义为已付款，付款流转后即进入 delivering）
UPDATE sys_order SET status = 'delivering', update_time = NOW()
 WHERE status = 'escrow_pending';

-- 2. 已取消的历史订单保留原值仅作只读展示，不再提供取消入口，无需迁移。

-- 3. 校验：执行后不应再存在 escrow_pending 状态的订单
SELECT COUNT(*) AS remaining_escrow_pending FROM sys_order WHERE status = 'escrow_pending';
