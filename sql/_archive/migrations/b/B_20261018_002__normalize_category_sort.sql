-- =====================================================================
-- B 模块内容 · 分类位次（sys_category.sort）归一到「稠密且唯一」序列
--
-- 背景：分类管理与标签管理一致，引入「位次」语义——位次全局唯一（1..N 互不重复），
--   且把分类设为位次 k 时，原 k..N 的分类整体后移一位（插入式重排）。
--   插入式重排的区间位移算法成立的前提是：存量位次本身已是「无重复、无空洞」的
--   序列。而云端 sys_category.sort 为 int NOT NULL，既无唯一索引也无默认值，历史
--   新增代码还曾以 0 兜底，故先做一次性归一。
--
-- 口径：
--   * 按现有展示顺序 (sort, category_id) 重新编号为 1..N，保持既有相对顺序不变。
--   * 归一后 sort 严格为 1..N 的连续整数，两两不重复。
--   * 与标签口径一致：全局一套位次（不按 parent_id 分段），与
--     selectCategoryList「order by sort, category_id」及 Service 层
--     countBySort/makeRoomForSort 的全局面次判定保持一致。
--
-- 幂等：整表重编号，重复执行结果一致（第二次起为「无变化」更新）。
--   不改动列定义、不新增索引、不动其它表。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261018_002__normalize_category_sort.sql"
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 归一：按 (sort, category_id) 现有顺序重编号为 1..N
--    用「JOIN 派生表」而非子查询，规避 MySQL「不能在子查询中更新同一张表」限制
-- ---------------------------------------------------------------------
UPDATE sys_category c
JOIN (
    SELECT category_id,
           ROW_NUMBER() OVER (ORDER BY sort, category_id) AS rn
    FROM sys_category
) r ON r.category_id = c.category_id
SET c.sort = r.rn;

-- ---------------------------------------------------------------------
-- 2. verify 闸门
-- ---------------------------------------------------------------------
SET @total    = (SELECT COUNT(*)             FROM sys_category);
SET @distinct = (SELECT COUNT(DISTINCT sort) FROM sys_category);
SET @min_sort = (SELECT MIN(sort)            FROM sys_category);
SET @max_sort = (SELECT MAX(sort)            FROM sys_category);

-- 位次两两不重复（空表视为通过）：应 1
SET @uniq_ok  = IF(@total = 0 OR @distinct = @total, 1, 0);
-- 位次自 1 起（空表视为通过）：应 1
SET @min_ok   = IF(@total = 0 OR @min_sort = 1, 1, 0);
-- 位次稠密到 N（空表视为通过）：应 1
SET @dense_ok = IF(@total = 0 OR @max_sort = @total, 1, 0);

SET @fail_cnt = 3 - (@uniq_ok + @min_ok + @dense_ok);

SELECT 'CHECK' AS check_result,
       CONCAT('total=', @total, ', distinct=', @distinct,
              ', min=', IFNULL(@min_sort, 'NULL'), ', max=', IFNULL(@max_sort, 'NULL')) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('分类位次未达「1..N 稠密唯一」：total=', @total, ', distinct=', @distinct,
              ', min=', IFNULL(@min_sort, 'NULL'), ', max=', IFNULL(@max_sort, 'NULL')) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, 分类位次已归一为 1..', @total, ' 稠密唯一') AS detail
WHERE @fail_cnt = 0;
