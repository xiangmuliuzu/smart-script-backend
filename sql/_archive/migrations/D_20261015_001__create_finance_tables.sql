-- =============================================================================
-- D_20261015_001  财务域建表：资金托管 / 发票 / 收益账户 / 账单流水 / 作者统计
-- 作者：C 模块（暂代 D 模块财务部分前端）
-- 说明：5 张业务表 + 1 个作者收益视图
-- =============================================================================

-- ---------- 1. 资金托管表 ----------
CREATE TABLE IF NOT EXISTS sys_escrow (
  escrow_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '托管ID',
  escrow_no      VARCHAR(32)  NOT NULL COMMENT '托管单号',
  order_id       BIGINT       NOT NULL COMMENT '关联订单ID',
  amount         DECIMAL(12,2) NOT NULL COMMENT '托管金额（元）',
  paid_at        DATETIME     NULL     COMMENT '支付时间',
  pay_channel    VARCHAR(20)  NULL     COMMENT '支付渠道（alipay/wechat/bank）',
  status         VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态（pending/escrowed/released/refunded）',
  release_time   DATETIME     NULL     COMMENT '释放时间',
  release_reason VARCHAR(255) NULL     COMMENT '释放原因',
  refund_amount  DECIMAL(12,2) NULL    COMMENT '退款金额',
  refunded_at    DATETIME     NULL     COMMENT '退款时间',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by      VARCHAR(64)  NULL     COMMENT '创建者',
  create_time    DATETIME     NULL     COMMENT '创建时间',
  update_by      VARCHAR(64)  NULL     COMMENT '更新者',
  update_time    DATETIME     NULL     COMMENT '更新时间',
  remark         VARCHAR(500) NULL     COMMENT '备注',
  PRIMARY KEY (escrow_id),
  UNIQUE KEY uk_escrow_no (escrow_no),
  KEY idx_order_id (order_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金托管表';

-- ---------- 2. 发票表 ----------
CREATE TABLE IF NOT EXISTS sys_invoice (
  invoice_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '发票ID',
  invoice_no     VARCHAR(32)  NOT NULL COMMENT '发票编号',
  order_id       BIGINT       NOT NULL COMMENT '关联订单ID',
  applicant_id   BIGINT       NOT NULL COMMENT '申请人ID',
  invoice_type   VARCHAR(20)  NOT NULL COMMENT '发票类型（normal/special）',
  email          VARCHAR(100) NULL     COMMENT '接收邮箱',
  title          VARCHAR(200) NOT NULL COMMENT '发票抬头',
  tax_no         VARCHAR(50)  NULL     COMMENT '税号',
  amount         DECIMAL(12,2) NOT NULL COMMENT '发票金额（元）',
  invoice_url    VARCHAR(500) NULL     COMMENT '发票文件URL',
  status         VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态（pending/approved/rejected/issued）',
  reject_reason  VARCHAR(255) NULL     COMMENT '拒绝原因',
  invoice_time   DATETIME     NULL     COMMENT '开票时间',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by      VARCHAR(64)  NULL     COMMENT '创建者',
  create_time    DATETIME     NULL     COMMENT '创建时间',
  update_by      VARCHAR(64)  NULL     COMMENT '更新者',
  update_time    DATETIME     NULL     COMMENT '更新时间',
  remark         VARCHAR(500) NULL     COMMENT '备注',
  PRIMARY KEY (invoice_id),
  UNIQUE KEY uk_invoice_no (invoice_no),
  KEY idx_order_id (order_id),
  KEY idx_applicant_id (applicant_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票表';

-- ---------- 3. 作者收益账户 ----------
CREATE TABLE IF NOT EXISTS sys_income_account (
  account_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '账户ID',
  user_id          BIGINT       NOT NULL COMMENT '作者用户ID',
  available_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '可提现余额',
  frozen_amount    DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '冻结金额',
  settled_amount   DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已结算金额',
  version          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (account_id),
  UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作者收益账户';

-- ---------- 4. 账单流水表 ----------
CREATE TABLE IF NOT EXISTS sys_bill_record (
  bill_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  bill_no       VARCHAR(32)  NOT NULL COMMENT '流水编号',
  user_id       BIGINT       NOT NULL COMMENT '用户ID',
  bill_type     VARCHAR(20)  NOT NULL COMMENT '类型（income/withdraw/freeze/settle）',
  amount        DECIMAL(12,2) NOT NULL COMMENT '金额（正=入，负=出）',
  balance_after DECIMAL(12,2) NOT NULL COMMENT '变动后余额',
  related_type  VARCHAR(20)  NULL     COMMENT '关联业务类型（order/withdraw）',
  related_id    BIGINT       NULL     COMMENT '关联业务ID',
  remark        VARCHAR(255) NULL     COMMENT '备注',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_by     VARCHAR(64)  NULL     COMMENT '创建者',
  create_time   DATETIME     NULL     COMMENT '创建时间',
  update_by     VARCHAR(64)  NULL     COMMENT '更新者',
  update_time   DATETIME     NULL     COMMENT '更新时间',
  PRIMARY KEY (bill_id),
  UNIQUE KEY uk_bill_no (bill_no),
  KEY idx_user_id (user_id),
  KEY idx_bill_type (bill_type),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账单流水表';

-- ---------- 5. 作者统计表 ----------
CREATE TABLE IF NOT EXISTS sys_creator_stats (
  stat_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '统计ID',
  user_id      BIGINT       NOT NULL COMMENT '作者用户ID',
  total_income DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '累计收益',
  month_income DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '本月收益',
  total_orders INT          NOT NULL DEFAULT 0 COMMENT '成交订单数',
  sold_count   INT          NOT NULL DEFAULT 0 COMMENT '已售出作品数',
  total_views  INT          NOT NULL DEFAULT 0 COMMENT '总浏览量',
  stat_date    DATE         NOT NULL COMMENT '统计日期',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (stat_id),
  KEY idx_user_date (user_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作者统计表';

-- ---------- 6. 作者收益视图 ----------
CREATE OR REPLACE VIEW v_creator_income AS
SELECT
  u.user_id, u.nick_name AS nickname, u.user_type,
  IFNULL(ia.available_amount, 0) AS available_amount,
  IFNULL(ia.frozen_amount, 0)    AS frozen_amount,
  IFNULL(ia.settled_amount, 0)   AS settled_amount,
  IFNULL(cs.total_income, 0)     AS total_income,
  IFNULL(cs.month_income, 0)     AS month_income,
  IFNULL(cs.total_orders, 0)     AS total_orders,
  IFNULL(cs.sold_count, 0)       AS sold_count,
  IFNULL(cs.total_views, 0)      AS total_views,
  cs.stat_date
FROM sys_user u
LEFT JOIN sys_income_account ia ON ia.user_id = u.user_id
LEFT JOIN sys_creator_stats cs ON cs.user_id = u.user_id
  AND cs.stat_date = (
    SELECT MAX(stat_date) FROM sys_creator_stats c2 WHERE c2.user_id = u.user_id
  )
WHERE u.del_flag = '0';
