-- =====================================================================
-- B 模块（内容与作品）· 测试数据清理 + 自增重置
--
-- 目的：清空内容域全部测试/联调数据，为灌入「经典真实感数据」腾出干净底座。
--       经只读盘点，下列表当前 100% 为测试数据（create_by ∈
--       {seed-content-testdata, seed-copyright-demo}，或标题含 TEST_/测试/联调、
--       超大 ID 9001+/900001+），无真实业务行。
--
-- 清理范围（内容与作品域，均无外键约束，删除顺序不敏感）：
--   作品族： sys_work、sys_work_chapter、sys_work_version、sys_work_file、sys_work_tag
--   元数据： sys_category、sys_tag
--   运营位： sys_banner
--   外部视频：sys_external_drama、sys_episode、sys_episode_like、sys_comment、sys_drama_channel
--   榜单：   sys_ranking_snapshot
--   互动：   sys_favorite、sys_bookshelf_record、sys_play_progress、sys_play_history、
--            sys_search_history、sys_browse_history、sys_read_record、sys_unlock_record、
--            sys_subscribe、sys_report
--   创作草稿：sys_draft、sys_script_outline、sys_selection、sys_category_group(_item)
--   自建作者账号：sys_user.user_name = 'author_%'（仅本种子体系创建，见 003 脚本）
--
-- 明确不含：sys_demand_tag（属「需求/交易域」，非 B 模块内容域，误删会波及 sys_demand）。
--
-- 幂等：DELETE 语义天然可重复执行。
-- 执行前建议：先导出备份
--   mysqldump -h<主机> -u<账号> script_platform_dev <上表...> > backup.sql
--   （口令只从 $env:MYSQL_PWD 读入，不进 argv）
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261009_002__clear_content_testdata.sql"
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0，出现 FAIL 行即整体失败。
-- =====================================================================

-- 连接排序规则须与内容域表列（utf8mb4_general_ci）一致：
-- 用户变量 @mark 的 coercibility=2，其排序规则取自 collation_connection，
-- 若用 SET NAMES utf8mb4（默认 0900_ai_ci）会与列比较时报 collation 冲突。
SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

SET @mark = 'seed-content-classic';

-- ---------------------------------------------------------------------
-- 1. 自建作者账号回收（只删本种子体系创建的行，绝不触碰真实用户）
-- ---------------------------------------------------------------------
DELETE FROM sys_user WHERE user_name LIKE 'author\_%' AND create_by = @mark;

-- ---------------------------------------------------------------------
-- 2. 作品族（子表先行，语义上更安全）
-- ---------------------------------------------------------------------
DELETE FROM sys_work_tag;
DELETE FROM sys_work_chapter;
DELETE FROM sys_work_version;
DELETE FROM sys_work_file;

-- ---------------------------------------------------------------------
-- 3. 外部视频域
-- ---------------------------------------------------------------------
DELETE FROM sys_episode_like;
DELETE FROM sys_comment;
DELETE FROM sys_episode;
DELETE FROM sys_external_drama;
DELETE FROM sys_drama_channel;

-- ---------------------------------------------------------------------
-- 4. 榜单 / 互动 / 创作草稿
-- ---------------------------------------------------------------------
DELETE FROM sys_ranking_snapshot;
DELETE FROM sys_favorite;
DELETE FROM sys_bookshelf_record;
DELETE FROM sys_play_progress;
DELETE FROM sys_play_history;
DELETE FROM sys_search_history;
DELETE FROM sys_browse_history;
DELETE FROM sys_read_record;
DELETE FROM sys_unlock_record;
DELETE FROM sys_subscribe;
DELETE FROM sys_report;
DELETE FROM sys_draft;
DELETE FROM sys_script_outline;
DELETE FROM sys_selection;
DELETE FROM sys_category_group_item;
DELETE FROM sys_category_group;

-- ---------------------------------------------------------------------
-- 5. 主体表
-- ---------------------------------------------------------------------
DELETE FROM sys_work;
DELETE FROM sys_banner;
DELETE FROM sys_tag;
DELETE FROM sys_category;

