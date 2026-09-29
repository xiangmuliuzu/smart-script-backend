-- =====================================================================
-- C_20260928_003__c_order_inquiry_unique.sql
-- 用途: 为 sys_order.inquiry_id 增加唯一索引，从数据库层杜绝「同一询盘重复生成订单」
--       （问题档案 P1-06 撞号/重复单的最终防线，配合 Service 层幂等 SELECT 双保险）。
-- 适用: MySQL 8.0.x
-- 事务: 幂等；索引已存在则跳过。
--
-- 说明：
--   - inquiry_id 可为 NULL（非询盘来源的订单）；InnoDB 唯一索引允许多行 NULL，不影响该类订单。
--   - C 的两条生成路径（询盘直接转单 / 接受报价转单）均写入非空 inquiry_id，故受唯一约束保护。
--   - 建索引前必须确认无历史重复数据，否则 DDL 失败；本脚本先做重复检测并报出决策。
--
-- 回滚: 见文件末尾「回滚指引」。
-- =====================================================================

SET NAMES utf8mb4;

SELECT 'C_ORDER_IDX_START' AS step, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 1) 重复数据检测：同一非空 inquiry_id 出现多行则报出（此时不应建唯一索引）
-- ---------------------------------------------------------------------
SELECT 'C_ORDER_IDX_DUPCHECK' AS step,
       (SELECT COUNT(*) FROM (
          SELECT inquiry_id FROM sys_order
          WHERE inquiry_id IS NOT NULL
          GROUP BY inquiry_id HAVING COUNT(*) > 1
        ) x) AS duplicate_inquiry_ids,
       IF(
         (SELECT COUNT(*) FROM (
            SELECT inquiry_id FROM sys_order
            WHERE inquiry_id IS NOT NULL
            GROUP BY inquiry_id HAVING COUNT(*) > 1
          ) y) = 0,
         'PROCEED', 'REFUSE_DUPLICATE_ORDERS'
       ) AS decision;

-- ---------------------------------------------------------------------
-- 2) 幂等建索引：uk_inquiry_id 不存在时才创建
--    用预处理语句包裹，因 MySQL 不支持 CREATE INDEX IF NOT EXISTS
-- ---------------------------------------------------------------------
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_order'
    AND index_name = 'uk_inquiry_id'
);

SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE sys_order ADD UNIQUE KEY uk_inquiry_id (inquiry_id) USING BTREE',
  'SELECT ''uk_inquiry_id already exists, skip'' AS msg'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3) 校验
-- ---------------------------------------------------------------------
SELECT 'C_ORDER_IDX_SUMMARY' AS step,
       (SELECT COUNT(*) FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = 'sys_order'
           AND index_name = 'uk_inquiry_id') AS uk_inquiry_id_present,
       (SELECT NON_UNIQUE FROM information_schema.statistics
         WHERE table_schema = DATABASE()
           AND table_name = 'sys_order'
           AND index_name = 'uk_inquiry_id'
         LIMIT 1) AS non_unique_flag;

SELECT index_name, column_name, non_unique
  FROM information_schema.statistics
 WHERE table_schema = DATABASE()
   AND table_name = 'sys_order'
 ORDER BY index_name, seq_in_index;

SELECT 'C_ORDER_IDX_DONE' AS step;

-- ---------------------------------------------------------------------
-- 回滚指引：
--   ALTER TABLE sys_order DROP INDEX uk_inquiry_id;
-- ---------------------------------------------------------------------
