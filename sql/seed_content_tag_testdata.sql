-- =====================================================================
-- B 模块书城 · 标签筛选链路测试数据（sys_work_tag）
--
-- 目的：让 App 端书城作品列表的 tagId 筛选（EXISTS 子查询）能命中真实行。
--       当前云端 script_platform_dev.sys_work_tag 存在但为空，标签筛选必然返回空列表。
--
-- 依据：附件5.1 表3-13 sys_work_tag
--       (id / work_id / tag_id / created_at / create_by / create_time /
--        update_by / update_time / remark)，唯一索引 uk_work_tag(work_id, tag_id)、
--        普通索引 idx_tag_id(tag_id)；sys_work.status='on_shelf' 表示已上架；
--       sys_tag.status='0' 表示正常。
--
-- 三条硬约束：
--   1. 幂等——重复执行不产生重复行（已有标签关联的作品直接跳过）；
--   2. 不写死 ID——素材全部取自库内现有 sys_work / sys_tag，按 ROW_NUMBER 轮流分配；
--   3. 不编造业务数据——作品与标签必须先由 PC 端后台建好，本脚本只建关联，
--      不改 sys_tag.use_count（该计数由 PC 端标签服务维护）。
--
-- 上限：最多为 200 个已上架作品建关联，避免在共享开发库上写入过长。
--
-- 执行：scripts\db\init-database.ps1 -d script_platform_dev -u <账号> -h <主机> -Steps scripts\db\seed-steps.txt
--       （口令只从 $env:DB_PASSWORD 读入，不进 argv、不落日志）
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0，出现 FAIL 行即整体失败。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. 前置守卫：素材不足时不做任何写入，直接进入校验段报 FAIL
-- ---------------------------------------------------------------------
SET @work_cnt = (SELECT COUNT(*) FROM sys_work WHERE is_deleted = 0 AND status = 'on_shelf');
SET @tag_cnt  = (SELECT COUNT(*) FROM sys_tag WHERE status = '0');

-- ---------------------------------------------------------------------
-- 1. 素材一：已上架作品（按上架时间倒序取前 200，编号 0 起）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_seed_work;
CREATE TEMPORARY TABLE tmp_seed_work (
    work_id BIGINT NOT NULL,
    rn      INT    NOT NULL,
    PRIMARY KEY (work_id)
) ENGINE = MEMORY;

INSERT INTO tmp_seed_work (work_id, rn)
SELECT work_id,
       ROW_NUMBER() OVER (ORDER BY create_time DESC, work_id DESC) - 1
FROM sys_work
WHERE is_deleted = 0
  AND status = 'on_shelf'
  AND @work_cnt > 0
ORDER BY create_time DESC, work_id DESC
LIMIT 200;

-- ---------------------------------------------------------------------
-- 2. 素材二：正常状态标签（按 sort 升序编号 0 起，cnt 为标签总数）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_seed_tag;
CREATE TEMPORARY TABLE tmp_seed_tag (
    tag_id BIGINT NOT NULL,
    rn     INT    NOT NULL,
    cnt    INT    NOT NULL,
    PRIMARY KEY (tag_id)
) ENGINE = MEMORY;

INSERT INTO tmp_seed_tag (tag_id, rn, cnt)
SELECT tag_id,
       ROW_NUMBER() OVER (ORDER BY sort, tag_id) - 1,
       COUNT(*) OVER ()
FROM sys_tag
WHERE status = '0'
  AND @tag_cnt > 0;

-- ---------------------------------------------------------------------
-- 3. 已有关联作品的快照（幂等依据：这些作品不再重复建关联）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_seed_done;
CREATE TEMPORARY TABLE tmp_seed_done (
    work_id BIGINT NOT NULL,
    PRIMARY KEY (work_id)
) ENGINE = MEMORY;

INSERT INTO tmp_seed_done (work_id)
SELECT DISTINCT work_id FROM sys_work_tag;

