package com.smartscript.platform.content.controller.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppChapterDetailDto;
import com.smartscript.platform.content.dto.AppChapterDto;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.service.IAppBookstoreService;

/**
 * App 作品章节（B 模块，接口文档 2.7.8 免费试读）。
 *
 * 鉴权：公开接口，游客可读（在 App 凭证域白名单登记，无需 App Token）。
 *
 * 试读边界：以 sys_work.preview_enabled / preview_episodes 为准，
 * 试读范围外的章节正文按 403 拒绝且不下发 content。
 *
 * 路由：本控制器以 /api/v1/content 为前缀收拢两个路径；
 * 目录路径与 AppWorkController 的 /{workId} 段数不同，不会互相覆盖。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content")
public class AppChapterController
{
    /** 作品/章节不存在或作品未上架 */
    private static final int CODE_NOT_FOUND = 404;

    /** 试读范围外 */
    private static final int CODE_FORBIDDEN = 403;

    private final IAppBookstoreService bookstoreService;

    public AppChapterController(IAppBookstoreService bookstoreService)
    {
        this.bookstoreService = bookstoreService;
    }

    /**
     * 章节目录
     *
     * @param workId 作品ID
     * @return {workId, previewEnabled, previewEpisodes, total, list[]}；
     *         previewEnabled 为库中存储列原样下发（"0"/"1"），
     *         每章 readable 表示是否在试读范围内。
     */
    @GetMapping("/works/{workId}/chapters")
    public AppApiResponse<Map<String, Object>> chapters(@PathVariable Long workId)
    {
        AppWorkDto work = bookstoreService.getWork(workId);
        if (work == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或已下架");
        }
        List<AppChapterDto> list = bookstoreService.listChapters(work);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workId", work.getWorkId());
        data.put("previewEnabled", work.getPreviewEnabled());
        // 未配置试读集数时归一为 0，客户端无需再处理 null
        data.put("previewEpisodes", work.getPreviewEpisodes() == null ? 0 : work.getPreviewEpisodes());
        data.put("total", list.size());
        data.put("list", list);
        return AppApiResponse.ok(data);
    }

    /**
     * 章节正文
     *
     * @param chapterId 章节ID
     * @return 可读时下发 content；超出试读范围按 403 返回且 data 不含 content。
     */
    @GetMapping("/chapters/{chapterId}")
    public AppApiResponse<AppChapterDetailDto> chapterDetail(@PathVariable Long chapterId)
    {
        AppChapterDetailDto detail = bookstoreService.getChapterContent(chapterId);
        if (detail == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "章节不存在或作品已下架");
        }
        if (!Boolean.TRUE.equals(detail.getReadable()))
        {
            return AppApiResponse.fail(CODE_FORBIDDEN, "试读结束，请合作或授权后查看完整剧本", detail);
        }
        return AppApiResponse.ok(detail);
    }
}