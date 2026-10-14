-- =====================================================================
-- B 模块（内容与作品）· 读者评分通道（新增 sys_work_rating 表）
--
-- 背景：设计文档只有作品平均分列 sys_work.rating（decimal(3,1)，此前无写入方），
--   既无读者评分表也无提交评分接口，App 读者端「每本书的评分通道」缺失：
--     * App 作品详情页只展示平均分，读者无处打分；
--     * 详情页/书城列表/评分榜/PC 排行榜读的都是 sys_work.rating，该列恒为 0。
--   本次按「一人一评、可覆盖」新增本表，后端提交评分后回写 sys_work.rating
--   = ROUND(AVG(score), 1)，一处回写即打通上述所有展示位。
--
-- 口径：满分为 10 分、每星 2 分（score 取 2/4/6/8/10）。与库中既有 sys_work.rating
--   量纲一致（存量作品为 7.9~9.7 的十分制数值，按 5 分制落库会把均分拉到 5 分档）；
--   (work_id, user_id) 唯一 → 再次提交即覆盖分值，不产生第二条记录，评分数（COUNT）也不因此增长。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行；不触碰任何既有表与列。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261014_001__create_work_rating_table.sql"
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

-- 连接排序规则须与内容域表列（utf8mb4_general_ci）一致
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 建表（读者评分记录：一人一评，唯一键落在 (work_id, user_id)）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_work_rating
(
    rating_id  bigint   NOT NULL AUTO_INCREMENT COMMENT '评分记录ID',
    work_id    bigint   NOT NULL COMMENT '作品ID',
    user_id    bigint   NOT NULL COMMENT '评分用户ID',
    score      tinyint  NOT NULL COMMENT '评分（满分 10 分，每星 2 分：2/4/6/8/10）',
    created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次评分时间',
    updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近评分时间',
    PRIMARY KEY (rating_id),
    UNIQUE KEY uk_work_user (work_id, user_id),
    KEY idx_work_id (work_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '作品读者评分表';

-- 已建过的表同步 score 列注释为 10 分制口径（幂等：重复执行无副作用）
ALTER TABLE sys_work_rating
    MODIFY COLUMN score tinyint NOT NULL COMMENT '评分（满分 10 分，每星 2 分：2/4/6/8/10）';

-- ---------------------------------------------------------------------
-- 2. 校验（闸门 summary）
-- ---------------------------------------------------------------------
-- 表存在（应 1）
SET @tbl_exists = (SELECT COUNT(*)
                   FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'sys_work_rating');

-- 6 个业务列齐备（应 6）
SET @col_cnt = (SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'sys_work_rating'
                  AND COLUMN_NAME IN ('rating_id', 'work_id', 'user_id', 'score', 'created_at', 'updated_at'));

-- (work_id, user_id) 唯一键存在且为复合 2 列（应 1）。
-- 注意：information_schema.STATISTICS 对复合索引每个列各返回一行，故需按「列数=2 且 NON_UNIQUE=0」聚合判定。
SET @uk_cnt = (SELECT IF(COUNT(*) = 2 AND MIN(NON_UNIQUE) = 0, 1, 0)
               FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'sys_work_rating'
                 AND INDEX_NAME = 'uk_work_user');

-- 排序规则一致（应 1）
SET @collation_ok = (SELECT COUNT(*)
                     FROM information_schema.TABLES
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 'sys_work_rating'
                       AND TABLE_COLLATION = 'utf8mb4_general_ci');

-- score 列注释为 10 分制口径（应 1）
SET @cmt_ok = (SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'sys_work_rating'
                  AND COLUMN_NAME = 'score'
                  AND COLUMN_COMMENT LIKE '%10 分%');

SET @fail_cnt = (@tbl_exists = 1)
    + (@col_cnt = 6)
    + (@uk_cnt = 1)
    + (@collation_ok = 1)
    + (@cmt_ok = 1);
SET @fail_cnt = 5 - @fail_cnt;

SELECT 'CHECK' AS check_result,
       CONCAT('table=', @tbl_exists,
              ', columns=', @col_cnt,
              ', uk_work_user=', @uk_cnt,
              ', collation=', @collation_ok,
              ', score_10pt=', @cmt_ok) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('建表未达预期：table=', @tbl_exists,
              ', columns=', @col_cnt,
              ', uk_work_user=', @uk_cnt,
              ', collation=', @collation_ok) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, sys_work_rating 已就绪（一人一评 + 唯一键）' AS detail
WHERE @fail_cnt = 0;
