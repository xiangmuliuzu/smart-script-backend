-- =====================================================================
-- A4 用户中心 · 创作者申请（新增 user_creator_application 表 + 管理端菜单）
--
-- 背景：App 普通用户需要「申请成为创作者」的通道，由 PC 运维端审核；通过后
--   自动开通作者能力（user_author_capability）。此前系统只有「作者能力」开关
--   （A4 手动开通），没有面向用户的申请与审核流程。
--
-- 口径：
--   * 申请状态机 PENDING -> APPROVED / REJECTED；驳回后可重新提交（新增行，
--     保留完整审计链，与实名申请一致，故 user_id 上不设唯一键，最新=最大 id）。
--   * 审核通过时由服务层调用 AuthorCapabilityService.setEnabled 开通作者能力；
--     本迁移只建表与菜单，不改任何既有行，也**不改写 sys_user.user_type**。
--   * 审批动作/期望状态沿用实名审核的枚举（APPROVE/REJECT、PENDING）。
--
-- 幂等：CREATE TABLE IF NOT EXISTS；菜单用 INSERT ... ON DUPLICATE KEY UPDATE；
--   超管授权用 INSERT IGNORE。可重复执行，不触碰任何既有行。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261017_001__create_creator_application.sql"
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 建表（创作者申请：驳回后重提新增行，最新=最大 id）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_creator_application
(
    id            bigint       NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    user_id       bigint       NOT NULL COMMENT '申请人用户ID',
    pen_name      varchar(50)  NOT NULL COMMENT '笔名',
    direction     varchar(200) NOT NULL COMMENT '创作方向/擅长类型',
    portfolio     varchar(500) NULL     COMMENT '代表作品',
    intro         varchar(500) NOT NULL COMMENT '申请说明',
    contact       varchar(100) NOT NULL COMMENT '联系方式',
    status        varchar(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/APPROVED/REJECTED',
    auditor_id    bigint       NULL     COMMENT '审核人ID',
    audited_at    datetime     NULL     COMMENT '审核时间',
    reject_reason varchar(500) NULL     COMMENT '驳回原因',
    create_by     varchar(64)  NOT NULL DEFAULT 'system' COMMENT '创建者',
    create_time   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     varchar(64)  NOT NULL DEFAULT '' COMMENT '更新者',
    update_time   datetime     NULL     COMMENT '更新时间',
    remark        varchar(500) NULL     COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_user_creator_application_user (user_id, status, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '创作者申请';

-- ---------------------------------------------------------------------
-- 2. 管理端菜单（用户中心 3000 下的页面 + 按钮 + 超管授权）
--    3016 页面「创作者审核」；3017 查询按钮；3018 审核按钮
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark) VALUES
(3016, '创作者审核',       3000, 3, 'creator-apply', 'user/creator-apply/index', '', 'A4CreatorApply', 1, 0, 'C', '0', '0', 'user:creatorapp:list',  'star', 'a4_migration', 'A4-创作者申请审核'),
(3017, '创作者申请查询', 3016, 1, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:creatorapp:query', '#',    'a4_migration', 'A4-创作者申请查询'),
(3018, '创作者申请审核', 3016, 2, '#', NULL, '', NULL, 1, 0, 'F', '0', '0', 'user:creatorapp:audit', '#',    'a4_migration', 'A4-创作者申请审核决定')
ON DUPLICATE KEY UPDATE
  menu_name=VALUES(menu_name), parent_id=VALUES(parent_id), order_num=VALUES(order_num),
  path=VALUES(path), component=VALUES(component), route_name=VALUES(route_name),
  perms=VALUES(perms), icon=VALUES(icon), remark=VALUES(remark);

-- 超级管理员授权（role_id=1）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 3016);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 3017);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 3018);

-- ---------------------------------------------------------------------
-- 3. verify 闸门
-- ---------------------------------------------------------------------
-- 表存在（应 1）
SET @tbl_exists = (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_creator_application');

-- 业务列齐备（应 16）
SET @col_cnt = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_creator_application'
                  AND COLUMN_NAME IN ('id','user_id','pen_name','direction','portfolio','intro','contact',
                                      'status','auditor_id','audited_at','reject_reason',
                                      'create_by','create_time','update_by','update_time','remark'));

-- 菜单行齐备（应 3）
SET @menu_cnt = (SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (3016, 3017, 3018));

-- 超管授权齐备（应 3）
SET @grant_cnt = (SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id IN (3016, 3017, 3018));

SET @fail_cnt = (@tbl_exists = 1) + (@col_cnt = 16) + (@menu_cnt = 3) + (@grant_cnt = 3);
SET @fail_cnt = 4 - @fail_cnt;

SELECT 'CHECK' AS check_result,
       CONCAT('table=', @tbl_exists, ', columns=', @col_cnt, ', menus=', @menu_cnt, ', grants=', @grant_cnt) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('创作者申请未达预期：table=', @tbl_exists, ', columns=', @col_cnt,
              ', menus=', @menu_cnt, ', grants=', @grant_cnt) AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, user_creator_application 与创作者审核菜单/授权已就绪' AS detail
WHERE @fail_cnt = 0;
