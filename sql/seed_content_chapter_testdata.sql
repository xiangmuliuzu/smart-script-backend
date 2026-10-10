-- =====================================================================
-- B 模块书城 · 免费试读链路测试数据（sys_work_chapter + sys_work + sys_work_file）
--
-- 目的：让 App 端「章节目录 / 章节正文 / 免费试读」三个接口能命中真实行，
--       并覆盖试读边界（前 preview_episodes 章可读，其余返回 403）。
--       当前云端 script_platform_dev 中 sys_work_chapter 与 sys_work_file 均为空，
--       且 sys_work.preview_enabled / preview_episodes / episode_count 全为 NULL。
--
-- 依据：附件5.1 表3-16 sys_work_chapter（chapter_id PK 自增 / work_id / chapter_no /
--       chapter_title / content / word_count / is_free / status，唯一键
--       uk_work_chapter_no(work_id, chapter_no)，索引 idx_work_id）；
--       表3-14 sys_work（preview_enabled / preview_episodes / episode_count）；
--       表3-15 sys_work_file（file_id PK 自增 / work_id / file_name / file_url /
--       file_type / file_size / is_preview / sort，索引 idx_work_id）。
--       口径：status tinyint 0=正常 1=停用；is_free tinyint 0=否 1=是；
--             is_preview tinyint 0=否 1=是；preview_enabled tinyint 0=否 1=是。
--
-- 三条硬约束：
--   1. 幂等——重复执行不产生重复行：章节靠 uk_work_chapter_no(work_id, chapter_no)
--      以 INSERT IGNORE 去重，试读开关为 UPDATE 语义天然幂等，作品文件按前置计数跳过；
--   2. 不写死 ID——目标作品取库内已上架作品中 work_id 最小者（开发库即 3），
--      无已上架作品时不做任何写入，直接进入校验段报 FAIL；
--   3. 不编造业务数据——章节题目与正文为明确标注的测试样例，仅用于打通试读链路。
--
-- 执行：scripts\db\init-database.ps1 -d script_platform_dev -u <账号> -h <主机> -Steps scripts\db\seed-steps.txt
--       （口令只从 $env:DB_PASSWORD 读入，不进 argv、不落日志）
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0，出现 FAIL 行即整体失败。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. 目标作品：已上架作品中 work_id 最小者（开发库为 3）
--    无已上架作品时 @work_id 为 NULL，后续所有写入被 WHERE 条件拦住
-- ---------------------------------------------------------------------
SET @work_id = (SELECT MIN(work_id) FROM sys_work WHERE is_deleted = 0 AND status = 'on_shelf');

-- ---------------------------------------------------------------------
-- 1. 章节：5 章，前 2 章标记免费（is_free=1），全部 status=0（正常）
--    幂等依据：uk_work_chapter_no(work_id, chapter_no)，重复行被 INSERT IGNORE 丢弃
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_work_chapter
    (work_id, chapter_no, chapter_title, content, word_count, is_free, status,
     create_by, create_time, remark)
SELECT @work_id,
       s.chapter_no,
       s.chapter_title,
       s.content,
       CHAR_LENGTH(s.content),
       s.is_free,
       0,
       'seed-content-testdata',
       NOW(),
       'B 模块免费试读测试数据'
FROM (
    SELECT 1 AS chapter_no, '第一章 雨夜来电' AS chapter_title, '雨点砸在玻璃上，像有人在门外数着秒。林澈把手机翻过来，屏幕亮起一个陌生号码。' AS content, 1 AS is_free
    UNION ALL SELECT 2, '第二章 旧档案', '档案室在负一层，灯管每隔三秒闪一次。管理员把牛皮纸袋推过来，封口处写着一个已注销的名字。', 1
    UNION ALL SELECT 3, '第三章 交易条件', '对方只提了一个条件：三天之内，把那份底稿交出来，否则第二份材料会出现在警局门口。', 0
    UNION ALL SELECT 4, '第四章 暗线', '沿着通话记录往回查，所有的线索都指向同一个中途停用的号码，而号码的登记人早已不在本地。', 0
    UNION ALL SELECT 5, '第五章 收网', '凌晨四点，仓库的卷闸门缓缓升起。林澈按下录音键，把最后一段对话完整地留了下来。', 0
) s
WHERE @work_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 2. 作品试读开关：开启试读并限制为前 2 章，同时补齐集数
--    （UPDATE 语义天然幂等，重复执行结果一致）
-- ---------------------------------------------------------------------
UPDATE sys_work
SET preview_enabled = '1',
    preview_episodes = 2,
    episode_count = 5,
    update_by = 'seed-content-testdata',
    update_time = NOW()
WHERE work_id = @work_id;

-- ---------------------------------------------------------------------
-- 3. 可预览作品文件：1 条 is_preview=1
--    sys_work_file 无业务唯一键，故先计数再插入（重复执行不再插入）
-- ---------------------------------------------------------------------
SET @file_exists = (SELECT COUNT(*) FROM sys_work_file
                    WHERE work_id = @work_id AND file_name = '试读样章.pdf');

