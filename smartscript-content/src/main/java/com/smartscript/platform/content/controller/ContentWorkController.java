package com.smartscript.platform.content.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.service.ContentWorkService;

/**
 * 内容/书架接口（A6 示例主流程，契约 A6-IDENTITY-CONTRACT-v1 §2.3）。
 *
 * 现状（B 模块 App 第一批）：公开作品列表已由真实库查询取代——
 * {@code GET /api/v1/content/works} 迁至 {@link com.smartscript.platform.content.controller.app.AppWorkController}，
 * 本控制器只保留 {@code /shelf} 示例入口（书架链路在 B 模块后续批次接入真实表时一并替换）。
 *
 * 鉴权分工：
 *   - `/shelf` 为私有接口（需 App Token），未带 Token 时由 App 凭证域过滤器直接拒绝。
 *
 * 与 A5 的用户中心一致：本控制器只负责取身份并返回数据，不自行判断 Token 字符串，
 * 也不从请求体读取用户 ID。
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

    /** 我的书架；需 App Token，归属取自服务端身份。 */
    @GetMapping("/shelf")
    public AppApiResponse<Map<String, Object>> shelf()
    {
        return AppApiResponse.ok(contentWorkService.shelf());
    }
}
