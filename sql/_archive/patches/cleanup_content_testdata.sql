-- =====================================================================
-- B 模块书城 · 测试数据清理（回滚 seed_content_*_testdata 三个脚本的写入）
--
-- 用途：seed_content_tag_testdata / seed_content_chapter_testdata /
--       seed_content_banner_testdata 三个脚本为联调向云端 script_platform_dev
--       写入了测试行。本脚本按「写入标记」精确删除，供联调结束后回收。
--
-- 删除范围（仅限带标记的行，绝不触碰真实业务数据）：
--   1. sys_work_tag     create_by = 'seed-content-testdata'
--   2. sys_work_chapter create_by = 'seed-content-testdata'
--   3. sys_work_file    create_by = 'seed-content-testdata'
--   4. sys_banner       create_by = 'seed-content-testdata'
--   5. sys_work         试读开关三列（preview_enabled / preview_episodes /
--                       episode_count）恢复为 NULL —— 仅针对 update_by =
--                       'seed-content-testdata' 的作品
--
-- 注意：
--   - 清理后 App 端章节目录/正文/试读与首页 Banner 将回到空数据，
--     test/live/content_preview_contract_test.dart 亦会失败；
--     如需再次联调，重跑 scripts\db\seed-steps.txt 即可（幂等）。
--   - sys_work 本体与其章节之外的真实数据不受影响。
--   - 幂等：DELETE 语义天然可重复执行。
--
-- 执行：scripts\db\init-database.ps1 -d script_platform_dev -u <账号> -h <主机> -Steps <含本脚本的步骤文件>
--       或直接：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/cleanup_content_testdata.sql"
--       （口令只从环境变量读入，不进 argv、不落日志）
-- =====================================================================

SET NAMES utf8mb4;

SET @mark = 'seed-content-testdata';

-- ---------------------------------------------------------------------
-- 1. 删除测试关联 / 章节 / 文件 / Banner
-- ---------------------------------------------------------------------
DELETE FROM sys_work_tag     WHERE create_by = @mark;
DELETE FROM sys_work_chapter WHERE create_by = @mark;
DELETE FROM sys_work_file    WHERE create_by = @mark;
DELETE FROM sys_banner       WHERE create_by = @mark;

-- ---------------------------------------------------------------------
-- 2. 试读开关恢复初始态（清理前为 NULL）
-- ---------------------------------------------------------------------
UPDATE sys_work
SET preview_enabled  = NULL,
    preview_episodes = NULL,
    episode_count    = NULL,
    update_by        = NULL,
    update_time      = NULL
WHERE update_by = @mark;

-- ---------------------------------------------------------------------
-- 3. 校验（闸门 summary）：清理后残留必须为 0
-- ---------------------------------------------------------------------
SET @left_tag     = (SELECT COUNT(*) FROM sys_work_tag     WHERE create_by = @mark);
SET @left_chapter = (SELECT COUNT(*) FROM sys_work_chapter WHERE create_by = @mark);
SET @left_file    = (SELECT COUNT(*) FROM sys_work_file    WHERE create_by = @mark);
SET @left_banner  = (SELECT COUNT(*) FROM sys_banner       WHERE create_by = @mark);
SET @left_preview = (SELECT COUNT(*) FROM sys_work WHERE update_by = @mark);

SET @fail_cnt = @left_tag + @left_chapter + @left_file + @left_banner + @left_preview;

SELECT 'CHECK' AS check_result,
       CONCAT('work_tag=', @left_tag,
              ', chapter=', @left_chapter,
              ', file=', @left_file,
              ', banner=', @left_banner,
              ', work_preview_flag=', @left_preview) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('仍有测试数据残留：work_tag=', @left_tag,
              ', chapter=', @left_chapter,
              ', file=', @left_file,
              ', banner=', @left_banner,
              ', work_preview_flag=', @left_preview) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, 测试数据已全部清理' AS detail
WHERE @fail_cnt = 0;