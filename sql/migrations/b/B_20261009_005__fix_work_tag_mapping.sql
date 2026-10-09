-- =====================================================================
-- B 模块（内容与作品）· 作品-标签映射修正
--
-- 背景：003 种子把每个题材硬编码 2 个标签，导致
--   * 都市下没有「重生」→「都市 + 重生」筛选命中 0 部；
--   * 玄幻下没有「系统」→「玄幻 + 系统」筛选命中 0 部；
--   * 「重生/穿书/大女主」等标签全程未被任何作品使用。
-- 本次修正为：每个题材给 3 个标签池，按 work_id 轮转取 2 个，
-- 既保证同题材内作品标签有差异（分类×标签联动才有意义），又保证示例组合非空。
--
-- 依赖：sys_work / sys_category / sys_tag 已由 003 灌入（create_by='seed-content-classic'）。
-- 幂等：先按标记删除本体系的作品-标签关联，再重建；只动 sys_work_tag，
--       不触碰任何作品ID，因此不影响 004 的剧集/榜单/互动数据。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261009_005__fix_work_tag_mapping.sql"
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

-- 连接排序规则须与内容域表列（utf8mb4_general_ci）一致，
-- 否则用户变量 @mark 与 create_by 列比较会报 collation 冲突。
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

SET @mark = 'seed-content-classic';
SET @now  = NOW();

-- ---------------------------------------------------------------------
-- 1. 幂等清理（仅回滚本体系的标签关联）
-- ---------------------------------------------------------------------
DELETE FROM sys_work_tag WHERE create_by = @mark;

-- ---------------------------------------------------------------------
-- 2. 题材标签池（每题材 3 个；与 003 脚本保持同一份映射）
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

-- ---------------------------------------------------------------------
-- 3. 重建作品-标签关联（每部作品 2 个标签）
-- ---------------------------------------------------------------------
INSERT INTO sys_work_tag (work_id, tag_id, create_by, create_time, remark)
SELECT w.work_id, t.tag_id, @mark, @now, ''
FROM sys_work w
JOIN sys_category c ON c.category_id = w.genre_id
JOIN tmp_genre_tag gt
     ON gt.genre_name = c.category_name
    AND gt.seq IN (MOD(w.work_id, 3), MOD(w.work_id + 1, 3))
JOIN sys_tag t ON t.tag_name = gt.tag_name
WHERE w.create_by = @mark AND w.is_deleted = 0;

DROP TEMPORARY TABLE IF EXISTS tmp_genre_tag;

-- ---------------------------------------------------------------------
-- 4. 校验（闸门 summary）
-- ---------------------------------------------------------------------
SET @c_wtag = (SELECT COUNT(*) FROM sys_work_tag WHERE create_by = @mark);

-- 孤儿关联（应 0）：关联到不存在的作品或标签
SET @orphan = (SELECT COUNT(*) FROM sys_work_tag wt
               LEFT JOIN sys_work w ON w.work_id = wt.work_id
               LEFT JOIN sys_tag  t ON t.tag_id  = wt.tag_id
               WHERE wt.create_by = @mark AND (w.work_id IS NULL OR t.tag_id IS NULL));

-- 示例组合必须非空：都市 + 重生
SET @city_rebirth = (SELECT COUNT(*) FROM sys_work w
                     JOIN sys_category c   ON c.category_id = w.genre_id
                     JOIN sys_work_tag wt  ON wt.work_id = w.work_id
                     JOIN sys_tag t        ON t.tag_id = wt.tag_id
                     WHERE w.create_by = @mark AND w.is_deleted = 0 AND w.status = 'on_shelf'
                       AND c.category_name = '都市' AND t.tag_name = '重生');

-- 示例组合必须非空：玄幻 + 系统
SET @fantasy_system = (SELECT COUNT(*) FROM sys_work w
                       JOIN sys_category c   ON c.category_id = w.genre_id
                       JOIN sys_work_tag wt  ON wt.work_id = w.work_id
                       JOIN sys_tag t        ON t.tag_id = wt.tag_id
                       WHERE w.create_by = @mark AND w.is_deleted = 0 AND w.status = 'on_shelf'
                         AND c.category_name = '玄幻' AND t.tag_name = '系统');

SET @fail_cnt = @orphan
              + IF(@c_wtag = 0, 1, 0)
              + IF(@city_rebirth   = 0, 1, 0)
              + IF(@fantasy_system = 0, 1, 0);

SELECT 'CHECK' AS check_result,
       CONCAT('work_tag=', @c_wtag, ', 都市+重生=', @city_rebirth,
              ', 玄幻+系统=', @fantasy_system, ', 孤儿关联=', @orphan) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('修正未达预期：work_tag=', @c_wtag, ', 都市+重生=', @city_rebirth,
              ', 玄幻+系统=', @fantasy_system, ', 孤儿=', @orphan) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, 作品-标签映射已修正，都市含重生、玄幻含系统' AS detail
WHERE @fail_cnt = 0;