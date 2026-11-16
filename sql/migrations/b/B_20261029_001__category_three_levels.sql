-- =====================================================================
-- B 模块内容 · 分类体系升级为三级（频道 → 题材 → 细分）
--
-- 背景：云端 script_platform_dev 的 sys_category 现为 67 条——16 条顶级题材
--   （parent_id=0，category_type='theme'）、51 条挂题材下的细分（其中军旅59/
--   职场60/校园61/古装62/推理63/美食64 为 category_type='work' 的题材子级）。
--   题材之上缺少「频道」层，App 只能平铺展示 16 个题材，无法按频道归类。
--
-- 本脚本做三件事：
--   1. 新增两个频道分类：146 男生频道、147 女生频道（category_type='channel'，
--      parent_id=0，status=0）；INSERT ... WHERE NOT EXISTS 保证幂等。
--   2. 把 16 个题材的 parent_id 回填到所属频道（幂等 UPDATE）。
--   3. 重排全表 sort 为全局唯一连续值：频道 1~2、题材 3~18、其余细分 19~69。
--      排序口径与既有「全局唯一位次」一致（Service 层 sort 唯一性强校验 +
--      插入式重排均以全局槽位为前提）：按「层级(0/1/2) → 原 sort → category_id」
--      生成 ROW_NUMBER。重复执行时原 sort 已是对应顺序，结果保持一致。
--
-- 说明：
--   * 不改列定义、不加唯一索引、不动其它表；被作品 genre_id 引用的分类 ID 不变。
--   * category_type='work' 的 6 条细分仍是第 3 级，无需改类型。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261029_001__category_three_levels.sql"
--       verify 闸门为 summary：要求末行 PASS 且 fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 新增频道（category_name 有唯一索引，重复执行时被 NOT EXISTS 拦截）
-- ---------------------------------------------------------------------
INSERT INTO sys_category (category_id, category_name, category_type, parent_id, sort, status, create_by, create_time, remark)
SELECT 146, '男生频道', 'channel', 0, 1, 0, 'category-three-levels', NOW(), '三级分类-频道'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_category WHERE category_id = 146);

INSERT INTO sys_category (category_id, category_name, category_type, parent_id, sort, status, create_by, create_time, remark)
SELECT 147, '女生频道', 'channel', 0, 2, 0, 'category-three-levels', NOW(), '三级分类-频道'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_category WHERE category_id = 147);

-- ---------------------------------------------------------------------
-- 2. 回填题材 parent_id
--    男生频道：玄幻25 仙侠26 武侠27 都市28 悬疑29 历史30
--             科幻31 游戏33 军事35 体育36 奇幻37 末世40
--    女生频道：言情32 二次元34 现实38 灵异49
-- ---------------------------------------------------------------------
UPDATE sys_category
SET parent_id = 146
WHERE category_id IN (25, 26, 27, 28, 29, 30, 31, 33, 35, 36, 37, 40)
  AND parent_id <> 146;

UPDATE sys_category
SET parent_id = 147
WHERE category_id IN (32, 34, 38, 49)
  AND parent_id <> 147;

-- ---------------------------------------------------------------------
-- 3. 全表位次重排（全局唯一连续 1..N）
--    JOIN 派生表规避 MySQL「不能在子查询中更新同一张表」限制；
--    ORDER BY 层级 → 原 sort → category_id，保证重复执行为同一结果。
-- ---------------------------------------------------------------------
UPDATE sys_category c
JOIN (
    SELECT category_id,
           ROW_NUMBER() OVER (
               ORDER BY CASE
                            WHEN category_type = 'channel' THEN 0
                            WHEN parent_id IN (146, 147) THEN 1
                            ELSE 2
                        END,
                        sort, category_id
           ) AS rn
    FROM sys_category
) r ON r.category_id = c.category_id
SET c.sort = r.rn;

-- ---------------------------------------------------------------------
-- 4. verify 闸门
-- ---------------------------------------------------------------------
SET @total      = (SELECT COUNT(*)             FROM sys_category);
SET @distinct   = (SELECT COUNT(DISTINCT sort) FROM sys_category);
SET @lvl1       = (SELECT COUNT(*) FROM sys_category WHERE parent_id = 0);
SET @lvl2       = (SELECT COUNT(*) FROM sys_category WHERE parent_id IN (146, 147));
SET @lvl3       = (SELECT COUNT(*) FROM sys_category WHERE parent_id <> 0 AND parent_id NOT IN (146, 147));
SET @dup_sort   = @total - @distinct;
SET @orphan_top = (SELECT COUNT(*) FROM sys_category WHERE parent_id = 0 AND category_id NOT IN (146, 147));
SET @min_sort   = (SELECT MIN(sort)            FROM sys_category);
SET @max_sort   = (SELECT MAX(sort)            FROM sys_category);

SET @fail_cnt = IF(@total <> 69, 1, 0)
              + IF(@lvl1 <> 2, 1, 0)
              + IF(@lvl2 <> 16, 1, 0)
              + IF(@lvl3 <> 51, 1, 0)
              + IF(@dup_sort <> 0, 1, 0)
              + IF(@orphan_top <> 0, 1, 0);

SELECT 'CHECK' AS check_result,
       CONCAT('total=', @total, ', lvl1=', @lvl1, ', lvl2=', @lvl2, ', lvl3=', @lvl3,
              ', dup_sort=', @dup_sort, ', orphan_top=', @orphan_top,
              ', sort_range=', IFNULL(@min_sort, 'NULL'), '..', IFNULL(@max_sort, 'NULL')) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('三级分类校验未通过，fail_cnt=', @fail_cnt) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, 三级分类已就绪：total=', @total,
              '（2 频道 / 16 题材 / 51 细分）') AS detail
WHERE @fail_cnt = 0;
