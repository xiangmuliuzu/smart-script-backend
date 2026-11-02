-- =====================================================================
-- B_20261102_001__work_genre_nullable.sql
-- 标签墙体系：作品不再设置分类（genre_id 不再写入）
--
-- 背景：
--   * App 创建/设置作品已改为只维护标签（sys_work_tag），分类体系仅保留
--     sys_category 中的频道（男生/女生）供分类页 Tab 使用。
--   * sys_work.genre_id 原为 int NOT NULL，若不改为可空，新作品插入会因
--     缺失分类而失败。
--
-- 处理：
--   * 仅将 genre_id 改为可空，存量历史值保留不做清空（避免影响历史数据追溯）。
--   * 不改列名、不删列、不加索引，避免影响其它模块的只读查询。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261102_001__work_genre_nullable.sql"
-- =====================================================================

ALTER TABLE sys_work
    MODIFY COLUMN genre_id int NULL COMMENT '历史题材ID（标签墙体系下不再写入，仅存量保留）';

-- 幂等校验：genre_id 必须已为可空，且作品标签关联表可用
SELECT
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_work'
       AND column_name = 'genre_id'
       AND is_nullable = 'YES') AS genre_nullable_cnt,
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_work_tag') AS work_tag_table_cnt;

-- 期望输出：genre_nullable_cnt = 1，work_tag_table_cnt = 1
