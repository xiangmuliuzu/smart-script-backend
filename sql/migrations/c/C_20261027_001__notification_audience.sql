-- 文件: C_20261027_001__notification_audience.sql
-- 用途: 交易站内通知增加「受众角色」列，用于按门户过滤——企业端(买方门户)只取 BUYER/BOTH，
--       创作者端(卖方门户)只取 SELLER/BOTH，APP端(买卖合一)不加过滤即全量可见。
--       修复「卖方视角通知(如『收到新询盘』『买方已付款请交割』)漏进企业端」的问题。
-- 依赖表: user_notification（smartscript_full_init.sql 创建；A4 已含 business_type/business_id 双列）
-- 执行库: script_platform_dev
-- 幂等: 经 information_schema 守卫，列存在则跳过，可重复执行。

-- user_notification：受众角色（BUYER/SELLER/BOTH），NULL=通用/非交易通知，全端可见（兼容存量行与公告类通知）
SET @sql := IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification' AND COLUMN_NAME = 'audience'),
    'ALTER TABLE user_notification ADD COLUMN audience VARCHAR(16) DEFAULT NULL COMMENT ''交易通知受众: BUYER买方/SELLER卖方/BOTH双方; NULL=通用全端可见'' AFTER business_id',
    'SELECT ''column user_notification.audience already exists, skipped'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 校验：列应存在（预期 1 行）
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_notification'
  AND COLUMN_NAME = 'audience';
