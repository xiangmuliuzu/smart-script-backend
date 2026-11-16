-- =====================================================================
-- B 模块内容 · 作品频道回填
--
-- 背景：
--   sys_work.channel_id（146 男频 / 147 女频）在历史数据中全部为 NULL：
--   App 作品设置在提交 channelId 后被服务端丢弃，未落库。
--   PC「作品管理」按频道筛选因此恒为空结果，列表频道列也恒显示为男频。
--
-- 内容：
--   1. 按作品标签归属回填 channel_id：仅当该作品的标签频道唯一时回填，
--      男女通用标签（channel_id = NULL）不参与判断；多频道标签冲突的作品保持 NULL。
--   2. verify 闸门：不存在「可唯一判定但仍为 NULL」的作品，且不存在非法频道值。
--
-- 幂等：WHERE channel_id IS NULL 限定只补空缺，可重复执行；末行输出 PASS 且 fail_cnt=0。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261105_002__work_channel_backfill.sql"
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 按标签频道回填（仅标签频道唯一的作品）
-- ---------------------------------------------------------------------
UPDATE sys_work w
JOIN (
    SELECT wt.work_id, MIN(t.channel_id) AS channel_id
    FROM sys_work_tag wt
    JOIN sys_tag t ON t.tag_id = wt.tag_id
    WHERE t.channel_id IS NOT NULL
    GROUP BY wt.work_id
    HAVING COUNT(DISTINCT t.channel_id) = 1
) src ON src.work_id = w.work_id
SET w.channel_id = src.channel_id
WHERE w.channel_id IS NULL
  AND w.is_deleted = 0;

-- ---------------------------------------------------------------------
-- 2. verify 闸门：任一不符则 fail_cnt > 0
-- ---------------------------------------------------------------------
SET @fail = 0;

-- 2.1 可唯一判定频道、但仍为 NULL 的作品
SET @fail = @fail + IF((SELECT COUNT(*)
    FROM sys_work w
    JOIN (
        SELECT wt.work_id
        FROM sys_work_tag wt
        JOIN sys_tag t ON t.tag_id = wt.tag_id
        WHERE t.channel_id IS NOT NULL
        GROUP BY wt.work_id
        HAVING COUNT(DISTINCT t.channel_id) = 1
    ) src ON src.work_id = w.work_id
    WHERE w.channel_id IS NULL
      AND w.is_deleted = 0) = 0, 0, 1);

-- 2.2 频道值只能为 146 / 147（NULL 表示未设置，允许）
SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_work
    WHERE channel_id IS NOT NULL
      AND channel_id NOT IN (146, 147)) = 0, 0, 1);

SELECT IF(@fail = 0, 'PASS', 'FAIL') AS result, @fail AS fail_cnt;
