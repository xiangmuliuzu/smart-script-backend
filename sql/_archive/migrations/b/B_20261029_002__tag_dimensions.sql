-- =====================================================================
-- B 模块内容 · 标签体系升级为「维度 + 分组 + 频道」
--
-- 背景：sys_tag 现有 32 条标签的 tag_type 全为 'theme'，但语义上都是
--   角色 / 情节类标签（神医、赘婿、复仇、穿越、甜宠、宫斗…）；没有分组，
--   也没有频道归属，App 无法按「角色 / 情节」维度分组展示。
--
-- 本脚本做四件事：
--   1. 幂等新增 group_name（分组）、channel_id（频道，NULL=男女通用）两列，
--      并新增导航索引 idx_tag_nav(status, channel_id, tag_type, sort)。
--   2. tag_type 语义改为维度：role 角色 / plot 情节；theme 退役。
--   3. 按分组归类 32 条存量标签（不改 tag_id，作品标签关联不受影响）。
--   4. 追加 10 条种子标签，位次接在现有 MAX(sort) 之后，保持全局唯一。
--
-- 说明：
--   * 不改列定义以外的东西，不改 sys_work_tag 关联。
--   * 位次仍为全局唯一序列（与 Service 层位次腾挪逻辑一致）。
--   * 脚本幂等：重复执行不会重复加列 / 加索引 / 插标签。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql --host=<主机> --user=<账号> \
--       --database=script_platform_dev --default-character-set=utf8mb4 \
--       --execute="source sql/migrations/b/B_20261029_002__tag_dimensions.sql"
--       verify 闸门为末行 PASS 且 fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

SET @db = DATABASE();

-- ---------------------------------------------------------------------
-- 1. 幂等加列 / 加索引
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_tag' AND COLUMN_NAME = 'group_name') = 0,
    'ALTER TABLE sys_tag ADD COLUMN group_name varchar(32) NULL COMMENT ''标签分组'' AFTER tag_type',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_tag' AND COLUMN_NAME = 'channel_id') = 0,
    'ALTER TABLE sys_tag ADD COLUMN channel_id bigint NULL COMMENT ''所属频道ID（NULL=通用）'' AFTER group_name',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_tag' AND INDEX_NAME = 'idx_tag_nav') = 0,
    'CREATE INDEX idx_tag_nav ON sys_tag (status, channel_id, tag_type, sort)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 存量标签归类（按标签名匹配，幂等）
-- ---------------------------------------------------------------------
-- 角色 · 人物身份（男生频道）
UPDATE sys_tag
SET tag_type = 'role', group_name = '人物身份', channel_id = 146
WHERE tag_name IN ('神医', '赘婿', '战神', '大佬', '总裁');

-- 角色 · 人物身份（女生频道）
UPDATE sys_tag
SET tag_type = 'role', group_name = '人物身份', channel_id = 147
WHERE tag_name IN ('大女主', '女主强大', '男主深情', '萌宝');

-- 情节 · 逆袭爽感（通用）
UPDATE sys_tag
SET tag_type = 'plot', group_name = '逆袭爽感', channel_id = NULL
WHERE tag_name IN ('复仇', '逆袭', '打脸', '爽文', '马甲');

-- 情节 · 穿越重生（通用）
UPDATE sys_tag
SET tag_type = 'plot', group_name = '穿越重生', channel_id = NULL
WHERE tag_name IN ('穿越', '重生', '系统', '穿书', '无限流');

-- 情节 · 情感关系（通用）
UPDATE sys_tag
SET tag_type = 'plot', group_name = '情感关系', channel_id = NULL
WHERE tag_name IN ('甜宠', '先婚后爱', '破镜重圆', '暗恋成真');

-- 情节 · 题材设定（通用，性别倾向者单独回填频道）
UPDATE sys_tag
SET tag_type = 'plot', group_name = '题材设定', channel_id = NULL
WHERE tag_name IN ('娱乐圈', '权谋', '种田', '年代', '修仙', '悬疑', '热血', '豪门', '宫斗');

UPDATE sys_tag SET channel_id = 146 WHERE tag_name = '热血';
UPDATE sys_tag SET channel_id = 147 WHERE tag_name IN ('豪门', '宫斗');

-- ---------------------------------------------------------------------
-- 3. 种子标签（补足分组观感；位次接在 MAX(sort) 之后）
-- ---------------------------------------------------------------------
INSERT INTO sys_tag (tag_name, tag_type, group_name, channel_id, use_count, sort, status, create_by, create_time)
SELECT s.tag_name, s.tag_type, s.group_name, s.channel_id, 0,
       (SELECT MAX(sort) FROM sys_tag) + s.rn,
       0, 'tag-dimensions', NOW()
FROM (
    SELECT '弃少'  AS tag_name, 'role' AS tag_type, '人物身份' AS group_name, 146 AS channel_id, 1 AS rn
    UNION ALL SELECT '神豪',  'role', '人物身份', 146, 2
    UNION ALL SELECT '白月光', 'role', '情感关系', 147, 3
    UNION ALL SELECT '替身',  'role', '情感关系', 147, 4
    UNION ALL SELECT '忠犬',  'role', '情感关系', 147, 5
    UNION ALL SELECT '病娇',  'role', '情感关系', 147, 6
    UNION ALL SELECT '团宠',  'plot', '逆袭爽感', NULL, 7
    UNION ALL SELECT '掉马',  'plot', '逆袭爽感', NULL, 8
    UNION ALL SELECT '升级流', 'plot', '穿越重生', NULL, 9
    UNION ALL SELECT '副本',  'plot', '穿越重生', NULL, 10
) s
WHERE NOT EXISTS (SELECT 1 FROM sys_tag t WHERE t.tag_name = s.tag_name);

-- ---------------------------------------------------------------------
-- 4. verify 闸门
-- ---------------------------------------------------------------------
SET @total   = (SELECT COUNT(*) FROM sys_tag);
SET @role    = (SELECT COUNT(*) FROM sys_tag WHERE tag_type = 'role');
SET @plot    = (SELECT COUNT(*) FROM sys_tag WHERE tag_type = 'plot');
SET @other   = (SELECT COUNT(*) FROM sys_tag WHERE tag_type NOT IN ('role', 'plot'));
SET @nogroup = (SELECT COUNT(*) FROM sys_tag WHERE group_name IS NULL OR group_name = '');
SET @badch   = (SELECT COUNT(*) FROM sys_tag WHERE channel_id IS NOT NULL AND channel_id NOT IN (146, 147));
SET @dup     = @total - (SELECT COUNT(DISTINCT sort) FROM sys_tag);

SET @fail_cnt = IF(@total <> 42, 1, 0)
              + IF(@role <> 15, 1, 0)
              + IF(@plot <> 27, 1, 0)
              + IF(@other <> 0, 1, 0)
              + IF(@nogroup <> 0, 1, 0)
              + IF(@badch <> 0, 1, 0)
              + IF(@dup <> 0, 1, 0);

SELECT 'CHECK' AS check_result,
       CONCAT('total=', @total, ', role=', @role, ', plot=', @plot, ', other=', @other,
              ', nogroup=', @nogroup, ', badch=', @badch, ', dup_sort=', @dup) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('标签维度校验未通过，fail_cnt=', @fail_cnt) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, 标签维度已就绪：total=', @total,
              '（role=', @role, ' / plot=', @plot, '）') AS detail
WHERE @fail_cnt = 0;
