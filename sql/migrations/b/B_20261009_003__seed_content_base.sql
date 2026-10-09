-- =====================================================================
-- B 模块（内容与作品）· 经典数据种子（基础篇）
--
-- 目标：把内容域灌成「贴近真实平台（起点/番茄/七猫）」的经典数据形态：
--       12 个题材分类、20 个标签、8 位作者、30 部原创风格剧本书名、
--       作品-标签关联、已上架作品的章节、5 条首页 Banner。
--
-- 依赖：须先执行 B_20261009_002__clear_content_testdata.sql 清空底座。
--       本脚本自带幂等前置清理（按 create_by='seed-content-classic' 精确回滚），
--       可单独重复执行。
--
-- 硬约束与依据：
--   * 字段与 NOT NULL 均以云端 script_platform_dev 实测 schema 为准，未虚构列。
--   * sys_work.author_id → sys_user.user_id；PC/App 列表的「作者」列取自
--     sys_user.nick_name（见 SysContentWorkMapper.xml selectWorkVo 的 JOIN）。
--     现有 129 个 App 用户 nick_name 均为「手机号掩码」，直接挂作品会导致作者列
--     显示手机号（不合理）。故本脚本新建 8 个专用「作者」账号（user_name='author_%'，
--     带笔名；create_by 打标，清理脚本按标记回收，不影响真实用户）。
--   * sys_work.genre_id → sys_category.category_id；分类统一 category_type='theme'
--     （与云端现状一致；App 端分类列表走同一 selectCategoryList，避免 style/audience
--      混入书城题材入口）。
--   * sys_work.status 合法域实测为 draft/pending/approved/on_shelf/off_shelf
--     （PC 作品管理筛选据此列出）；App 书城/详情只下发 on_shelf。
--   * trade_type 取权威字典 trade_license_type 三值：exclusive/non_exclusive/adaptation。
--   * ext_json 用 JSON_OBJECT 生成，保证列（json 类型）合法；recommendStatus 供 PC
--     书城页筛选（home_hot/category_rec/none）。
--   * cover 指向后端本地静态图（/profile/upload/content/cover_XX.png），由
--     scripts/db/gen-content-images.py 生成；如需换主机只改 @img 前缀。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261009_003__seed_content_base.sql"
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
DELETE FROM sys_work_tag   WHERE create_by = @mark;
DELETE FROM sys_work_chapter WHERE create_by = @mark;
DELETE FROM sys_work       WHERE create_by = @mark;
DELETE FROM sys_banner     WHERE create_by = @mark;
DELETE FROM sys_tag        WHERE create_by = @mark;
DELETE FROM sys_category   WHERE create_by = @mark;
DELETE FROM sys_user       WHERE user_name LIKE 'author\_%' AND create_by = @mark;

-- ---------------------------------------------------------------------
-- 1. 作者账号（8 位，笔名）
--    bio 为 NOT NULL 列，显式给空串；password 留空（不可登录）。
--    phonenumber 有唯一索引 uk_sys_user_phonenumber，空串会让 8 行互相冲突，
--    故显式置 NULL（该列可空，唯一索引允许多个 NULL）。
-- ---------------------------------------------------------------------
INSERT INTO sys_user (user_name, nick_name, user_type, status, del_flag, phonenumber, bio, create_by, create_time, remark) VALUES
 ('author_01', '墨雨无痕', '00', '0', '0', NULL, '', @mark, @now, 'B模块经典数据-作者'),
 ('author_02', '北冥有鱼', '00', '0', '0', NULL, '', @mark, @now, 'B模块经典数据-作者'),
 ('author_03', '顾清欢',   '00', '0', '0', NULL, '', @mark, @now, 'B模块经典数据-作者'),
 ('author_04', '苏晚棠',   '00', '0', '0', NULL, '', @mark, @now, 'B模块经典数据-作者'),
 ('author_05', '江左沉舟', '00', '0', '0', NULL, '', @mark, @now, 'B模块经典数据-作者'),
 ('author_06', '云中鹤',   '00', '0', '0', NULL, '', @mark, @now, 'B模块经典数据-作者'),
 ('author_07', '白露未晞', '00', '0', '0', NULL, '', @mark, @now, 'B模块经典数据-作者'),
 ('author_08', '沈墨白',   '00', '0', '0', NULL, '', @mark, @now, 'B模块经典数据-作者');

