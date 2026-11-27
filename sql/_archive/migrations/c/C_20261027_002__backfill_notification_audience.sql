-- 文件: C_20261027_002__backfill_notification_audience.sql
-- 用途: 回填存量交易通知的受众角色（C_20261027_001 加列后历史行 audience=NULL 会全端可见）。
--       依据 TradeNotifyService 确定性 requestId 前缀反推受众：
--       INQ-CRT(新询盘→卖方) / ORD-PAY(买方已付款→卖方) = SELLER；
--       INQ-ACC(询盘被接受→买方) / CTC-CMP(签署完成待付款→买方) = BUYER；
--       QTE-ACC / CTC-SGN / ORD-CMP / ORD-CXL 双方视角 = BOTH。
-- 依赖表: user_notification（audience 列由 C_20261027_001 引入）
-- 执行库: script_platform_dev
-- 幂等: 全部限定 audience IS NULL 且仅改 request_id 前缀匹配行，重复执行 0 行受影响。

UPDATE user_notification SET audience = 'SELLER' WHERE audience IS NULL AND request_id LIKE 'TRD-INQ-CRT-%';
UPDATE user_notification SET audience = 'SELLER' WHERE audience IS NULL AND request_id LIKE 'TRD-ORD-PAY-%';
UPDATE user_notification SET audience = 'BUYER'  WHERE audience IS NULL AND request_id LIKE 'TRD-INQ-ACC-%';
UPDATE user_notification SET audience = 'BUYER'  WHERE audience IS NULL AND request_id LIKE 'TRD-CTC-CMP-%';
UPDATE user_notification SET audience = 'BOTH'   WHERE audience IS NULL AND request_id LIKE 'TRD-QTE-ACC-%';
UPDATE user_notification SET audience = 'BOTH'   WHERE audience IS NULL AND request_id LIKE 'TRD-CTC-SGN-%';
UPDATE user_notification SET audience = 'BOTH'   WHERE audience IS NULL AND request_id LIKE 'TRD-ORD-CMP-%';
UPDATE user_notification SET audience = 'BOTH'   WHERE audience IS NULL AND request_id LIKE 'TRD-ORD-CXL-%';

-- 校验：交易通知受众分布（NULL 应只剩非 TRD- 前缀的通用/评审/公告类通知）
SELECT audience, COUNT(*) AS cnt
FROM user_notification
WHERE request_id LIKE 'TRD-%'
GROUP BY audience;
