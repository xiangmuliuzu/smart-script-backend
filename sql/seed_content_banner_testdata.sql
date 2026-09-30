-- =====================================================================
-- B 模块书城 · 首页 Banner 轮播测试数据（sys_banner）
--
-- 目的：让 App 首页 Banner 接口（2.7.13 表2-93）能命中真实行，并覆盖
--       查询的固定过滤条件：status='on' 且在展示时间窗内
--       （start_time 为 NULL 或不晚于当前，end_time 为 NULL 或不早于当前）。
--       当前云端 script_platform_dev.sys_banner 为空，首页轮播必然无数据。
--
-- 依据：附件5.1 表3-88 sys_banner（banner_id PK 自增 / title / image_url /
--       link_type / link_id / link_url / position / sort_order / status /
--       start_time / end_time）。App 端查询见 SysBannerMapper.selectAppBannerList。
--
-- 数据构成（4 条 position='home_top' + 1 条 position='home_middle'）：
--   1) 时间窗已覆盖当前    → 预期出现在 home_top 列表
--   2) start_time 在未来   → 预期被时间窗过滤掉
--   3) end_time 已过期     → 预期被时间窗过滤掉
--   4) status='off'        → 预期被状态过滤掉
--   5) position 非 home_top→ 预期不出现在 home_top 列表
--   因此 home_top 查询应恰好命中 1 条，可用返回条数直接验证过滤是否生效。
--
-- 三条硬约束：
--   1. 幂等——sys_banner 无业务唯一键，按 title 前置计数跳过重复插入；
--   2. 不写死 ID——banner_id 交给自增；
--   3. 不编造业务数据——link_type/link_id/link_url 留空（App 端尚未消费跳转字段），
--      图片使用占位图服务，不引用不存在的素材地址。
--
-- 执行：scripts\db\init-database.ps1 -d script_platform_dev -u <账号> -h <主机> -Steps scripts\db\seed-steps.txt
--       （口令只从 $env:DB_PASSWORD 读入，不进 argv、不落日志）
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0，出现 FAIL 行即整体失败。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 逐条插入（每条先按 title 计数，已存在则跳过）
-- ---------------------------------------------------------------------
SET @exists = (SELECT COUNT(*) FROM sys_banner WHERE title = '书城首页轮播-进行中');

INSERT INTO sys_banner
    (title, image_url, position, sort_order, status, start_time, end_time,
     create_by, create_time, remark)
SELECT '书城首页轮播-进行中',
       'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=rainy%20city%20street%20at%20night%2C%20neon%20reflections%2C%20cinematic%20banner%2C%20no%20text&image_size=landscape_16_9',
       'home_top', 1, 'on',
       DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY),
       'seed-content-testdata', NOW(), 'B 模块首页 Banner 测试数据'
FROM DUAL
WHERE @exists = 0;

SET @exists = (SELECT COUNT(*) FROM sys_banner WHERE title = '书城首页轮播-未开始');

INSERT INTO sys_banner
    (title, image_url, position, sort_order, status, start_time, end_time,
     create_by, create_time, remark)
SELECT '书城首页轮播-未开始',
       'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=ancient%20chinese%20palace%20courtyard%2C%20dramatic%20lighting%2C%20cinematic%20banner%2C%20no%20text&image_size=landscape_16_9',
       'home_top', 2, 'on',
       DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 40 DAY),
       'seed-content-testdata', NOW(), 'B 模块首页 Banner 测试数据（应被时间窗过滤）'
FROM DUAL
WHERE @exists = 0;

SET @exists = (SELECT COUNT(*) FROM sys_banner WHERE title = '书城首页轮播-已过期');

INSERT INTO sys_banner
    (title, image_url, position, sort_order, status, start_time, end_time,
     create_by, create_time, remark)
SELECT '书城首页轮播-已过期',
       'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=futuristic%20space%20station%20interior%2C%20blue%20light%2C%20cinematic%20banner%2C%20no%20text&image_size=landscape_16_9',
       'home_top', 3, 'on',
       DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY),
       'seed-content-testdata', NOW(), 'B 模块首页 Banner 测试数据（应被时间窗过滤）'
FROM DUAL
WHERE @exists = 0;

SET @exists = (SELECT COUNT(*) FROM sys_banner WHERE title = '书城首页轮播-已停用');

INSERT INTO sys_banner
    (title, image_url, position, sort_order, status, start_time, end_time,
     create_by, create_time, remark)
