-- 分镜复用作品的 AI 初审规则、评分结果和问题定位字段。
-- 规则来源仍为启用中的 AI 审核规则与敏感词库，不新增一套分镜规则。
-- 先选择后端实例实际连接的库，并确认已创建 sys_work_storyboard。
-- 按列补齐，可在部分字段已存在的库上安全重跑，不修改已有列或历史数据。

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

SELECT COUNT(CASE WHEN COLUMN_NAME = 'ai_result' THEN 1 END),
       COUNT(CASE WHEN COLUMN_NAME = 'ai_risk_level' THEN 1 END),
       COUNT(CASE WHEN COLUMN_NAME = 'ai_sensitive_words' THEN 1 END),
       COUNT(CASE WHEN COLUMN_NAME = 'ai_start_time' THEN 1 END),
       COUNT(CASE WHEN COLUMN_NAME = 'ai_end_time' THEN 1 END)
INTO @has_result, @has_risk, @has_words, @has_start, @has_end
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_work_storyboard';

SET @storyboard_add := CONCAT_WS(', ',
  IF(@has_result = 0, 'ADD COLUMN ai_result mediumtext NULL COMMENT ''AI审核结果JSON，与作品审核格式一致''', NULL),
  IF(@has_risk = 0, 'ADD COLUMN ai_risk_level varchar(20) NULL COMMENT ''AI风险等级：low/medium/high''', NULL),
  IF(@has_words = 0, 'ADD COLUMN ai_sensitive_words varchar(1000) NULL COMMENT ''AI命中的敏感词，逗号分隔''', NULL),
  IF(@has_start = 0, 'ADD COLUMN ai_start_time datetime NULL COMMENT ''AI审核开始时间''', NULL),
  IF(@has_end = 0, 'ADD COLUMN ai_end_time datetime NULL COMMENT ''AI审核结束时间''', NULL)
);
SET @storyboard_ddl := IF(@storyboard_add = '',
  'SELECT ''分镜 AI 审核字段已齐全'' AS migration_result',
  CONCAT('ALTER TABLE sys_work_storyboard ', @storyboard_add));
PREPARE storyboard_stmt FROM @storyboard_ddl;
EXECUTE storyboard_stmt;
DEALLOCATE PREPARE storyboard_stmt;

SELECT TABLE_SCHEMA AS database_name, COUNT(*) AS storyboard_ai_columns
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_work_storyboard'
  AND COLUMN_NAME IN ('ai_result', 'ai_risk_level', 'ai_sensitive_words', 'ai_start_time', 'ai_end_time')
GROUP BY TABLE_SCHEMA;