DROP TEMPORARY TABLE IF EXISTS tmp_author;
CREATE TEMPORARY TABLE tmp_author (
    slot      INT    NOT NULL,
    author_id BIGINT NOT NULL,
    PRIMARY KEY (slot)
) ENGINE = MEMORY;

INSERT INTO tmp_author (slot, author_id)
SELECT ROW_NUMBER() OVER (ORDER BY user_id) - 1, user_id
FROM sys_user
WHERE user_name LIKE 'author\_%' AND create_by = @mark;

-- ---------------------------------------------------------------------
-- 2. 题材分类（12 个，category_type='theme'）
-- ---------------------------------------------------------------------
INSERT INTO sys_category (category_name, category_type, parent_id, sort, status, create_by, create_time, remark) VALUES
 ('玄幻', 'theme', 0, 1,  0, @mark, @now, ''),
 ('仙侠', 'theme', 0, 2,  0, @mark, @now, ''),
 ('武侠', 'theme', 0, 3,  0, @mark, @now, ''),
 ('都市', 'theme', 0, 4,  0, @mark, @now, ''),
 ('悬疑', 'theme', 0, 5,  0, @mark, @now, ''),
 ('历史', 'theme', 0, 6,  0, @mark, @now, ''),
 ('科幻', 'theme', 0, 7,  0, @mark, @now, ''),
 ('言情', 'theme', 0, 8,  0, @mark, @now, ''),
 ('游戏', 'theme', 0, 9,  0, @mark, @now, ''),
 ('二次元', 'theme', 0, 10, 0, @mark, @now, ''),
 ('军事', 'theme', 0, 11, 0, @mark, @now, ''),
 ('体育', 'theme', 0, 12, 0, @mark, @now, '');

-- ---------------------------------------------------------------------
-- 3. 标签（题材 16 + 受众 4）
--    use_count 由 PC 标签服务维护，种子期置 0。
-- ---------------------------------------------------------------------
INSERT INTO sys_tag (tag_name, tag_type, use_count, sort, status, create_by, create_time, remark) VALUES
 ('爽文',   'theme',    0, 1,  0, @mark, @now, ''),
 ('逆袭',   'theme',    0, 2,  0, @mark, @now, ''),
 ('复仇',   'theme',    0, 3,  0, @mark, @now, ''),
 ('甜宠',   'theme',    0, 4,  0, @mark, @now, ''),
 ('战神',   'theme',    0, 5,  0, @mark, @now, ''),
 ('重生',   'theme',    0, 6,  0, @mark, @now, ''),
 ('系统',   'theme',    0, 7,  0, @mark, @now, ''),
 ('穿书',   'theme',    0, 8,  0, @mark, @now, ''),
 ('打脸',   'theme',    0, 9,  0, @mark, @now, ''),
 ('无限流', 'theme',    0, 10, 0, @mark, @now, ''),
 ('大女主', 'theme',    0, 11, 0, @mark, @now, ''),
 ('种田',   'theme',    0, 12, 0, @mark, @now, ''),
 ('热血',   'theme',    0, 13, 0, @mark, @now, ''),
 ('悬疑',   'theme',    0, 14, 0, @mark, @now, ''),
 ('年代',   'theme',    0, 15, 0, @mark, @now, ''),
 ('修仙',   'theme',    0, 16, 0, @mark, @now, ''),
 ('女性向', 'audience', 0, 17, 0, @mark, @now, ''),
 ('男性向', 'audience', 0, 18, 0, @mark, @now, ''),
 ('学生党', 'audience', 0, 19, 0, @mark, @now, ''),
 ('上班族', 'audience', 0, 20, 0, @mark, @now, '');

