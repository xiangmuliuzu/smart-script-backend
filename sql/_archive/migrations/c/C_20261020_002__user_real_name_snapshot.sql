-- =====================================================================
-- C_20261020_002  合同签署：实名通过后的明文姓名快照列（sys_user.real_name）
-- 背景：合同正文与签署 PDF 需要甲乙方真实姓名；实名模块掩码链
--       （user_real_name_auth.real_name_mask）按隐私边界不存明文，
--       故实名核验通过时把明文姓名快照写入本列，仅供合同签署域渲染。
--       证件号明文仍然不落库。
-- 幂等：ALTER TABLE ADD COLUMN 先判列是否存在
-- =====================================================================

SET @col = 'real_name';
SET @sql = CONCAT('ALTER TABLE sys_user ADD COLUMN ', @col,
  ' VARCHAR(64) DEFAULT NULL COMMENT ''实名认证通过后的真实姓名快照（合同签署域使用）''');
SELECT COUNT(*) INTO @exists FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = @col;
SET @sql = IF(@exists = 0, @sql, 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
