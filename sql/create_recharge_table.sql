-- 积分充值订单表（充值页 + 模拟支付壳子）
-- 真实微信/支付宝回调时复用同一张表：验签通过后把 status 置 PAID 并触发积分入账。
-- 幂等：仅 status='PENDING' 时才允许更新为 PAID（见 RechargeOrderMapper.markPaid）。

CREATE TABLE IF NOT EXISTS `sys_recharge_order` (
  `order_id`   bigint       NOT NULL AUTO_INCREMENT,
  `order_no`   varchar(32)  COLLATE utf8mb4_general_ci NOT NULL COMMENT '平台订单号（唯一，支付对账用）',
  `user_id`    bigint       NOT NULL COMMENT '充值用户',
  `amount_fen` int          NOT NULL COMMENT '支付金额（分）',
  `points`     int          NOT NULL COMMENT '到账积分',
  `channel`    varchar(20)  COLLATE utf8mb4_general_ci NOT NULL COMMENT '支付渠道 WECHAT/ALIPAY',
  `status`     varchar(20)  COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待支付 PAID-已支付 CANCELLED-已取消',
  `pay_time`   datetime     DEFAULT NULL COMMENT '支付成功时间',
  `remark`     varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='积分充值订单表（模拟支付壳子）';
