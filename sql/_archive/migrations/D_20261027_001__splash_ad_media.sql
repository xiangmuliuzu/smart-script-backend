-- 自建开屏素材；发布新版本前执行一次。
ALTER TABLE sys_ad_config
    ADD COLUMN image_url VARCHAR(1000) DEFAULT NULL,
    ADD COLUMN link_url VARCHAR(1000) DEFAULT NULL,
    ADD COLUMN duration_seconds INT NOT NULL DEFAULT 5;
