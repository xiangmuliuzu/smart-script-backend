-- B 模块：作品分镜目录
-- 分镜与章节同级，但每条分镜必须关联当前作品已有章节；保存后进入 pending 审核状态。
-- 已有环境执行本迁移；新环境也可重复执行。

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS sys_work_storyboard (
  `storyboard_id` bigint NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `chapter_id` bigint NOT NULL,
  `chapter_title` varchar(100) NOT NULL,
  `content` mediumtext NOT NULL,
  `word_count` int NOT NULL DEFAULT 0,
  `source` varchar(20) NOT NULL DEFAULT 'manual' COMMENT 'manual=手动 ai=AI生成',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending=待审核 approved=通过 rejected=驳回',
  `reviewer_id` bigint DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`storyboard_id`),
  UNIQUE KEY `uk_work_storyboard_chapter` (`work_id`,`chapter_id`),
  KEY `idx_storyboard_work` (`work_id`),
  KEY `idx_storyboard_chapter` (`chapter_id`),
  KEY `idx_storyboard_status` (`status`),
  KEY `idx_storyboard_author_time` (`work_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作品分镜目录';
