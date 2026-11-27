-- =====================================================================
-- B 模块内容 · 创作台账表 + 章节摘要字段
--
-- 背景：大纲与正文不连续，需要把人物卡、关系状态、伏笔、时间线、物品位置、
--   未解线索等跨章事实落库（原先只存在 PC 浏览器 localStorage），
--   同时给每集（=章）保存一条摘要，供后续章节续写注入前情。
--
-- 处理口径：
--   1. 新建 sys_work_ledger，一作品一条；七类台账均为自由文本，后端只存取不解析。
--   2. sys_work_chapter 增加 summary varchar(500)，与 content/word_count 同级。
--   3. 两步均可重复执行（建表 IF NOT EXISTS，加列前查 information_schema）。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261110_001__work_ledger_and_chapter_summary.sql"
--       verify 闸门要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 作品创作台账表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_work_ledger (
    work_id       BIGINT       NOT NULL                COMMENT '作品ID',
    story_summary TEXT         NULL                    COMMENT '故事简介',
    characters    MEDIUMTEXT   NULL                    COMMENT '主要人物卡',
    relations     MEDIUMTEXT   NULL                    COMMENT '人物关系当前状态（含上次变化原因）',
    foreshadow    MEDIUMTEXT   NULL                    COMMENT '伏笔台账（埋在哪章/计划回收章/是否已回收）',
    timeline      MEDIUMTEXT   NULL                    COMMENT '时间线',
    items         MEDIUMTEXT   NULL                    COMMENT '物品位置',
    threads       MEDIUMTEXT   NULL                    COMMENT '未解线索',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (work_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作品创作台账（跨端共享）';

-- ---------------------------------------------------------------------
-- 2. 章节摘要字段（幂等：已存在则跳过）
-- ---------------------------------------------------------------------
SET @has_summary = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_work_chapter'
      AND column_name = 'summary'
);

SET @ddl = IF(@has_summary = 0,
    'ALTER TABLE sys_work_chapter ADD COLUMN summary varchar(500) NULL COMMENT ''章节摘要（只记本集推进了什么）'' AFTER word_count',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. verify 闸门
-- ---------------------------------------------------------------------
SET @table_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_work_ledger'
);
SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_work_chapter' AND column_name = 'summary'
);
SET @fail_cnt = IF(@table_exists = 1 AND @column_exists = 1, 0, 1);

SELECT 'CHECK' AS check_result,
       CONCAT('ledger_table=', @table_exists, ', summary_column=', @column_exists) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('台账表或摘要字段未建成') AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, sys_work_ledger 与 sys_work_chapter.summary 均已就绪') AS detail
WHERE @fail_cnt = 0;
