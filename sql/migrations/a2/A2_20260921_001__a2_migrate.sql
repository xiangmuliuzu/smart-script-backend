-- =====================================================================
-- A2_20260921_001__a2_migrate.sql
-- 用途: A2 数据库兼容迁移（结构加固 + 兼容映射 + App 表骨架）
-- 适用: MySQL 8.0.36
-- 前置:
--   1) A2_20260921_001__a2_precheck.sql 最终 SUMMARY 为 PASS
--   2) 目标库为隔离测试库，或已批准维护窗口
--   3) 禁止对生产库/未备份共享库直接执行
-- 事务: MySQL DDL 隐式提交；失败以 verify/rollback 收敛
-- 可重复: 是（IF NOT EXISTS / 条件 ALTER；history UPSERT；preimage 仅在无活动版本时重拍）
-- 锁表影响: sys_user ALTER/UPDATE；大库需窗口
-- 回滚: U20260921_001__a2_rollback.sql
-- 关键设计:
--   - a2_table_ownership 记录本版本是否新建业务表；回滚只删 CREATED
--   - a2_sys_user_preimage 在变更前保存字段原值；回滚据此逆变换
-- 禁止: 执行 ry_20260320.sql 全量覆盖；禁止重编号 user_id
-- =====================================================================

SET NAMES utf8mb4;
SET @a2_version := 'A2_20260921_001';

SELECT 'MIGRATE_START' AS step, @a2_version AS version, DATABASE() AS db_name, NOW() AS ts;

-- ---------------------------------------------------------------------
-- 0) 控制表：历史 / 表归属 / sys_user 迁移前快照
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

