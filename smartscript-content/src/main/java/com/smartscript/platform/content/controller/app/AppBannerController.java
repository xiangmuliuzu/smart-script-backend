package com.smartscript.platform.content.controller.app;

import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.service.IAppBookstoreService;

/**
 * App 首页 Banner 轮播（B 模块，接口文档 2.7.13 表 2-93）。
 *
 * 鉴权：公开接口，游客可读（在 App 凭证域白名单登记，无需 App Token）。
 * 返回：App 信封 {code, message, data:{list}}。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content/banners")
public class AppBannerController
{
    private final IAppBookstoreService bookstoreService;

    public AppBannerController(IAppBookstoreService bookstoreService)
    {
        this.bookstoreService = bookstoreService;
    }

    /**
     * Banner 列表
     *
     * @param position 展示位置（可选，如 home_top）
     */
    @GetMapping
    public AppApiResponse<Map<String, Object>> list(@RequestParam(required = false) String position)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", bookstoreService.listBanners(position));
        return AppApiResponse.ok(data);
    }
}