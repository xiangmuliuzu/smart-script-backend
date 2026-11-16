-- For isolated sandbox databases, or a development database explicitly authorized via APP_ALIPAY_SANDBOX_ALLOWED_DATABASES.
CREATE TABLE IF NOT EXISTS sandbox_alipay_payment (
  order_id BIGINT NOT NULL,
  out_trade_no VARCHAR(64) NOT NULL,
  buyer_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  trade_no VARCHAR(64) DEFAULT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (order_id),
  UNIQUE KEY uk_sandbox_alipay_trade (out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付宝沙箱支付记录';
