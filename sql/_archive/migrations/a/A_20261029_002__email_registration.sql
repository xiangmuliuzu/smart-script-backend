-- Email verification reuses the existing one-time code storage with a namespaced email key.
ALTER TABLE app_sms_code MODIFY COLUMN phone VARCHAR(64) NOT NULL COMMENT '手机号或邮箱验证码标识';

-- Only email registrations use this table; existing sys_user email values remain untouched.
CREATE TABLE IF NOT EXISTS app_user_email (
  user_id BIGINT NOT NULL,
  email VARCHAR(50) NOT NULL,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_app_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱注册账号唯一绑定';