INSERT INTO sys_work_file
    (work_id, file_name, file_url, file_type, file_size, is_preview, sort,
     create_by, create_time, remark)
SELECT @work_id,
       '试读样章.pdf',
       'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=screenplay%20sample%20document%20on%20desk%2C%20warm%20lamp%20light%2C%20no%20text&image_size=landscape_16_9',
       'pdf',
       512000,
       1,
       1,
       'seed-content-testdata',
       NOW(),
       'B 模块免费试读附件测试数据'
FROM DUAL
WHERE @work_id IS NOT NULL
  AND @file_exists = 0;

-- ---------------------------------------------------------------------
-- 4. 校验（闸门 summary）
--    列分隔符由 mysql --batch 生成真实制表符，故 PASS/FAIL 与说明分成两列输出
-- ---------------------------------------------------------------------
SET @ch_cnt      = (SELECT COUNT(*) FROM sys_work_chapter WHERE work_id = @work_id);
SET @free_cnt    = (SELECT COUNT(*) FROM sys_work_chapter WHERE work_id = @work_id AND is_free = 1);
SET @file_cnt    = (SELECT COUNT(*) FROM sys_work_file WHERE work_id = @work_id AND is_preview = 1);
SET @bad_content = (SELECT COUNT(*) FROM sys_work_chapter WHERE work_id = @work_id AND (content IS NULL OR content = ''));
SET @bad_dup     = (SELECT COUNT(*) FROM (SELECT chapter_no FROM sys_work_chapter
                                           WHERE work_id = @work_id
                                           GROUP BY chapter_no HAVING COUNT(*) > 1) x);
SET @preview_ok  = IFNULL((SELECT (preview_enabled = 1 AND preview_episodes = 2)
                           FROM sys_work WHERE work_id = @work_id), 0);
SET @covered_ep  = (SELECT COUNT(*) FROM sys_work_chapter
                    WHERE work_id = @work_id AND chapter_no <= 2);

SET @fail_cnt = (@work_id IS NULL) + (@ch_cnt < 5) + (@free_cnt < 2)
              + (@file_cnt < 1) + (@bad_content > 0) + (@bad_dup > 0)
              + (@preview_ok = 0) + (@covered_ep = 0);

SELECT 'CHECK' AS check_result,
       CONCAT('work_id=', IFNULL(@work_id, 'NULL'),
              ', chapter=', @ch_cnt,
              ', free_chapter=', @free_cnt,
              ', preview_file=', @file_cnt,
              ', preview_enabled/episodes=',
              IFNULL((SELECT CONCAT(preview_enabled, '/', preview_episodes)
                      FROM sys_work WHERE work_id = @work_id), 'NULL'),
              ', readable_chapter=', @covered_ep) AS detail;

SELECT 'FAIL' AS check_result,
       'sys_work 无已上架作品（is_deleted=0 且 status=on_shelf），请先在 PC 端「内容管理-作品管理」上架作品' AS detail
WHERE @work_id IS NULL;

SELECT 'FAIL' AS check_result,
       CONCAT('目标作品章节不足 5 章，实际 chapter=', @ch_cnt, '（可能被 uk_work_chapter_no 去重跳过）') AS detail
WHERE @work_id IS NOT NULL AND @ch_cnt < 5;

SELECT 'FAIL' AS check_result,
       CONCAT('免费章不足 2 章，实际 free_chapter=', @free_cnt) AS detail
WHERE @work_id IS NOT NULL AND @free_cnt < 2;

SELECT 'FAIL' AS check_result,
       '无可预览作品文件（sys_work_file.is_preview=1），免费试读接口的 previewFiles 将为空' AS detail
WHERE @work_id IS NOT NULL AND @file_cnt < 1;

SELECT 'FAIL' AS check_result,
       CONCAT('存在空正文章节，bad_content=', @bad_content) AS detail
WHERE @bad_content > 0;

SELECT 'FAIL' AS check_result,
       CONCAT('同一作品存在重复 chapter_no，bad_dup=', @bad_dup) AS detail
WHERE @bad_dup > 0;

SELECT 'FAIL' AS check_result,
       'preview_enabled 未开启或 preview_episodes 不等于 2，试读边界无法验证' AS detail
WHERE @preview_ok = 0;

SELECT 'FAIL' AS check_result,
       '试读范围内无任何章节（chapter_no<=preview_episodes），目录 readable 将全为 false' AS detail
WHERE @work_id IS NOT NULL AND @covered_ep = 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, work_id=', @work_id,
              ', chapter=', @ch_cnt,
              ', free_chapter=', @free_cnt,
              ', preview_file=', @file_cnt,
              ', readable_chapter=', @covered_ep) AS detail
WHERE @fail_cnt = 0;