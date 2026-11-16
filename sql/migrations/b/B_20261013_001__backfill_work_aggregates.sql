-- =====================================================================
-- B 模块（内容与作品）· 作品聚合列回填（字数 / 章节数）
--
-- 背景：sys_work.word_count / episode_count 是冗余统计列，后端此前只在
--   insertWork 时写 0，章节增删改从不回写，导致全库这两列恒为 0/NULL：
--     * App 作品详情「作品信息 → 字数」、「书城列表 / 搜索结果」的「N 字 · N 集」显示 0；
--     * PC「内容管理-作品管理」的「字数 / 章节数」列显示 0。
--   代码侧已在章节增删改后回写（SysContentWorkMapper.refreshWorkAggregates），
--   本脚本负责把存量行一次性补齐，口径与代码侧完全一致。
--
-- 口径：word_count    = 该作品全部章节 word_count 之和（含隐藏章节）；
--       episode_count = 章节总数（含隐藏章节）。
--   sys_work_chapter 只有物理删除，无逻辑删除列，故不额外过滤。
--
-- 幂等：全量重算，可重复执行；只写 sys_work 两列，不触碰章节表与其它列。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261013_001__backfill_work_aggregates.sql"
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

-- 连接排序规则须与内容域表列（utf8mb4_general_ci）一致
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 全量重算（仅未删除作品）
--    子查询取自 sys_work_chapter（与 sys_work 不同表），不触发 MySQL 同表更新限制。
-- ---------------------------------------------------------------------
UPDATE sys_work w
SET word_count = (SELECT COALESCE(SUM(c.word_count), 0)
                  FROM sys_work_chapter c
                  WHERE c.work_id = w.work_id),
    episode_count = (SELECT COUNT(1)
                     FROM sys_work_chapter c
                     WHERE c.work_id = w.work_id)
WHERE w.is_deleted = 0;

-- ---------------------------------------------------------------------
-- 2. 校验（闸门 summary）
-- ---------------------------------------------------------------------
-- 与章节表聚合结果不符的作品数（应 0）。用 NULL 安全等值 <=> 兜底 NULL 列。
SET @mismatch = (SELECT COUNT(*) FROM sys_work w
                 WHERE w.is_deleted = 0
                   AND (NOT (w.word_count <=> (SELECT COALESCE(SUM(c.word_count), 0)
                                               FROM sys_work_chapter c
                                               WHERE c.work_id = w.work_id))
                     OR NOT (w.episode_count <=> (SELECT COUNT(1)
                                                  FROM sys_work_chapter c
                                                  WHERE c.work_id = w.work_id))));

-- 已有关联章节但字数仍为 0 的作品数（应 0）
SET @zero_but_has_chapter = (SELECT COUNT(*) FROM sys_work w
                             WHERE w.is_deleted = 0 AND w.word_count = 0
                               AND EXISTS (SELECT 1 FROM sys_work_chapter c
                                           WHERE c.work_id = w.work_id));

SET @fail_cnt = @mismatch + @zero_but_has_chapter;

SELECT 'CHECK' AS check_result,
       CONCAT('works=', (SELECT COUNT(*) FROM sys_work WHERE is_deleted = 0),
              ', mismatch=', @mismatch,
              ', 有章节但字数0=', @zero_but_has_chapter) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('回填未达预期：mismatch=', @mismatch,
              ', 有章节但字数0=', @zero_but_has_chapter) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, 作品字数/章节数已与章节表保持一致' AS detail
WHERE @fail_cnt = 0;
