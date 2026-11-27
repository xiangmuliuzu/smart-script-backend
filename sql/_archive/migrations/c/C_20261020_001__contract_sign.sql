-- =====================================================================
-- C_20261020_001  合同签署：为 sys_contract 增加签名/印章图片列
-- 幂等：ALTER TABLE ADD COLUMN 先判列是否存在
-- =====================================================================

-- 买方手写签名图片 URL
SET @col = 'buyer_signature_image';
SET @sql = CONCAT('ALTER TABLE sys_contract ADD COLUMN ', @col,
  ' VARCHAR(500) DEFAULT NULL COMMENT ''买方手写签名图片URL''');
SELECT COUNT(*) INTO @exists FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_contract' AND column_name = @col;
SET @sql = IF(@exists = 0, @sql, 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 买方印章图片 URL
SET @col = 'buyer_seal_image';
SET @sql = CONCAT('ALTER TABLE sys_contract ADD COLUMN ', @col,
  ' VARCHAR(500) DEFAULT NULL COMMENT ''买方印章图片URL''');
SELECT COUNT(*) INTO @exists FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_contract' AND column_name = @col;
SET @sql = IF(@exists = 0, @sql, 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 卖方手写签名图片 URL
SET @col = 'seller_signature_image';
SET @sql = CONCAT('ALTER TABLE sys_contract ADD COLUMN ', @col,
  ' VARCHAR(500) DEFAULT NULL COMMENT ''卖方手写签名图片URL''');
SELECT COUNT(*) INTO @exists FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_contract' AND column_name = @col;
SET @sql = IF(@exists = 0, @sql, 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 卖方印章图片 URL
SET @col = 'seller_seal_image';
SET @sql = CONCAT('ALTER TABLE sys_contract ADD COLUMN ', @col,
  ' VARCHAR(500) DEFAULT NULL COMMENT ''卖方印章图片URL''');
SELECT COUNT(*) INTO @exists FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_contract' AND column_name = @col;
SET @sql = IF(@exists = 0, @sql, 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 签署完成后合成的 PDF URL
SET @col = 'signed_pdf_url';
SET @sql = CONCAT('ALTER TABLE sys_contract ADD COLUMN ', @col,
  ' VARCHAR(500) DEFAULT NULL COMMENT ''签署完成合成PDF文件URL''');
SELECT COUNT(*) INTO @exists FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'sys_contract' AND column_name = @col;
SET @sql = IF(@exists = 0, @sql, 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