-- ---------------------------------------------------------------------
-- 4. 作品草稿表（30 部，仅内存态，便于按 idx 关联封面/作者）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_work;
CREATE TEMPORARY TABLE tmp_work (
    idx              INT          NOT NULL,
    title            VARCHAR(100) NOT NULL,
    genre_name       VARCHAR(30)  NOT NULL,
    length_type      VARCHAR(20)  NOT NULL,
    trade_type       VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    price            DECIMAL(12,2) NOT NULL,
    word_count       INT          NOT NULL,
    episode_count    INT          NOT NULL,
    duration         INT          NOT NULL,
    view_count       INT          NOT NULL,
    favorite_count   INT          NOT NULL,
    sale_count       INT          NOT NULL,
    rating           DECIMAL(3,1) NOT NULL,
    quality_level    VARCHAR(20)  NULL,
    recommend_status VARCHAR(20)  NOT NULL,
    recommend_weight INT          NOT NULL,
    PRIMARY KEY (idx)
) ENGINE = MEMORY;

INSERT INTO tmp_work
 (idx, title, genre_name, length_type, trade_type, status, price, word_count, episode_count, duration, view_count, favorite_count, sale_count, rating, quality_level, recommend_status, recommend_weight) VALUES
 ( 1, '九天神王',        '玄幻', 'long',  'exclusive',     'on_shelf', 2999.00, 860000, 40, 1800, 12800000,  92000, 3200, 9.6, 'S', 'home_hot',     100),
 ( 2, '焚天纪',          '玄幻', 'long',  'non_exclusive', 'on_shelf', 1999.00, 720000, 36, 1600,  8600000,  51000, 1800, 9.3, 'A', 'home_hot',      90),
 ( 3, '青莲问道',        '仙侠', 'long',  'exclusive',     'on_shelf', 2599.00, 640000, 32, 1500,  4200000,  33000,  900, 9.1, 'A', 'none',           0),
 ( 4, '江湖夜雨录',      '武侠', 'long',  'adaptation',    'on_shelf', 1599.00, 520000, 28, 1400,  3600000,  27000,  700, 8.9, 'B', 'category_rec',  60),
 ( 5, '都市战神归来',    '都市', 'short', 'exclusive',     'on_shelf', 3999.00, 300000, 80,  200, 25600000, 210000, 5800, 9.7, 'S', 'home_hot',     120),
 ( 6, '重生之豪门弃女',  '都市', 'short', 'non_exclusive', 'on_shelf', 2499.00, 280000, 76,  190, 18300000, 156000, 4200, 9.4, 'S', 'home_hot',     110),
 ( 7, '离婚后我成了首富','都市', 'short', 'exclusive',     'on_shelf', 2299.00, 260000, 70,  180, 15200000, 121000, 3600, 9.2, 'A', 'category_rec',  80),
 ( 8, '绝世神医',        '都市', 'short', 'non_exclusive', 'on_shelf', 1899.00, 240000, 64,  170, 11800000,  88000, 2400, 9.0, 'A', 'category_rec',  70),
 ( 9, '我的老婆是总裁',  '都市', 'short', 'adaptation',    'on_shelf', 1699.00, 220000, 60,  160,  9400000,  63000, 1900, 8.8, 'B', 'none',           0),
 (10, '深海迷航',        '悬疑', 'short', 'exclusive',     'on_shelf', 2799.00, 310000, 72,  185,  7600000,  71000, 2300, 9.2, 'A', 'category_rec',  65),
 (11, '雾城谜案',        '悬疑', 'short', 'non_exclusive', 'on_shelf', 2099.00, 260000, 58,  150,  5400000,  39000, 1400, 8.9, 'B', 'none',           0),
 (12, '暗河无声',        '悬疑', 'long',  'adaptation',    'pending',  1499.00, 480000, 26, 1350,   320000,   1200,   60, 8.5, 'C', 'none',           0),
 (13, '大明第一书生',    '历史', 'long',  'non_exclusive', 'on_shelf', 1899.00, 560000, 30, 1450,  4800000,  34000, 1100, 9.0, 'B', 'category_rec',  55),
 (14, '贞观小吏',        '历史', 'long',  'adaptation',    'on_shelf', 1699.00, 440000, 24, 1300,  2900000,  21000,  700, 8.7, 'B', 'none',           0),
 (15, '星际拓荒记',      '科幻', 'long',  'exclusive',     'approved', 2399.00, 600000, 34, 1550,  2100000,  18000,  500, 8.8, 'B', 'none',           0),
 (16, '硅基黎明',        '科幻', 'short', 'non_exclusive', 'on_shelf', 1999.00, 280000, 62,  165,  3900000,  29000,  900, 8.9, 'B', 'category_rec',  50),
 (17, '闪婚老公是大佬',  '言情', 'short', 'exclusive',     'on_shelf', 2199.00, 300000, 78,  195, 32600000, 268000, 6400, 9.8, 'S', 'home_hot',     130),
 (18, '总裁的替嫁新娘',  '言情', 'short', 'non_exclusive', 'on_shelf', 1999.00, 290000, 74,  190, 21400000, 172000, 4800, 9.5, 'S', 'home_hot',     115),
 (19, '春日告白',        '言情', 'short', 'adaptation',    'on_shelf', 1499.00, 210000, 56,  150,  6800000,  56000, 1500, 9.1, 'A', 'category_rec',  60),
 (20, '余生向暖',        '言情', 'long',  'non_exclusive', 'off_shelf',1799.00, 500000, 28, 1400,  2600000,  19000,  600, 8.6, 'B', 'none',           0),
 (21, '全服公敌',        '游戏', 'long',  'exclusive',     'on_shelf', 2299.00, 580000, 30, 1450,  3400000,  24000,  800, 8.8, 'B', 'none',           0),
 (22, '开局一把木剑',    '游戏', 'short', 'non_exclusive', 'on_shelf', 1699.00, 230000, 54,  145,  4200000,  31000, 1000, 8.7, 'B', 'none',           0),
 (23, '星野高校物语',    '二次元','short','adaptation',    'on_shelf', 1399.00, 200000, 48,  140,  3100000,  24000,  700, 8.6, 'C', 'none',           0),
 (24, '铁血征程',        '军事', 'long',  'non_exclusive', 'on_shelf', 1899.00, 620000, 30, 1500,  1800000,  14000,  400, 8.5, 'B', 'none',           0),
 (25, '绿茵传奇',        '体育', 'long',  'adaptation',    'on_shelf', 1599.00, 460000, 26, 1350,  1500000,  11000,  300, 8.4, 'C', 'none',           0),
 (26, '战神奶爸',        '都市', 'short', 'exclusive',     'on_shelf', 2999.00, 320000, 82,  200, 19800000, 158000, 4600, 9.5, 'S', 'home_hot',     105),
 (27, '万古神尊',        '玄幻', 'long',  'non_exclusive', 'draft',    1799.00, 400000, 20, 1200,        0,      0,    0, 0.0, NULL,'none',           0),
 (28, '重生之嫡女不好惹','言情', 'short', 'exclusive',     'on_shelf', 2099.00, 270000, 68,  175, 12400000,  96000, 2700, 9.2, 'A', 'category_rec',  75),
 (29, '大佬的隐婚妻子',  '都市', 'short', 'non_exclusive', 'approved', 1899.00, 250000, 60,  160,   900000,    800,   20, 8.3, 'C', 'none',           0),
 (30, '沉默的证人',      '悬疑', 'short', 'adaptation',    'off_shelf',1599.00, 240000, 56,  150,  3600000,  28000,  900, 8.8, 'B', 'none',           0);