SELECT '书城首页轮播-已停用',
       'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=spring%20campus%20cherry%20blossoms%2C%20warm%20sunlight%2C%20cinematic%20banner%2C%20no%20text&image_size=landscape_16_9',
       'home_top', 4, 'off',
       DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY),
       'seed-content-testdata', NOW(), 'B 模块首页 Banner 测试数据（应被状态过滤）'
FROM DUAL
WHERE @exists = 0;

SET @exists = (SELECT COUNT(*) FROM sys_banner WHERE title = '书城中部横幅-进行中');

INSERT INTO sys_banner
    (title, image_url, position, sort_order, status, start_time, end_time,
     create_by, create_time, remark)
SELECT '书城中部横幅-进行中',
       'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=modern%20city%20skyline%20at%20dusk%2C%20warm%20tones%2C%20cinematic%20banner%2C%20no%20text&image_size=landscape_16_9',
       'home_middle', 1, 'on',
       DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY),
       'seed-content-testdata', NOW(), 'B 模块中部横幅测试数据（不应出现在 home_top）'
FROM DUAL
WHERE @exists = 0;

-- ---------------------------------------------------------------------
-- 2. 校验（闸门 summary）
--    列分隔符由 mysql --batch 生成真实制表符，故 PASS/FAIL 与说明分成两列输出
-- ---------------------------------------------------------------------
SET @total_cnt   = (SELECT COUNT(*) FROM sys_banner);
SET @top_visible = (SELECT COUNT(*) FROM sys_banner
                    WHERE position = 'home_top' AND status = 'on'
                      AND (start_time IS NULL OR start_time <= NOW())
                      AND (end_time IS NULL OR end_time >= NOW()));
SET @future_cnt  = (SELECT COUNT(*) FROM sys_banner
                    WHERE title = '书城首页轮播-未开始' AND start_time > NOW());
SET @expired_cnt = (SELECT COUNT(*) FROM sys_banner
                    WHERE title = '书城首页轮播-已过期' AND end_time < NOW());
SET @off_cnt     = (SELECT COUNT(*) FROM sys_banner
                    WHERE title = '书城首页轮播-已停用' AND status = 'off');
SET @middle_cnt  = (SELECT COUNT(*) FROM sys_banner WHERE position = 'home_middle');
SET @bad_img     = (SELECT COUNT(*) FROM sys_banner
                    WHERE image_url IS NULL OR image_url NOT LIKE 'http%');

SET @fail_cnt = (@total_cnt < 5) + (@top_visible <> 1) + (@future_cnt < 1)
              + (@expired_cnt < 1) + (@off_cnt < 1) + (@middle_cnt < 1)
              + (@bad_img > 0);

SELECT 'CHECK' AS check_result,
       CONCAT('banner=', @total_cnt,
              ', home_top_visible=', @top_visible,
              ', future=', @future_cnt,
              ', expired=', @expired_cnt,
              ', off=', @off_cnt,
              ', home_middle=', @middle_cnt,
              ', bad_image=', @bad_img) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('sys_banner 行数不足 5 条，实际=', @total_cnt) AS detail
WHERE @total_cnt < 5;

SELECT 'FAIL' AS check_result,
       CONCAT('home_top 可见 Banner 数应为 1（只保留时间窗内且 status=on 的那条），实际=', @top_visible) AS detail
WHERE @top_visible <> 1;

SELECT 'FAIL' AS check_result,
       '缺少 start_time 在未来的样例 Banner，无法验证时间窗上界过滤' AS detail
WHERE @future_cnt < 1;

SELECT 'FAIL' AS check_result,
       '缺少 end_time 已过期的样例 Banner，无法验证时间窗下界过滤' AS detail
WHERE @expired_cnt < 1;

SELECT 'FAIL' AS check_result,
       '缺少 status=off 的样例 Banner，无法验证状态过滤' AS detail
WHERE @off_cnt < 1;

SELECT 'FAIL' AS check_result,
       '缺少 position 非 home_top 的样例 Banner，无法验证位置过滤' AS detail
WHERE @middle_cnt < 1;

SELECT 'FAIL' AS check_result,
       CONCAT('存在非法图片地址（应为 http(s) 开头），bad_image=', @bad_img) AS detail
WHERE @bad_img > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, banner=', @total_cnt,
              ', home_top_visible=', @top_visible,
              ', future=', @future_cnt,
              ', expired=', @expired_cnt,
              ', off=', @off_cnt,
              ', home_middle=', @middle_cnt) AS detail
WHERE @fail_cnt = 0;