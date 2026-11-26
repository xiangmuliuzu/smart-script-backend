-- =====================================================================
-- B 模块（内容与作品）· 经典数据种子（扩展篇）
--
-- 目标：外部视频域（渠道/视频/剧集）、榜单快照、以及 App 侧互动数据，
--       让 PC 各列表页与 App 书城主链路都有真实感数据可看。
--
-- 依赖：先执行 B_20261009_002__clear_content_testdata.sql，
--       再执行 B_20261009_003__seed_content_base.sql（本脚本需引用其中的作品）。
--       本脚本自带幂等前置清理（按 create_by='seed-content-classic' 精确回滚）。
--
-- 依据与口径：
--   * sys_external_drama.source_type 实测仅 'douyin'；authorization_status 仅 'authorized'；
--     sync_status 取 'synced'；status ∈ on_shelf/off_shelf。
--   * sys_episode 无 drama_id 列，剧集挂在 work_id 上；外部视频经 related_work_id 关联作品，
--     故本脚本为「被绑定作品」生成剧集。
--   * sys_ranking_snapshot.status：PC 排行榜页 0→有效、1→失效，故种子取 0。
--     （注：SysRankingSnapshotServiceImpl 重算时写 STATUS_ACTIVE="1"，与页面口径不一致，
--       属既有代码问题，已在方案中单独标注，不在本数据脚本内改动。）
--   * 互动表引用现有 App 用户（sys_user.user_name LIKE 'app\_%'），不新建用户。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261009_004__seed_content_extra.sql"
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

-- 连接排序规则须与内容域表列（utf8mb4_general_ci）一致，
-- 否则用户变量 @mark 与 create_by 列比较会报 collation 冲突。
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;
SET SESSION cte_max_recursion_depth = 1000;

SET @mark = 'seed-content-classic';
SET @img  = 'http://localhost:8080/profile/upload/content/';
SET @now  = NOW();

-- ---------------------------------------------------------------------
-- 0. 幂等前置清理（仅回滚本标记的数据）
-- ---------------------------------------------------------------------
DELETE FROM sys_episode_like    WHERE create_by = @mark;
DELETE FROM sys_comment         WHERE create_by = @mark;
DELETE FROM sys_episode         WHERE create_by = @mark;
DELETE FROM sys_external_drama  WHERE create_by = @mark;
DELETE FROM sys_drama_channel   WHERE create_by = @mark;
DELETE FROM sys_ranking_snapshot WHERE create_by = @mark;
DELETE FROM sys_favorite        WHERE create_by = @mark;
DELETE FROM sys_bookshelf_record WHERE create_by = @mark;
DELETE FROM sys_play_progress   WHERE create_by = @mark;
DELETE FROM sys_play_history    WHERE create_by = @mark;
DELETE FROM sys_search_history  WHERE create_by = @mark;
DELETE FROM sys_browse_history  WHERE create_by = @mark;
DELETE FROM sys_read_record     WHERE create_by = @mark;
DELETE FROM sys_unlock_record   WHERE create_by = @mark;
DELETE FROM sys_subscribe       WHERE create_by = @mark;
DELETE FROM sys_report          WHERE create_by = @mark;

-- ---------------------------------------------------------------------
-- 1. 外部视频渠道（2 个，platform 自由文本）
-- ---------------------------------------------------------------------
INSERT INTO sys_drama_channel
 (channel_name, channel_code, platform, account_name, account_id, api_config,
  is_auto_distribute, status, sort, create_by, create_time, remark) VALUES
 ('抖音星火短剧', 'douyin_star',   'douyin', '星火短剧官方', 'MS4wLjAC0001',
  JSON_OBJECT('baseUrl', 'https://open.douyin.com', 'apiKey', 'demo-key-star'),
  1, 0, 1, @mark, @now, 'B模块经典数据-渠道'),
 ('抖音番茄剧场', 'douyin_fanqie', 'douyin', '番茄剧场官方', 'MS4wLjAC0002',
  JSON_OBJECT('baseUrl', 'https://open.douyin.com', 'apiKey', 'demo-key-fanqie'),
  0, 0, 2, @mark, @now, 'B模块经典数据-渠道');