-- ---------------------------------------------------------------------
-- 5. 作品落库
-- ---------------------------------------------------------------------
INSERT INTO sys_work (
    title, cover, author_id, genre_id, work_type, upload_type, length_type,
    summary, core_setting, character_setting,
    price, trade_type, trade_enabled, quote_valid_days,
    word_count, episode_count, duration, is_free,
    preview_enabled, preview_episodes,
    status, is_copyrighted,
    view_count, favorite_count, sale_count, rating, quality_level,
    reviewer_id, review_time, ext_json, is_deleted,
    create_by, create_time, remark
)
SELECT
    w.title,
    CONCAT(@img, 'cover_', LPAD(w.idx, 2, '0'), '.png'),
    a.author_id,
    c.category_id,
    'script',
    'original',
    w.length_type,
    CONCAT('《', w.title, '》：', w.genre_name, '题材',
           IF(w.length_type = 'short', '短剧', IF(w.length_type = 'long', '长剧', '电影')),
           '剧本。围绕主角在', w.genre_name, '世界中的成长与抉择展开，节奏紧凑、反转密集，适配竖屏短剧与长剧改编。'),
    CONCAT('核心设定：', w.genre_name, '世界观 + 强冲突主线 + 三幕式结构 + 明确爽点节奏。'),
    CONCAT('人物设定：主角（成长弧光）、对手（利益对立）、关键配角（情感支点）。'),
    w.price,
    w.trade_type,
    IF(w.status = 'on_shelf' AND w.trade_type <> 'adaptation', 1, 0),
    60,
    w.word_count,
    w.episode_count,
    w.duration,
    0,
    IF(w.status = 'on_shelf', 1, NULL),
    IF(w.status = 'on_shelf', 3, NULL),
    w.status,
    IF(w.trade_type = 'exclusive', 1, 0),
    w.view_count, w.favorite_count, w.sale_count,
    w.rating,
    w.quality_level,
    IF(w.status IN ('on_shelf','approved','off_shelf'), 1, NULL),
    IF(w.status IN ('on_shelf','approved','off_shelf'), DATE_SUB(@now, INTERVAL 7 DAY), NULL),
    JSON_OBJECT('recommendStatus', w.recommend_status,
                'showScope', JSON_ARRAY('bookstore','category_page'),
                'recommendWeight', w.recommend_weight),
    0,
    @mark, DATE_SUB(@now, INTERVAL w.idx DAY), ''
