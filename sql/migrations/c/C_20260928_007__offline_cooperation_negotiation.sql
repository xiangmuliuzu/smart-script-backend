-- =====================================================================
-- C_20260928_007__offline_cooperation_negotiation.sql
-- 用途: 分工条目 15（线上合作意向）/ 16（线下谈判）落地。
--       给 C 主导表 sys_offline_cooperation 补线下谈判结构化列，并把
--       合作记录来源/状态枚举落若依数据字典。
-- 适用: MySQL 8.0.x
-- 依赖: 必须在 C_20260923_000__c_trade_tables.sql（建 sys_offline_cooperation）之后执行。
-- 事务: 幂等、非破坏性；列已存在则跳过，字典按唯一键去重，可重复执行不报错、不丢数据。
--
-- 设计口径:
--   - source 区分记录来源: online=线上合作意向, offline=线下谈判（同一张表两类记录）。
--   - 「谈判时间」复用既有列 next_follow_at，不新增列。
--   - 「地点/联系人/联系方式」按 2026-09-28 决策新增可空列（结构化、可筛选），
--     contact_person/contact_value 为业务侧明文快照，与 sys_contact_profile 的
--     加密档案（phone_cipher 等）无关，后者仍由他域主导、C 只读展示。
-- =====================================================================

SET NAMES utf8mb4;

SELECT 'C_COOP_MIGRATE_START' AS step, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 1) 结构化列（条件 ALTER，逐列判断是否已存在，保证幂等）
-- ---------------------------------------------------------------------
SET @tbl := 'sys_offline_cooperation';

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'negotiation_place');
SET @d1 := IF(@c1 = 0,
  'ALTER TABLE `sys_offline_cooperation` ADD COLUMN `negotiation_place` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''线下谈判地点（分工16）'' AFTER `next_follow_at`',
  'SELECT 1');
PREPARE s1 FROM @d1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'contact_person');
SET @d2 := IF(@c2 = 0,
  'ALTER TABLE `sys_offline_cooperation` ADD COLUMN `contact_person` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''联系人姓名（分工16，明文快照）'' AFTER `negotiation_place`',
  'SELECT 1');
PREPARE s2 FROM @d2; EXECUTE s2; DEALLOCATE PREPARE s2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @tbl AND COLUMN_NAME = 'contact_value');
SET @d3 := IF(@c3 = 0,
  'ALTER TABLE `sys_offline_cooperation` ADD COLUMN `contact_value` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ''联系方式（分工16，明文电话/微信/邮箱）'' AFTER `contact_person`',
  'SELECT 1');
PREPARE s3 FROM @d3; EXECUTE s3; DEALLOCATE PREPARE s3;

-- ---------------------------------------------------------------------
-- 2) 字典类型（2 个）：按 dict_type 唯一键幂等插入
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_c_coop_dict_type;
CREATE TEMPORARY TABLE tmp_c_coop_dict_type (
  dict_type VARCHAR(100) NOT NULL PRIMARY KEY,
  dict_name VARCHAR(100) NOT NULL,
  remark    VARCHAR(500) NULL
) ENGINE=MEMORY;

INSERT INTO tmp_c_coop_dict_type (dict_type, dict_name, remark) VALUES
  ('trade_cooperation_source', '交易-合作记录来源', 'sys_offline_cooperation.source；对应前端 cooperationSource'),
  ('trade_cooperation_status', '交易-合作记录状态', 'sys_offline_cooperation.status；对应前端 cooperationStatus');

INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT t.dict_name, t.dict_type, '0', 'c-dict-migration', NOW(), t.remark
FROM tmp_c_coop_dict_type t
LEFT JOIN sys_dict_type d ON d.dict_type = t.dict_type
WHERE d.dict_id IS NULL;

-- ---------------------------------------------------------------------
-- 3) 字典数据（6 项）：按 (dict_type, dict_value) 幂等插入
--    list_class = 枚举 tagType，保证前后台回显一致
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_c_coop_dict_data;
CREATE TEMPORARY TABLE tmp_c_coop_dict_data (
  dict_type  VARCHAR(100) NOT NULL,
  dict_value VARCHAR(100) NOT NULL,
  dict_label VARCHAR(100) NOT NULL,
  dict_sort  INT          NOT NULL,
  list_class VARCHAR(100) NOT NULL DEFAULT 'default',
  PRIMARY KEY (dict_type, dict_value)
) ENGINE=MEMORY;

INSERT INTO tmp_c_coop_dict_data (dict_type, dict_value, dict_label, dict_sort, list_class) VALUES
  -- 合作记录来源（分工 15/16）
  ('trade_cooperation_source','online','线上合作意向',1,'primary'),
  ('trade_cooperation_source','offline','线下谈判',2,'warning'),
  -- 合作记录状态（C 定义，待产品确认）
  ('trade_cooperation_status','pending','待跟进',1,'warning'),
  ('trade_cooperation_status','ongoing','洽谈中',2,'primary'),
  ('trade_cooperation_status','completed','已达成',3,'success'),
  ('trade_cooperation_status','cancelled','已终止',4,'info');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT t.dict_sort, t.dict_label, t.dict_value, t.dict_type, NULL, t.list_class, 'N', '0', 'c-dict-migration', NOW(), 'C 模块合作记录枚举'
FROM tmp_c_coop_dict_data t
LEFT JOIN sys_dict_data d ON d.dict_type = t.dict_type AND d.dict_value = t.dict_value
WHERE d.dict_code IS NULL;

-- ---------------------------------------------------------------------
-- 4) 校验
-- ---------------------------------------------------------------------
SELECT 'C_COOP_COLUMNS' AS step, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_offline_cooperation'
  AND COLUMN_NAME IN ('negotiation_place', 'contact_person', 'contact_value')
ORDER BY ORDINAL_POSITION;

SELECT 'C_COOP_DICT_SUMMARY' AS step,
       (SELECT COUNT(*) FROM sys_dict_type WHERE dict_type IN ('trade_cooperation_source','trade_cooperation_status')) AS coop_dict_types,
       (SELECT COUNT(*) FROM sys_dict_data WHERE dict_type IN ('trade_cooperation_source','trade_cooperation_status')) AS coop_dict_data,
       (SELECT COUNT(*) FROM tmp_c_coop_dict_type t LEFT JOIN sys_dict_type d ON d.dict_type=t.dict_type WHERE d.dict_id IS NULL) AS missing_types,
       (SELECT COUNT(*) FROM tmp_c_coop_dict_data t LEFT JOIN sys_dict_data d ON d.dict_type=t.dict_type AND d.dict_value=t.dict_value WHERE d.dict_code IS NULL) AS missing_data;

DROP TEMPORARY TABLE IF EXISTS tmp_c_coop_dict_type;
DROP TEMPORARY TABLE IF EXISTS tmp_c_coop_dict_data;

SELECT 'C_COOP_MIGRATE_DONE' AS step;

-- ---------------------------------------------------------------------
-- 回滚指引（如需撤销本迁移）：
--   DELETE FROM sys_dict_data WHERE dict_type IN ('trade_cooperation_source','trade_cooperation_status') AND create_by = 'c-dict-migration';
--   DELETE FROM sys_dict_type WHERE dict_type IN ('trade_cooperation_source','trade_cooperation_status') AND create_by = 'c-dict-migration';
--   ALTER TABLE sys_offline_cooperation DROP COLUMN contact_value, DROP COLUMN contact_person, DROP COLUMN negotiation_place;
-- ---------------------------------------------------------------------