-- ---------------------------------------------------------------------
-- 2. 外部视频（4 部，绑定到 3 部已上架作品 + 1 部下架作品）
-- ---------------------------------------------------------------------
INSERT INTO sys_external_drama
 (channel_id, external_content_id, title, cover_file_id, external_url, related_work_id,
  source_type, authorization_status, sync_status, copyright_note, status, created_at, updated_at,
  create_by, create_time, remark)
SELECT ch.channel_id, d.ext_id, d.title, NULL, d.ext_url,
       (SELECT work_id FROM sys_work WHERE title = d.work_title AND create_by = @mark LIMIT 1),
       'douyin', 'authorized', 'synced', d.note, d.status, @now, @now,
       @mark, @now, 'B模块经典数据-外部视频'
FROM (
    SELECT 'douyin_star'   AS channel_code, 'DY20260001' AS ext_id, '都市战神归来·短剧版' AS title,
           'https://www.douyin.com/video/7300000000000000001' AS ext_url,
           '都市战神归来' AS work_title, '抖音星火短剧独家授权，授权期 12 个月' AS note, 'on_shelf' AS status
    UNION ALL SELECT 'douyin_star',   'DY20260002', '闪婚老公是大佬·短剧版',
           'https://www.douyin.com/video/7300000000000000002',
           '闪婚老公是大佬', '抖音星火短剧独家授权，授权期 12 个月', 'on_shelf'
    UNION ALL SELECT 'douyin_fanqie', 'DY20260003', '深海迷航·短剧版',
           'https://www.douyin.com/video/7300000000000000003',
           '深海迷航', '抖音番茄剧场授权，授权期 6 个月', 'on_shelf'
    UNION ALL SELECT 'douyin_fanqie', 'DY20260004', '沉默的证人·短剧版',
           'https://www.douyin.com/video/7300000000000000004',
           '沉默的证人', '抖音番茄剧场授权（已到期下架）', 'off_shelf'
) d
JOIN sys_drama_channel ch ON ch.channel_code = d.channel_code AND ch.create_by = @mark;

-- ---------------------------------------------------------------------
-- 3. 剧集（绑定作品各 6 集，前 2 集免费）
-- ---------------------------------------------------------------------
INSERT INTO sys_episode
 (work_id, episode_no, title, video_url, cover_url, duration, is_free, unlock_type, price,
  play_count, like_count, comment_count, completion_rate, status, create_by, create_time, remark)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 6
)
SELECT
    w.work_id,
    s.n,
    CONCAT('第', s.n, '集 ', w.title),
    CONCAT('https://www.douyin.com/video/', 7300000000000000000 + w.work_id * 100 + s.n),
    CONCAT(@img, 'cover_', LPAD(m.cover_no, 2, '0'), '.png'),
    90 + MOD(w.work_id * 7 + s.n * 11, 150),
    IF(s.n <= 2, 1, 0),
    IF(s.n <= 2, NULL, 'coin'),
    IF(s.n <= 2, NULL, 2.00),
    MOD(w.view_count, 900000) + s.n * 1300,
    MOD(w.favorite_count, 60000) + s.n * 90,
    MOD(w.favorite_count, 3000) + s.n * 7,
    ROUND(60 + MOD(w.work_id + s.n, 35) + s.n * 0.5, 2),
    0, @mark, @now, 'B模块经典数据-剧集'
FROM sys_work w
JOIN (
    SELECT '都市战神归来' AS title, 5 AS cover_no
    UNION ALL SELECT '闪婚老公是大佬', 17
    UNION ALL SELECT '深海迷航', 10
    UNION ALL SELECT '沉默的证人', 30
) m ON m.title = w.title
JOIN seq s
WHERE w.create_by = @mark;

