-- 无版权作品分流：license_type 允许为空（无版权询盘/报价/订单不携带授权类型）。
-- 已在目标库执行一次；与 smartscript_full_init.sql 中三表 DDL 同步。
ALTER TABLE sys_inquiry MODIFY `license_type` varchar(20) COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '授权类型；无版权作品为空';
ALTER TABLE sys_quote   MODIFY `license_type` varchar(20) COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '授权类型；无版权作品为空';
ALTER TABLE sys_order   MODIFY `license_type` varchar(20) COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '授权类型；无版权作品为空';
