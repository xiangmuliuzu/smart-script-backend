package com.smartscript.platform.content.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.service.ContentWorkService;

/**
 * 内容/书城接口（A6 示例主流程，契约 A6-IDENTITY-CONTRACT-v1 §2.3）。
 *
 * 鉴权分工：
 *   - `/works` 为公开接口（游客可读），在 {@code /api/v1/content/**` 下且已在
 *     App 凭证域的公开路径白名单中登记，因此不需要 Token；
 *   - `/shelf` 为私有接口（需 App Token），未带 Token 时由 App 凭证域过滤器直接拒绝。
 *
 * 与 A5 的用户中心一致：本控制器只负责取身份并返回数据，不自行判断 Token 字符串，
 * 也从请求体读取用户 ID。
 */
@RestController
@RequestMapping("/api/v1/content")
public class ContentWorkController
{
    private final ContentWorkService contentWorkService;

    public ContentWorkController(ContentWorkService contentWorkService)
    {
        this.contentWorkService = contentWorkService;
    }

    /** 公开作品列表；游客可读，已登录时附带个性化标记与身份摘要。 */
    @GetMapping("/works")
    public AppApiResponse<Map<String, Object>> listWorks()
    {
        return AppApiResponse.ok(contentWorkService.listPublicWorks());
    }

    /** 我的书架；需 App Token，归属取自服务端身份。 */
    @GetMapping("/shelf")
    public AppApiResponse<Map<String, Object>> shelf()
    {
        return AppApiResponse.ok(contentWorkService.shelf());
    }
}