FROM tmp_work w
JOIN sys_category c ON c.category_name = w.genre_name
JOIN tmp_author  a ON a.slot = MOD(w.idx - 1, 8)
ORDER BY w.idx;

-- ---------------------------------------------------------------------
-- 6. 作品-标签关联（每部作品按题材的标签池轮转挂 2 个）
--    每个题材给 3 个标签，按 work_id 取其中 2 个（MOD 轮转），使同题材内
--    不同作品的标签有差异，分类×标签筛选才有意义（如「都市+重生」）。
--    与迁移 B_20261009_005__fix_work_tag_mapping.sql 保持同一份映射。
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_genre_tag;
CREATE TEMPORARY TABLE tmp_genre_tag (
    genre_name VARCHAR(30) NOT NULL,
    tag_name   VARCHAR(30) NOT NULL,
    seq        INT         NOT NULL
) ENGINE = MEMORY;

INSERT INTO tmp_genre_tag (genre_name, tag_name, seq) VALUES
 ('玄幻', '系统', 0), ('玄幻', '爽文', 1), ('玄幻', '热血', 2),
 ('仙侠', '修仙', 0), ('仙侠', '热血', 1), ('仙侠', '爽文', 2),
 ('武侠', '复仇', 0), ('武侠', '热血', 1), ('武侠', '修仙', 2),
 ('都市', '重生', 0), ('都市', '逆袭', 1), ('都市', '战神', 2),
 ('悬疑', '悬疑', 0), ('悬疑', '复仇', 1), ('悬疑', '打脸', 2),
 ('历史', '年代', 0), ('历史', '种田', 1), ('历史', '大女主', 2),
 ('科幻', '无限流', 0), ('科幻', '系统', 1), ('科幻', '悬疑', 2),
 ('言情', '甜宠', 0), ('言情', '大女主', 1), ('言情', '穿书', 2),
 ('游戏', '系统', 0), ('游戏', '爽文', 1), ('游戏', '无限流', 2),
 ('二次元', '学生党', 0), ('二次元', '穿书', 1), ('二次元', '种田', 2),
 ('军事', '男性向', 0), ('军事', '热血', 1), ('军事', '复仇', 2),
 ('体育', '男性向', 0), ('体育', '热血', 1), ('体育', '爽文', 2);

