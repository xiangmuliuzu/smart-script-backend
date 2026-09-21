-- =====================================================================
-- A2_20260921_001__a2_migrate.sql
-- 用途: A2 数据库兼容迁移（结构加固 + 兼容规范 + App 表骨架）
-- 适用: MySQL 8.0.36
-- 前置:
--   1) 已执行 A2_20260921_001__a2_precheck.sql 且 fail_cnt=0
--   2) 目标库为可隔离测试库，或已批准的开发库维护窗口
--   3) 禁止对生产库或未备份共享库直接执行
-- 事务: 单事务包裹 DDL/DML；MySQL DDL 隐式提交，失败时以 verify/rollback 收敛
-- 可重复: 基本可重复（IF NOT EXISTS / 条件性 ALTER）；history 记录使用 INSERT IGNORE
-- 锁表影响: ALTER sys_user 在行数很小时可接受；大库需窗口期
-- 回滚: U20260921_001__a2_rollback.sql
-- 禁止: 执行 ry_20260320.sql 全量覆盖；禁止重编号 user_id
-- =====================================================================

SET NAMES utf8mb4;
SET @a2_version := 'A2_20260921_001';

SELECT 'MIGRATE_START' AS step, @a2_version AS version, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 0) 迁移历史表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS a2_migration_history (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  version       VARCHAR(64)  NOT NULL COMMENT '迁移版本',
  purpose       VARCHAR(255) NOT NULL DEFAULT '' COMMENT '用途',
  applied_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  rolled_back   TINYINT(1)   NOT NULL DEFAULT 0,
  rollback_at   DATETIME     NULL,
  remark        VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_a2_history_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A2 迁移历史';

-- ---------------------------------------------------------------------
-- 1) sys_user 兼容加固
-- ---------------------------------------------------------------------
-- 1.1 密码列扩到 255（兼容旧实体设计；不修改已有散列）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'password' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 255
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sys_user MODIFY COLUMN password varchar(255) DEFAULT '''' COMMENT ''密码BCrypt''',
  'SELECT ''password_col_already_compatible'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.1b user_type 扩到 varchar(20) 以容纳旧枚举，映射后仍保留两位编码值
