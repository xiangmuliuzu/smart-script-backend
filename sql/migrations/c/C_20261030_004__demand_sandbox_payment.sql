-- =============================================================================
-- C_20261030_004  需求任务支付宝沙箱收款记录表 demand_sandbox_payment
--   与队友订单沙箱表 sandbox_alipay_payment 并存、互不影响。
--   仅记录入账（托管 escrow / 定金 deposit）两笔真实沙箱收款。
--   For isolated sandbox databases, or a dev db authorized via APP_ALIPAY_SANDBOX_ALLOWED_DATABASES.
-- =============================================================================

CREATE TABLE IF NOT EXISTS demand_sandbox_payment (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  demand_id    BIGINT NOT NULL COMMENT '需求任务ID',
  purpose      VARCHAR(16) NOT NULL COMMENT '收款用途: escrow 托管 | deposit 定金',
  payer_id     BIGINT NOT NULL COMMENT '付款人用户ID(发布者/接单人)',
  amount       DECIMAL(12,2) NOT NULL COMMENT '收款金额(元)',
  out_trade_no VARCHAR(64) NOT NULL COMMENT '商户订单号(SDESC/SDDEP前缀)',
  trade_no     VARCHAR(64) DEFAULT NULL COMMENT '支付宝交易号',
  status       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|PAID|CLOSED',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_demand_purpose (demand_id, purpose),
  UNIQUE KEY uk_demand_out_trade_no (out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求任务支付宝沙箱收款记录';
