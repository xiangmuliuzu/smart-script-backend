-- =====================================================================
-- C_20261124_001：任务发布回到需求方自助 + 平台审核
-- 需求：买方填表提交 → 待审核（sys_demand.status='pending_review'）→
--       运营审核通过进任务广场（open）/ 不通过落 rejected（可改后重新提交）；
--       后续逐轮验收仍由管理端运营执行，末轮通过后需求方才能查看最终作品。
-- 口径：广场/统计/角标/抢单/自动关闭四处查询本来就写死 status='open'，
--       新增两个状态不进广场，读侧无需改动，因此这里只补审核三列与字典。
-- 幂等：列存在则跳过 ADD；字典按 (dict_type, dict_value) 去重。
-- =====================================================================

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_demand'
      AND COLUMN_NAME = 'audit_remark') = 0,
  'ALTER TABLE sys_demand ADD COLUMN audit_remark VARCHAR(500) NULL COMMENT ''发布审核意见（驳回时给需求方看，重新提交后清空）'' AFTER source_script_name',
  'SELECT ''audit_remark already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_demand'
      AND COLUMN_NAME = 'audit_at') = 0,
  'ALTER TABLE sys_demand ADD COLUMN audit_at DATETIME NULL COMMENT ''发布审核时间'' AFTER audit_remark',
  'SELECT ''audit_at already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_demand'
      AND COLUMN_NAME = 'auditor_id') = 0,
  'ALTER TABLE sys_demand ADD COLUMN auditor_id BIGINT NULL COMMENT ''发布审核人（后台账号ID）'' AFTER audit_at',
  'SELECT ''auditor_id already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 状态字典补两条（与 TradeDemandService 常量同名）：待审核、审核未通过
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class)
SELECT t.dict_sort, t.dict_label, t.dict_value, t.dict_type, t.list_class
FROM (
  SELECT 4 AS dict_sort, '待审核' AS dict_label, 'pending_review' AS dict_value,
         'trade_demand_status' AS dict_type, 'warning' AS list_class
  UNION ALL
  SELECT 5, '审核未通过', 'rejected', 'trade_demand_status', 'danger'
) t
LEFT JOIN sys_dict_data d ON d.dict_type = t.dict_type AND d.dict_value = t.dict_value
WHERE d.dict_code IS NULL;

-- 自检：三列到位 + 两条字典到位（任一为 0 说明迁移未跑全）
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_demand'
      AND COLUMN_NAME IN ('audit_remark', 'audit_at', 'auditor_id')) AS audit_columns,
  (SELECT COUNT(*) FROM sys_dict_data
    WHERE dict_type = 'trade_demand_status'
      AND dict_value IN ('pending_review', 'rejected')) AS status_dict_rows;
