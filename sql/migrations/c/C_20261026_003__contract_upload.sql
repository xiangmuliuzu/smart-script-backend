-- 文件: C_20261026_003__contract_upload.sql
-- 用途: 合同支持"上传 PDF 自助签章"——新增来源类型、上传 PDF 文件地址、页数与买卖双方各自的签章定位 JSON。
--       source_type=template 走平台模板（可编辑条款、系统固定位落章）；uploaded 走上传 PDF（不可在线编辑、签署方逐页拖拽定位）。
-- 依赖表: sys_contract（copyright_settlement_finance.sql 创建；C_20261020_001 补签名/印章/签署中PDF列；C_20261026_002 补 clauses_text）
-- 执行库: script_platform_dev
-- 幂等: 全部经 information_schema 守卫，列存在则跳过，可重复执行。

-- sys_contract：合同来源类型（template/uploaded），默认 template 兼容存量数据
SET @sql := IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_contract' AND COLUMN_NAME = 'source_type'),
    'ALTER TABLE sys_contract ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT ''template'' COMMENT ''合同来源: template平台模板/uploaded用户上传PDF'' AFTER template_id',
    'SELECT ''column sys_contract.source_type already exists, skipped'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_contract：上传合同 PDF 文件 URL（/profile 静态可访问）
SET @sql := IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_contract' AND COLUMN_NAME = 'source_pdf_url'),
    'ALTER TABLE sys_contract ADD COLUMN source_pdf_url VARCHAR(500) DEFAULT NULL COMMENT ''上传合同PDF文件URL'' AFTER signed_pdf_url',
    'SELECT ''column sys_contract.source_pdf_url already exists, skipped'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_contract：上传 PDF 页数（逐页渲染供前端翻页定位）
SET @sql := IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_contract' AND COLUMN_NAME = 'pdf_page_count'),
    'ALTER TABLE sys_contract ADD COLUMN pdf_page_count INT DEFAULT NULL COMMENT ''上传PDF页数'' AFTER source_pdf_url',
    'SELECT ''column sys_contract.pdf_page_count already exists, skipped'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_contract：买方签章定位 JSON 数组 [{kind,page,x,y,w,h}]（比例坐标，仅 uploaded 使用）
SET @sql := IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_contract' AND COLUMN_NAME = 'buyer_sign_pos'),
    'ALTER TABLE sys_contract ADD COLUMN buyer_sign_pos TEXT DEFAULT NULL COMMENT ''买方签章定位JSON(上传件拖拽坐标)'' AFTER seller_seal_image',
    'SELECT ''column sys_contract.buyer_sign_pos already exists, skipped'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sys_contract：卖方签章定位 JSON 数组（结构同买方）
SET @sql := IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_contract' AND COLUMN_NAME = 'seller_sign_pos'),
    'ALTER TABLE sys_contract ADD COLUMN seller_sign_pos TEXT DEFAULT NULL COMMENT ''卖方签章定位JSON(上传件拖拽坐标)'' AFTER buyer_sign_pos',
    'SELECT ''column sys_contract.seller_sign_pos already exists, skipped'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 校验：五列均应在 sys_contract 上可见（预期 5 行）
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_contract'
  AND COLUMN_NAME IN ('source_type','source_pdf_url','pdf_page_count','buyer_sign_pos','seller_sign_pos')
ORDER BY ORDINAL_POSITION;
