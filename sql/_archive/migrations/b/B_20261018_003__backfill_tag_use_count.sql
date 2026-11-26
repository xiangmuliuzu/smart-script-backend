-- =====================================================================
-- B 模块内容 · 标签使用数量（sys_tag.use_count）回填为「实际被引用次数」
--
-- 背景：use_count 为冗余统计字段，历史新增/作品标签写入均未维护，导致「标签管理」
--   与 App 书城标签列表（selectAppTagList 按 use_count desc 排序）看到的数量恒为 0，
--   与真实引用完全脱节，标签热度排序也失去意义。
--
-- 口径（与 SysTagMapper.syncTagUseCount 完全一致）：
--   use_count = 该标签在 sys_work_tag 中被「未删除作品（sys_work.is_deleted = 0）」
--              引用的行数；无引用的标签置 0。
--   * 逻辑删除的作品不计入（与项目内 is_deleted=0 的通行口径一致）。
--   * 不新增列、不改列定义、不动其它表。
--
-- 幂等：整表按实际引用重算，重复执行结果一致（第二次起为「无变化」更新）。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261018_003__backfill_tag_use_count.sql"
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 回填：按 (tag_id -> 未删除作品引用数) 重算 use_count
--    用「JOIN 派生表」而非子查询，规避 MySQL「不能在子查询中更新同一张表」限制
-- ---------------------------------------------------------------------
UPDATE sys_tag t
LEFT JOIN (
    SELECT wt.tag_id AS tag_id,
           COUNT(*) AS c
    FROM sys_work_tag wt
    JOIN sys_work w ON w.work_id = wt.work_id AND w.is_deleted = 0
    GROUP BY wt.tag_id
) x ON x.tag_id = t.tag_id
SET t.use_count = IFNULL(x.c, 0);

-- ---------------------------------------------------------------------
-- 2. verify 闸门：use_count 必须与「实际被引用次数」逐一相等
-- ---------------------------------------------------------------------
SET @mismatch = (
    SELECT COUNT(*)
    FROM sys_tag t
    LEFT JOIN (
        SELECT wt.tag_id AS tag_id,
               COUNT(*) AS c
        FROM sys_work_tag wt
        JOIN sys_work w ON w.work_id = wt.work_id AND w.is_deleted = 0
        GROUP BY wt.tag_id
    ) x ON x.tag_id = t.tag_id
    WHERE t.use_count IS NULL OR t.use_count <> IFNULL(x.c, 0)
);

SET @total   = (SELECT COUNT(*) FROM sys_tag);
SET @nonzero = (SELECT COUNT(*) FROM sys_tag WHERE use_count > 0);

SET @fail_cnt = @mismatch;

SELECT 'CHECK' AS check_result,
       CONCAT('total=', @total, ', use_count>0=', @nonzero, ', mismatch=', @mismatch) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('仍有 ', @mismatch, ' 个标签的 use_count 与实际引用不一致') AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, ', @total, ' 个标签的 use_count 已与引用次数接轨（其中 ', @nonzero, ' 个非 0）') AS detail
WHERE @fail_cnt = 0;
