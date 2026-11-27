-- =====================================================================
-- B 模块内容 · 作品推广：坑位「锁定 / 可覆盖」模型
--
-- 内容：
--   1. sys_promotion_application 增加 exclusive 字段：
--        1 = 锁定独占（付费投放、管理员锁定上架）
--        0 = 可被付费素材覆盖的运营兜底素材
--   2. 删除旧「Banner 管理」页遗留的首页轮播素材（sys_banner.position = 'home_top'），
--      轮播位统一由推广申请单派生，不再保留 Banner 兜底数据。
--
-- 幂等：EXCLUSIVE 列存在性判断；删除语句可重复执行；末行输出 PASS/FAIL。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261112_001__promotion_slot_exclusive.sql"
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 申请单增加锁定标记
-- ---------------------------------------------------------------------
SET @has_exclusive = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_promotion_application'
    AND column_name = 'exclusive'
);

SET @ddl = IF(@has_exclusive = 0,
  'ALTER TABLE sys_promotion_application ADD COLUMN exclusive TINYINT NOT NULL DEFAULT 1 COMMENT ''1=锁定独占，0=可被付费素材覆盖'' AFTER source',
  'SELECT ''exclusive already exists'' AS notice');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 清理旧 Banner 管理页遗留的首页轮播素材
-- ---------------------------------------------------------------------
DELETE FROM sys_banner WHERE position = 'home_top';

-- ---------------------------------------------------------------------
-- 3. verify 闸门：任一不符则 fail_cnt > 0
-- ---------------------------------------------------------------------
SET @fail = 0;

SET @fail = @fail + IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_promotion_application'
      AND column_name = 'exclusive'
      AND column_default = '1') = 1, 0, 1);

SET @fail = @fail + IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_promotion_application'
      AND column_name = 'exclusive'
      AND is_nullable = 'NO') = 1, 0, 1);

SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_banner WHERE position = 'home_top') = 0, 0, 1);

SELECT IF(@fail = 0, 'PASS', 'FAIL') AS result, @fail AS fail_cnt;
