package com.smartscript.platform.content.controller.app;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.domain.WorkGenEpisode;
import com.smartscript.platform.content.domain.WorkGenJob;
import com.smartscript.platform.content.dto.AppWorkGenJobRequest;
import com.smartscript.platform.content.service.IAppChapterAuthorService;
import com.smartscript.platform.content.service.IWorkGenJobService;
import com.smartscript.platform.content.service.impl.WorkGenJobServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 作品批量生成任务（后台队列）
 *
 * 路径：/api/v1/content/works/{workId}/gen-jobs
 *   POST  创建并启动（提交后立即返回，后台逐集跑）
 *   GET   /latest  最近一条任务（刷新页面恢复进度）
 *   GET   /{jobId} 任务进度
 *   POST  /{jobId}/resume 续跑
 *   POST  /{jobId}/cancel 取消
 *
 * 鉴权：App 凭证域私有接口，作品作者必须为当前身份（与章节编辑同一套归属判定）。
 *
 * @author smartscript
 */
@RestController
@PreAuthorize("@contentAuthorAccess.allowed()")
@RequestMapping("/api/v1/content")
public class AppWorkGenJobController
{
    private static final int CODE_NOT_FOUND = 404;

    private static final int CODE_FORBIDDEN = 403;

    private static final int CODE_BAD_REQUEST = 400;

    private static final int CODE_CONFLICT = 409;

    private final IAppChapterAuthorService chapterService;

    private final IWorkGenJobService jobService;

    private final IdentityProvider identityProvider;

    public AppWorkGenJobController(IAppChapterAuthorService chapterService,
            IWorkGenJobService jobService, IdentityProvider identityProvider)
    {
        this.chapterService = chapterService;
        this.jobService = jobService;
        this.identityProvider = identityProvider;
    }

    /** 提交并启动任务。 */
    @PostMapping("/works/{workId}/gen-jobs")
    public AppApiResponse<Map<String, Object>> create(@PathVariable Long workId,
            @RequestBody(required = false) AppWorkGenJobRequest request)
    {
        String denied = checkAccess(workId);
        if (denied != null)
        {
            return AppApiResponse.fail(deniedCode(denied), denied);
        }
        if (request == null || request.getEpisodes() == null || request.getEpisodes().isEmpty())
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "待生成集列表不能为空");
        }
        WorkGenJob job = new WorkGenJob();
        job.setOutline(request.getOutline());
        job.setCharacters(request.getCharacters());
        job.setRoster(request.getRoster());
        job.setGenre(request.getGenre());
        job.setUserPrompt(request.getUserPrompt());
        job.setModel(request.getModel());
        job.setEpisodes(jobService.writeEpisodes(request.getEpisodes()));
        try
        {
            WorkGenJob created = jobService.create(identityProvider.currentUserId(), workId, job);
            return AppApiResponse.ok(progress(created));
        }
        catch (IllegalStateException e)
        {
            return AppApiResponse.fail(CODE_CONFLICT, e.getMessage());
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
    }

    /** 最近一条任务（无任务返回 null，前端据此隐藏进度面板）。 */
    @GetMapping("/works/{workId}/gen-jobs/latest")
    public AppApiResponse<Map<String, Object>> latest(@PathVariable Long workId)
    {
        String denied = checkAccess(workId);
        if (denied != null)
        {
            return AppApiResponse.fail(deniedCode(denied), denied);
        }
        WorkGenJob job = jobService.latest(workId);
        return AppApiResponse.ok(job == null ? null : progress(job));
    }

    /** 任务进度。 */
    @GetMapping("/works/{workId}/gen-jobs/{jobId}")
    public AppApiResponse<Map<String, Object>> get(@PathVariable Long workId, @PathVariable Long jobId)
    {
        String denied = checkAccess(workId);
        if (denied != null)
        {
            return AppApiResponse.fail(deniedCode(denied), denied);
        }
        WorkGenJob job = jobService.get(workId, jobId);
        if (job == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "任务不存在");
        }
        return AppApiResponse.ok(progress(job));
    }

    /** 续跑。 */
    @PostMapping("/works/{workId}/gen-jobs/{jobId}/resume")
    public AppApiResponse<Map<String, Object>> resume(@PathVariable Long workId, @PathVariable Long jobId)
    {
        String denied = checkAccess(workId);
        if (denied != null)
        {
            return AppApiResponse.fail(deniedCode(denied), denied);
        }
        try
        {
            WorkGenJob job = jobService.resume(identityProvider.currentUserId(), workId, jobId);
            return AppApiResponse.ok(progress(job));
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, e.getMessage());
        }
    }

    /** 取消。 */
    @PostMapping("/works/{workId}/gen-jobs/{jobId}/cancel")
    public AppApiResponse<Map<String, Object>> cancel(@PathVariable Long workId, @PathVariable Long jobId)
    {
        String denied = checkAccess(workId);
        if (denied != null)
        {
            return AppApiResponse.fail(deniedCode(denied), denied);
        }
        try
        {
            WorkGenJob job = jobService.cancel(workId, jobId);
            return AppApiResponse.ok(progress(job));
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, e.getMessage());
        }
    }

    /** 归属校验；返回 null 表示通过。 */
    private String checkAccess(Long workId)
    {
        int access = chapterService.resolveWorkAccess(workId);
        if (access == IAppChapterAuthorService.ACCESS_NOT_FOUND)
        {
            return "作品不存在";
        }
        if (access == IAppChapterAuthorService.ACCESS_DENIED)
        {
            return "无权操作该作品";
        }
        return null;
    }

    private int deniedCode(String message)
    {
        return "作品不存在".equals(message) ? CODE_NOT_FOUND : CODE_FORBIDDEN;
    }

    private Map<String, Object> progress(WorkGenJob job)
    {
        List<WorkGenEpisode> episodes = jobService.parseEpisodes(job.getEpisodes());
        return WorkGenJobServiceImpl.toProgress(job, episodes);
    }
}
