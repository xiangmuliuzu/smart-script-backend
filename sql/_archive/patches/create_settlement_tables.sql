-- 版权结算与财务异常表结构

-- 1. 结算单主表
CREATE TABLE IF NOT EXISTS sys_settlement (
  settlement_id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  settlement_no VARCHAR(32) NOT NULL COMMENT 'Settlement Number',
  author_id BIGINT NOT NULL COMMENT 'Author User ID',
  period_start DATE NOT NULL COMMENT 'Period Start',
  period_end DATE NOT NULL COMMENT 'Period End',
  order_count INT NOT NULL DEFAULT 0 COMMENT 'Order Count',
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Total Amount',
  platform_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Platform Amount',
  author_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT 'Author Amount',
  platform_ratio INT NOT NULL DEFAULT 20 COMMENT 'Platform Ratio',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'Status: pending/settled/abnormal',
  settlement_time DATETIME DEFAULT NULL COMMENT 'Settlement Time',
  abnormal_reason VARCHAR(500) DEFAULT NULL COMMENT 'Abnormal Reason',
  handle_remark VARCHAR(1000) DEFAULT NULL COMMENT 'Handle Remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  create_time DATETIME DEFAULT NULL,
  update_by VARCHAR(64) DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  remark VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (settlement_id),
  UNIQUE KEY uk_settlement_no (settlement_no),
  KEY idx_author_id (author_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Settlement Table';

-- 2. 财务异常统一表
CREATE TABLE IF NOT EXISTS sys_finance_abnormal (
  abnormal_id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
  abnormal_type VARCHAR(20) NOT NULL COMMENT 'Type: duplicate/refund/escrow/settlement',
  related_no VARCHAR(32) NOT NULL COMMENT 'Related Number',
  order_id BIGINT DEFAULT NULL COMMENT 'Order ID',
  user_id BIGINT DEFAULT NULL COMMENT 'User ID',
  abnormal_amount DECIMAL(12,2) DEFAULT NULL COMMENT 'Amount',
  abnormal_reason VARCHAR(500) DEFAULT NULL COMMENT 'Reason',
  abnormal_desc VARCHAR(500) DEFAULT NULL COMMENT 'Description',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'Status: pending/handled',
  handle_solution VARCHAR(50) DEFAULT NULL COMMENT 'Solution',
  handle_remark VARCHAR(1000) DEFAULT NULL COMMENT 'Handle Remark',
  handle_by VARCHAR(64) DEFAULT NULL COMMENT 'Handler',
  handle_time DATETIME DEFAULT NULL COMMENT 'Handle Time',
  duplicate_count INT DEFAULT NULL COMMENT 'Duplicate Count',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (abnormal_id),
  KEY idx_type (abnormal_type),
  KEY idx_related_no (related_no),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Finance Abnormal Table';
