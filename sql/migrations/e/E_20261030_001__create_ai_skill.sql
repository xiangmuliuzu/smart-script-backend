-- =====================================================================
-- E 模块 · AI Skill（导演风格 skill）配置表 + 两份示例 skill
--
-- 背景：AI 创作从「辅助写作」升级为「一条龙剧本生产」（AI 剧本 → AI 分镜），
--   skill 用于加强 AI 调用时的写作能力，明确表达用户想要的风格与类型。
--   管理员在「AI Skill 配置」页上传 skill 文件（仅 skill 名称 + 文件），
--   用户在 PC/App 端 AI 调用时选择「导演(skill)」，后端把 skill 文件内容
--   注入 AI 调用的 system prompt。
--
-- 口径：
--   * 表字段：skill_name（唯一）+ file_name（原文件名）+ file_content
--     （文件文本内容，UTF-8，TEXT）+ status（0 正常 / 1 停用）+ 审计字段。
--   * 文件以文本内容落库（便于管理员端回显与管理，不依赖本地磁盘路径）。
--   * 删除为物理删除；AI 调用侧未找到 / 已停用 skill 时按「无 skill」处理，
--     不影响既有调用。
--   * seed 两份常用示例 skill（悬疑烧脑 / 甜宠爆款），便于端上测试「导演选择」。
--
-- 幂等：CREATE TABLE IF NOT EXISTS；示例数据用 INSERT ... ON DUPLICATE KEY UPDATE
--   （skill_name 唯一键）。可重复执行，不触碰既有行。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/e/E_20261030_001__create_ai_skill.sql"
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 建表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_ai_skill
(
    id           bigint       NOT NULL AUTO_INCREMENT COMMENT 'skill ID',
    skill_name   varchar(100) NOT NULL COMMENT 'skill 名称（唯一）',
    file_name    varchar(200) NOT NULL COMMENT '上传的原始文件名',
    file_content text         NULL     COMMENT 'skill 文件文本内容（UTF-8）',
    status       char(1)      NOT NULL DEFAULT '0' COMMENT '状态：0正常 1停用',
    create_by    varchar(64)  NOT NULL DEFAULT 'system' COMMENT '创建者',
    create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by    varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
    update_time  datetime     NULL     COMMENT '更新时间',
    remark       varchar(500) NULL     COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_skill_name (skill_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = 'AI 导演风格 skill 配置';

-- ---------------------------------------------------------------------
-- 2. seed 两份常用示例 skill（悬疑烧脑 / 甜宠爆款）
-- ---------------------------------------------------------------------
INSERT INTO sys_ai_skill (skill_name, file_name, file_content, status, create_by, remark) VALUES
('悬疑烧脑导演', '悬疑烧脑导演.skill.md',
'# 导演 Skill：悬疑烧脑
## 定位
本 skill 用于指导 AI 生成高悬念、强反转、逻辑自洽的悬疑剧本与分镜。

## 核心要求
1. 悬念先行：开场即抛出钩子（案件/失踪/密室/身份疑云），前 1 幕内必须出现第一次反转。
2. 节奏控制：每幕结尾留悬念（cliffhanger），关键信息分幕释放，禁止过早剧透。
3. 逻辑闭环：所有伏笔必须回收；线索可藏但不可违背已交代的事实；人物动机可推测但必须成立。
4. 氛围塑造：多用环境细节（光线、声音、时间、封闭空间）烘托压抑与未知感。
5. 对白要求：角色语言克制，避免长篇自白；用信息差制造误会与博弈。

## 输出格式偏好
- 剧本：按「片名 / 类型 / 一句话简介 / 人物 / 分幕剧情（含反转点标注）」组织。
- 分镜：每个镜头标注景别（远景/全景/中景/近景/特写）、镜头运动（推拉摇移跟/固定）、
  画面内容、台词/音效、时长（秒）、转场方式。
- 反转点必须单独用「【反转 N】」标出，并写明前情铺垫位置。

## 禁忌
- 禁止机械降神式解谜（结尾引入全新设定）；禁止无铺垫的巧合破案；
  禁止把悬念留给续作却不给阶段性解答。
', '0', 'e_migration', 'E-悬疑烧脑导演 skill 示例'),
('甜宠爆款导演', '甜宠爆款导演.skill.md',
'# 导演 Skill：甜宠爆款
## 定位
本 skill 用于指导 AI 生成高糖分、强人设、剧情甜的甜宠向剧本与分镜。

## 核心要求
1. 人设先行：男女主必须有记忆点（身份反差/性格反差），人设互动贯穿全剧。
2. 糖点密度：每幕至少一个「心动瞬间」（对视、护短、吃醋、误会解释、双向奔赴），
   糖点需与剧情推进结合，禁止为甜而甜。
3. 情绪曲线：甜-虐-更甜；虐点服务糖点（误会不超过 2 幕必须解除）。
4. 台词风格：轻松、俏皮、有梗，撒糖对白自然不油腻。
5. 节奏：前 3 幕必须完成「初遇/心动/小冲突」，黄金 3 幕法则。

## 输出格式偏好
- 剧本：按「片名 / 类型 / 一句话简介 / 人物（含标签）/ 分幕剧情」组织，
  每幕标注糖点/虐点标签。
- 分镜：每个镜头标注景别、镜头运动、画面内容、台词/音效、时长（秒）、转场；
  心动瞬间镜头用「【糖点】」标出。

## 禁忌
- 禁止狗血降智误会；禁止油腻情话堆砌；禁止配角强行送人头；
  禁止糖点与主线脱节。
', '0', 'e_migration', 'E-甜宠爆款导演 skill 示例')
ON DUPLICATE KEY UPDATE
  file_name = VALUES(file_name), file_content = VALUES(file_content),
  status = VALUES(status), remark = VALUES(remark), update_time = NOW();

-- ---------------------------------------------------------------------
-- 3. verify 闸门
-- ---------------------------------------------------------------------
SET @tbl_exists = (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill');

SET @col_cnt = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill'
                  AND COLUMN_NAME IN ('id','skill_name','file_name','file_content','status',
                                      'create_by','create_time','update_by','update_time','remark'));

SET @seed_cnt = (SELECT COUNT(*) FROM sys_ai_skill WHERE skill_name IN ('悬疑烧脑导演','甜宠爆款导演'));

SET @fail_cnt = (@tbl_exists = 1) + (@col_cnt = 10) + (@seed_cnt = 2);
SET @fail_cnt = 3 - @fail_cnt;

SELECT 'CHECK' AS check_result,
       CONCAT('table=', @tbl_exists, ', columns=', @col_cnt, ', seeds=', @seed_cnt) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('sys_ai_skill 未达预期：table=', @tbl_exists, ', columns=', @col_cnt, ', seeds=', @seed_cnt) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, sys_ai_skill 与示例 skill 已就绪' AS detail
WHERE @fail_cnt = 0;
