-- =====================================================================
-- B 模块（内容与作品）· 榜单类型值与 App 端四榜对齐（存量改名）
--
-- 背景：
--   PC 排行榜管理重构为按 App 端四榜分页签管理，后端 rankingType
--   白名单同步收紧为 4 值（view/favorite/sale/rating，与
--   AppBookstoreServiceImpl 榜单白名单一致）。
--   云端存量 ranking_type 为 seed 灌入的 view_rank / favorite_rank，
--   需改名为 view / favorite，否则 PC 各榜页签查不到存量数据。
--
-- 依据表结构（云端 script_platform_dev sys_ranking_snapshot）：
--   ranking_type varchar(20) NOT NULL
--   唯一键 uk_ranking_work_period (ranking_type, period_start, period_end, work_id)
--   库内仅存在 view_rank / favorite_rank 两类，改名无唯一键冲突。
--
-- 处理（幂等，可重复执行）：
--   1. view_rank     → view
--   2. favorite_rank → favorite
--
-- 注意：
--   B_20261009_004__seed_content_extra.sql 中榜单种子仍写旧值；
--   如在全新库重放种子脚本，按序再执行本脚本即可收敛。
--
-- 执行：
--   $env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev `
--     -e "source sql/migrations/b/B_20261009_007__rename_ranking_type.sql"
--   （口令只从环境变量读入，不进 argv、不落日志）
--   verify 闸门：要求末行 PASS fail_cnt=0，出现 FAIL 行即整体失败。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 存量改名：view_rank → view
-- ---------------------------------------------------------------------
UPDATE sys_ranking_snapshot
SET ranking_type = 'view',
    update_by    = 'rename-ranking-type-20261009',
    update_time  = NOW()
WHERE ranking_type = 'view_rank';

-- ---------------------------------------------------------------------
-- 2. 存量改名：favorite_rank → favorite
-- ---------------------------------------------------------------------
UPDATE sys_ranking_snapshot
SET ranking_type = 'favorite',
    update_by    = 'rename-ranking-type-20261009',
    update_time  = NOW()
WHERE ranking_type = 'favorite_rank';

-- ---------------------------------------------------------------------
-- 3. 校验（闸门 summary）
-- ---------------------------------------------------------------------
SET @bad_cnt  = (SELECT COUNT(*) FROM sys_ranking_snapshot
                 WHERE ranking_type NOT IN ('view', 'favorite', 'sale', 'rating'));
SET @view_cnt = (SELECT COUNT(*) FROM sys_ranking_snapshot WHERE ranking_type = 'view');
SET @fav_cnt  = (SELECT COUNT(*) FROM sys_ranking_snapshot WHERE ranking_type = 'favorite');

SET @fail_cnt = IF(@bad_cnt > 0, 1, 0);

SELECT 'CHECK' AS check_result,
       CONCAT('unknown_type_rows=', @bad_cnt, ', view=', @view_cnt, ', favorite=', @fav_cnt) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('仍存在白名单外榜单类型：', @bad_cnt) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, 榜单类型已全部收敛为 view/favorite/sale/rating') AS detail
WHERE @fail_cnt = 0;
