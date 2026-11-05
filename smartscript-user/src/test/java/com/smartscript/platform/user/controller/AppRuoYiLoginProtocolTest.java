package com.smartscript.platform.user.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import com.ruoyi.common.core.domain.model.LoginBody;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.dto.AuthSessionDto;
import com.smartscript.platform.user.service.AppAuthenticationService;
import com.smartscript.platform.user.service.SmsCodeService;

class AppRuoYiLoginProtocolTest
{
    @Test
    void errorUsesFriendlyMsgAndPreservesCooldownData()
    {
        var controller = new AppAuthController(mock(AppAuthenticationService.class), mock(SmsCodeService.class), new AppAuthProperties(),
                mock(com.smartscript.platform.review.service.RiskCheckService.class));
        var error = new com.smartscript.platform.user.exception.AppAuthException(AppAuthErrorCodes.SMS_COOLDOWN, 429, "操作过于频繁，请稍后再试",
                java.util.Map.of("retryAfterSeconds", 60));
        var result = controller.authError(error);
        assertEquals(429, result.getStatusCode().value());
        assertEquals("操作过于频繁，请稍后再试", result.getBody().get("msg"));
        assertEquals(java.util.Map.of("retryAfterSeconds", 60), result.getBody().get("data"));
        assertFalse(result.getBody().containsKey("message"));
    }

    @Test
    void loginConsumesLoginBodyAndReturnsTopLevelToken()
    {
        AppAuthenticationService service = mock(AppAuthenticationService.class);
        AuthSessionDto session = new AuthSessionDto(); session.setToken("signed-token");
        when(service.ruoyiPasswordLogin("user", "password", "1234", "captcha", "127.0.0.1"))
                .thenReturn(session);
        AppAuthController controller = new AppAuthController(service, mock(SmsCodeService.class), new AppAuthProperties(),
                mock(com.smartscript.platform.review.service.RiskCheckService.class));
        LoginBody request = new LoginBody(); request.setUsername(" user "); request.setPassword("password");
        request.setCode("1234"); request.setUuid("captcha");
        var result = controller.login(request, new MockHttpServletRequest());
        assertEquals(200, result.get("code"));
        assertTrue(result.containsKey("msg"));
        assertEquals("signed-token", result.get("token"));
        assertFalse(result.containsKey("refreshToken"));
        assertFalse(result.containsKey("data"));
    }
}
