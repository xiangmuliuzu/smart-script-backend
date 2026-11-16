-- =============================================================================
-- C_20261030_001  任务验收轮次：sys_demand 加 total_episodes + 新建 sys_demand_stage
-- =============================================================================

-- 1. sys_demand 增加总集数字段（幂等）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand' AND COLUMN_NAME = 'total_episodes');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE sys_demand ADD COLUMN total_episodes INT DEFAULT NULL COMMENT ''总集数(如24)''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 新建验收轮次表
CREATE TABLE IF NOT EXISTS sys_demand_stage (
  stage_id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '轮次ID',
  demand_id        BIGINT        NOT NULL COMMENT '所属征集',
  stage_order      INT           NOT NULL COMMENT '轮次序号(1,2,3...)',
  episode_to       INT           NOT NULL COMMENT '本轮验收截止集数',
  amount           DECIMAL(12,2) NOT NULL COMMENT '本轮放款金额(元)',
  status           VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT 'pending/submitted/approved/rejected',
  deliverable_desc VARCHAR(1000) NULL COMMENT '创作者交付说明',
  deliverable_url  VARCHAR(500)  NULL COMMENT '交付物文件URL(可选)',
  submitted_at     DATETIME      NULL COMMENT '创作者提交时间',
  reviewed_at      DATETIME      NULL COMMENT '买方审核时间',
  reject_reason    VARCHAR(500)  NULL COMMENT '驳回原因',
  release_flag     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '放款标记: 0未放/1已发信号给支付模块',
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by        VARCHAR(64)   NULL COMMENT '创建者',
  update_by        VARCHAR(64)   NULL COMMENT '更新者',
  remark           VARCHAR(500)  NULL COMMENT '备注',
  PRIMARY KEY (stage_id),
  UNIQUE KEY uk_demand_stage (demand_id, stage_order),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务验收轮次';