-- ---------------------------------------------------------------------
-- 4. 榜单快照（阅读榜 + 收藏榜，各覆盖全部已上架作品）
-- ---------------------------------------------------------------------
INSERT INTO sys_ranking_snapshot
 (ranking_type, period_start, period_end, work_id, rank_no, score, view_count,
  bookshelf_count, growth_score, snapshot_time, status, create_by, create_time, remark)
SELECT 'view_rank', DATE_FORMAT(@now, '%Y-%m-01'), LAST_DAY(@now),
       t.work_id, t.rn, t.view_count, t.view_count, t.favorite_count,
       ROUND(t.view_count * 0.1200, 4), @now, 0, @mark, @now, 'B模块经典数据-榜单'
FROM (SELECT work_id, ROW_NUMBER() OVER (ORDER BY view_count DESC) AS rn, view_count, favorite_count
      FROM sys_work WHERE create_by = @mark AND status = 'on_shelf') t
UNION ALL
SELECT 'favorite_rank', DATE_FORMAT(@now, '%Y-%m-01'), LAST_DAY(@now),
       t.work_id, t.rn, t.favorite_count, t.view_count, t.favorite_count,
       ROUND(t.favorite_count * 0.3500, 4), @now, 0, @mark, @now, 'B模块经典数据-榜单'
FROM (SELECT work_id, ROW_NUMBER() OVER (ORDER BY favorite_count DESC) AS rn, view_count, favorite_count
      FROM sys_work WHERE create_by = @mark AND status = 'on_shelf') t;

-- ---------------------------------------------------------------------
-- 5. 互动数据：素材准备（20 个 App 用户 / 全部剧集）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_app_user;
CREATE TEMPORARY TABLE tmp_app_user (
    slot   INT    NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (slot)
) ENGINE = MEMORY;

INSERT INTO tmp_app_user (slot, user_id)
SELECT ROW_NUMBER() OVER (ORDER BY user_id) - 1, user_id
FROM sys_user
WHERE user_name LIKE 'app\_%' AND del_flag = '0'
ORDER BY user_id
LIMIT 20;

DROP TEMPORARY TABLE IF EXISTS tmp_epi;
CREATE TEMPORARY TABLE tmp_epi (
    slot       INT    NOT NULL,
    episode_id BIGINT NOT NULL,
    work_id    BIGINT NOT NULL,
    episode_no INT    NOT NULL,
    PRIMARY KEY (slot)
) ENGINE = MEMORY;

INSERT INTO tmp_epi (slot, episode_id, work_id, episode_no)
SELECT ROW_NUMBER() OVER (ORDER BY episode_id) - 1, episode_id, work_id, episode_no
FROM sys_episode
WHERE create_by = @mark;

-- 作品指标快照（先物化到临时表）：sys_favorite 上有 AFTER INSERT 触发器
-- trg_sys_favorite_ai_work_count 会回写 sys_work.favorite_count，若同一语句内
-- 再读 sys_work 会报 ERROR 1442。故先固化作品指标，后续互动插入只读临时表。
DROP TEMPORARY TABLE IF EXISTS tmp_work_metrics;
CREATE TEMPORARY TABLE tmp_work_metrics (
    work_id        BIGINT NOT NULL,
    view_rank0     INT    NOT NULL,
    fav_rank0      INT    NOT NULL,
    work_rank0     INT    NOT NULL,
    create_rank0   INT    NOT NULL,
    view_count     BIGINT NOT NULL,
    favorite_count BIGINT NOT NULL,
    PRIMARY KEY (work_id)
) ENGINE = MEMORY;

INSERT INTO tmp_work_metrics
SELECT work_id,
       ROW_NUMBER() OVER (ORDER BY view_count DESC) - 1,
       ROW_NUMBER() OVER (ORDER BY favorite_count DESC) - 1,
       ROW_NUMBER() OVER (ORDER BY work_id) - 1,
       ROW_NUMBER() OVER (ORDER BY create_time DESC) - 1,
       view_count, favorite_count
