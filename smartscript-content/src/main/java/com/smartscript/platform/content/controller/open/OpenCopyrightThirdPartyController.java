package com.smartscript.platform.content.controller.open;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.copyright.CopyrightThirdPartyMessageRequest;
import com.smartscript.platform.content.service.ICopyrightThirdPartyMessageService;
import jakarta.validation.Valid;

/**
 * 版权第三方消息接收（代理回调，匿名可达）。
 *
 * 链路：国版 → 代理账号 → POST 本接口 → 落库并驱动申请状态 → App 展示/通知用户。
 *
 * 路径：/api/v1/open/copyright/third-party/messages
 */
@RestController
@RequestMapping("/api/v1/open/copyright/third-party")
public class OpenCopyrightThirdPartyController
{
    private static final int CODE_BAD_REQUEST = 400;

    private final ICopyrightThirdPartyMessageService messageService;

    public OpenCopyrightThirdPartyController(ICopyrightThirdPartyMessageService messageService)
    {
        this.messageService = messageService;
    }

    /**
     * 接收第三方代理同步的消息（补正/通知/发证/驳回）。
     */
    @PostMapping("/messages")
    public AppApiResponse<Map<String, Object>> receive(@Valid @RequestBody CopyrightThirdPartyMessageRequest request)
    {
        try
        {
            return AppApiResponse.ok(messageService.receiveMessage(request));
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (Exception e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "接收消息失败：" + e.getMessage());
        }
    }
}
