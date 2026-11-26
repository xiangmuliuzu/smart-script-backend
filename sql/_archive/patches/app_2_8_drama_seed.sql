-- =====================================================================
-- B 模块 · APP 2.8 外部视频模块联调种子数据
--   （sys_drama_channel + sys_external_drama + sys_episode）
--
-- 目的：让 2.8 的浏览类接口能命中真实行：
--   2.8.1 短剧信息流        GET  /api/v1/content/drama-feed
--   2.8.2 剧集列表          GET  /api/v1/content/works/{workId}/episodes
--   2.8.3 剧集详情          GET  /api/v1/content/episodes/{episodeId}
--   2.8.15 外部视频详情     GET  /api/v1/content/external-dramas/{dramaId}
--   2.8.16 找同款剧本       GET  /api/v1/content/external-dramas/{dramaId}/related-work
--
-- 依据（云端 script_platform_dev 实测信息，非文档推断）：
--   sys_drama_channel  : channel_id int PK 自增 / channel_name / channel_code(uk) /
--                        platform / account_name / account_id / api_config json /
--                        is_auto_distribute tinyint NOT NULL / status tinyint NOT NULL /
--                        sort int NOT NULL / created_at+updated_at 有默认值。
--   sys_external_drama : drama_id bigint PK 自增 / channel_id NOT NULL / external_content_id NOT NULL
--                        (uk_channel_external(channel_id, external_content_id)) / title NOT NULL /
--                        cover_file_id 可空 / external_url NOT NULL / related_work_id 可空 /
--                        source_type/authorization_status/sync_status/status 均 NOT NULL /
--                        created_at+updated_at NOT NULL 且**无默认值**（必须显式赋值）。
--   sys_episode        : episode_id bigint PK 自增 / work_id NOT NULL /
--                        (uk_work_episode_no(work_id, episode_no)) /
--                        episode_no/title/video_url/cover_url/duration/is_free/
--                        play_count/like_count/comment_count/status 均 NOT NULL。
--
-- 三条硬约束：
--   1. 幂等——先按固定 ID DELETE 再 INSERT，重复执行结果一致（不产生重复行）。
--   2. 不写死作品 ID——剧集挂到「已上架未删除作品中 work_id 最小者」（开发库即 3）；
--      无已上架作品时不做写入，直接进校验段报 FAIL。
--   3. 不编造业务数据——标题/文案均为明确标注的测试样例，仅用于打通 2.8 链路。
--
-- 覆盖点（供契约测试断言）：
--   - drama_id=900001 related_work_id 指向真实作品 → hasRelatedWork=true、work 非空；
--   - drama_id=900002 related_work_id 为 NULL        → hasRelatedWork=false、work 为 null；
--   - 剧集 1、2 免费(is_free=1)，剧集 3 付费(is_free=0, unlock_type=coin) → 覆盖解锁态差异。
--
-- 清理：联调结束后按固定 ID 删除（见文件末「清理」注释段），不影响真实业务数据。
--
-- 执行：因本机无 mysql CLI，用一次性 JDBC 程序读 .env.local 后执行本文件。
-- =====================================================================

SET NAMES utf8mb4;

SET @mark = 'seed-content-testdata';

-- ---------------------------------------------------------------------
-- 0. 目标作品：已上架未删除作品中 work_id 最小者（开发库为 3）
--    无已上架作品时 @work_id 为 NULL，剧集写入被 WHERE 拦住
-- ---------------------------------------------------------------------
SET @work_id = (SELECT MIN(work_id) FROM sys_work WHERE is_deleted = 0 AND status = 'on_shelf');

-- ---------------------------------------------------------------------
-- 1. 幂等清理（按固定 ID，绝不触碰真实业务数据）
-- ---------------------------------------------------------------------
DELETE FROM sys_episode         WHERE episode_id IN (900001, 900002, 900003);
DELETE FROM sys_external_drama  WHERE drama_id  IN (900001, 900002);
DELETE FROM sys_drama_channel   WHERE channel_id = 90001;

-- ---------------------------------------------------------------------
-- 2. 渠道：1 条（channel_code 唯一，固定 90001 不影响真实自增区间）
-- ---------------------------------------------------------------------
INSERT INTO sys_drama_channel
    (channel_id, channel_name, channel_code, platform, account_name, account_id,
     api_config, is_auto_distribute, status, sort, create_by, create_time, remark)
VALUES
    (90001, '抖音短剧（测试）', 'seed_douyin_2_8', 'douyin', '测试渠道账号', 'dy_seed_2_8',
     NULL, 0, 0, 1, @mark, NOW(), 'B 模块 APP 2.8 外部视频测试数据');

-- ---------------------------------------------------------------------
-- 3. 外部视频：2 条
--    - 900001 绑定真实作品（related_work_id=@work_id）→ 找同款剧本 hasRelatedWork=true
--    - 900002 未绑定（related_work_id=NULL）          → 找同款剧本 hasRelatedWork=false
--    status='on_shelf' 才会被信息流出（与 sys_work 上下架口径一致）
-- ---------------------------------------------------------------------
INSERT INTO sys_external_drama
    (drama_id, channel_id, external_content_id, title, cover_file_id, external_url, related_work_id,
     source_type, authorization_status, sync_status, copyright_note, status,
     created_at, updated_at, create_by, create_time, remark)
VALUES
    (900001, 90001, 'dy_ext_900001', '测试外部短剧·雨夜谜案', NULL,
     'https://www.douyin.com/video/900001', @work_id,
     'douyin', 'authorized', 'synced', 'B 模块联调测试数据，非真实版权信息', 'on_shelf',
     NOW(), NOW(), @mark, NOW(), 'B 模块 APP 2.8 外部视频测试数据'),
    (900002, 90001, 'dy_ext_900002', '测试外部短剧·未绑定原著', NULL,
     'https://www.douyin.com/video/900002', NULL,
     'douyin', 'authorized', 'synced', 'B 模块联调测试数据，非真实版权信息', 'on_shelf',
     NOW(), NOW(), @mark, NOW(), 'B 模块 APP 2.8 外部视频测试数据');

