-- =====================================================================
-- B 模块内容 · 作品标签维度清理
--
-- 规则：主分类 1 + 主题 2 + 角色 2 + 情节 2（每部作品最多 7 个标签）
--
-- 背景：
--   历史种子数据存在同一维度超限（如 2 个主分类、3 个主题、4 个情节），
--   导致 App 作品卡片标签过多、作品分类信息失真。
--
-- 内容：
--   1. 按维度保留每个作品最早关联的前 N 个标签（主分类 1 / 主题 2 / 角色 2 / 情节 2），
--      删除超额关联；同一关联时间按自增 id 保证结果确定性。
--   2. 刷新 sys_tag.use_count，使标签使用量与作品实际引用一致。
--   3. verify 闸门：不存在超维度或超总数的作品。
--
-- 幂等：第二次执行不会删除任何记录（已无超额行）；末行输出 PASS 且 fail_cnt=0。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261105_003__work_tag_dimension_cleanup.sql"
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 删除同一维度超额的标签关联（保留最早关联的前 N 个）
-- ---------------------------------------------------------------------
DELETE wt
FROM sys_work_tag wt
JOIN (
    SELECT ranked.id
    FROM (
        SELECT wt2.id,
               wt2.work_id,
               t.tag_type,
               ROW_NUMBER() OVER (PARTITION BY wt2.work_id, t.tag_type ORDER BY wt2.id) AS rn
        FROM sys_work_tag wt2
        JOIN sys_tag t ON t.tag_id = wt2.tag_id
    ) ranked
    WHERE (ranked.tag_type = 'hot'   AND ranked.rn > 1)
       OR (ranked.tag_type = 'theme' AND ranked.rn > 2)
       OR (ranked.tag_type = 'role'  AND ranked.rn > 2)
       OR (ranked.tag_type = 'plot'  AND ranked.rn > 2)
) d ON d.id = wt.id;

-- ---------------------------------------------------------------------
-- 2. 刷新标签使用数量
-- ---------------------------------------------------------------------
UPDATE sys_tag t
SET t.use_count = (SELECT COUNT(*) FROM sys_work_tag wt WHERE wt.tag_id = t.tag_id);

-- ---------------------------------------------------------------------
-- 3. verify 闸门：任一不符则 fail_cnt > 0
-- ---------------------------------------------------------------------
SET @fail = 0;

-- 3.1 单维度超额
SET @fail = @fail + IF((SELECT COUNT(*)
    FROM (
        SELECT wt.work_id, t.tag_type, COUNT(*) AS cnt
        FROM sys_work_tag wt
        JOIN sys_tag t ON t.tag_id = wt.tag_id
        WHERE t.tag_type IN ('hot', 'theme', 'role', 'plot')
        GROUP BY wt.work_id, t.tag_type
        HAVING (t.tag_type = 'hot'   AND cnt > 1)
            OR (t.tag_type = 'theme' AND cnt > 2)
            OR (t.tag_type = 'role'  AND cnt > 2)
            OR (t.tag_type = 'plot'  AND cnt > 2)
    ) x) = 0, 0, 1);

-- 3.2 单作品标签总数上限 7
SET @fail = @fail + IF((SELECT COUNT(*)
    FROM (
        SELECT work_id FROM sys_work_tag GROUP BY work_id HAVING COUNT(*) > 7
    ) x) = 0, 0, 1);

SELECT IF(@fail = 0, 'PASS', 'FAIL') AS result, @fail AS fail_cnt;
