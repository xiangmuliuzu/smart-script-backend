-- 分镜复用作品的 AI 初审规则、评分结果和问题定位字段。
-- 规则来源仍为启用中的 AI 审核规则与敏感词库，不新增一套分镜规则。

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

ALTER TABLE sys_work_storyboard
  ADD COLUMN ai_result mediumtext NULL COMMENT 'AI审核结果JSON，与作品审核格式一致' AFTER reject_reason,
  ADD COLUMN ai_risk_level varchar(20) NULL COMMENT 'AI风险等级：low/medium/high' AFTER ai_result,
  ADD COLUMN ai_sensitive_words varchar(1000) NULL COMMENT 'AI命中的敏感词，逗号分隔' AFTER ai_risk_level,
  ADD COLUMN ai_start_time datetime NULL COMMENT 'AI审核开始时间' AFTER ai_sensitive_words,
  ADD COLUMN ai_end_time datetime NULL COMMENT 'AI审核结束时间' AFTER ai_start_time;