FROM sys_work
WHERE create_by = @mark AND status = 'on_shelf';

-- 5.1 收藏
INSERT INTO sys_favorite (user_id, work_id, create_by, create_time, remark)
SELECT u.user_id, m.work_id, @mark, DATE_SUB(@now, INTERVAL MOD(u.slot + m.view_rank0, 30) DAY), ''
FROM tmp_app_user u
JOIN tmp_work_metrics m ON m.view_rank0 < 12
WHERE MOD(u.slot + m.view_rank0, 3) = 0;

-- 5.2 书架
INSERT INTO sys_bookshelf_record
 (user_id, work_id, add_source, last_read_chapter_id, last_read_at, is_deleted, created_at, updated_at, create_by, create_time, remark)
SELECT u.user_id, m.work_id, IF(MOD(u.slot, 2) = 0, 'favorite', 'manual'), NULL,
       DATE_SUB(@now, INTERVAL MOD(u.slot, 10) DAY), 0,
       DATE_SUB(@now, INTERVAL MOD(u.slot + 3, 20) DAY), @now, @mark, @now, ''
FROM tmp_app_user u
JOIN tmp_work_metrics m ON m.fav_rank0 < 8
WHERE MOD(u.slot + m.fav_rank0, 4) = 0;

-- 5.3 播放进度
INSERT INTO sys_play_progress
 (user_id, episode_id, work_id, progress_seconds, total_duration, create_by, create_time, remark)
SELECT u.user_id, e.episode_id, e.work_id,
       30 + MOD(u.slot * 17 + e.slot * 13, 180), 90 + MOD(e.slot, 150), @mark, @now, ''
FROM tmp_app_user u
JOIN tmp_epi e ON MOD(u.slot + e.slot, 5) = 0;

-- 5.4 观看历史
INSERT INTO sys_play_history (user_id, episode_id, work_id, play_time, create_by, create_time, remark)
SELECT u.user_id, e.episode_id, e.work_id,
       DATE_SUB(@now, INTERVAL MOD(u.slot + e.slot, 7) DAY), @mark, @now, ''
FROM tmp_app_user u
JOIN tmp_epi e ON MOD(u.slot + e.slot, 7) = 0;

-- 5.5 阅读记录
INSERT INTO sys_read_record
 (user_id, work_id, chapter_id, read_duration, read_progress, created_at, create_by, create_time, remark)
SELECT u.user_id, m.work_id, NULL,
       300 + MOD(u.slot * 23, 900), ROUND(10 + MOD(u.slot + m.work_rank0, 90), 2),
       DATE_SUB(@now, INTERVAL MOD(u.slot, 15) DAY), @mark, @now, ''
FROM tmp_app_user u
JOIN tmp_work_metrics m
WHERE MOD(u.slot + m.work_rank0, 6) = 0;

-- 5.6 浏览足迹
INSERT INTO sys_browse_history
 (user_id, work_id, browse_count, last_browse_at, created_at, create_by, create_time, remark)
SELECT u.user_id, m.work_id, 1 + MOD(u.slot + m.work_rank0, 9),
       DATE_SUB(@now, INTERVAL MOD(u.slot + m.work_rank0, 20) DAY),
       DATE_SUB(@now, INTERVAL MOD(u.slot + 1, 20) DAY), @mark, @now, ''
FROM tmp_app_user u
JOIN tmp_work_metrics m
WHERE MOD(u.slot + m.work_rank0, 8) = 0;

-- 5.7 搜索历史
INSERT INTO sys_search_history (user_id, keyword, search_count, last_search_at, create_by, create_time, remark)
SELECT u.user_id, k.kw, 1 + MOD(u.slot, 5),
       DATE_SUB(@now, INTERVAL MOD(u.slot, 10) DAY), @mark, @now, ''
