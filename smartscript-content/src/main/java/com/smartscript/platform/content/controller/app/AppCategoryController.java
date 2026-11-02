package com.smartscript.platform.content.controller.app;

import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppCategoryNavDto;
import com.smartscript.platform.content.service.IAppBookstoreService;

/**
 * App 分类列表（B 模块，接口文档 2.7.1 作品列表的分类筛选项 / 2.10 分类）。
 *
 * 鉴权：公开接口，游客可读。
 * 返回：App 信封 {code, message, data:{list}}；只下发 status 正常的分类。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content/categories")
public class AppCategoryController
{
    private final IAppBookstoreService bookstoreService;

    public AppCategoryController(IAppBookstoreService bookstoreService)
    {
        this.bookstoreService = bookstoreService;
    }

    /**
     * 分类列表
     *
     * @param categoryType 分类类型（可选）
     * @param parentId     父分类ID（可选，传 0 取顶级分类）
     */
    @GetMapping
    public AppApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String categoryType,
            @RequestParam(required = false) Long parentId)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", bookstoreService.listCategories(categoryType, parentId));
        return AppApiResponse.ok(data);
    }

    /**
     * 分类树（一次返回三级：频道 → 题材 → 细分）
     */
    @GetMapping("/tree")
    public AppApiResponse<Map<String, Object>> tree()
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", bookstoreService.listCategoryTree());
        return AppApiResponse.ok(data);
    }

    /**
     * 分类页导航：频道 + 热门标签 / 主题 / 角色 / 情节 四维度
     *
     * @param channelId 频道ID（可选，为空时回落第一个频道）
     */
    @GetMapping("/nav")
    public AppApiResponse<AppCategoryNavDto> nav(@RequestParam(required = false) Long channelId)
    {
        return AppApiResponse.ok(bookstoreService.getCategoryNav(channelId));
    }
}