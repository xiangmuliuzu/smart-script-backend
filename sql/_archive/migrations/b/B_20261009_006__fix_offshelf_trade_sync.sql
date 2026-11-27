-- =====================================================================
-- B 模块（内容与作品）· 下架作品交易开关存量修复
--
-- 背景：
--   书城作品管理页此前「下架」不联动关闭交易，存在
--   status='off_shelf' 且 trade_enabled=1 的存量脏数据
--   （已下架作品仍可交易）。
--   后端已同步修复（SysBookstoreServiceImpl）：
--     下架自动关闭交易；开启交易前置校验上架状态。
--   本脚本一次性修复存量数据，使库内数据与后端联动口径一致。
--
-- 依据表结构（sql/smartscript_full_init.sql:1635）：
--   sys_work.status         varchar(20) NOT NULL（on_shelf/off_shelf）
--   sys_work.trade_enabled  tinyint     NOT NULL（1=开启 0=关闭）
--   sys_work.is_deleted     逻辑删除标记（0=未删除）
--
-- 处理（幂等，可重复执行）：
--   1. 已下架且开启交易的作品，统一关闭交易（trade_enabled=0）
--
-- 执行：
--   $env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev `
--     -e "source sql/migrations/b/B_20261009_006__fix_offshelf_trade_sync.sql"
--   （口令只从环境变量读入，不进 argv、不落日志）
--   verify 闸门：要求末行 PASS fail_cnt=0，出现 FAIL 行即整体失败。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 修复存量：已下架作品关闭交易
-- ---------------------------------------------------------------------
UPDATE sys_work
SET trade_enabled = 0,
    update_by     = 'fix-offshelf-trade-20261009',
    update_time   = NOW()
WHERE is_deleted = 0
  AND status = 'off_shelf'
  AND trade_enabled = 1;

-- ---------------------------------------------------------------------
-- 2. 校验（闸门 summary）
-- ---------------------------------------------------------------------
SET @bad_cnt   = (SELECT COUNT(*) FROM sys_work
                  WHERE is_deleted = 0 AND status = 'off_shelf' AND trade_enabled = 1);
SET @fixed_cnt = (SELECT COUNT(*) FROM sys_work
                  WHERE update_by = 'fix-offshelf-trade-20261009');

SET @fail_cnt = IF(@bad_cnt > 0, 1, 0);

SELECT 'CHECK' AS check_result,
       CONCAT('off_shelf_but_trade_on=', @bad_cnt, ', fixed_rows=', @fixed_cnt) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('仍存在已下架但开启交易的作品：', @bad_cnt) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, 下架作品交易开关已全部关闭, fixed_rows=', @fixed_cnt) AS detail
WHERE @fail_cnt = 0;