CREATE TABLE IF NOT EXISTS a2_table_ownership (
  version     VARCHAR(64) NOT NULL,
  table_name  VARCHAR(64) NOT NULL,
  action      VARCHAR(16) NOT NULL COMMENT 'CREATED=本版本新建; PREEXISTING=迁移前已存在',
  noted_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (version, table_name),
  KEY idx_a2_own_action (version, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='A2 表归属：回滚只删除 CREATED';

CREATE TABLE IF NOT EXISTS a2_sys_user_preimage (
  user_id      BIGINT       NOT NULL,
  phonenumber  VARCHAR(20)  NULL,
  user_type    VARCHAR(20)  NULL,
  status       VARCHAR(4)   NULL,
  del_flag     VARCHAR(4)   NULL,
  nick_name    VARCHAR(64)  NULL,
  email        VARCHAR(100) NULL,
  avatar       VARCHAR(255) NULL,
  login_ip     VARCHAR(128) NULL,
  remark       VARCHAR(500) NULL,
  password     VARCHAR(255) NULL,
  snapshot_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='sys_user 迁移前字段快照（供回滚逆变换）';

-- 若本版本当前无“未回滚”记录，则重拍快照（避免把已迁移值当成原值）
SET @active_hist := (
  SELECT COUNT(*) FROM a2_migration_history
  WHERE version = @a2_version AND rolled_back = 0
);
SET @sql := IF(@active_hist = 0,
  'DELETE FROM a2_sys_user_preimage',
  'SELECT ''keep_existing_preimage'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO a2_sys_user_preimage
  (user_id, phonenumber, user_type, status, del_flag, nick_name, email, avatar, login_ip, remark, password, snapshot_at)
SELECT user_id, phonenumber, user_type, status, del_flag, nick_name, email, avatar, login_ip, remark, password, NOW()
FROM sys_user su
WHERE NOT EXISTS (SELECT 1 FROM a2_sys_user_preimage LIMIT 1);

SELECT 'PREIMAGE' AS step, COUNT(*) AS preimage_rows FROM a2_sys_user_preimage;

-- ---------------------------------------------------------------------
-- 辅助：登记业务表归属（只在“表尚不存在”时标记 CREATED）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_a2_domain_tables;
CREATE TEMPORARY TABLE tmp_a2_domain_tables (
  table_name VARCHAR(64) PRIMARY KEY
) ENGINE=MEMORY;
INSERT INTO tmp_a2_domain_tables (table_name) VALUES
  ('app_sms_code'),('app_refresh_session'),('app_user_consent'),('app_user_oauth'),
  ('user_real_name_auth'),('user_phone_change_log'),('user_author_capability'),
  ('user_notification'),('user_notification_receiver'),('user_notification_preference'),
  ('user_feedback');

-- 归属登记幂等规则：
--   仅当 (version, table_name) 尚无记录时写入。
--   已有 CREATED 不得在重跑时被改写为 PREEXISTING（否则回滚会漏删本版本建的表）。
--   已有 PREEXISTING 同样保持不变。
INSERT INTO a2_table_ownership (version, table_name, action, noted_at)
SELECT @a2_version,
       CONVERT(t.table_name USING utf8mb4) COLLATE utf8mb4_general_ci,
       IF(i.TABLE_NAME IS NULL, 'CREATED', 'PREEXISTING'),
       NOW()
FROM tmp_a2_domain_tables t
LEFT JOIN information_schema.TABLES i
  ON i.TABLE_SCHEMA = DATABASE()
 AND CONVERT(i.TABLE_NAME USING utf8mb4) COLLATE utf8mb4_general_ci
     = CONVERT(t.table_name USING utf8mb4) COLLATE utf8mb4_general_ci
WHERE NOT EXISTS (
  SELECT 1 FROM a2_table_ownership o
  WHERE CONVERT(o.version USING utf8mb4) COLLATE utf8mb4_general_ci = CONVERT(@a2_version USING utf8mb4) COLLATE utf8mb4_general_ci
    AND CONVERT(o.table_name USING utf8mb4) COLLATE utf8mb4_general_ci
        = CONVERT(t.table_name USING utf8mb4) COLLATE utf8mb4_general_ci
);

-- ---------------------------------------------------------------------
-- 1) sys_user 兼容加固（先快照后变更）
-- ---------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'password' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH >= 255
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE sys_user MODIFY COLUMN password varchar(255) DEFAULT '''' COMMENT ''密码BCrypt''',
  'SELECT ''password_col_already_compatible'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ut_len := (
  SELECT IFNULL(CHARACTER_MAXIMUM_LENGTH,0) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'user_type'
);
SET @sql := IF(@ut_len < 20,
  'ALTER TABLE sys_user MODIFY COLUMN user_type varchar(20) DEFAULT ''00'' COMMENT ''用户类型00/01/02/03''',
  'SELECT ''user_type_already_wide'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 空手机号 -> NULL
UPDATE sys_user
SET phonenumber = NULL
WHERE phonenumber IS NOT NULL AND TRIM(phonenumber) = '';

-- 1.3 旧 user_type 字符串 -> 两位编码
UPDATE sys_user SET user_type = '00' WHERE user_type = 'admin';
UPDATE sys_user SET user_type = '01' WHERE user_type IN ('user','app');
UPDATE sys_user SET user_type = '02' WHERE user_type IN ('creator','author');
UPDATE sys_user SET user_type = '03' WHERE user_type IN ('client','customer');
UPDATE sys_user SET user_type = '00' WHERE user_type IS NULL OR TRIM(user_type) = '';

-- 1.4 status：仅规范化 NULL/非法值（不把若依 '1' 停用改成 '0'）
UPDATE sys_user SET status = '0' WHERE status IS NULL OR status NOT IN ('0','1');

-- 1.5 del_flag：旧逻辑删除 1 -> 若依 2
UPDATE sys_user SET del_flag = '2' WHERE del_flag = '1';
UPDATE sys_user SET del_flag = '0' WHERE del_flag IS NULL OR TRIM(del_flag) = '';

-- 1.6 空昵称回退
UPDATE sys_user SET nick_name = user_name WHERE nick_name IS NULL OR TRIM(nick_name) = '';

-- 1.7 空值规范
UPDATE sys_user SET login_ip = '' WHERE login_ip IS NULL;
UPDATE sys_user SET email = '' WHERE email IS NULL;
UPDATE sys_user SET avatar = '' WHERE avatar IS NULL;
UPDATE sys_user SET remark = IFNULL(remark, '');

-- 1.8 唯一键
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

-- ---------------------------------------------------------------------
-- 2) App / 用户域表骨架（仅当不存在时创建；归属已登记）
-- ---------------------------------------------------------------------

SET @has := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_sms_code');
SET @sql := IF(@has=0, 'CREATE TABLE app_sms_code (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  phone           VARCHAR(20)   NOT NULL COMMENT ''手机号'',
  scene           VARCHAR(32)   NOT NULL,
  code_hash       VARCHAR(64)   NOT NULL,
  request_ip      VARCHAR(45)   NULL,
  failed_attempts INT           NOT NULL DEFAULT 0,
  used_at         DATETIME      NULL,
  expires_at      DATETIME      NOT NULL,
  create_by       VARCHAR(64)   NOT NULL DEFAULT ''system'',
  create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by       VARCHAR(64)   NOT NULL DEFAULT '''',
  update_time     DATETIME      NULL,
  remark          VARCHAR(500)  NULL,
  PRIMARY KEY (id),
  KEY idx_app_sms_phone_scene_created (phone, scene, create_time),
  KEY idx_app_sms_ip_created (request_ip, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4', 'SELECT ''app_sms_code_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_refresh_session');
SET @sql := IF(@has=0, 'CREATE TABLE app_refresh_session (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  family_id VARCHAR(64) NOT NULL,
  device_id VARCHAR(64) NULL,
  device_name VARCHAR(64) NULL,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  revoked_reason VARCHAR(64) NULL,
  replaced_by_id BIGINT NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT ''system'',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '''',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_refresh_token_hash (token_hash),
  KEY idx_app_refresh_user_revoked (user_id, revoked_at),
  KEY idx_app_refresh_family (family_id),
  KEY idx_app_refresh_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4', 'SELECT ''app_refresh_session_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user_consent');
SET @sql := IF(@has=0, 'CREATE TABLE app_user_consent (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  agreement_type VARCHAR(32) NOT NULL,
  agreement_version VARCHAR(32) NOT NULL,
  accepted_at DATETIME NOT NULL,
  ip VARCHAR(45) NULL,
  device_id VARCHAR(64) NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT ''system'',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '''',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_app_consent_user_type (user_id, agreement_type, agreement_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4', 'SELECT ''app_user_consent_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user_oauth');
SET @sql := IF(@has=0, 'CREATE TABLE app_user_oauth (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  provider VARCHAR(16) NOT NULL,
  open_id VARCHAR(64) NOT NULL,
  union_id VARCHAR(64) NULL,
  unbound_at DATETIME NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT ''system'',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '''',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_oauth_provider_open (provider, open_id),
  KEY idx_app_oauth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4', 'SELECT ''app_user_oauth_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_real_name_auth');
SET @sql := IF(@has=0, 'CREATE TABLE user_real_name_auth (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  real_name_mask VARCHAR(64) NULL,
  id_number_mask VARCHAR(64) NULL,
  material_ref VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL DEFAULT ''PENDING'',
  auditor_id BIGINT NULL,
  audited_at DATETIME NULL,
  reject_reason VARCHAR(500) NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT ''system'',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '''',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_user_realname_user (user_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4', 'SELECT ''user_real_name_auth_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_phone_change_log');
SET @sql := IF(@has=0, 'CREATE TABLE user_phone_change_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  old_phone_mask VARCHAR(32) NULL,
  new_phone_mask VARCHAR(32) NULL,
  result VARCHAR(32) NOT NULL DEFAULT ''SUCCESS'',
  client_ip VARCHAR(45) NULL,
  device_id VARCHAR(64) NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT ''system'',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '''',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_user_phone_change_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4', 'SELECT ''user_phone_change_log_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_author_capability');
SET @sql := IF(@has=0, 'CREATE TABLE user_author_capability (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  operator_id BIGINT NULL,
  reason VARCHAR(255) NULL,
  operated_at DATETIME NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT ''system'',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '''',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_author_capability_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4', 'SELECT ''user_author_capability_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_notification');
SET @sql := IF(@has=0, 'CREATE TABLE user_notification (
  id BIGINT NOT NULL AUTO_INCREMENT,
  type VARCHAR(32) NOT NULL,
  title VARCHAR(200) NOT NULL,
  body VARCHAR(2000) NULL,
  template_code VARCHAR(64) NULL,
  template_params VARCHAR(2000) NULL,
  biz_ref VARCHAR(64) NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT ''system'',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '''',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_user_notification_type_time (type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4', 'SELECT ''user_notification_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_notification_receiver');
SET @sql := IF(@has=0, 'CREATE TABLE user_notification_receiver (
  id BIGINT NOT NULL AUTO_INCREMENT,
  notification_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  read_at DATETIME NULL,
  deleted_flag TINYINT(1) NOT NULL DEFAULT 0,
  create_by VARCHAR(64) NOT NULL DEFAULT ''system'',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '''',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_notification_receiver (notification_id, user_id),
  KEY idx_user_notification_receiver_user (user_id, deleted_flag, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4', 'SELECT ''user_notification_receiver_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_notification_preference');
SET @sql := IF(@has=0, 'CREATE TABLE user_notification_preference (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  channel VARCHAR(32) NOT NULL,
  type VARCHAR(32) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  create_by VARCHAR(64) NOT NULL DEFAULT ''system'',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '''',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_notification_pref (user_id, channel, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4', 'SELECT ''user_notification_pref_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_feedback');
SET @sql := IF(@has=0, 'CREATE TABLE user_feedback (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  category VARCHAR(32) NOT NULL DEFAULT ''OTHER'',
  content VARCHAR(2000) NOT NULL,
  attachment_ref VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL DEFAULT ''OPEN'',
  reply VARCHAR(2000) NULL,
  handler_id BIGINT NULL,
  handled_at DATETIME NULL,
  create_by VARCHAR(64) NOT NULL DEFAULT ''system'',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NOT NULL DEFAULT '''',
  update_time DATETIME NULL,
  remark VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_user_feedback_user (user_id, create_time),
  KEY idx_user_feedback_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4', 'SELECT ''user_feedback_exists'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DROP TEMPORARY TABLE IF EXISTS tmp_a2_domain_tables;

-- ---------------------------------------------------------------------
-- 3) 登记版本
-- ---------------------------------------------------------------------
INSERT INTO a2_migration_history (version, purpose, remark)
VALUES (
  @a2_version,
  'sys_user unique keys + preimage + ownership-aware app tables',
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
       (SELECT COUNT(*) FROM a2_sys_user_preimage) AS preimage_rows,
       (SELECT COUNT(*) FROM a2_table_ownership WHERE version=@a2_version AND action='CREATED') AS tables_created,
       (SELECT COUNT(*) FROM a2_table_ownership WHERE version=@a2_version AND action='PREEXISTING') AS tables_preexisting;
