-- =====================================================================
-- B 模块 · 排行榜（方案 A）演示数据
--
-- 背景：排行榜重写后改为实时统计，指标来源为
--   view     → sys_work.view_count 累计浏览量（本脚本写入的阅读记录仅作行为埋点）
--   favorite → sys_favorite 近 7 天收藏条数
--   sale     → sys_order 近 7 天已支付（pay_time 落地）订单条数
--   rating   → sys_work_rating 平均评分
-- 现状：窗口内数据几乎为空（阅读 0 条、收藏 2 条、订单 4 条），榜单分数全为 0/1，
--   无法体现排序差异。本脚本按作品累计浏览量分档补一批演示数据，单作品各指标均 < 1000。
--
-- 口径与约束：
--   1. 只写真实存在的表/列（sys_read_record / sys_favorite / sys_order / sys_work_rating），
--      不新增表结构；用户ID、章节ID、作者ID 全部取自库内真实记录。
--   2. 演示数据统一带 remark='demo-ranking-window'，可重复执行（先删后插）；
--      sys_work_rating 无 remark 列，靠唯一键 (work_id,user_id) + INSERT IGNORE 保证幂等。
--   3. 阅读记录只落在「已发布章节（status='0'）」上；无已发布章节的作品不造阅读数据。
--   4. 订单状态取 escrow_pending（与后端「支付成功即 pay_time 落地」的口径一致），
--      seller_id 取作品作者，buyer_id 取真实用户；金额 99~299，不做夸张数值。
--   5. 评分按 10 分制（2/4/6/8/10），以 8、10 为主，少量 6，写完后按后端同一口径
--      回写 sys_work.rating = ROUND(AVG(score),1)，避免详情页均分与评分表不一致。
--
-- 执行（口令走环境变量，禁止明文写在命令行）：
--   $env:MYSQL_PWD='<口令>'; & mysql -h<主机> -u<账号> script_platform_dev `
--     -e "source sql/migrations/b/B_20261021_001__seed_ranking_window_demo.sql"
-- 校验：末行应输出 PASS fail_cnt=0。
-- 清理：DELETE FROM sys_read_record / sys_favorite / sys_order
--       WHERE remark='demo-ranking-window'（评分需按 rating_id 范围或用本脚本重跑覆盖）。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 作品分档（按累计浏览量倒序；各指标目标条数均 < 1000）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_demo_works;
CREATE TEMPORARY TABLE tmp_demo_works
(
    work_id      bigint  NOT NULL,
    author_id    bigint,
    rn           bigint  NOT NULL,
    read_target  int     NOT NULL,
    fav_target   int     NOT NULL,
    order_target int     NOT NULL,
    rating_target int    NOT NULL,
    PRIMARY KEY (work_id)
);

-- 注意：窗口函数返回 BIGINT UNSIGNED，直接参与减法会报 1690 越界，
-- 故先在外层子查询里 CAST 成 SIGNED，后续 CASE 只引用别名 rn。
INSERT INTO tmp_demo_works (work_id, author_id, rn, read_target, fav_target, order_target, rating_target)
SELECT t.work_id,
       t.author_id,
       t.rn,
       CASE
           WHEN t.rn <= 5 THEN 960 - (t.rn - 1) * 60
           WHEN t.rn <= 12 THEN 600 - (t.rn - 6) * 40
           ELSE GREATEST(260 - (t.rn - 13) * 10, 50)
       END AS read_target,
       CASE
           WHEN t.rn <= 3 THEN 220 - (t.rn - 1) * 20
           WHEN t.rn <= 10 THEN 160 - (t.rn - 4) * 10
           ELSE GREATEST(90 - (t.rn - 11) * 4, 15)
       END AS fav_target,
       CASE
           WHEN t.rn <= 5 THEN 35 - (t.rn - 1) * 4
           WHEN t.rn <= 12 THEN 16 - (t.rn - 6) * 2
           ELSE 3
       END AS order_target,
       CASE
           WHEN t.rn <= 5 THEN 40 - (t.rn - 1) * 4
           WHEN t.rn <= 12 THEN 20 - (t.rn - 6) * 2
           ELSE GREATEST(8 - (t.rn - 13), 1)
       END AS rating_target
FROM (SELECT w.work_id,
             w.author_id,
             CAST(ROW_NUMBER() OVER (ORDER BY w.view_count DESC, w.work_id DESC) AS SIGNED) AS rn
      FROM sys_work w
      WHERE w.is_deleted = 0
        AND w.status = 'on_shelf') t;

-- ---------------------------------------------------------------------
-- 2. 真实用户表与已发布章节表（用于构造合法的 user_id / chapter_id）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_demo_users;
CREATE TEMPORARY TABLE tmp_demo_users
(
    user_id bigint NOT NULL,
    un      bigint NOT NULL,
    PRIMARY KEY (user_id),
    KEY idx_un (un)
);
INSERT INTO tmp_demo_users (user_id, un)
SELECT user_id, ROW_NUMBER() OVER (ORDER BY user_id) FROM sys_user;

SET @user_cnt = (SELECT COUNT(*) FROM tmp_demo_users);

DROP TEMPORARY TABLE IF EXISTS tmp_demo_chapters;
CREATE TEMPORARY TABLE tmp_demo_chapters
(
    work_id    bigint NOT NULL,
    chapter_id bigint NOT NULL,
    cn         bigint NOT NULL,
    ctotal     bigint NOT NULL,
    PRIMARY KEY (chapter_id)
);
INSERT INTO tmp_demo_chapters (work_id, chapter_id, cn, ctotal)
SELECT work_id,
       chapter_id,
       ROW_NUMBER() OVER (PARTITION BY work_id ORDER BY chapter_no, chapter_id),
       COUNT(*) OVER (PARTITION BY work_id)
FROM sys_work_chapter
WHERE status = '0';

-- ---------------------------------------------------------------------
-- 3. 序号生成器 1..1000（借用 information_schema 行数，避免递归深度限制）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_demo_seq;
CREATE TEMPORARY TABLE tmp_demo_seq
(
    n int NOT NULL,
    PRIMARY KEY (n)
);
INSERT INTO tmp_demo_seq (n)
SELECT seq_no
FROM (SELECT ROW_NUMBER() OVER (ORDER BY TABLE_NAME, ORDINAL_POSITION) AS seq_no
      FROM information_schema.COLUMNS) x
WHERE seq_no <= 1000;

-- ---------------------------------------------------------------------
-- 4. 清理上一次演示数据（只删本脚本写入的标记行）
-- ---------------------------------------------------------------------
DELETE FROM sys_read_record WHERE remark = 'demo-ranking-window';
DELETE FROM sys_favorite    WHERE remark = 'demo-ranking-window';
DELETE FROM sys_order       WHERE remark = 'demo-ranking-window';

-- ---------------------------------------------------------------------
-- 5. 近 7 天阅读记录（同一作品按序号轮换用户与章节）
-- ---------------------------------------------------------------------
INSERT INTO sys_read_record
    (user_id, work_id, chapter_id, read_duration, read_progress,
     created_at, create_by, create_time, remark)
SELECT us.user_id,
       tw.work_id,
       ch.chapter_id,
       60 + ((seq.n * 13) % 240)                       AS read_duration,
       ROUND(5 + ((seq.n * 7) % 94), 2)                AS read_progress,
       DATE_SUB(NOW(), INTERVAL (seq.n % 7) DAY) + INTERVAL (seq.n % 10) HOUR,
       CAST(us.user_id AS CHAR),
       NOW(),
       'demo-ranking-window'
FROM tmp_demo_works tw
         JOIN tmp_demo_chapters ch ON ch.work_id = tw.work_id
         JOIN tmp_demo_seq seq ON seq.n <= tw.read_target
         JOIN tmp_demo_users us ON us.un = 1 + ((seq.n * 37) % @user_cnt)
WHERE ch.cn = 1 + ((seq.n - 1) % ch.ctotal);

-- ---------------------------------------------------------------------
-- 6. 近 7 天收藏（uk_user_work，IGNORE 掉与真实收藏冲突的行）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_favorite
    (user_id, work_id, created_at, create_by, create_time, remark)
SELECT us.user_id,
       tw.work_id,
       DATE_SUB(NOW(), INTERVAL (seq.n % 7) DAY) + INTERVAL (seq.n % 9) HOUR,
       CAST(us.user_id AS CHAR),
       NOW(),
       'demo-ranking-window'
FROM tmp_demo_works tw
         JOIN tmp_demo_seq seq ON seq.n <= tw.fav_target
         JOIN tmp_demo_users us ON us.un = 1 + ((seq.n * 37) % @user_cnt);

-- ---------------------------------------------------------------------
-- 7. 近 7 天已支付订单（escrow_pending：payment 落地即 pay_time 有值）
-- ---------------------------------------------------------------------
INSERT INTO sys_order
    (order_no, work_id, buyer_id, seller_id, order_type, license_type,
     total_amount, platform_fee, split_rate, seller_amount,
     status, pay_time, created_at, create_by, create_time, remark)
SELECT CONCAT('DEMO', LPAD(tw.work_id, 8, '0'), LPAD(seq.n, 5, '0')) AS order_no,
       tw.work_id,
       us.user_id,
       COALESCE(tw.author_id, us.user_id),
       'quote',
       'non_exclusive',
       99.00 + (seq.n % 5) * 50,
       0.00,
       0.0000,
       99.00 + (seq.n % 5) * 50,
       'escrow_pending',
       DATE_SUB(NOW(), INTERVAL (seq.n % 7) DAY) + INTERVAL (seq.n % 10) HOUR,
       DATE_SUB(NOW(), INTERVAL (seq.n % 7) DAY),
       'demo-seed',
       NOW(),
       'demo-ranking-window'
FROM tmp_demo_works tw
         JOIN tmp_demo_seq seq ON seq.n <= tw.order_target
         JOIN tmp_demo_users us ON us.un = 1 + ((seq.n * 53) % @user_cnt);

-- ---------------------------------------------------------------------
-- 8. 读者评分（10 分制；已存在的 (work_id,user_id) 由唯一键跳过）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_work_rating (work_id, user_id, score, created_at, updated_at)
SELECT tw.work_id,
       us.user_id,
       CASE (seq.n % 10)
           WHEN 0 THEN 6
           WHEN 1 THEN 6
           WHEN 2 THEN 8
           WHEN 3 THEN 8
           WHEN 4 THEN 8
           WHEN 5 THEN 8
           WHEN 6 THEN 8
           WHEN 7 THEN 10
           WHEN 8 THEN 10
           ELSE 10
       END,
       DATE_SUB(NOW(), INTERVAL (seq.n % 30) DAY),
       DATE_SUB(NOW(), INTERVAL (seq.n % 30) DAY)
FROM tmp_demo_works tw
         JOIN tmp_demo_seq seq ON seq.n <= tw.rating_target
         JOIN tmp_demo_users us ON us.un = 1 + ((seq.n * 41) % @user_cnt);

-- 与后端提交评分同一口径回写作品均分，避免详情页与评分榜不一致
UPDATE sys_work w
    JOIN (SELECT work_id, ROUND(AVG(score), 1) AS avg_score
          FROM sys_work_rating
          GROUP BY work_id) r ON r.work_id = w.work_id
SET w.rating = r.avg_score;

-- ---------------------------------------------------------------------
-- 9. 校验闸门：各榜窗口内数据存在且单作品不超 1000
-- ---------------------------------------------------------------------
SET @read_works  = (SELECT COUNT(DISTINCT work_id) FROM sys_read_record WHERE remark = 'demo-ranking-window');
SET @read_max    = (SELECT COALESCE(MAX(c), 0) FROM (SELECT COUNT(*) c FROM sys_read_record WHERE remark = 'demo-ranking-window' GROUP BY work_id) x);
SET @fav_works   = (SELECT COUNT(DISTINCT work_id) FROM sys_favorite WHERE remark = 'demo-ranking-window');
SET @fav_max     = (SELECT COALESCE(MAX(c), 0) FROM (SELECT COUNT(*) c FROM sys_favorite WHERE remark = 'demo-ranking-window' GROUP BY work_id) x);
SET @sale_works  = (SELECT COUNT(DISTINCT work_id) FROM sys_order WHERE remark = 'demo-ranking-window');
SET @sale_max    = (SELECT COALESCE(MAX(c), 0) FROM (SELECT COUNT(*) c FROM sys_order WHERE remark = 'demo-ranking-window' GROUP BY work_id) x);
SET @rating_works = (SELECT COUNT(DISTINCT work_id) FROM sys_work_rating);
SET @rating_max  = (SELECT COALESCE(MAX(c), 0) FROM (SELECT COUNT(*) c FROM sys_work_rating GROUP BY work_id) x);

SET @fail_cnt = (@read_works > 0) + (@fav_works > 0) + (@sale_works > 0) + (@rating_works > 0)
              + (@read_max <= 1000) + (@fav_max <= 1000) + (@sale_max <= 1000) + (@rating_max <= 1000);
SET @fail_cnt = 8 - @fail_cnt;

SELECT 'CHECK' AS check_result,
       CONCAT('read_works=', @read_works, ', read_max=', @read_max,
              ', fav_works=', @fav_works, ', fav_max=', @fav_max,
              ', sale_works=', @sale_works, ', sale_max=', @sale_max,
              ', rating_works=', @rating_works, ', rating_max=', @rating_max) AS detail;

SELECT 'FAIL' AS check_result, '演示数据未达预期，请检查上方 CHECK' AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result, 'fail_cnt=0, 排行榜窗口演示数据已就绪（单作品指标 <1000）' AS detail
WHERE @fail_cnt = 0;
