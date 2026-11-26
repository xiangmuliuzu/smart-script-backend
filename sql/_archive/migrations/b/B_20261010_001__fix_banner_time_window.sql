-- =====================================================================
-- B 模块（内容与作品）· Banner 展示时间窗非法修复
--
-- 背景：
--   PC Banner 管理页的「开始/结束时间」是 App 端可见性的第二个闸门
--   （App 端 selectAppBannerList：status='on' 且 start_time<=now<=end_time）。
--   库内存在 end_time 早于 start_time 的非法时间窗（年份误填），
--   导致即使状态为上架、Banner 也恒不在 App 端展示，
--   表现为「状态开关失效 / 被时间抢占」。
--   PC 端已补：起止校验（结束必须晚于开始）+ 可见性列 + 窗前提示。
--   本脚本一次性修复存量非法时间窗。
--
-- 依据表结构（云端 script_platform_dev sys_banner）：
--   start_time / end_time  datetime NULL（NULL 视为不限制）
--   status                 varchar(20)（on/off）
--
-- 处理（幂等，可重复执行）：
--   1. 起止均非空且 end_time < start_time 的行，按「年份误填」推定：
--      end_time 加 1 年；若加 1 年仍不晚于 start_time，则取 start_time 次日，
--      保证修复后时间窗必然有效（两条分支取较大者）。
--      例：banner_id=1 => 2026-01-15 13:10:09 → 2027-01-15 13:10:09。
--      修复后 end_time >= start_time，条件不再匹配，重复执行无副作用。
--
-- 执行：
--   $env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev `
--     -e "source sql/migrations/b/B_20261010_001__fix_banner_time_window.sql"
--   （口令只从环境变量读入，不进 argv、不落日志）
--   verify 闸门：要求末行 PASS fail_cnt=0，出现 FAIL 行即整体失败。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 修复存量：结束时间早于开始时间的非法时间窗
-- ---------------------------------------------------------------------
UPDATE sys_banner
SET end_time = GREATEST(DATE_ADD(end_time, INTERVAL 1 YEAR),
                        DATE_ADD(start_time, INTERVAL 1 DAY)),
    update_by   = 'fix-banner-time-window-20261010',
    update_time = NOW()
WHERE start_time IS NOT NULL
  AND end_time IS NOT NULL
  AND end_time < start_time;

-- ---------------------------------------------------------------------
-- 2. 校验（闸门 summary）
-- ---------------------------------------------------------------------
SET @bad_cnt   = (SELECT COUNT(*) FROM sys_banner
                  WHERE start_time IS NOT NULL AND end_time IS NOT NULL
                    AND end_time < start_time);
SET @fixed_cnt = (SELECT COUNT(*) FROM sys_banner
                  WHERE update_by = 'fix-banner-time-window-20261010');

SET @fail_cnt = IF(@bad_cnt > 0, 1, 0);

SELECT 'CHECK' AS check_result,
       CONCAT('invalid_window_rows=', @bad_cnt, ', fixed_rows=', @fixed_cnt) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('仍存在结束时间早于开始时间的 Banner：', @bad_cnt) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, Banner 展示时间窗已全部合法, fixed_rows=', @fixed_cnt) AS detail
WHERE @fail_cnt = 0;