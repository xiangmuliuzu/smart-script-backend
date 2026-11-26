-- =====================================================================
-- B 模块（内容与作品）· 漫剧竖向视频流演示数据
--
-- 背景：
--   漫剧页改为抖音式全屏竖向视频流（上下滑动切换），需要可直接播放的视频。
--   原 sys_external_drama.external_url 存的是抖音网页链接，浏览器可打开但播放器
--   无法内嵌播放；真实视频源接入方案待与团队负责人商定。故先用「后端自托管的
--   测试视频」把上下滑动交互跑通，本脚本只改演示数据，不改表结构、不新增接口。
--
-- 测试视频（已置于磁盘 D:/ruoyi/uploadPath/upload/content/）：
--   drama_test_1.mp4 / drama_test_2.mp4 / drama_test_3.mp4
--   对应 URL：http://localhost:8080/profile/upload/content/drama_test_N.mp4
--   （App 端 AppConfig.resolveAssetUrl 会把 localhost 归一到模拟器宿主机 10.0.2.2）
--
-- 处理（幂等，可重复执行）：
--   1. 「经典数据」中已上架的外部视频，external_url 按 drama_id 轮转为测试视频直链；
--   2. 追加若干上架演示记录（external_content_id 形如 DYTEST0001..），使滑动有内容；
--      已存在则跳过。
--
-- 回滚：
--   DELETE FROM sys_external_drama WHERE external_content_id LIKE 'DYTEST%';
--   原 external_url 为占位抖音网页链接，如需还原请人工恢复。
--
-- 执行：
--   $env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev `
--     -e "source sql/migrations/b/B_20261010_003__seed_drama_test_videos.sql"
--   verify 闸门：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 存量上架外部视频 → 测试视频直链（按 drama_id 轮转 3 个样例）
--    仅限「经典数据」演示记录，避免覆盖后续接入的真实视频数据。
-- ---------------------------------------------------------------------
UPDATE sys_external_drama
SET external_url = CONCAT('http://localhost:8080/profile/upload/content/drama_test_',
                          MOD(drama_id, 3) + 1, '.mp4'),
    update_by = 'seed-drama-test-video-20261010',
    update_time = NOW()
WHERE status = 'on_shelf'
  AND remark = 'B模块经典数据-外部视频';

-- ---------------------------------------------------------------------
-- 2. 追加上架演示记录（drama_id 为自增列，勿显式指定）
-- ---------------------------------------------------------------------
INSERT INTO sys_external_drama (
    channel_id, external_content_id, title, external_url, source_type,
    authorization_status, sync_status, status, copyright_note,
    created_at, updated_at, create_by, create_time, remark
)
SELECT t.channel_id, t.external_content_id, t.title, t.external_url, 'douyin',
       'authorized', 'synced', 'on_shelf', '测试数据（仅用于跑通竖向视频流交互）',
       NOW(), NOW(), 'seed-drama-test-video-20261010', NOW(),
       '漫剧竖向视频流演示-20261010'
FROM (
              SELECT 3 AS channel_id, 'DYTEST0001' AS external_content_id,
                     '【测试】漫剧样片一' AS title,
                     'http://localhost:8080/profile/upload/content/drama_test_1.mp4' AS external_url
    UNION ALL SELECT 3, 'DYTEST0002', '【测试】漫剧样片二', 'http://localhost:8080/profile/upload/content/drama_test_2.mp4'
    UNION ALL SELECT 4, 'DYTEST0003', '【测试】漫剧样片三', 'http://localhost:8080/profile/upload/content/drama_test_3.mp4'
    UNION ALL SELECT 4, 'DYTEST0004', '【测试】漫剧样片四', 'http://localhost:8080/profile/upload/content/drama_test_1.mp4'
    UNION ALL SELECT 3, 'DYTEST0005', '【测试】漫剧样片五', 'http://localhost:8080/profile/upload/content/drama_test_2.mp4'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM sys_external_drama d WHERE d.external_content_id = t.external_content_id
);

-- ---------------------------------------------------------------------
-- 3. 校验（闸门 summary）：上架记录数达标，且全部为可直连播放的视频地址
-- ---------------------------------------------------------------------
SET @onshelf_cnt = (SELECT COUNT(*) FROM sys_external_drama WHERE status = 'on_shelf');
SET @direct_cnt  = (SELECT COUNT(*) FROM sys_external_drama
                    WHERE status = 'on_shelf' AND external_url LIKE '%.mp4');

SET @fail_cnt = IF(@onshelf_cnt < 8 OR @direct_cnt < @onshelf_cnt, 1, 0);

SELECT 'CHECK' AS check_result,
       CONCAT('on_shelf=', @onshelf_cnt, ', direct_video=', @direct_cnt) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('上架记录不足或存在非直链地址：on_shelf=', @onshelf_cnt, ', direct_video=', @direct_cnt) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, 漫剧竖向视频流演示数据就绪，on_shelf=', @onshelf_cnt) AS detail
WHERE @fail_cnt = 0;