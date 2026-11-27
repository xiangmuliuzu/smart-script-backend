-- =====================================================================
-- C_20261103_002：延时记录补充"本次动作生效后的截止时间"
-- 需求：延时记录只显示延长多久，看不出顺延到什么时候，逐条追加 due_after；
--       同时修正 creator_id 列注释（apply_type=self 时该列存的是发布者）。
-- 幂等：列存在则跳过 ADD；注释类 MODIFY 可重复执行。
-- =====================================================================

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_demand_extend_apply'
      AND COLUMN_NAME = 'due_after') = 0,
  'ALTER TABLE sys_demand_extend_apply ADD COLUMN due_after DATETIME NULL COMMENT ''本次动作生效后的轮次截止时间：自主延时/申请通过时写入，待处理与驳回为空'' AFTER decided_by',
  'SELECT ''due_after already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 发起人语义修正：request=接单创作者，self=发布者
ALTER TABLE sys_demand_extend_apply
  MODIFY COLUMN creator_id BIGINT NOT NULL COMMENT '发起人(申请延时=接单创作者, 自主延时=发布者)';
