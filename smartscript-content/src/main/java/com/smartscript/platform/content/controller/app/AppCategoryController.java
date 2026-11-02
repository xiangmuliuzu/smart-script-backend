package com.smartscript.platform.content.controller.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppCategoryNavDto;
import com.smartscript.platform.content.service.IAppBookstoreService;

/**
 * App 分类页导航（B 模块，接口文档 2.10 分类）。
 *
 * 标签墙体系下作品只按标签归属，不再有分类树；本控制器仅保留「频道 + 四维度标签」导航。
 * 鉴权：公开接口，游客可读。
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