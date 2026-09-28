-- =====================================================================
-- C_20260928_002__c_trade_dict.sql
-- 用途: 将 C 模块交易枚举落入若依数据字典（sys_dict_type / sys_dict_data），
--       使后台「系统管理-字典管理」可视化维护，问题档案 P2-10 收口。
-- 适用: MySQL 8.0.x
-- 事务: 幂等、非破坏性；可重复执行，不产生重复字典项，不删除既有数据。
--
-- 依据: 前端 src/constants/tradeEnum.js（枚举单一事实源）。
--   - 订单状态 = PRD 9.3；询盘/报价状态 = 2026-09-28 定稿；其余取接口 2.25/2.35/2.38
--     或《C模块开发清单》建议值。
--   - list_class 直接取枚举 tagType（Element Plus el-tag 类型），保证前后台回显一致。
--
-- dict_type 命名规范：trade_<域>_<含义>，与前端注册表键对应关系见各段注释。
-- 回滚: 见文件末尾「回滚指引」。
-- =====================================================================

SET NAMES utf8mb4;

SELECT 'C_DICT_MIGRATE_START' AS step, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 1) 字典类型（12 个）：按 dict_type 唯一键幂等插入
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_c_dict_type;
CREATE TEMPORARY TABLE tmp_c_dict_type (
  dict_type VARCHAR(100) NOT NULL PRIMARY KEY,
  dict_name VARCHAR(100) NOT NULL,
  remark    VARCHAR(500) NULL
) ENGINE=MEMORY;

INSERT INTO tmp_c_dict_type (dict_type, dict_name, remark) VALUES
  ('trade_order_status',       '交易-订单状态',     'PRD 9.3；对应前端 order'),
  ('trade_inquiry_status',      '交易-询盘状态',     '2026-09-28 定稿；对应前端 inquiry'),
  ('trade_quote_status',       '交易-报价状态',     '2026-09-28 定稿；对应前端 quote'),
  ('trade_quoter_role',        '交易-报价方角色',   'sys_quote.quoter_role；对应前端 quoterRole'),
  ('trade_partner_type',       '交易-合作方类型',   '接口 2.35；对应前端 partnerType'),
  ('trade_partner_status',     '交易-合作方状态',   'sys_partner.status；对应前端 partnerStatus'),
  ('trade_license_type',       '交易-授权类型',     '接口 2.25；对应前端 license'),
  ('trade_work_listing_status','交易-作品上架状态', '接口 2.25 listingStatus；对应前端 tradeWork'),
  ('trade_follow_status',      '交易-商务跟进状态', '接口 2.38；对应前端 follow'),
  ('trade_follow_method',      '交易-商务跟进方式', '接口 2.38 method；对应前端 followMethod'),
  ('trade_demand_status',      '交易-征集项目状态', 'sys_demand.status；对应前端 demand'),
  ('trade_submission_status',  '交易-投稿状态',     'sys_demand_submission.status；对应前端 submission');

INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT t.dict_name, t.dict_type, '0', 'c-dict-migration', NOW(), t.remark
FROM tmp_c_dict_type t
LEFT JOIN sys_dict_type d ON d.dict_type = t.dict_type
WHERE d.dict_id IS NULL;

-- ---------------------------------------------------------------------
-- 2) 字典数据（46 项）：临时表驱动，按 (dict_type, dict_value) 幂等插入
--    list_class = 枚举 tagType；无 tagType 者用 'default'
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_c_dict_data;
CREATE TEMPORARY TABLE tmp_c_dict_data (
  dict_type  VARCHAR(100) NOT NULL,
  dict_value VARCHAR(100) NOT NULL,
  dict_label VARCHAR(100) NOT NULL,
  dict_sort  INT          NOT NULL,
  list_class VARCHAR(100) NOT NULL DEFAULT 'default',
  PRIMARY KEY (dict_type, dict_value)
) ENGINE=MEMORY;

