-- =====================================================================
-- B 模块内容 · 作品推广：轮播素材地址
--
-- 内容：
--   1. sys_promotion_application 增加 material_url（作者申请轮播推广时提交的素材）
--
-- 说明：
--   banner 资源位由申请单自身携带素材，App 首页轮播在投放时间窗内直接读申请单派生展示，
--   不再要求管理员先建 sys_banner。banner_id 保留为管理员手动绑定运营 Banner 的兼容路径。
--
-- 幂等：information_schema 判断列是否存在，不存在才 ALTER；末行 verify 闸门要求 PASS fail_cnt=0。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261104_002__promotion_banner_material.sql"
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 申请单增加轮播素材地址
-- ---------------------------------------------------------------------
SET @has_material_url = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_promotion_application'
    AND column_name = 'material_url'
);

SET @ddl = IF(@has_material_url = 0,
  'ALTER TABLE sys_promotion_application ADD COLUMN material_url VARCHAR(500) DEFAULT NULL COMMENT ''轮播素材图片地址（slot_type=banner 必填）'' AFTER banner_id',
  'SELECT ''material_url already exists'' AS notice');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. verify 闸门：任一不符则 fail_cnt > 0
-- ---------------------------------------------------------------------
SET @fail = 0;

SET @fail = @fail + IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_promotion_application'
      AND column_name = 'material_url') = 1, 0, 1);

SET @fail = @fail + IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_promotion_application'
      AND column_name = 'material_url'
      AND character_maximum_length = 500) = 1, 0, 1);

SELECT IF(@fail = 0, 'PASS', 'FAIL') AS result, @fail AS fail_cnt;
