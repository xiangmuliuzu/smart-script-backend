CREATE TABLE IF NOT EXISTS sys_trade_agent_session (
    session_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    user_id       BIGINT       NOT NULL COMMENT '企业用户ID（sys_user.user_id）',
    title         VARCHAR(120) NOT NULL DEFAULT '新选本对话' COMMENT '会话标题',
    brief_json    TEXT         NULL COMMENT '已提取的采购画像JSON（预算/题材/集数/诉求）',
    total_tokens  INT          NOT NULL DEFAULT 0 COMMENT '累计消耗Token',
    status        VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT 'active / archived',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id),
    KEY idx_agent_session_user_updated (user_id, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业采购选本Agent会话表';

CREATE TABLE IF NOT EXISTS sys_trade_agent_message (
    message_id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    session_id              BIGINT       NOT NULL COMMENT '会话ID',
    role                    VARCHAR(16)  NOT NULL COMMENT 'user / assistant / tool',
    content                 MEDIUMTEXT   NULL COMMENT '文本内容',
    tool_calls_json         MEDIUMTEXT   NULL COMMENT 'assistant 发起的 tool_calls JSON',
    tool_call_id            VARCHAR(64)  NULL COMMENT 'tool 消息对应的 tool_call_id',
    tool_name               VARCHAR(64)  NULL COMMENT '工具名',
    structured_payload_json MEDIUMTEXT   NULL COMMENT '供前端渲染的结构化卡片JSON数组',
    tokens                  INT          NOT NULL DEFAULT 0 COMMENT '本条消耗Token',
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id),
    KEY idx_agent_msg_session_id (session_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业采购选本Agent消息表';
