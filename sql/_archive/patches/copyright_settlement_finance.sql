-- =====================================================================
-- 版权结算与财务异常表结构
-- =====================================================================

-- 1. 合同表（补充，ContractManageService已在使用但表未定义）
CREATE TABLE IF NOT EXISTS sys_contract (
  contract_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '合同ID',
  contract_no VARCHAR(32) NOT NULL COMMENT '合同编号',
  contract_type VARCHAR(20) NOT NULL COMMENT '合同类型: transfer转让/license授权',
  template_id VARCHAR(50) NOT NULL COMMENT '模板ID',
  amount DECIMAL(12,2) NOT NULL COMMENT '合同金额',
  order_id BIGINT NOT NULL COMMENT '关联订单ID',
  buyer_id BIGINT NOT NULL COMMENT '买方用户ID',
  seller_id BIGINT NOT NULL COMMENT '卖方用户ID',
  buyer_sign_status TINYINT DEFAULT 0 COMMENT '买方签署状态: 0未签/1已签',
  seller_sign_status TINYINT DEFAULT 0 COMMENT '卖方签署状态: 0未签/1已签',
  buyer_sign_time DATETIME DEFAULT NULL COMMENT '买方签署时间',
  seller_sign_time DATETIME DEFAULT NULL COMMENT '卖方签署时间',
  status VARCHAR(20) NOT NULL COMMENT '合同状态: pending待签/partial_signed部分签署/completed已完成/archived已归档/cancelled已作废',
  contract_content TEXT COMMENT '合同内容HTML',
  contract_url VARCHAR(500) DEFAULT '' COMMENT '合同文件URL',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  create_time DATETIME DEFAULT NULL COMMENT '创建时间（若依标准）',
  update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间（若依标准）',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (contract_id),
  UNIQUE KEY uk_contract_no (contract_no),
  KEY idx_order_id (order_id),
  KEY idx_buyer_id (buyer_id),
  KEY idx_seller_id (seller_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版权合同';

-- 2. 结算单主表
CREATE TABLE IF NOT EXISTS sys_settlement (
  settlement_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '结算ID',
  settlement_no VARCHAR(32) NOT NULL COMMENT '结算单号',
  author_id BIGINT NOT NULL COMMENT '作者用户ID',
  period_start DATE NOT NULL COMMENT '结算周期开始日期',
  period_end DATE NOT NULL COMMENT '结算周期结束日期',
  order_count INT NOT NULL DEFAULT 0 COMMENT '订单数量',
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '总交易金额',
  platform_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '平台分成金额',
  author_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '作者应得金额',
  platform_ratio INT NOT NULL DEFAULT 20 COMMENT '平台分成比例(百分比)',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '结算状态: pending待结算/settled已结算/abnormal异常',
  settlement_time DATETIME DEFAULT NULL COMMENT '结算完成时间',
  abnormal_reason VARCHAR(500) DEFAULT NULL COMMENT '异常原因',
  handle_remark VARCHAR(1000) DEFAULT NULL COMMENT '处理说明',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  create_time DATETIME DEFAULT NULL COMMENT '创建时间（若依标准）',
  update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间（若依标准）',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (settlement_id),
  UNIQUE KEY uk_settlement_no (settlement_no),
  KEY idx_author_id (author_id),
  KEY idx_period (period_start, period_end),
  KEY idx_status (status),
  KEY idx_settlement_time (settlement_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算单主表';

-- 3. 结算明细表（关联订单）
CREATE TABLE IF NOT EXISTS sys_settlement_detail (
  detail_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  settlement_id BIGINT NOT NULL COMMENT '结算单ID',
  order_id BIGINT NOT NULL COMMENT '订单ID',
  order_no VARCHAR(32) NOT NULL COMMENT '订单号',
  work_title VARCHAR(200) DEFAULT NULL COMMENT '作品标题',
  order_amount DECIMAL(12,2) NOT NULL COMMENT '订单金额',
  author_income DECIMAL(12,2) NOT NULL COMMENT '作者收入',
  order_time DATETIME NOT NULL COMMENT '订单时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (detail_id),
  KEY idx_settlement_id (settlement_id),
  KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算明细';

-- 4. 财务异常 - 重复支付
CREATE TABLE IF NOT EXISTS sys_finance_duplicate_payment (
  duplicate_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  order_no VARCHAR(32) NOT NULL COMMENT '订单号',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  user_name VARCHAR(100) DEFAULT NULL COMMENT '用户名',
  duplicate_count INT NOT NULL DEFAULT 2 COMMENT '重复次数',
  duplicate_amount DECIMAL(12,2) NOT NULL COMMENT '重复金额',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending待处理/handled已处理',
  solution VARCHAR(50) DEFAULT NULL COMMENT '处理方案: refund退款/manual人工',
  handle_remark VARCHAR(1000) DEFAULT NULL COMMENT '处理备注',
  handle_time DATETIME DEFAULT NULL COMMENT '处理时间',
  handle_by VARCHAR(64) DEFAULT NULL COMMENT '处理人',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (duplicate_id),
  KEY idx_order_no (order_no),
  KEY idx_user_id (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='重复支付异常';

-- 5. 财务异常 - 退款异常
CREATE TABLE IF NOT EXISTS sys_finance_refund_abnormal (
  refund_abnormal_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  refund_no VARCHAR(32) NOT NULL COMMENT '退款单号',
  order_no VARCHAR(32) NOT NULL COMMENT '订单号',
  refund_amount DECIMAL(12,2) NOT NULL COMMENT '退款金额',
  abnormal_reason VARCHAR(500) NOT NULL COMMENT '异常原因',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending待处理/handled已处理',
  solution VARCHAR(50) DEFAULT NULL COMMENT '处理方案',
  handle_remark VARCHAR(1000) DEFAULT NULL COMMENT '处理备注',
  handle_time DATETIME DEFAULT NULL COMMENT '处理时间',
  handle_by VARCHAR(64) DEFAULT NULL COMMENT '处理人',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (refund_abnormal_id),
  KEY idx_refund_no (refund_no),
  KEY idx_order_no (order_no),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款异常';

-- 6. 财务异常 - 托管异常
CREATE TABLE IF NOT EXISTS sys_finance_escrow_abnormal (
  escrow_abnormal_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  escrow_no VARCHAR(32) NOT NULL COMMENT '托管单号',
  order_no VARCHAR(32) NOT NULL COMMENT '订单号',
  escrow_amount DECIMAL(12,2) NOT NULL COMMENT '托管金额',
  abnormal_type VARCHAR(50) NOT NULL COMMENT '异常类型: timeout超时/release_fail释放失败',
  abnormal_desc VARCHAR(500) DEFAULT NULL COMMENT '异常描述',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending待处理/handled已处理',
  solution VARCHAR(50) DEFAULT NULL COMMENT '处理方案',
  handle_remark VARCHAR(1000) DEFAULT NULL COMMENT '处理备注',
  handle_time DATETIME DEFAULT NULL COMMENT '处理时间',
  handle_by VARCHAR(64) DEFAULT NULL COMMENT '处理人',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (escrow_abnormal_id),
  KEY idx_escrow_no (escrow_no),
  KEY idx_order_no (order_no),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='托管异常';

-- 7. 财务异常 - 结算异常
CREATE TABLE IF NOT EXISTS sys_finance_settlement_abnormal (
  settlement_abnormal_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  settlement_no VARCHAR(32) NOT NULL COMMENT '结算单号',
  settlement_id BIGINT DEFAULT NULL COMMENT '关联结算单ID',
  author_id BIGINT NOT NULL COMMENT '作者ID',
  author_name VARCHAR(100) DEFAULT NULL COMMENT '作者名',
  settlement_amount DECIMAL(12,2) NOT NULL COMMENT '结算金额',
  abnormal_reason VARCHAR(500) NOT NULL COMMENT '异常原因',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending待处理/handled已处理',
  solution VARCHAR(50) DEFAULT NULL COMMENT '处理方案',
  handle_remark VARCHAR(1000) DEFAULT NULL COMMENT '处理备注',
  handle_time DATETIME DEFAULT NULL COMMENT '处理时间',
  handle_by VARCHAR(64) DEFAULT NULL COMMENT '处理人',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (settlement_abnormal_id),
  KEY idx_settlement_no (settlement_no),
  KEY idx_settlement_id (settlement_id),
  KEY idx_author_id (author_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算异常';
