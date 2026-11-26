-- =====================================================================
-- C_20261026_002  合同内容可编辑：为 sys_contract 增加正文条款纯文本列
-- 幂等：ALTER TABLE ADD COLUMN 先判列是否存在
-- clauses_text 存放可编辑的正文条款（第一条~第六条区段）纯文本，
-- 换行分段、以「第…条 」识别标题行；null 时渲染回退模板默认条款。
-- =====================================================================

SET @col = 'clauses_text';
SET @sql = CONCAT('ALTER TABLE sys_contract ADD COLUMN ', @col,
  ' TEXT DEFAULT NULL COMMENT ''可编辑的正文条款纯文本(null=模板默认)''');
SELECT COUNT(*) INTO @exists FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_contract' AND column_name = @col;
SET @sql = IF(@exists = 0, @sql, 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
