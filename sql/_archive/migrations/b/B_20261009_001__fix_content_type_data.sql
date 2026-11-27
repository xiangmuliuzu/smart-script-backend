-- =====================================================================
-- B 模块（内容与作品）· 分类/标签「类型」数据规范化
--
-- 背景：
--   设计文档（附件5.1_数据库设计文档）未给出 sys_category.category_type 与
--   sys_tag.tag_type 的枚举定义，云端库现存为脏值：
--     sys_category.category_type：'校园'、'龙王'、''（空）—— 均为误填的题材名
--     sys_tag：仅 1 条 'test'（tag_type='1'）—— 垃圾数据
--   PC 端「分类管理 / 标签管理」查询区已改为「预设类型下拉」，预设值集中在
--     smart-script-web/src/constants/contentEnum.js
--       CATEGORY_TYPE = theme(题材) / style(风格) / audience(受众)
--       TAG_TYPE      = theme(题材) / audience(受众)
--   本脚本把库内数据对齐到该预设，使下拉筛选可命中真实行。
--
-- 处理（幂等，可重复执行）：
--   1. sys_category.category_type 非预设值统一归一到 'theme'（题材）——
--      现有分类均为题材名（言情/都市/悬疑）。
--   2. 删除垃圾标签 'test'（已确认 sys_work_tag 无引用、无外键约束）。
--   3. 插入经典标签（题材 4 条 + 受众 2 条），按 tag_name 去重。
--
-- 依据表结构（information_schema）：
--   sys_category.category_type  varchar(20) NOT NULL
--   sys_tag.tag_type            varchar(20) NOT NULL
--   sys_tag.tag_name            varchar(30) NOT NULL UNIQUE
--
-- 执行：
--   $env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev \
--     -e "source sql/migrations/b/B_20261009_001__fix_content_type_data.sql"
--   （口令只从环境变量读入，不进 argv、不落日志）
--   verify 闸门为 summary：要求末行 PASS fail_cnt=0，出现 FAIL 行即整体失败。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 分类类型归一：校园/龙王/空 → theme（题材）
-- ---------------------------------------------------------------------
UPDATE sys_category
SET category_type = 'theme'
WHERE category_type NOT IN ('theme', 'style', 'audience');

-- ---------------------------------------------------------------------
-- 2. 删除垃圾标签
-- ---------------------------------------------------------------------
DELETE FROM sys_tag WHERE tag_name = 'test';

-- ---------------------------------------------------------------------
-- 3. 插入经典标签（按 tag_name 去重，避免撞唯一键）
-- ---------------------------------------------------------------------
INSERT INTO sys_tag (tag_name, tag_type, use_count, sort, status, create_by, create_time, remark)
SELECT t.tag_name, t.tag_type, 0, t.sort, 0, 'admin', NOW(), '经典标签数据'
FROM (
    SELECT '逆袭'  AS tag_name, 'theme'    AS tag_type, 1 AS sort UNION ALL
    SELECT '甜宠',             'theme',                2       UNION ALL
    SELECT '复仇',             'theme',                3       UNION ALL
    SELECT '悬疑',             'theme',                4       UNION ALL
    SELECT '女性向',           'audience',             5       UNION ALL
    SELECT '男性向',           'audience',             6
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_tag s WHERE s.tag_name = t.tag_name);

-- ---------------------------------------------------------------------
-- 4. 校验（闸门 summary）
-- ---------------------------------------------------------------------
SET @bad_cat   = (SELECT COUNT(*) FROM sys_category WHERE category_type NOT IN ('theme', 'style', 'audience'));
SET @junk_tag  = (SELECT COUNT(*) FROM sys_tag WHERE tag_name = 'test');
SET @theme_cat = (SELECT COUNT(*) FROM sys_category WHERE category_type = 'theme');
SET @theme_tag = (SELECT COUNT(*) FROM sys_tag WHERE tag_type = 'theme');
SET @aud_tag   = (SELECT COUNT(*) FROM sys_tag WHERE tag_type = 'audience');

SET @fail_cnt = (@bad_cat > 0) + (@junk_tag > 0) + (@theme_cat = 0) + (@theme_tag = 0) + (@aud_tag = 0);

SELECT 'CHECK' AS check_result,
       CONCAT('bad_category_type=', @bad_cat,
              ', junk_tag=', @junk_tag,
              ', theme_category=', @theme_cat,
              ', theme_tag=', @theme_tag,
              ', audience_tag=', @aud_tag) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('仍有非预设分类类型：bad_category_type=', @bad_cat) AS detail
WHERE @bad_cat > 0;

SELECT 'FAIL' AS check_result,
       '垃圾标签 test 仍存在' AS detail
WHERE @junk_tag > 0;

SELECT 'FAIL' AS check_result,
       '分类类型 theme 为空，分类数据异常' AS detail
WHERE @theme_cat = 0;

SELECT 'FAIL' AS check_result,
       '经典标签未写入（theme/audience 至少各 1 条）' AS detail
WHERE @theme_tag = 0 OR @aud_tag = 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, theme_category=', @theme_cat,
              ', theme_tag=', @theme_tag,
              ', audience_tag=', @aud_tag) AS detail
WHERE @fail_cnt = 0;