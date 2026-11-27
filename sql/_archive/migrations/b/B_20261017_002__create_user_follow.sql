-- =====================================================================
-- A5 用户社交 · 关注关系表（sys_user_follow 唯一键补齐）
--
-- 背景：个人主页需要「关注 / 粉丝」关系（关注数、粉丝数按真实关注关系计算）。
--   云端已有 sys_user_follow 表但缺唯一键，无法阻止重复关注；本迁移补齐
--   (follower_id, followee_id) 唯一键，并保证全新环境也能建出该表。
--
-- 口径：
--   * follower_id 关注 followee_id（关注者 → 被关注者）。
--   * 唯一键 uk_follower_followee 使重复关注幂等（INSERT IGNORE）。
--   * 关注自己由服务层拒绝，不入库。
--
-- 幂等：CREATE TABLE IF NOT EXISTS；唯一键用 information_schema 判定后按需 ALTER。
--   可重复执行，不改动任何既有列与数据。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261017_002__create_user_follow.sql"
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 建表（全新环境）；已存在则保留（云端已有该表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_follow
(
    id          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    follower_id bigint       NOT NULL COMMENT '关注者用户ID',
    followee_id bigint       NOT NULL COMMENT '被关注者用户ID',
    created_at  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    create_by   varchar(64)  NULL     COMMENT '创建者',
    create_time datetime     NULL     COMMENT '创建时间',
    update_by   varchar(64)  NULL     COMMENT '更新者',
    update_time datetime     NULL     COMMENT '更新时间',
    remark      varchar(500) NULL     COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_follower (follower_id),
    KEY idx_followee (followee_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '用户关注关系';

-- ---------------------------------------------------------------------
-- 2. 补齐唯一键（幂等：仅当不存在时 ALTER）
-- ---------------------------------------------------------------------
SET @has_uk = (SELECT COUNT(*)
               FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'sys_user_follow'
                 AND INDEX_NAME = 'uk_follower_followee');

SET @ddl = IF(@has_uk = 0,
    'ALTER TABLE sys_user_follow ADD UNIQUE KEY uk_follower_followee (follower_id, followee_id)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. verify 闸门
-- ---------------------------------------------------------------------
-- 表存在（应 1）
SET @tbl_exists = (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_follow');

-- 唯一键存在且为复合 2 列（应 1）
SET @uk_ok = (SELECT IF(COUNT(*) = 2 AND MIN(NON_UNIQUE) = 0, 1, 0)
              FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'sys_user_follow'
                AND INDEX_NAME = 'uk_follower_followee');

SET @fail_cnt = (@tbl_exists = 1) + (@uk_ok = 1);
SET @fail_cnt = 2 - @fail_cnt;

SELECT 'CHECK' AS check_result,
       CONCAT('table=', @tbl_exists, ', uk_follower_followee=', @uk_ok) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('关注表未达预期：table=', @tbl_exists, ', uk=', @uk_ok) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, sys_user_follow 与唯一键已就绪' AS detail
WHERE @fail_cnt = 0;
