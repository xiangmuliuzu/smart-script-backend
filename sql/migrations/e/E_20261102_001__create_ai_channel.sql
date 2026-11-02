-- =====================================================================
-- E 模块 · 新建 sys_ai_channel（AI 调用渠道/模型配置表）+ DeepSeek 示例 seed
--
-- 背景：管理端「AI 创作 Skill 和模型配置」页需支持配置「我们调用的 AI 的
--   API」（含 baseUrl / apiKey / 可用模型 / 默认模型 / 启停），PC 用户端与
--   App 端 AI 调用需让用户自选模型。因此新增 sys_ai_channel 渠道表：
--     * channel_name 唯一；base_url 为 OpenAI 兼容 Chat Completions 基址；
--     * api_key 可空（空时该渠道不参与调用路由，调用回退默认环境配置）；
--     * models 逗号分隔可用模型列表；default_model 须属于 models；
--     * status 0 启用 / 1 停用；sort 小者优先（未选模型时的默认渠道）。
--
-- 口径：
--   * 建表幂等：information_schema 判断 + PREPARE 动态 DDL。
--   * seed 幂等：INSERT ... ON DUPLICATE KEY UPDATE（channel_name 唯一键）。
--   * seed 的 api_key 留空，由 run 脚本从后端 .env.local 读取 AI_API_KEY 注入
--     （密钥不进迁移文件，避免提交到仓库）。
--
-- 执行：python sql/migrations/e/run_E_20261102_001.py
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 幂等建表（MySQL 无 CREATE TABLE IF NOT EXISTS 的列判断需求，直接判表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_ai_channel (
  channel_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '渠道ID',
  channel_name  VARCHAR(50)  NOT NULL COMMENT '渠道名称（唯一）',
  provider      VARCHAR(30)  NOT NULL DEFAULT 'openai-compatible' COMMENT '服务商（预留）',
  base_url      VARCHAR(200) NOT NULL COMMENT 'API 基址',
  api_key       VARCHAR(200) NOT NULL DEFAULT '' COMMENT '接口密钥（可空，空时不参与调用路由）',
  models        VARCHAR(500) NOT NULL COMMENT '可用模型列表（逗号分隔）',
  default_model VARCHAR(50)  NOT NULL COMMENT '默认模型（须属于 models）',
  status        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态：0启用 1停用',
  sort          INT          NOT NULL DEFAULT 0 COMMENT '排序（小者优先）',
  remark        VARCHAR(200) DEFAULT NULL COMMENT '备注',
  create_by     VARCHAR(32)  DEFAULT '' COMMENT '创建者',
  create_time   DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by     VARCHAR(32)  DEFAULT '' COMMENT '更新者',
  update_time   DATETIME     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (channel_id),
  UNIQUE KEY uk_ai_channel_name (channel_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用渠道/模型配置';

-- ---------------------------------------------------------------------
-- 2. DeepSeek 示例 seed（api_key 留空，由 run 脚本注入 .env.local 密钥）
-- ---------------------------------------------------------------------
INSERT INTO sys_ai_channel (channel_name, provider, base_url, api_key, models, default_model, status, sort, remark, create_by) VALUES
('DeepSeek', 'deepseek', 'https://api.deepseek.com', '',
 'deepseek-chat,deepseek-reasoner,deepseek-flash', 'deepseek-flash', '0', 1,
 'E-Default AI 渠道（密钥由迁移脚本从后端 .env.local 注入，或管理员在页面补充）', 'e_migration')
ON DUPLICATE KEY UPDATE
  provider = VALUES(provider), base_url = VALUES(base_url),
  models = VALUES(models), default_model = VALUES(default_model),
  status = VALUES(status), sort = VALUES(sort),
  remark = VALUES(remark), update_time = NOW();

-- ---------------------------------------------------------------------
-- 3. verify 闸门
-- ---------------------------------------------------------------------
SET @tbl_exists = (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_channel');

SET @seed_cnt = (SELECT COUNT(*) FROM sys_ai_channel WHERE channel_name = 'DeepSeek');
SET @cols_ok = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_channel'
                  AND COLUMN_NAME IN ('channel_id','channel_name','base_url','api_key','models','default_model','status','sort'));

SET @fail_cnt = (@tbl_exists = 1) + (@seed_cnt >= 1) + (@cols_ok = 8);
SET @fail_cnt = 3 - @fail_cnt;

SELECT 'CHECK' AS check_result,
       CONCAT('tbl=', @tbl_exists, ', seed=', @seed_cnt, ', cols_ok=', @cols_ok) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('sys_ai_channel 未达预期：tbl=', @tbl_exists,
              ', seed=', @seed_cnt, ', cols_ok=', @cols_ok) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, sys_ai_channel 已就绪且 DeepSeek 渠道已入库' AS detail
WHERE @fail_cnt = 0;
