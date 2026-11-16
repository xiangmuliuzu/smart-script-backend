package com.smartscript.platform.identity;

import java.util.Map;

/** 作品删除的二次验证边界，由认证模块实现，不向内容模块暴露手机号或认证表。 */
public interface WorkDeletionVerification
{
    Map<String, Object> sendCode(Long userId, String ip, String requestId);

    void consumeCode(Long userId, String code);
}
