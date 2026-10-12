-- =====================================================================
-- B 模块（内容与作品）· 存量作品「上传资料」演示数据填充
--
-- 背景：
--   PC「作品上传资料」页（/content/workfile）读取 sys_work_file 与 sys_work_version。
--   这两张表原先无人写入（App 上传接口在作品创建前无从关联 work_id），页面恒为空。
--   现已打通链路：App 创建作品（2.9.2）时随请求登记剧本文件到 sys_work_file。
--   但存量 39 部作品产生于打通之前，需补一份基础数据，页面才不为空。
--
-- 处理（幂等，可重复执行）：
--   1. 为每部未删除作品补 1 条 sys_work_file（file_type='script'，is_preview=0，sort=1）；
--      仅当该作品尚无任何文件行时插入，重复执行无副作用。
--   2. 为每部未删除作品补 1 条 sys_work_version（version_no='1'，is_current=1）；
--      仅当该作品尚无任何版本行时插入。
--   3. 文件 URL 与本地静态资源一致：
--      http://localhost:8080/profile/upload/content/<workId>_script.txt
--      （对应磁盘 D:/ruoyi/uploadPath/upload/content/<workId>_script.txt，每个 1024 字节）
--
-- 回滚：
--   DELETE FROM sys_work_file    WHERE remark = 'seed-workfile-20261010';
--   DELETE FROM sys_work_version WHERE remark = 'seed-workfile-20261010';
--
-- 执行：
--   $env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev `
--     -e "source sql/migrations/b/B_20261010_002__seed_workfile_version.sql"
--   verify 闸门：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 作品文件（sys_work_file）：NOT NULL 列全部显式赋值
-- ---------------------------------------------------------------------
INSERT INTO sys_work_file (
    work_id, file_name, file_url, file_type, file_size, is_preview, sort,
    create_by, create_time, remark
)
SELECT w.work_id,
       CONCAT(w.title, '.txt'),
       CONCAT('http://localhost:8080/profile/upload/content/', w.work_id, '_script.txt'),
       'script',
       1024,
       0,
       1,
       w.author_id,
       NOW(),
       'seed-workfile-20261010'
FROM sys_work w
WHERE w.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_work_file f WHERE f.work_id = w.work_id);

-- ---------------------------------------------------------------------
-- 2. 作品版本（sys_work_version）：status 留空，与 App 端 createVersion 行为一致
-- ---------------------------------------------------------------------
INSERT INTO sys_work_version (
    work_id, version_no, title, content, change_log, creator_id, is_current,
    create_by, create_time, remark
)
SELECT w.work_id,
       '1',
       CONCAT(w.title, ' 初始版本'),
       COALESCE(NULLIF(w.summary, ''), '（初始版本，暂无正文）'),
       '初始版本',
       w.author_id,
       1,
       w.author_id,
       NOW(),
       'seed-workfile-20261010'
FROM sys_work w
WHERE w.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_work_version v WHERE v.work_id = w.work_id);

-- ---------------------------------------------------------------------
-- 3. 校验（闸门 summary）：不存在「未删除但无文件/无版本」的作品
-- ---------------------------------------------------------------------
SET @work_cnt   = (SELECT COUNT(*) FROM sys_work WHERE is_deleted = 0);
SET @file_cnt   = (SELECT COUNT(DISTINCT work_id) FROM sys_work_file WHERE work_id IN (SELECT work_id FROM sys_work WHERE is_deleted = 0));
SET @ver_cnt    = (SELECT COUNT(DISTINCT work_id) FROM sys_work_version WHERE work_id IN (SELECT work_id FROM sys_work WHERE is_deleted = 0));

SET @fail_cnt = IF(@file_cnt < @work_cnt OR @ver_cnt < @work_cnt, 1, 0);

SELECT 'CHECK' AS check_result,
       CONCAT('works=', @work_cnt, ', works_with_file=', @file_cnt, ', works_with_version=', @ver_cnt) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('存在未删除作品缺文件或版本：works=', @work_cnt, ', file=', @file_cnt, ', version=', @ver_cnt) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, 存量作品上传资料已补齐，works=', @work_cnt) AS detail
WHERE @fail_cnt = 0;