-- =====================================================================
-- B 模块内容 · 卷级状态快照表 + 每集验收表
--
-- 背景：50 集量级下，台账逐集追加会被注入预算截断，最近状态反而丢失；
--   需要在每 10 集（一卷）末尾压出一份「当前值快照」，写正文时注入
--   「本卷快照 + 上卷快照」代替整份台账。
--   同时正文生成后需要一次机器验收（人物/因果/连续性/伏笔/结构），
--   不通过时提示重生成，避免漂移静默累积。
--
-- 处理口径：
--   1. 新建 sys_work_volume，一作品一卷一条，snapshot 为覆盖式当前状态。
--   2. 新建 sys_chapter_review，一集一条，存 5 项验收结果。
--   3. 两步均可重复执行（IF NOT EXISTS）。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261111_001__volume_snapshot_and_chapter_review.sql"
--       verify 闸门要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 卷级状态快照（每 10 集一卷，覆盖写，不追加历史）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_work_volume (
    work_id       BIGINT       NOT NULL                COMMENT '作品ID',
    volume_no     INT          NOT NULL                COMMENT '卷号（第N卷）',
    episode_start INT          NOT NULL                COMMENT '起始集号',
    episode_end   INT          NOT NULL                COMMENT '结束集号',
    snapshot      MEDIUMTEXT   NULL                    COMMENT '卷级当前状态快照（人物当前态/未回收伏笔/道具归属/卷末悬念）',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (work_id, volume_no),
    KEY idx_work_volume (work_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作品卷级状态快照（写正文时注入）';

-- ---------------------------------------------------------------------
-- 2. 每集验收结果（一集一条，重跑覆盖）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_chapter_review (
    chapter_id    BIGINT       NOT NULL                COMMENT '章节ID',
    work_id       BIGINT       NOT NULL                COMMENT '作品ID',
    episode_no    INT          NULL                    COMMENT '集号',
    pass_flag     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否通过（1通过 0需重写）',
    score         INT          NULL                    COMMENT '通过项数 0-5',
    issues        TEXT         NULL                    COMMENT '未通过项明细（一行一项）',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (chapter_id),
    KEY idx_work_review (work_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '每集连贯性验收结果';

-- ---------------------------------------------------------------------
-- 3. verify 闸门
-- ---------------------------------------------------------------------
SET @volume_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_work_volume'
);
SET @review_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_chapter_review'
);
SET @fail_cnt = IF(@volume_exists = 1 AND @review_exists = 1, 0, 1);

SELECT 'CHECK' AS check_result,
       CONCAT('volume_table=', @volume_exists, ', review_table=', @review_exists) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('卷快照表或验收表未建成') AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, sys_work_volume 与 sys_chapter_review 均已就绪') AS detail
WHERE @fail_cnt = 0;
