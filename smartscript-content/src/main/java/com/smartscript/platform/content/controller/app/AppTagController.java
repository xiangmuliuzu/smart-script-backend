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
 * App 标签列表（B 模块，接口文档 2.7.1 作品列表的标签筛选项）。
 *
 * 鉴权：公开接口，游客可读。
 * 返回：App 信封 {code, message, data:{list}}；只下发 status 正常的标签，按 use_count 降序。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content/tags")
public class AppTagController
{
    private final IAppBookstoreService bookstoreService;

    public AppTagController(IAppBookstoreService bookstoreService)
    {
        this.bookstoreService = bookstoreService;
    }

    /**
     * 标签列表
     *
     * @param tagType    标签维度（可选，role/plot）
     * @param channelId  频道ID（可选；命中本频道标签 + 男女通用标签）
     */
    @GetMapping
    public AppApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String tagType,
            @RequestParam(required = false) Long channelId)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", bookstoreService.listTags(tagType, channelId));
        return AppApiResponse.ok(data);
    }
}