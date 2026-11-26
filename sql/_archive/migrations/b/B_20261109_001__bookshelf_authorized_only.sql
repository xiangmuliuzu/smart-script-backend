-- =====================================================================
-- B 模块书城 · 书架语义调整：由「手工收藏作品」改为「已购 / 已授权作品」
--
-- 背景：
--   平台用户以版权买家和内容运营方为主，书架不再承担「收藏普通作品」的功能。
--   改造后书架列表由 sys_order（已完成订单）与 sys_copyright_authorization（生效授权）
--   派生，sys_bookshelf_record 仅保留 last_read_chapter_id / last_read_at 作为阅读进度。
--
-- 变更：
--   1. 清空 sys_bookshelf_record 中历史手工加入的书架记录（含其阅读进度）。
--      判定口径已迁移为购买派生，旧记录不再表达任何可见性，保留只会产生脏数据。
--   2. 校验唯一索引 uk_user_work(user_id, work_id) 存在——进度写入依赖
--      INSERT ... ON DUPLICATE KEY，缺少唯一索引会退化为重复插入。
--
-- 幂等：重复执行等价于再次清空（新进度行会随 App 阅读重新产生）；末行 PASS/FAIL 闸门。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261109_001__bookshelf_authorized_only.sql"
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 清空历史手工书架记录
-- ---------------------------------------------------------------------
DELETE FROM sys_bookshelf_record;

-- ---------------------------------------------------------------------
-- 2. verify 闸门：任一不符则 fail_cnt > 0
-- ---------------------------------------------------------------------
SET @fail = 0;

-- 2.1 历史记录已清空
SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_bookshelf_record) = 0, 0, 1);

-- 2.2 唯一索引覆盖 (user_id, work_id)，进度 upsert 依赖该索引
SET @fail = @fail + IF((
  SELECT COUNT(DISTINCT column_name)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_bookshelf_record'
    AND non_unique = 0
    AND column_name IN ('user_id', 'work_id')
) = 2, 0, 1);

-- 2.3 进度列齐备
SET @fail = @fail + IF((
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_bookshelf_record'
    AND column_name IN ('last_read_chapter_id', 'last_read_at')
) = 2, 0, 1);

SELECT IF(@fail = 0, 'PASS', 'FAIL') AS result, @fail AS fail_cnt;
