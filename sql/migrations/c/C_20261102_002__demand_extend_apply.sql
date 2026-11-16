-- =====================================================================
-- C_20261102_002：延时申请历史记录表
-- 需求：延时申请不再覆盖 sys_demand_stage 上的最近一次快照，
--       每条申请（通过/不通过）都作为独立记录永久保留，再次申请即新增一条。
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_demand_extend_apply (
  apply_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  demand_id    BIGINT       NOT NULL COMMENT '所属征集',
  stage_id     BIGINT       NOT NULL COMMENT '所属验收轮次',
  creator_id   BIGINT       NOT NULL COMMENT '发起人(接单创作者用户ID)',
  days         INT          NOT NULL DEFAULT 0 COMMENT '申请延长天数',
  hours        INT          NOT NULL DEFAULT 0 COMMENT '申请延长小时数',
  reason       VARCHAR(500) NULL COMMENT '申请原因',
  status       VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  applied_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  decided_at   DATETIME     NULL COMMENT '处理时间',
  decided_by   BIGINT       NULL COMMENT '处理人(发布者用户ID)',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (apply_id),
  KEY idx_stage_status (stage_id, status),
  KEY idx_demand (demand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务轮次延时申请历史(每条申请一行, 通过/驳回均保留)';

-- 存量数据回填：把各轮次行上已有的最近一次申请快照搬进历史表（幂等：同轮同申请时间只插一次）
INSERT INTO sys_demand_extend_apply
    (demand_id, stage_id, creator_id, days, hours, reason, status, applied_at, decided_at, decided_by)
SELECT s.demand_id, s.stage_id,
       COALESCE(d.accepted_creator_id, 0),
       COALESCE(s.extend_days, 0), COALESCE(s.extend_hours, 0), s.extend_reason,
       s.extend_status, COALESCE(s.extend_applied_at, s.updated_at, NOW()),
       CASE WHEN s.extend_status <> 'pending' THEN COALESCE(s.updated_at, NOW()) ELSE NULL END,
       CASE WHEN s.extend_status <> 'pending' AND s.update_by REGEXP '^[0-9]+$'
            THEN CAST(s.update_by AS UNSIGNED) ELSE NULL END
FROM sys_demand_stage s
LEFT JOIN sys_demand d ON d.demand_id = s.demand_id
WHERE s.extend_status IS NOT NULL
  AND s.extend_applied_at IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_demand_extend_apply a
    WHERE a.stage_id = s.stage_id AND a.applied_at = s.extend_applied_at
  );
