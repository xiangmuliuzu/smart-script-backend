-- 作品时间线事件（只追加）
--
-- 用途：每集写完，由 /ai/chapter-summary 的同一次模型调用一并产出 0~3 条重要事件，
-- 按 chapter_no 排序存档；续写前按「最近事件 + 全部未兑现」注入提示词，
-- 后文填坑时可按集号、人物、道具反查当初埋下的约定与伏笔。
--
-- 约束：历史集事件不可改；整集重写时只允许先删该集事件再重建（deleteByChapter）。

CREATE TABLE IF NOT EXISTS sys_work_timeline_event (
    event_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    work_id       BIGINT       NOT NULL COMMENT '作品ID',
    chapter_no    INT          NOT NULL COMMENT '埋在哪一集（排序与检索主键）',
    seq           INT          NOT NULL DEFAULT 1 COMMENT '同集内序号',
    event_type    VARCHAR(20)  NOT NULL DEFAULT '其他'
                  COMMENT '约定/伏笔/道具转移/身份变化/关系变化/生死退场/能力突破/信息揭示/期限/其他',
    fact          VARCHAR(255) NOT NULL COMMENT '一句话事实：谁+做了什么+条件或代价',
    actors        VARCHAR(255) DEFAULT NULL COMMENT '涉及人物，顿号分隔',
    props         VARCHAR(255) DEFAULT NULL COMMENT '涉及道具或线索，顿号分隔',
    status        VARCHAR(20)  NOT NULL DEFAULT '未兑现' COMMENT '未兑现/已兑现/已失效',
    deadline_no   INT          DEFAULT NULL COMMENT '计划兑现集号',
    story_time    VARCHAR(60)  DEFAULT NULL COMMENT '故事内时间（自由文本，不参与排序）',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_work_chapter_seq (work_id, chapter_no, seq),
    KEY idx_work_status (work_id, status),
    KEY idx_work_chapter (work_id, chapter_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品时间线事件（只追加）';