-- ---------------------------------------------------------------------
-- 4. 建关联：作品按编号轮流分配标签（MOD），每个作品恰好一条
--    唯一键为 uk_work_tag(work_id, tag_id)（复合），故同一作品可挂多个标签；
--    本脚本按 MOD 轮转只建一条，仅为让标签筛选命中真实行
-- ---------------------------------------------------------------------
INSERT INTO sys_work_tag (work_id, tag_id, create_by, create_time, remark)
SELECT w.work_id,
       t.tag_id,
       'seed-content-testdata',
       NOW(),
       'B 模块书城标签筛选测试数据'
FROM tmp_seed_work w
JOIN tmp_seed_tag t ON t.rn = MOD(w.rn, t.cnt)
LEFT JOIN tmp_seed_done d ON d.work_id = w.work_id
WHERE d.work_id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_seed_done;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_tag;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_work;

-- ---------------------------------------------------------------------
-- 5. 校验（闸门 summary）
--    列分隔符由 mysql --batch 生成真实制表符，故 PASS/FAIL 与说明分成两列输出
-- ---------------------------------------------------------------------
SET @rel_cnt   = (SELECT COUNT(*) FROM sys_work_tag);
SET @cover_cnt = (SELECT COUNT(DISTINCT wt.work_id) FROM sys_work_tag wt
                  JOIN sys_work w ON w.work_id = wt.work_id
                  WHERE w.is_deleted = 0 AND w.status = 'on_shelf');
SET @used_tag  = (SELECT COUNT(*) FROM (SELECT tag_id FROM sys_work_tag GROUP BY tag_id) x);
SET @bad_work  = (SELECT COUNT(*) FROM sys_work_tag wt
                  LEFT JOIN sys_work w ON w.work_id = wt.work_id
                  WHERE w.work_id IS NULL);
SET @bad_tag   = (SELECT COUNT(*) FROM sys_work_tag wt
                  LEFT JOIN sys_tag t ON t.tag_id = wt.tag_id
                  WHERE t.tag_id IS NULL);

SET @fail_cnt = (@work_cnt = 0) + (@tag_cnt = 0) + (@rel_cnt = 0)
              + (@used_tag = 0) + (@bad_work > 0) + (@bad_tag > 0);

SELECT 'CHECK' AS check_result,
       CONCAT('work=', @work_cnt,
              ', tag=', @tag_cnt,
              ', relation=', @rel_cnt,
              ', covered_work=', @cover_cnt,
              ', used_tag=', @used_tag,
              ', orphan_work=', @bad_work,
              ', orphan_tag=', @bad_tag) AS detail;

SELECT 'FAIL' AS check_result,
       'sys_work 无已上架作品（is_deleted=0 且 status=on_shelf），请先在 PC 端「内容管理-作品管理」上架作品' AS detail
WHERE @work_cnt = 0;

SELECT 'FAIL' AS check_result,
       'sys_tag 无正常状态标签（status=0），请先在 PC 端「内容管理-标签管理」新增标签' AS detail
WHERE @tag_cnt = 0;

SELECT 'FAIL' AS check_result,
       'sys_work_tag 关联为空：素材不足或 uk_work_tag 唯一键导致插入被跳过，标签筛选仍会返回空列表' AS detail
WHERE @work_cnt > 0 AND @tag_cnt > 0 AND @rel_cnt = 0;

SELECT 'FAIL' AS check_result,
       '没有任何标签被作品使用，标签筛选必然返回空列表' AS detail
WHERE @rel_cnt > 0 AND @used_tag = 0;

SELECT 'FAIL' AS check_result,
       CONCAT('存在指向不存在作品的脏关联，orphan_work=', @bad_work) AS detail
WHERE @bad_work > 0;

SELECT 'FAIL' AS check_result,
       CONCAT('存在指向不存在标签的脏关联，orphan_tag=', @bad_tag) AS detail
WHERE @bad_tag > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, work=', @work_cnt,
              ', tag=', @tag_cnt,
              ', relation=', @rel_cnt,
              ', covered_work=', @cover_cnt,
              ', used_tag=', @used_tag) AS detail
WHERE @fail_cnt = 0;