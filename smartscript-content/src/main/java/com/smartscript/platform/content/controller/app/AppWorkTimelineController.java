package com.smartscript.platform.content.controller.app;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.domain.SysWorkTimelineEvent;
import com.smartscript.platform.content.service.IAppChapterAuthorService;
import com.smartscript.platform.content.service.IWorkTimelineService;

/**
 * 作品时间线只读查询（PC 用户端与 App 共用）
 *
 * 路径：/api/v1/content/works/{workId}/timeline
 *   GET status=未兑现/已兑现/已失效（可选）、beforeNo=只看某集之前（可选）、limit（可选）
 *
 * 时间线只追加：由每集摘要调用一并产出事件，历史集事件不可改，此处仅提供查询。
 * 鉴权与台账一致：作品作者必须为当前身份，非本人 403、作品不存在 404。
 */
@RestController
@PreAuthorize("@contentAuthorAccess.allowed()")
@RequestMapping("/api/v1/content")
public class AppWorkTimelineController
{
    private static final int CODE_NOT_FOUND = 404;

    private static final int CODE_FORBIDDEN = 403;

    /** 单次查询上限，避免把 50 集全量打成大包 */
    private static final int MAX_LIMIT = 200;

    private final IAppChapterAuthorService chapterService;

    private final IWorkTimelineService timelineService;

    public AppWorkTimelineController(IAppChapterAuthorService chapterService,
            IWorkTimelineService timelineService)
    {
        this.chapterService = chapterService;
        this.timelineService = timelineService;
    }

    /**
     * 查询作品时间线（按集号倒序）。
     *
     * @param workId 作品ID
     * @param status 状态过滤，空则全部
     * @param beforeNo 只看该集之前的事件，空则全部
     * @param limit 条数上限
     * @return data = 事件数组
     */
    @GetMapping("/works/{workId}/timeline")
    public AppApiResponse<List<SysWorkTimelineEvent>> list(@PathVariable Long workId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer beforeNo,
            @RequestParam(required = false) Integer limit)
    {
        int access = chapterService.resolveWorkAccess(workId);
        if (access == IAppChapterAuthorService.ACCESS_NOT_FOUND)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在");
        }
        if (access == IAppChapterAuthorService.ACCESS_DENIED)
        {
            return AppApiResponse.fail(CODE_FORBIDDEN, "无权操作该作品");
        }
        int size = (limit == null || limit <= 0) ? 100 : Math.min(limit, MAX_LIMIT);
        return AppApiResponse.ok(timelineService.list(workId, status, beforeNo, size));
    }
}