-- ---------------------------------------------------------------------
-- 4. 剧集：3 集，挂在 @work_id 下
--    第 1、2 集免费(is_free=1)，第 3 集付费(is_free=0, unlock_type='coin', price=1.00)
--    status 取 0（该列在库端无权威枚举且无写入方，App 侧按「不过滤 status」口径处理）
-- ---------------------------------------------------------------------
INSERT INTO sys_episode
    (episode_id, work_id, episode_no, title, video_url, cover_url, duration, is_free,
     unlock_type, price, play_count, like_count, comment_count, completion_rate, status,
     create_by, create_time, remark)
SELECT s.episode_id, @work_id, s.episode_no, s.title, s.video_url, s.cover_url, s.duration, s.is_free,
       s.unlock_type, s.price, 0, 0, 0, NULL, 0,
       @mark, NOW(), 'B 模块 APP 2.8 外部视频测试数据'
FROM (
    SELECT 900001 AS episode_id, 1 AS episode_no, '第1集 雨夜来电' AS title,
           'https://media.w3.org/2010/05/sintel/trailer.mp4' AS video_url,
           'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=rainy%20night%20city%20street%20neon%20reflection%20cinematic&image_size=portrait_16_9' AS cover_url,
           180 AS duration, 1 AS is_free, NULL AS unlock_type, NULL AS price
    UNION ALL
    SELECT 900002, 2, '第2集 旧档案',
           'https://media.w3.org/2010/05/sintel/trailer.mp4',
           'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=old%20archive%20room%20flickering%20fluorescent%20light%20files&image_size=portrait_16_9',
           175, 1, NULL, NULL
    UNION ALL
    SELECT 900003, 3, '第3集 交易条件',
           'https://media.w3.org/2010/05/sintel/trailer.mp4',
           'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=dim%20warehouse%20at%20dawn%20dramatic%20shadows&image_size=portrait_16_9',
           192, 0, 'coin', 1.00
) s
WHERE @work_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 5. 校验（闸门 summary）：末行必须 PASS fail_cnt=0
-- ---------------------------------------------------------------------
SET @ch_cnt    = (SELECT COUNT(*) FROM sys_drama_channel WHERE channel_id = 90001);
SET @drama_cnt = (SELECT COUNT(*) FROM sys_external_drama WHERE drama_id IN (900001, 900002));
SET @bound_cnt = (SELECT COUNT(*) FROM sys_external_drama
                  WHERE drama_id = 900001 AND related_work_id IS NOT NULL);
SET @unbound_cnt = (SELECT COUNT(*) FROM sys_external_drama
                    WHERE drama_id = 900002 AND related_work_id IS NULL);
SET @ep_cnt    = (SELECT COUNT(*) FROM sys_episode WHERE episode_id IN (900001, 900002, 900003));
SET @free_cnt  = (SELECT COUNT(*) FROM sys_episode WHERE episode_id IN (900001, 900002) AND is_free = 1);
SET @paid_cnt  = (SELECT COUNT(*) FROM sys_episode WHERE episode_id = 900003 AND is_free = 0 AND unlock_type = 'coin');
SET @on_shelf_cnt = (SELECT COUNT(*) FROM sys_external_drama
                     WHERE drama_id IN (900001, 900002) AND status = 'on_shelf');

SET @fail_cnt = (@work_id IS NULL) + (@ch_cnt < 1) + (@drama_cnt < 2) + (@bound_cnt < 1)
              + (@unbound_cnt < 1) + (@ep_cnt < 3) + (@free_cnt < 2) + (@paid_cnt < 1)
              + (@on_shelf_cnt < 2);

SELECT 'CHECK' AS check_result,
       CONCAT('work_id=', IFNULL(@work_id, 'NULL'),
              ', channel=', @ch_cnt,
              ', drama=', @drama_cnt,
              ', bound=', @bound_cnt,
              ', unbound=', @unbound_cnt,
              ', episode=', @ep_cnt,
              ', free=', @free_cnt,
              ', paid=', @paid_cnt,
              ', on_shelf=', @on_shelf_cnt) AS detail;

SELECT 'FAIL' AS check_result,
       'sys_work 无已上架作品（is_deleted=0 且 status=on_shelf），请先在 PC 端「内容管理-作品管理」上架作品' AS detail
WHERE @work_id IS NULL;

SELECT 'FAIL' AS check_result,
       CONCAT('种子行数不足：channel=', @ch_cnt, ', drama=', @drama_cnt,
              ', episode=', @ep_cnt, ', free=', @free_cnt, ', paid=', @paid_cnt) AS detail
WHERE @fail_cnt > 0 AND @work_id IS NOT NULL;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, work_id=', @work_id,
              ', drama=', @drama_cnt, ', episode=', @ep_cnt) AS detail
WHERE @fail_cnt = 0;

-- =====================================================================
-- 清理（联调结束后执行，按固定 ID 精确删除，幂等）：
--   DELETE FROM sys_episode        WHERE episode_id IN (900001, 900002, 900003);
--   DELETE FROM sys_external_drama WHERE drama_id  IN (900001, 900002);
--   DELETE FROM sys_drama_channel  WHERE channel_id = 90001;
--   （并清理联调过程中用户写下的 sys_play_progress / sys_play_history /
--     sys_subscribe / sys_report 测试行，见联调收尾说明）
-- =====================================================================