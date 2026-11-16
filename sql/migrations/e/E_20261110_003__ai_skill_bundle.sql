-- =====================================================================
-- E 模块 · sys_ai_skill 支持「多文件 skill 包」（整目录上传）
--
-- 背景：真实 skill 多为「目录」形态——一个 SKILL.md 入口 + references/ 若干
--   专题文件（如 D:\ai漫剧制作\skill\分镜skill\shortdrama-storyboard：
--   SKILL.md + references/01~05 共 6 个文件 37KB）。管理员端现只能传单文件，
--   分镜 skill 传上去只有入口、细则全部丢失；强行把 references 合并进
--   SKILL.md 会破坏「按需查阅」结构，且以后多数 skill 都是这种目录形态。
--
-- 口径：
--   * file_content 由 text 改 mediumtext（TEXT 上限 65535 字节，utf8mb4 中文
--     约 2.1 万字就溢出；mediumtext 16MB，容得下整包内联）。
--   * 新增 file_tree mediumtext：整目录文件集合，自定义分隔格式
--     （每段以 @@@FILE:<相对路径>@@@ 开头），可直接阅读、无需 JSON 解析。
--   * 新增 file_count int 默认 1：目录文件数，供列表/预览显示。
--   * 存量行不受影响（file_tree 为空 → 仍走原单文件 file_content 逻辑）。
--   * 幂等：改类型先查 DATA_TYPE；加列用 information_schema + 动态 DDL。
--
-- 执行：python sql/migrations/e/run_E_20261110_003.py
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. file_content: text -> mediumtext（仅当仍是 text 时执行）
-- ---------------------------------------------------------------------
SET @ct_type = (SELECT DATA_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill'
                  AND COLUMN_NAME = 'file_content');

SET @ddl_modify = IF(@ct_type = 'text',
  'ALTER TABLE sys_ai_skill MODIFY COLUMN file_content mediumtext NULL COMMENT ''skill 文件文本内容（UTF-8，单文件兼容口径）''',
  'SELECT 1 AS noop');

PREPARE stmt FROM @ddl_modify;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 新增 file_tree（整目录文件集合）
-- ---------------------------------------------------------------------
SET @tree_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill'
                      AND COLUMN_NAME = 'file_tree');

SET @ddl_tree = IF(@tree_exists = 0,
  'ALTER TABLE sys_ai_skill ADD COLUMN file_tree mediumtext NULL COMMENT ''多文件 skill 包：@@@FILE:路径@@@ + 内容 顺序拼接'' AFTER file_content',
  'SELECT 1 AS noop');

PREPARE stmt FROM @ddl_tree;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 新增 file_count（目录内文件数）
-- ---------------------------------------------------------------------
SET @cnt_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill'
                     AND COLUMN_NAME = 'file_count');

SET @ddl_cnt = IF(@cnt_exists = 0,
  'ALTER TABLE sys_ai_skill ADD COLUMN file_count int NOT NULL DEFAULT 1 COMMENT ''skill 包内文件数（单文件=1）'' AFTER file_tree',
  'SELECT 1 AS noop');

PREPARE stmt FROM @ddl_cnt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4. verify 闸门
-- ---------------------------------------------------------------------
SET @ct_type = (SELECT DATA_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill'
                  AND COLUMN_NAME = 'file_content');
SET @tree_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill'
                      AND COLUMN_NAME = 'file_tree');
SET @cnt_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill'
                     AND COLUMN_NAME = 'file_count');

SET @fail_cnt = (@ct_type = 'mediumtext') + (@tree_exists = 1) + (@cnt_exists = 1);
SET @fail_cnt = 3 - @fail_cnt;

SELECT 'CHECK' AS check_result,
       CONCAT('file_content=', @ct_type, ', file_tree=', @tree_exists, ', file_count=', @cnt_exists) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('sys_ai_skill 多文件包字段未达预期：file_content=', @ct_type,
              ', file_tree=', @tree_exists, ', file_count=', @cnt_exists) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, sys_ai_skill 已支持多文件 skill 包' AS detail
WHERE @fail_cnt = 0;
