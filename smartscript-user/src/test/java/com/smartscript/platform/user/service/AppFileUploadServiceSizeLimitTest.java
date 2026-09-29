package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.ruoyi.framework.config.ServerConfig;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.exception.AppAuthException;

/**
 * H-04 决策甲（2026-09-28 负责人批准）：服务层 5MB 头像预检与容器 10MB 兜底统一为
 * 413/41300 与同一安全文案（原为 400/40000「图片不能超过 5MB」）。
 *
 * 与容器层 {@code AppUploadSizeExceptionHandlerTest}（11MB→413）互补：本用例覆盖
 * 「未到容器上限、由服务层拒绝」的 5–10MB 区间。
 */
class AppFileUploadServiceSizeLimitTest
{
    private final AppFileUploadService service = new AppFileUploadService(mock(ServerConfig.class));

    @Test
    void sixMegabyteAvatarRejectedWithUnified413()
    {
        MockMultipartFile file = new MockMultipartFile("file", "h04-6m.png", "image/png",
                new byte[6 * 1024 * 1024]);

        AppAuthException ex = assertThrows(AppAuthException.class, () -> service.uploadImage(file));

        assertEquals(413, ex.getHttpStatus(), "服务层超限必须为 413（与容器一致）");
        assertEquals(AppUserErrorCodes.PAYLOAD_TOO_LARGE, ex.getCode(), "业务码必须为 41300");
        assertEquals(AppUserErrorCodes.PAYLOAD_TOO_LARGE_TEXT, ex.getMessage(), "必须使用统一安全文案");
    }

    @Test
    void emptyFileStillRejectedAsParam400()
    {
        MockMultipartFile file = new MockMultipartFile("file", "h04-empty.png", "image/png", new byte[0]);

        AppAuthException ex = assertThrows(AppAuthException.class, () -> service.uploadImage(file));

        assertEquals(400, ex.getHttpStatus(), "空文件仍属参数问题（400/40000），不随超限口径变更");
        assertEquals(AppUserErrorCodes.PARAM, ex.getCode());
    }
}