SET @ut_len := (
  SELECT IFNULL(CHARACTER_MAXIMUM_LENGTH,0) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'user_type'
);
SET @sql := IF(@ut_len < 20,
  'ALTER TABLE sys_user MODIFY COLUMN user_type varchar(20) DEFAULT ''00'' COMMENT ''用户类型00/01/02/03''',
  'SELECT ''user_type_already_wide'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 空字符串手机号规范为 NULL（为 UK 做准备）
UPDATE sys_user
SET phonenumber = NULL
WHERE phonenumber IS NOT NULL AND TRIM(phonenumber) = '';

-- 1.3 兼容映射：旧 user_type 字符串 → 若依两位编码（幂等）
UPDATE sys_user SET user_type = '00' WHERE user_type = 'admin';
UPDATE sys_user SET user_type = '01' WHERE user_type IN ('user','app');
UPDATE sys_user SET user_type = '02' WHERE user_type IN ('creator','author');
UPDATE sys_user SET user_type = '03' WHERE user_type IN ('client','customer');
UPDATE sys_user SET user_type = '00' WHERE user_type IS NULL OR TRIM(user_type) = '';

-- 1.4 status 规范：
-- 若依语义已是 char('0' 正常 / '1' 停用)，不得把 '1' 误改成 '0'。
-- 仅处理 NULL 与非法值；旧 TINYINT 语义（1 正常）只在导入夹具/来源列明确为旧枚举时由导入映射处理。
UPDATE sys_user SET status = '0' WHERE status IS NULL OR status NOT IN ('0','1');

-- 1.5 兼容映射：is_deleted 语义若通过数据观察到旧值写入 del_flag（1 删除）则规范为 2
UPDATE sys_user SET del_flag = '2' WHERE del_flag = '1';
UPDATE sys_user SET del_flag = '0' WHERE del_flag IS NULL OR TRIM(del_flag) = '';

-- 1.6 空昵称回退 user_name
UPDATE sys_user SET nick_name = user_name WHERE nick_name IS NULL OR TRIM(nick_name) = '';

-- 1.7 唯一键（在 precheck 通过后建立）
SET @uk_user := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
    AND INDEX_NAME = 'uk_sys_user_user_name'
);
SET @sql := IF(@uk_user = 0,
  'ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_user_name (user_name)',
  'SELECT ''uk_sys_user_user_name_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @uk_phone := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
    AND INDEX_NAME = 'uk_sys_user_phonenumber'
);
SET @sql := IF(@uk_phone = 0,
  'ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_phonenumber (phonenumber)',
  'SELECT ''uk_sys_user_phonenumber_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.8 登录相关列空值规范
UPDATE sys_user SET login_ip = '' WHERE login_ip IS NULL;
UPDATE sys_user SET email = '' WHERE email IS NULL;
UPDATE sys_user SET avatar = '' WHERE avatar IS NULL;
UPDATE sys_user SET remark = IFNULL(remark, '');

-- ---------------------------------------------------------------------
-- 2) App / 用户域业务表骨架（规格 6.2）
--    只建结构，不在 A2 写业务数据
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS app_sms_code (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  phone           VARCHAR(20)   NOT NULL COMMENT '手机号',
  scene           VARCHAR(32)   NOT NULL COMMENT '场景 LOGIN/REGISTER/...',
  code_hash       VARCHAR(64)   NOT NULL COMMENT '验证码散列，不存明文',
  request_ip      VARCHAR(45)   NULL,
  failed_attempts INT           NOT NULL DEFAULT 0,
  used_at         DATETIME      NULL,
  expires_at      DATETIME      NOT NULL,
  create_by       VARCHAR(64)   NOT NULL DEFAULT 'system',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by       VARCHAR(64)   NOT NULL DEFAULT '',
  update_time     DATETIME      NULL,
  remark          VARCHAR(500)  NULL,
  PRIMARY KEY (id),
  KEY idx_app_sms_phone_scene_created (phone, scene, create_time),
  KEY idx_app_sms_ip_created (request_ip, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App 短信验证码';

CREATE TABLE IF NOT EXISTS app_refresh_session (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  user_id         BIGINT        NOT NULL COMMENT 'sys_user.user_id',
  token_hash      VARCHAR(64)   NOT NULL COMMENT 'Refresh Token 散列',
  family_id       VARCHAR(64)   NOT NULL COMMENT 'token family，重放检测',
  device_id       VARCHAR(64)   NULL,
  device_name     VARCHAR(64)   NULL,
  expires_at      DATETIME      NOT NULL,
  revoked_at      DATETIME      NULL,
  revoked_reason  VARCHAR(64)   NULL,
  replaced_by_id  BIGINT        NULL COMMENT '轮换后新会话 id',
  create_by       VARCHAR(64)   NOT NULL DEFAULT 'system',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by       VARCHAR(64)   NOT NULL DEFAULT '',
  update_time     DATETIME      NULL,
  remark          VARCHAR(500)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_refresh_token_hash (token_hash),
  KEY idx_app_refresh_user_revoked (user_id, revoked_at),
  KEY idx_app_refresh_family (family_id),
  KEY idx_app_refresh_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App Refresh Token 会话';

CREATE TABLE IF NOT EXISTS app_user_consent (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  user_id           BIGINT        NOT NULL,
  agreement_type    VARCHAR(32)   NOT NULL COMMENT 'USER_AGREEMENT/PRIVACY_POLICY',
  agreement_version VARCHAR(32)   NOT NULL,
  accepted_at       DATETIME      NOT NULL,
  ip                VARCHAR(45)   NULL,
  device_id         VARCHAR(64)   NULL,
  create_by         VARCHAR(64)   NOT NULL DEFAULT 'system',
  create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by         VARCHAR(64)   NOT NULL DEFAULT '',
  update_time       DATETIME      NULL,
  remark            VARCHAR(500)  NULL,
  PRIMARY KEY (id),
  KEY idx_app_consent_user_type (user_id, agreement_type, agreement_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协议与隐私确认留痕';

CREATE TABLE IF NOT EXISTS app_user_oauth (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  user_id     BIGINT        NOT NULL,
  provider    VARCHAR(16)   NOT NULL COMMENT 'WECHAT/QQ',
  open_id     VARCHAR(64)   NOT NULL,
  union_id    VARCHAR(64)   NULL,
  unbound_at  DATETIME      NULL,
  create_by   VARCHAR(64)   NOT NULL DEFAULT 'system',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by   VARCHAR(64)   NOT NULL DEFAULT '',
  update_time DATETIME      NULL,
  remark      VARCHAR(500)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_oauth_provider_open (provider, open_id),
  KEY idx_app_oauth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方账号绑定预留';

CREATE TABLE IF NOT EXISTS user_real_name_auth (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  user_id            BIGINT        NOT NULL,
  real_name_mask     VARCHAR(64)   NULL COMMENT '姓名掩码或加密引用',
  id_number_mask     VARCHAR(64)   NULL COMMENT '身份证掩码',
  material_ref       VARCHAR(255)  NULL COMMENT '材料文件ID/引用，不存永久公开URL',
  status             VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
  auditor_id         BIGINT        NULL,
  audited_at         DATETIME      NULL,
  reject_reason      VARCHAR(500)  NULL,
  create_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
  create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by          VARCHAR(64)   NOT NULL DEFAULT '',
  update_time        DATETIME      NULL,
  remark             VARCHAR(500)  NULL,
  PRIMARY KEY (id),
  KEY idx_user_realname_user (user_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实名认证申请';

CREATE TABLE IF NOT EXISTS user_phone_change_log (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  user_id        BIGINT        NOT NULL,
  old_phone_mask VARCHAR(32)   NULL,
  new_phone_mask VARCHAR(32)   NULL,
  result         VARCHAR(32)   NOT NULL DEFAULT 'SUCCESS',
  client_ip      VARCHAR(45)   NULL,
  device_id      VARCHAR(64)   NULL,
  create_by      VARCHAR(64)   NOT NULL DEFAULT 'system',
  create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by      VARCHAR(64)   NOT NULL DEFAULT '',
  update_time    DATETIME      NULL,
  remark         VARCHAR(500)  NULL COMMENT '不存验证码',
  PRIMARY KEY (id),
  KEY idx_user_phone_change_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='换绑手机号审计';

CREATE TABLE IF NOT EXISTS user_author_capability (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  user_id      BIGINT        NOT NULL,
  enabled      TINYINT(1)    NOT NULL DEFAULT 0,
  operator_id  BIGINT        NULL,
  reason       VARCHAR(255)  NULL,
  operated_at  DATETIME      NULL,
  create_by    VARCHAR(64)   NOT NULL DEFAULT 'system',
  create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by    VARCHAR(64)   NOT NULL DEFAULT '',
  update_time  DATETIME      NULL,
  remark       VARCHAR(500)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_author_capability_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作者能力开关';

CREATE TABLE IF NOT EXISTS user_notification (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  type          VARCHAR(32)   NOT NULL COMMENT 'SYSTEM/AUDIT/TRADE/WELFARE',
  title         VARCHAR(200)  NOT NULL,
  body          VARCHAR(2000) NULL,
  template_code VARCHAR(64)   NULL,
  template_params VARCHAR(2000) NULL,
  biz_ref       VARCHAR(64)   NULL,
  create_by     VARCHAR(64)   NOT NULL DEFAULT 'system',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by     VARCHAR(64)   NOT NULL DEFAULT '',
  update_time   DATETIME      NULL,
  remark        VARCHAR(500)  NULL,
  PRIMARY KEY (id),
  KEY idx_user_notification_type_time (type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息主体';

CREATE TABLE IF NOT EXISTS user_notification_receiver (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  notification_id BIGINT        NOT NULL,
  user_id         BIGINT        NOT NULL,
  read_at         DATETIME      NULL,
  deleted_flag    TINYINT(1)    NOT NULL DEFAULT 0,
  create_by       VARCHAR(64)   NOT NULL DEFAULT 'system',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by       VARCHAR(64)   NOT NULL DEFAULT '',
  update_time     DATETIME      NULL,
  remark          VARCHAR(500)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_notification_receiver (notification_id, user_id),
  KEY idx_user_notification_receiver_user (user_id, deleted_flag, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息收件箱';

CREATE TABLE IF NOT EXISTS user_notification_preference (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  user_id      BIGINT        NOT NULL,
  channel      VARCHAR(32)   NOT NULL COMMENT 'IN_APP/PUSH/...',
  type         VARCHAR(32)   NOT NULL,
  enabled      TINYINT(1)    NOT NULL DEFAULT 1,
  create_by    VARCHAR(64)   NOT NULL DEFAULT 'system',
  create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by    VARCHAR(64)   NOT NULL DEFAULT '',
  update_time  DATETIME      NULL,
  remark       VARCHAR(500)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_notification_pref (user_id, channel, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知偏好';

CREATE TABLE IF NOT EXISTS user_feedback (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  user_id       BIGINT        NOT NULL,
  category      VARCHAR(32)   NOT NULL DEFAULT 'OTHER',
  content       VARCHAR(2000) NOT NULL,
  attachment_ref VARCHAR(255) NULL,
  status        VARCHAR(32)   NOT NULL DEFAULT 'OPEN',
  reply         VARCHAR(2000) NULL,
  handler_id    BIGINT        NULL,
  handled_at    DATETIME      NULL,
  create_by     VARCHAR(64)   NOT NULL DEFAULT 'system',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by     VARCHAR(64)   NOT NULL DEFAULT '',
  update_time   DATETIME      NULL,
  remark        VARCHAR(500)  NULL,
  PRIMARY KEY (id),
  KEY idx_user_feedback_user (user_id, create_time),
  KEY idx_user_feedback_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意见反馈';

-- ---------------------------------------------------------------------
-- 3) 登记版本
-- ---------------------------------------------------------------------
INSERT INTO a2_migration_history (version, purpose, remark)
VALUES (
  @a2_version,
  'sys_user unique keys + phone null-normalize + user_type/status map + app/user domain tables',
  'A2 database compatibility migration'
)
ON DUPLICATE KEY UPDATE
  purpose = VALUES(purpose),
  applied_at = NOW(),
  rolled_back = 0,
  rollback_at = NULL,
  remark = VALUES(remark);

SELECT 'MIGRATE_DONE' AS step, @a2_version AS version,
       (SELECT COUNT(*) FROM sys_user) AS user_count,
       (SELECT COUNT(*) FROM a2_migration_history WHERE version = @a2_version) AS history_rows;