FROM tmp_app_user u
JOIN (
    SELECT '战神' AS kw UNION ALL SELECT '重生' UNION ALL SELECT '甜宠' UNION ALL SELECT '悬疑'
    UNION ALL SELECT '总裁' UNION ALL SELECT '短剧' UNION ALL SELECT '逆袭' UNION ALL SELECT '修仙'
    UNION ALL SELECT '年代' UNION ALL SELECT '无限流'
) k
WHERE MOD(u.slot, 2) = 0;

-- 5.8 订阅更新提醒
INSERT INTO sys_subscribe (user_id, work_id, notify_enabled, created_at, create_by, create_time, remark)
SELECT u.user_id, m.work_id, 1,
       DATE_SUB(@now, INTERVAL MOD(u.slot, 10) DAY), @mark, @now, ''
FROM tmp_app_user u
JOIN tmp_work_metrics m ON m.create_rank0 < 6
WHERE MOD(u.slot + m.create_rank0, 5) = 0;

-- 5.9 解锁记录（仅付费剧集，episode_no > 2）
INSERT INTO sys_unlock_record
 (user_id, episode_id, work_id, unlock_type, amount, expire_at, created_at, create_by, create_time, remark)
SELECT u.user_id, e.episode_id, e.work_id, 'coin', 2.00,
       DATE_ADD(@now, INTERVAL 30 DAY), DATE_SUB(@now, INTERVAL MOD(u.slot + e.slot, 5) DAY),
       @mark, @now, ''
FROM tmp_app_user u
JOIN tmp_epi e ON MOD(u.slot + e.slot, 11) = 0 AND e.episode_no > 2;

-- 5.10 剧集评论
INSERT INTO sys_comment
 (episode_id, user_id, parent_id, content, like_count, reply_count, status, created_at, create_by, create_time, remark)
SELECT e.episode_id, u.user_id, 0, c.msg, MOD(u.slot * 3, 50), 0, 0,
       DATE_SUB(@now, INTERVAL MOD(u.slot + e.slot, 6) DAY), @mark, @now, ''
FROM tmp_epi e
JOIN tmp_app_user u ON u.slot = MOD(e.slot * 3, 20)
JOIN (
    SELECT 0 AS ci, '开局真爽，一口气看完' AS msg
    UNION ALL SELECT 1, '演技在线，节奏很好'
    UNION ALL SELECT 2, '这个反转没想到'
    UNION ALL SELECT 3, '求更新，太短了'
) c ON c.ci = MOD(e.slot, 4);

-- 5.11 剧集点赞
INSERT INTO sys_episode_like (user_id, episode_id, created_at, create_by, create_time, remark)
SELECT u.user_id, e.episode_id,
       DATE_SUB(@now, INTERVAL MOD(u.slot + e.slot, 6) DAY), @mark, @now, ''
FROM tmp_app_user u
JOIN tmp_epi e ON MOD(u.slot + e.slot, 6) = 0;

-- 5.12 举报（3 条，初始态 pending）
INSERT INTO sys_report
 (user_id, target_type, target_id, reason, description, status, created_at, create_by, create_time, remark)
SELECT u.user_id, 'work', m.work_id, r.reason, r.descr, 'pending',
       DATE_SUB(@now, INTERVAL 2 DAY), @mark, @now, ''
FROM (
    SELECT 0 AS s, '内容违规' AS reason, '疑似含违规内容，请核查' AS descr
    UNION ALL SELECT 1, '侵权', '疑似抄袭他人作品'
    UNION ALL SELECT 2, '低俗', '简介存在低俗描述'
) r
JOIN tmp_app_user u ON u.slot = r.s
JOIN tmp_work_metrics m ON m.view_rank0 = 0;

