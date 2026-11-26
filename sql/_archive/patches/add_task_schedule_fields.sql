-- 福利任务补充：生效日期 + 每日领取上限
ALTER TABLE sys_task
  ADD COLUMN start_time DATETIME NULL COMMENT '生效开始时间(空=立即)' AFTER icon,
  ADD COLUMN end_time   DATETIME NULL COMMENT '生效结束时间(空=长期)' AFTER start_time,
  ADD COLUMN daily_limit INT NOT NULL DEFAULT 0 COMMENT '每日可领取次数上限(0=不限)' AFTER end_time;

-- 补齐 NOT NULL 字段默认值，防止插入时漏字段报错
ALTER TABLE sys_task MODIFY COLUMN description varchar(255) NOT NULL DEFAULT '';
ALTER TABLE sys_task MODIFY COLUMN target_count int NOT NULL DEFAULT 1;
ALTER TABLE sys_task MODIFY COLUMN reward_points int NOT NULL DEFAULT 0;
ALTER TABLE sys_task MODIFY COLUMN sort int NOT NULL DEFAULT 0;
ALTER TABLE sys_task MODIFY COLUMN status tinyint NOT NULL DEFAULT 1;

