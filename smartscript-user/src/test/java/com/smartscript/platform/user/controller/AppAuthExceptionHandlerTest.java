package com.smartscript.platform.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.constant.AppUserErrorCodes;

/** 面向用户的错误文案：固定码中文、参数码安全透传、英文/技术内容兜底。 */
class AppAuthExceptionHandlerTest
{
    @Test
    void fixedCodesUseChineseCopy()
    {
        assertEquals("验证码错误，请重新输入",
                AppAuthExceptionHandler.safeMessage(AppAuthErrorCodes.SMS_CODE_INVALID, "sms code invalid"));
        assertEquals("账号暂时无法使用，请联系客服或管理员",
                AppAuthExceptionHandler.safeMessage(AppAuthErrorCodes.ACCOUNT_DISABLED, "account disabled"));
        assertEquals("登录状态已过期，请重新登录",
                AppAuthExceptionHandler.safeMessage(AppAuthErrorCodes.UNAUTHORIZED, "unauthorized"));
        assertEquals("服务暂时不可用，请稍后再试",
                AppAuthExceptionHandler.safeMessage(AppAuthErrorCodes.SYSTEM_ERROR, "internal error"));
        assertEquals("内容不存在或暂时不可用",
                AppAuthExceptionHandler.safeMessage(AppUserErrorCodes.RESOURCE_NOT_FOUND, null));
    }

    @Test
    void paramKeepsSafeChineseAndRejectsEnglishOrTechnicalCopy()
    {
        assertEquals("姓名长度不正确，请检查后重试",
                AppAuthExceptionHandler.safeMessage(AppAuthErrorCodes.PARAM, "姓名长度不正确，请检查后重试"));
        assertEquals("您的信息不符合规范，请检查后重试",
                AppAuthExceptionHandler.safeMessage(AppAuthErrorCodes.PARAM, "invalid request"));
        assertEquals("您的信息不符合规范，请检查后重试",
                AppAuthExceptionHandler.safeMessage(AppAuthErrorCodes.PARAM, "HTTP 404"));
        assertEquals("您的信息不符合规范，请检查后重试",
                AppAuthExceptionHandler.safeMessage(AppAuthErrorCodes.PARAM, null));
    }

    @Test
    void unknownCodeUsesSafeFallback()
    {
        assertEquals("自定义提示", AppAuthExceptionHandler.safeMessage(99999, "自定义提示"));
        assertEquals("服务暂时不可用，请稍后再试",
                AppAuthExceptionHandler.safeMessage(99999, "internal error"));
    }
}
