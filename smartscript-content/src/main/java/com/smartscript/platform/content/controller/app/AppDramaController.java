package com.smartscript.platform.content.controller.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppDramaFeedItem;
import com.smartscript.platform.content.dto.AppExternalDramaDetailDto;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppRelatedWorkDto;
import com.smartscript.platform.content.service.IAppDramaService;

/**
 * App 外部视频浏览（B 模块，接口文档 2.8.1 表2-94 / 2.8.15 表2-108 / 2.8.16 表2-109）。
 *
 * 鉴权：公开接口，游客可读（已在 {@code AppAuthSecurityConfig} 白名单登记
 * /content/drama-feed 与 /content/external-dramas/**，无需 App Token）。
 *
 * 路径说明：文档写的是 /api/dramas/feed、/api/external-dramas/:id，B 模块既有接口统一收拢在
 * App 凭证域 /api/v1/content/** 下（见 AppAuthSecurityConfig.APP_PATH_PREFIXES），故落此前缀。
 * 其中信息流为 /content/drama-feed（避免与既有 /content/works 等命名混淆）。
 *
 * 可见性：只回 status='on_shelf' 的外部视频；未上架/不存在按 404，不区分二者，
 * 避免通过错误信息探测未公开内容（与书城作品口径一致）。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content")
public class AppDramaController
{
    /** 外部视频不存在或未上架 */
    private static final int CODE_NOT_FOUND = 404;

    /** 信息流默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final IAppDramaService dramaService;

    public AppDramaController(IAppDramaService dramaService)
    {
        this.dramaService = dramaService;
    }

    /**
     * 短剧信息流（2.8.1，分页）
     *
     * @param page     页码（可选，默认 1）
     * @param pageSize 每页条数（可选，默认 20，上限 50）
     * @return data = {total, list}
     */
    @GetMapping("/drama-feed")
    public AppApiResponse<AppPageResult<AppDramaFeedItem>> feed(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize)
    {
        int pageNum = page == null ? 1 : page;
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        return AppApiResponse.ok(dramaService.pageFeed(pageNum, size));
    }

    /**
     * 外部视频详情（2.8.15）
     *
     * @param dramaId 外部视频ID
     * @return 详情；未上架/不存在按 404
     */
    @GetMapping("/external-dramas/{dramaId}")
    public AppApiResponse<AppExternalDramaDetailDto> detail(@PathVariable Long dramaId)
    {
        AppExternalDramaDetailDto dto = dramaService.getDramaDetail(dramaId);
        if (dto == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "外部视频不存在或已下架");
        }
        return AppApiResponse.ok(dto);
    }

    /**
     * 找同款剧本（2.8.16）
     *
     * @param dramaId 外部视频ID
     * @return data = {dramaId, hasRelatedWork, work}；未上架/不存在按 404
     */
    @GetMapping("/external-dramas/{dramaId}/related-work")
    public AppApiResponse<AppRelatedWorkDto> relatedWork(@PathVariable Long dramaId)
    {
        AppRelatedWorkDto dto = dramaService.getRelatedWork(dramaId);
        if (dto == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "外部视频不存在或已下架");
        }
        return AppApiResponse.ok(dto);
    }
}