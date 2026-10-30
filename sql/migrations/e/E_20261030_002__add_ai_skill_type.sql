-- =====================================================================
-- E 模块 · sys_ai_skill 增加 skill_type（write / storyboard）+ 分镜类示例 seed
--
-- 背景：分镜用的 skill（镜头语言/画面拆解/一致性锚定）与剧本/写作/润色
--   用的 skill（创作风格：悬疑烧脑、甜宠爆款等）规则差异大，管理员端需
--   分开配置；端上 AI 调用也按场景只展示对应类型的「导演(skill)」。
--   因此给 sys_ai_skill 增加 skill_type 维度：
--     * write       → 剧本 / 写作 / 润色 共用（默认）
--     * storyboard  → 分镜 单独一类
--
-- 口径：
--   * skill_type varchar(20)，默认 'write'，非空。
--   * 存量行（两条示例 seed）统一标 'write'（幂等 UPDATE）。
--   * 新增一条 storyboard 类示例 seed「分镜导演（镜头语言）」，内容按
--     「分镜故事板生成器」类 skill 规则整理（镜头语言/节奏/一致性），
--     适配本系统纯文本分镜脚本输出（不做图片生成指令）。
--   * 幂等：加列用 information_schema 判断 + PREPARE 动态 DDL；
--     seed 用 INSERT ... ON DUPLICATE KEY UPDATE（skill_name 唯一键）。
--
-- 执行：python sql/migrations/e/run_E_20261030_002.py
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 幂等加列（MySQL 无 ALTER IF NOT EXISTS，用信息架构判断 + 动态 SQL）
-- ---------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill'
                     AND COLUMN_NAME = 'skill_type');

SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE sys_ai_skill ADD COLUMN skill_type varchar(20) NOT NULL DEFAULT ''write'' COMMENT ''skill 类型：write（剧本/写作/润色）storyboard（分镜）'' AFTER skill_name',
  'SELECT 1 AS noop');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 存量行统一标 write（幂等，不触碰已有内容）
-- ---------------------------------------------------------------------
UPDATE sys_ai_skill SET skill_type = 'write'
WHERE skill_type IS NULL OR skill_type = '';

-- ---------------------------------------------------------------------
-- 3. 分镜类示例 seed（storyboard 单独一类）
-- ---------------------------------------------------------------------
INSERT INTO sys_ai_skill (skill_name, skill_type, file_name, file_content, status, create_by, remark) VALUES
('分镜导演（镜头语言）', 'storyboard', '分镜导演镜头语言.skill.md',
'# 导演 Skill：分镜镜头语言（storyboard 类）
## 定位
本 skill 用于指导 AI 把剧本文本拆解为可直接拍摄与售卖的分镜脚本：以镜头语言、画面拆解、
节奏与一致性为核心，不改变剧情与台词。

## 核心要求
1. 镜头拆解：按剧情拆为 6-10 个分镜，每个镜头必须包含：镜号、景别（远景/全景/中景/近景/特写/俯拍/仰拍）、
   运镜（推/拉/摇/移/跟/固定）、画面内容（人物动作 + 环境 + 光线）、台词/音效、时长（秒）、转场方式。
2. 节奏控制：开场用远景交代环境；情绪转折处用景别变化强调；高潮用特写或快切；
   每场戏至少一个情绪锚点镜头。
3. 一致性锚定：同一角色的外观（发型/服装/体型）、同一场景的调色与光线逻辑在全部镜头中保持一致，
   禁止前后矛盾；对白须与剧本原意一致。
4. 情绪传达：每个镜头标注情绪基调（平静/紧张/温馨/压抑/爆发等），画面描述必须服务于该情绪。
5. 文本为主：输出纯文本分镜脚本，不做图片/视频生成指令。

## 输出格式
开头一行「分镜脚本 · 共 N 镜」，随后每镜一段：
【镜 N】景别｜运镜｜情绪
画面：……
台词/音效：……
时长：X 秒｜转场：……

## 禁忌
- 禁止改变剧本剧情与台词原意；禁止漏掉关键情节节点；
- 禁止角色外观/场景光线前后不一致；禁止出现与剧情无关的镜头。
', '0', 'e_migration', 'E-分镜类 skill 示例（storyboard 单独一类）')
ON DUPLICATE KEY UPDATE
  skill_type = VALUES(skill_type), file_name = VALUES(file_name),
  file_content = VALUES(file_content), status = VALUES(status),
  remark = VALUES(remark), update_time = NOW();

-- ---------------------------------------------------------------------
-- 4. verify 闸门
-- ---------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill'
                     AND COLUMN_NAME = 'skill_type');

SET @write_cnt = (SELECT COUNT(*) FROM sys_ai_skill WHERE skill_type = 'write');
SET @sb_cnt = (SELECT COUNT(*) FROM sys_ai_skill WHERE skill_type = 'storyboard');
SET @seed_sb = (SELECT COUNT(*) FROM sys_ai_skill WHERE skill_name = '分镜导演（镜头语言）');

SET @fail_cnt = (@col_exists = 1) + (@write_cnt >= 2) + (@sb_cnt >= 1) + (@seed_sb = 1);
SET @fail_cnt = 4 - @fail_cnt;

SELECT 'CHECK' AS check_result,
       CONCAT('col=', @col_exists, ', write=', @write_cnt, ', storyboard=', @sb_cnt, ', seed_sb=', @seed_sb) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('sys_ai_skill.skill_type 未达预期：col=', @col_exists,
              ', write=', @write_cnt, ', storyboard=', @sb_cnt, ', seed_sb=', @seed_sb) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, skill_type 已就绪且分镜类 skill 已入库' AS detail
WHERE @fail_cnt = 0;
