-- =====================================================================
-- 广告配置分类字段：区分「看广告得积分」福利任务 与 广告位（开屏/插屏）
-- category: WELFARE-广告福利任务（激励视频，看了得积分）
--           PLACEMENT-广告位（开屏/插屏，广告运营配置页管理）
-- 日期：2026-10-20
-- =====================================================================

-- 1. 新增分类字段，默认 PLACEMENT（广告位为安全默认，避免误当福利）
ALTER TABLE sys_ad_config
    ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'PLACEMENT' AFTER ad_id;

-- 2. 历史开屏广告明确归类为广告位（默认值已是 PLACEMENT，此语句幂等保险）
UPDATE sys_ad_config SET category = 'PLACEMENT'
WHERE ad_position = 'splash' OR ad_type = 'splash_ad';

-- 3. 广告福利任务测试数据（App 端福利页「看广告得积分」）
INSERT INTO sys_ad_config
    (category, ad_name, ad_position, ad_type, ad_source, ad_unit_id,
     is_enabled, frequency_limit, daily_cap, watch_limit, reward_points,
     sort, start_date, remark, create_by, created_at)
VALUES
    ('WELFARE', '看激励视频得积分', 'welfare', 'reward_video', 'local', 'REWARD_VIDEO_01',
     1, 5, 20, 1, 10,
     1, CURDATE(), '每日最多观看5次，每次10积分', 'admin', NOW()),
    ('WELFARE', '看图文广告得积分', 'welfare', 'reward_image', 'local', 'REWARD_IMAGE_01',
     1, 3, 20, 1, 5,
     2, CURDATE(), '每日最多观看3次，每次5积分', 'admin', NOW());