INSERT INTO sys_work_tag (work_id, tag_id, create_by, create_time, remark)
SELECT w.work_id, t.tag_id, @mark, @now, ''
FROM sys_work w
JOIN sys_category c ON c.category_id = w.genre_id
JOIN tmp_genre_tag gt
     ON gt.genre_name = c.category_name
    AND gt.seq IN (MOD(w.work_id, 3), MOD(w.work_id + 1, 3))
JOIN sys_tag t ON t.tag_name = gt.tag_name
WHERE w.create_by = @mark AND w.is_deleted = 0;

-- ---------------------------------------------------------------------
-- 7. 章节（已上架作品，每部 6 章；前 2 章免费）
-- ---------------------------------------------------------------------
INSERT INTO sys_work_chapter
 (work_id, chapter_no, chapter_title, content, word_count, is_free, status, create_by, create_time, remark)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 6
)
SELECT
    w.work_id,
    s.n,
    CONCAT('第', s.n, '章 ',
           ELT(1 + MOD(w.work_id + s.n, 6), '风起云涌', '暗流涌动', '峰回路转', '正面交锋', '绝处逢生', '尘埃落定')),
    CONCAT('《', w.title, '》第', s.n, '章正文。本段为经典数据示例内容，用于验证章节目录、试读与付费解锁链路。', CHAR(10),
           '剧情推进：主角在关键抉择中扭转局势，冲突升级，留下下一章钩子。'),
    900 + MOD(w.work_id * 37 + s.n * 53, 900),
    IF(s.n <= 2, 1, 0),
    0,
    @mark,
    DATE_ADD(DATE_SUB(@now, INTERVAL w.work_id DAY), INTERVAL s.n DAY),
    ''
FROM sys_work w
JOIN seq s
WHERE w.create_by = @mark
  AND w.is_deleted = 0
  AND w.status = 'on_shelf';

-- ---------------------------------------------------------------------
-- 8. 首页 Banner（5 条，本地上传默认图）
-- ---------------------------------------------------------------------
INSERT INTO sys_banner
 (title, image_url, link_type, link_id, link_url, position, sort_order, status, start_time, end_time, create_by, create_time, remark)
SELECT '都市战神归来·重磅上线', CONCAT(@img, 'banner_01.png'), 'work',
       (SELECT work_id FROM sys_work WHERE title = '都市战神归来' AND create_by = @mark LIMIT 1),
       NULL, 'home_top', 1, 'on', DATE_SUB(@now, INTERVAL 1 DAY), DATE_ADD(@now, INTERVAL 90 DAY), @mark, @now, ''
UNION ALL
SELECT '新书首发·九天神王', CONCAT(@img, 'banner_02.png'), 'work',
       (SELECT work_id FROM sys_work WHERE title = '九天神王' AND create_by = @mark LIMIT 1),
       NULL, 'home_top', 2, 'on', DATE_SUB(@now, INTERVAL 1 DAY), DATE_ADD(@now, INTERVAL 90 DAY), @mark, @now, ''
UNION ALL
SELECT '短剧热播·战神奶爸', CONCAT(@img, 'banner_03.png'), 'work',
       (SELECT work_id FROM sys_work WHERE title = '战神奶爸' AND create_by = @mark LIMIT 1),
       NULL, 'home_top', 3, 'on', DATE_SUB(@now, INTERVAL 1 DAY), DATE_ADD(@now, INTERVAL 90 DAY), @mark, @now, ''
UNION ALL
SELECT '限时活动·创作激励计划', CONCAT(@img, 'banner_04.png'), 'url', 0,
       'https://www.example.com/activity/creator-incentive',
       'home_top', 4, 'on', DATE_SUB(@now, INTERVAL 1 DAY), DATE_ADD(@now, INTERVAL 30 DAY), @mark, @now, ''