-- ---------------------------------------------------------------------
-- 6. 校验（闸门 summary）
-- ---------------------------------------------------------------------
SET @c_channel = (SELECT COUNT(*) FROM sys_drama_channel WHERE create_by = @mark);
SET @c_drama   = (SELECT COUNT(*) FROM sys_external_drama WHERE create_by = @mark);
SET @c_episode = (SELECT COUNT(*) FROM sys_episode WHERE create_by = @mark);
SET @c_rank    = (SELECT COUNT(*) FROM sys_ranking_snapshot WHERE create_by = @mark);
SET @c_fav     = (SELECT COUNT(*) FROM sys_favorite WHERE create_by = @mark);
SET @c_shelf   = (SELECT COUNT(*) FROM sys_bookshelf_record WHERE create_by = @mark);
SET @c_prog    = (SELECT COUNT(*) FROM sys_play_progress WHERE create_by = @mark);
SET @c_hist    = (SELECT COUNT(*) FROM sys_play_history WHERE create_by = @mark);
SET @c_read    = (SELECT COUNT(*) FROM sys_read_record WHERE create_by = @mark);
SET @c_browse  = (SELECT COUNT(*) FROM sys_browse_history WHERE create_by = @mark);
SET @c_search  = (SELECT COUNT(*) FROM sys_search_history WHERE create_by = @mark);
SET @c_sub     = (SELECT COUNT(*) FROM sys_subscribe WHERE create_by = @mark);
SET @c_unlock  = (SELECT COUNT(*) FROM sys_unlock_record WHERE create_by = @mark);
SET @c_comment = (SELECT COUNT(*) FROM sys_comment WHERE create_by = @mark);
SET @c_like    = (SELECT COUNT(*) FROM sys_episode_like WHERE create_by = @mark);
SET @c_report  = (SELECT COUNT(*) FROM sys_report WHERE create_by = @mark);

-- 孤儿检查（应为 0）
SET @orphan_drama = (SELECT COUNT(*) FROM sys_external_drama d
                     LEFT JOIN sys_work w ON w.work_id = d.related_work_id
                     WHERE d.create_by = @mark AND w.work_id IS NULL);
SET @orphan_episode = (SELECT COUNT(*) FROM sys_episode e
                       LEFT JOIN sys_work w ON w.work_id = e.work_id
                       WHERE e.create_by = @mark AND w.work_id IS NULL);
SET @orphan_rank = (SELECT COUNT(*) FROM sys_ranking_snapshot r
                    LEFT JOIN sys_work w ON w.work_id = r.work_id
                    WHERE r.create_by = @mark AND w.work_id IS NULL);

-- 非法枚举（应为 0）
SET @bad_drama = (SELECT COUNT(*) FROM sys_external_drama
                  WHERE create_by = @mark AND (source_type <> 'douyin' OR authorization_status <> 'authorized'
                        OR status NOT IN ('on_shelf','off_shelf')));

SET @fail_cnt = @orphan_drama + @orphan_episode + @orphan_rank + @bad_drama
              + IF(@c_channel <> 2, 1, 0) + IF(@c_drama <> 4, 1, 0)
              + IF(@c_episode <> 24, 1, 0) + IF(@c_report <> 3, 1, 0);

SELECT 'CHECK' AS check_result,
       CONCAT('channel=', @c_channel, ', drama=', @c_drama, ', episode=', @c_episode,
              ', ranking=', @c_rank, ', favorite=', @c_fav, ', bookshelf=', @c_shelf,
              ', progress=', @c_prog, ', play_history=', @c_hist, ', read=', @c_read,
              ', browse=', @c_browse, ', search=', @c_search, ', subscribe=', @c_sub,
              ', unlock=', @c_unlock, ', comment=', @c_comment, ', like=', @c_like,
              ', report=', @c_report) AS detail;

SELECT 'CHECK_ASSOC' AS check_result,
       CONCAT('orphan_drama=', @orphan_drama, ', orphan_episode=', @orphan_episode,
              ', orphan_ranking=', @orphan_rank, ', bad_drama_enum=', @bad_drama) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('扩展种子校验未通过，fail_cnt=', @fail_cnt) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, 扩展经典数据已灌入（2渠道/4视频/24剧集/榜单/互动）' AS detail
WHERE @fail_cnt = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_work_metrics;
DROP TEMPORARY TABLE IF EXISTS tmp_epi;
DROP TEMPORARY TABLE IF EXISTS tmp_app_user;