INSERT INTO tmp_c_dict_data (dict_type, dict_value, dict_label, dict_sort, list_class) VALUES
  -- 订单状态（PRD 9.3）
  ('trade_order_status','inquiry','询盘中',1,'info'),
  ('trade_order_status','quoted','已报价',2,'warning'),
  ('trade_order_status','confirmed','已确认',3,'primary'),
  ('trade_order_status','contract_pending','待签约',4,'warning'),
  ('trade_order_status','escrow_pending','待托管',5,'warning'),
  ('trade_order_status','delivering','交割中',6,'primary'),
  ('trade_order_status','completed','已完成',7,'success'),
  ('trade_order_status','cancelled','已取消',8,'info'),
  ('trade_order_status','refunded','已退款',9,'danger'),
  -- 询盘状态（定稿）
  ('trade_inquiry_status','pending','待回复',1,'warning'),
  ('trade_inquiry_status','accepted','已接受',2,'success'),
  ('trade_inquiry_status','rejected','已拒绝',3,'danger'),
  ('trade_inquiry_status','closed','已关闭',4,'info'),
  ('trade_inquiry_status','quoted','已报价',5,'primary'),
  ('trade_inquiry_status','deal','已达成',6,'success'),
  -- 报价状态（定稿）
  ('trade_quote_status','pending','待买方确认',1,'warning'),
  ('trade_quote_status','accepted','已接受',2,'success'),
  ('trade_quote_status','rejected','已拒绝',3,'danger'),
  ('trade_quote_status','expired','已过期',4,'info'),
  -- 报价方角色
  ('trade_quoter_role','seller','卖方报价',1,'primary'),
  ('trade_quoter_role','buyer','买方议价',2,'warning'),
  -- 合作方类型
  ('trade_partner_type','investor','投资方',1,'default'),
  ('trade_partner_type','studio','制作机构',2,'default'),
  ('trade_partner_type','platform','发行平台',3,'default'),
  -- 合作方状态
  ('trade_partner_status','active','正常',1,'success'),
  ('trade_partner_status','inactive','停用',2,'info'),
  ('trade_partner_status','pending','待审核',3,'warning'),
  -- 授权类型
  ('trade_license_type','exclusive','独家',1,'default'),
  ('trade_license_type','non_exclusive','非独家',2,'default'),
  ('trade_license_type','adaptation','改编',3,'default'),
  ('trade_license_type','negotiable','可议价',4,'default'),
  -- 作品上架状态
  ('trade_work_listing_status','listed','已上架',1,'success'),
  ('trade_work_listing_status','offline','已下架',2,'info'),
  -- 商务跟进状态
  ('trade_follow_status','ongoing','进行中',1,'success'),
  ('trade_follow_status','pending','待跟进',2,'warning'),
  ('trade_follow_status','completed','已完成',3,'info'),
  -- 商务跟进方式
  ('trade_follow_method','phone','电话',1,'default'),
  ('trade_follow_method','email','邮件',2,'default'),
  ('trade_follow_method','meeting','面谈',3,'default'),
  -- 征集项目状态
  ('trade_demand_status','open','征集中',1,'success'),
  ('trade_demand_status','closed','已截止',2,'info'),
  ('trade_demand_status','selected','已选定',3,'primary'),
  -- 投稿状态
  ('trade_submission_status','submitted','已投稿',1,'warning'),
  ('trade_submission_status','shortlisted','入围',2,'primary'),
  ('trade_submission_status','accepted','已选用',3,'success'),
  ('trade_submission_status','rejected','未选用',4,'info');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT t.dict_sort, t.dict_label, t.dict_value, t.dict_type, NULL, t.list_class, 'N', '0', 'c-dict-migration', NOW(), 'C 模块交易枚举'
FROM tmp_c_dict_data t
LEFT JOIN sys_dict_data d ON d.dict_type = t.dict_type AND d.dict_value = t.dict_value
WHERE d.dict_code IS NULL;

-- ---------------------------------------------------------------------
-- 3) 校验
-- ---------------------------------------------------------------------
SELECT 'C_DICT_SUMMARY' AS step,
       (SELECT COUNT(*) FROM sys_dict_type WHERE dict_type LIKE 'trade\_%') AS trade_dict_types,
       (SELECT COUNT(*) FROM sys_dict_data WHERE dict_type LIKE 'trade\_%') AS trade_dict_data,
       (SELECT COUNT(*) FROM tmp_c_dict_type t LEFT JOIN sys_dict_type d ON d.dict_type=t.dict_type WHERE d.dict_id IS NULL) AS missing_types,
       (SELECT COUNT(*) FROM tmp_c_dict_data t LEFT JOIN sys_dict_data d ON d.dict_type=t.dict_type AND d.dict_value=t.dict_value WHERE d.dict_code IS NULL) AS missing_data;

SELECT dict_type, COUNT(*) AS item_count
  FROM sys_dict_data
 WHERE dict_type LIKE 'trade\_%'
 GROUP BY dict_type
 ORDER BY dict_type;

DROP TEMPORARY TABLE IF EXISTS tmp_c_dict_type;
DROP TEMPORARY TABLE IF EXISTS tmp_c_dict_data;

SELECT 'C_DICT_MIGRATE_DONE' AS step;

-- ---------------------------------------------------------------------
-- 回滚指引（如需撤销本迁移）：
--   DELETE FROM sys_dict_data WHERE dict_type LIKE 'trade\_%' AND create_by = 'c-dict-migration';
--   DELETE FROM sys_dict_type WHERE dict_type LIKE 'trade\_%' AND create_by = 'c-dict-migration';
-- ---------------------------------------------------------------------
