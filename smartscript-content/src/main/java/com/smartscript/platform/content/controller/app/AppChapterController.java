package com.smartscript.platform.content.controller.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppChapterDetailDto;
import com.smartscript.platform.content.dto.AppChapterListDto;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.service.IAppBookstoreService;

/**
 * App 作品章节（B 模块，接口文档 2.7.8 免费试读）。
 *
 * 鉴权：公开接口，游客可读（在 App 凭证域白名单登记，无需 App Token）；
 * 若请求带合法 App Token，过滤器（OPTIONAL_IDENTITY_PATHS）会建立身份，
 * 用于「已获版权授权则放开全文」的判定。
 *
 * 可读边界 = 试读范围（sys_work.preview_enabled / preview_episodes）
 *           或 当前身份持有生效中的版权授权（sys_copyright_authorization）。
 * 两条分支都不满足时，章节正文按 403 拒绝且不下发 content。
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

    /** 试读范围外且未获授权 */
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
     * @return 目录载荷：{workId, previewEnabled, previewEpisodes, accessScope, unlocked, total, list[]}；
     *         previewEnabled 为库中存储列原样下发（"0"/"1"）；
     *         accessScope 取值 preview（仅试读）/ full（已授权全文），unlocked 为其布尔等价形态；
     *         每章 readable 表示当前身份能否阅读本章。
     */
    @GetMapping("/works/{workId}/chapters")
    public AppApiResponse<AppChapterListDto> chapters(@PathVariable Long workId)
    {
        AppWorkDto work = bookstoreService.getWork(workId);
        if (work == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或已下架");
        }
        return AppApiResponse.ok(bookstoreService.getChapterCatalog(work));
    }

    /**
     * 章节正文
     *
     * @param chapterId 章节ID
     * @return 可读（试读范围内 或 已获授权）时下发 content；
     *         两条分支都不满足按 403 返回且 data 不含 content。
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