-- ---------------------------------------------------------------------
-- 6. 自增重置（仅对含 auto_increment 列的表）
-- ---------------------------------------------------------------------
ALTER TABLE sys_work                AUTO_INCREMENT = 1;
ALTER TABLE sys_work_chapter        AUTO_INCREMENT = 1;
ALTER TABLE sys_work_version        AUTO_INCREMENT = 1;
ALTER TABLE sys_work_file           AUTO_INCREMENT = 1;
ALTER TABLE sys_work_tag            AUTO_INCREMENT = 1;
ALTER TABLE sys_category            AUTO_INCREMENT = 1;
ALTER TABLE sys_tag                 AUTO_INCREMENT = 1;
ALTER TABLE sys_banner              AUTO_INCREMENT = 1;
ALTER TABLE sys_external_drama      AUTO_INCREMENT = 1;
ALTER TABLE sys_episode             AUTO_INCREMENT = 1;
ALTER TABLE sys_episode_like        AUTO_INCREMENT = 1;
ALTER TABLE sys_comment             AUTO_INCREMENT = 1;
ALTER TABLE sys_drama_channel       AUTO_INCREMENT = 1;
ALTER TABLE sys_ranking_snapshot    AUTO_INCREMENT = 1;
ALTER TABLE sys_favorite            AUTO_INCREMENT = 1;
ALTER TABLE sys_bookshelf_record    AUTO_INCREMENT = 1;
ALTER TABLE sys_play_progress       AUTO_INCREMENT = 1;
ALTER TABLE sys_play_history        AUTO_INCREMENT = 1;
ALTER TABLE sys_search_history      AUTO_INCREMENT = 1;
ALTER TABLE sys_browse_history      AUTO_INCREMENT = 1;
ALTER TABLE sys_read_record         AUTO_INCREMENT = 1;
ALTER TABLE sys_unlock_record       AUTO_INCREMENT = 1;
ALTER TABLE sys_subscribe           AUTO_INCREMENT = 1;
ALTER TABLE sys_report              AUTO_INCREMENT = 1;
ALTER TABLE sys_draft               AUTO_INCREMENT = 1;
ALTER TABLE sys_script_outline      AUTO_INCREMENT = 1;
ALTER TABLE sys_selection           AUTO_INCREMENT = 1;

-- ---------------------------------------------------------------------
-- 7. 校验（闸门 summary）：清理后残留必须为 0
-- ---------------------------------------------------------------------
SET @left = 0
  + (SELECT COUNT(*) FROM sys_work)
  + (SELECT COUNT(*) FROM sys_work_chapter)
  + (SELECT COUNT(*) FROM sys_work_version)
  + (SELECT COUNT(*) FROM sys_work_file)
  + (SELECT COUNT(*) FROM sys_work_tag)
  + (SELECT COUNT(*) FROM sys_category)
  + (SELECT COUNT(*) FROM sys_tag)
  + (SELECT COUNT(*) FROM sys_banner)
  + (SELECT COUNT(*) FROM sys_external_drama)
  + (SELECT COUNT(*) FROM sys_episode)
  + (SELECT COUNT(*) FROM sys_episode_like)
  + (SELECT COUNT(*) FROM sys_comment)
  + (SELECT COUNT(*) FROM sys_drama_channel)
  + (SELECT COUNT(*) FROM sys_ranking_snapshot)
  + (SELECT COUNT(*) FROM sys_favorite)
  + (SELECT COUNT(*) FROM sys_bookshelf_record)
  + (SELECT COUNT(*) FROM sys_play_progress)
  + (SELECT COUNT(*) FROM sys_play_history)
  + (SELECT COUNT(*) FROM sys_search_history)
  + (SELECT COUNT(*) FROM sys_browse_history)
  + (SELECT COUNT(*) FROM sys_read_record)
  + (SELECT COUNT(*) FROM sys_unlock_record)
  + (SELECT COUNT(*) FROM sys_subscribe)
  + (SELECT COUNT(*) FROM sys_report)
  + (SELECT COUNT(*) FROM sys_draft)
  + (SELECT COUNT(*) FROM sys_script_outline)
  + (SELECT COUNT(*) FROM sys_selection)
  + (SELECT COUNT(*) FROM sys_category_group)
  + (SELECT COUNT(*) FROM sys_category_group_item);

SET @left_author = (SELECT COUNT(*) FROM sys_user WHERE user_name LIKE 'author\_%' AND create_by = @mark);

SET @fail_cnt = @left + @left_author;

SELECT 'CHECK' AS check_result,
       CONCAT('内容域残留行=', @left, ', 自建作者残留=', @left_author) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('清理后仍有残留：内容域=', @left, ', 作者=', @left_author) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, 内容域测试数据已全部清理，自增已重置' AS detail
WHERE @fail_cnt = 0;