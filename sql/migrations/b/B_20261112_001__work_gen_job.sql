-- 作品批量生成任务（后台队列）
-- 用途：点一次提交后，后端按集串行生成、逐集落库，页面关闭/断网不影响；
--       episodes 保存整条队列的每集状态（JSON），据此断点续跑。
CREATE TABLE IF NOT EXISTS `sys_work_gen_job` (
  `job_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `user_id` bigint NOT NULL COMMENT '提交用户ID',
  `status` varchar(16) NOT NULL DEFAULT 'queued' COMMENT 'queued/running/paused/succeeded/failed/cancelled',
  `total` int NOT NULL DEFAULT 0 COMMENT '待生成集数',
  `done_count` int NOT NULL DEFAULT 0 COMMENT '已完成集数',
  `current_no` int DEFAULT NULL COMMENT '正在生成的集号',
  `current_title` varchar(200) DEFAULT NULL COMMENT '正在生成的集名',
  `outline` mediumtext COMMENT '全剧事件卡',
  `characters` mediumtext COMMENT '人物设定（注入用）',
  `roster` text COMMENT '人物白名单（顿号分隔）',
  `episodes` longtext COMMENT '集队列 JSON：[{no,title,card,status,chapterId,error,attempts}]',
  `last_error` varchar(1000) DEFAULT NULL COMMENT '最近一次失败原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`job_id`),
  KEY `idx_work_id` (`work_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品批量生成任务（后台队列）';
