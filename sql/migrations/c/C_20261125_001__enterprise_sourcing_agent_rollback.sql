-- 回滚企业采购选本 Agent。仅删除本次迁移创建的表。
DROP TABLE IF EXISTS sys_trade_agent_message;
DROP TABLE IF EXISTS sys_trade_agent_session;