UNION ALL
SELECT '分类推荐·言情专区', CONCAT(@img, 'banner_05.png'), 'page', 0, '/pages/category/list?type=言情',
       'home_middle', 5, 'on', DATE_SUB(@now, INTERVAL 1 DAY), DATE_ADD(@now, INTERVAL 90 DAY), @mark, @now, '';

-- ---------------------------------------------------------------------
-- 9. 校验（闸门 summary）
-- ---------------------------------------------------------------------
SET @c_author   = (SELECT COUNT(*) FROM sys_user WHERE user_name LIKE 'author\_%' AND create_by = @mark);
SET @c_category = (SELECT COUNT(*) FROM sys_category WHERE create_by = @mark);
SET @c_tag      = (SELECT COUNT(*) FROM sys_tag WHERE create_by = @mark);
SET @c_work     = (SELECT COUNT(*) FROM sys_work WHERE create_by = @mark);
SET @c_chapter  = (SELECT COUNT(*) FROM sys_work_chapter WHERE create_by = @mark);
SET @c_wtag     = (SELECT COUNT(*) FROM sys_work_tag WHERE create_by = @mark);
SET @c_banner   = (SELECT COUNT(*) FROM sys_banner WHERE create_by = @mark);

-- 关联完整性：孤儿作品标签数（应为 0）
SET @orphan_wtag = (SELECT COUNT(*) FROM sys_work_tag wt
                    LEFT JOIN sys_work w ON w.work_id = wt.work_id
                    LEFT JOIN sys_tag  t ON t.tag_id  = wt.tag_id
                    WHERE wt.create_by = @mark AND (w.work_id IS NULL OR t.tag_id IS NULL));

-- 上架作品必须都有关联标签与章节（缺失数应为 0）
SET @shelf_no_tag = (SELECT COUNT(*) FROM sys_work w
                     WHERE w.create_by = @mark AND w.status = 'on_shelf'
                       AND NOT EXISTS (SELECT 1 FROM sys_work_tag wt WHERE wt.work_id = w.work_id));
SET @shelf_no_chapter = (SELECT COUNT(*) FROM sys_work w
                         WHERE w.create_by = @mark AND w.status = 'on_shelf'
                           AND NOT EXISTS (SELECT 1 FROM sys_work_chapter c WHERE c.work_id = w.work_id));

-- 非法枚举值（应为 0）
SET @bad_status = (SELECT COUNT(*) FROM sys_work
                   WHERE create_by = @mark AND status NOT IN ('draft','pending','approved','on_shelf','off_shelf'));
SET @bad_trade  = (SELECT COUNT(*) FROM sys_work
                   WHERE create_by = @mark AND (trade_type IS NULL
                         OR trade_type NOT IN ('exclusive','non_exclusive','adaptation')));

SET @fail_cnt = @orphan_wtag + @shelf_no_tag + @shelf_no_chapter + @bad_status + @bad_trade
              + IF(@c_work <> 30, 1, 0) + IF(@c_category <> 12, 1, 0) + IF(@c_tag <> 20, 1, 0)
              + IF(@c_author <> 8, 1, 0) + IF(@c_banner <> 5, 1, 0);

SELECT 'CHECK' AS check_result,
       CONCAT('author=', @c_author, ', category=', @c_category, ', tag=', @c_tag,
              ', work=', @c_work, ', chapter=', @c_chapter, ', work_tag=', @c_wtag,
              ', banner=', @c_banner) AS detail;

SELECT 'CHECK_ASSOC' AS check_result,
       CONCAT('orphan_work_tag=', @orphan_wtag,
              ', shelf_missing_tag=', @shelf_no_tag,
              ', shelf_missing_chapter=', @shelf_no_chapter,
              ', bad_status=', @bad_status, ', bad_trade=', @bad_trade) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('种子校验未通过，fail_cnt=', @fail_cnt) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, 基础经典数据已灌入（8作者/12分类/20标签/30作品/章节/关联/5Banner）' AS detail
WHERE @fail_cnt = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_genre_tag;
DROP TEMPORARY TABLE IF EXISTS tmp_work;
DROP TEMPORARY TABLE IF EXISTS tmp_author;