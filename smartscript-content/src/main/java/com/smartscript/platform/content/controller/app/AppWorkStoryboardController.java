package com.smartscript.platform.content.controller.app;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.domain.SysWorkStoryboard;
import com.smartscript.platform.content.dto.AppStoryboardDto;
import com.smartscript.platform.content.service.IAppWorkStoryboardService;
import com.smartscript.platform.identity.IdentityProvider;

/** App/PC 作品分镜目录接口；GET 为公开阅读，写操作仍只允许作者。 */
@RestController
@RequestMapping("/api/v1/content/works/{workId}/storyboards")
public class AppWorkStoryboardController
{
    private final IAppWorkStoryboardService storyboardService;
    private final IdentityProvider identityProvider;

    public AppWorkStoryboardController(IAppWorkStoryboardService storyboardService,
            IdentityProvider identityProvider)
    {
        this.storyboardService = storyboardService;
        this.identityProvider = identityProvider;
    }

    @GetMapping
    public AppApiResponse<List<AppStoryboardDto>> list(@PathVariable Long workId)
    {
        return AppApiResponse.ok(storyboardService.list(identityProvider.currentUserId(), workId));
    }

    /** 作品详情页公开展示审核通过的分镜数量。 */
    @GetMapping("/summary")
    public AppApiResponse<Map<String, Object>> summary(@PathVariable Long workId)
    {
        return AppApiResponse.ok(Map.of("workId", workId, "count", storyboardService.countPublic(workId)));
    }

    @PostMapping
    @PreAuthorize("@contentAuthorAccess.allowed()")
    public AppApiResponse<SysWorkStoryboard> save(@PathVariable Long workId,
            @RequestBody Map<String, Object> body)
    {
        String title = body == null || body.get("title") == null ? null : body.get("title").toString();
        String content = body == null || body.get("content") == null ? null : body.get("content").toString();
        String source = body == null || body.get("source") == null ? null : body.get("source").toString();
        try
        {
            Long chapterId = AppRequestNumbers.positiveLong(body == null ? null : body.get("chapterId"), "章节 ID");
            Integer chapterNo = AppRequestNumbers.positiveInt(body == null ? null : body.get("chapterNo"), "章号");
            return AppApiResponse.ok(storyboardService.save(requireLogin(), workId, chapterId, chapterNo, title, content, source));
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
        catch (IllegalStateException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
        catch (SecurityException e)
        {
            return AppApiResponse.fail(404, e.getMessage());
        }
    }

    @DeleteMapping("/{storyboardId}")
    @PreAuthorize("@contentAuthorAccess.allowed()")
    public AppApiResponse<Void> delete(@PathVariable Long workId,
            @PathVariable Long storyboardId)
    {
        try
        {
            storyboardService.delete(requireLogin(), workId, storyboardId);
            return AppApiResponse.ok(null);
        }
        catch (IllegalStateException e)
        {
            return AppApiResponse.fail(400, e.getMessage());
        }
        catch (SecurityException e)
        {
            return AppApiResponse.fail(404, e.getMessage());
        }
    }

    private Long requireLogin()
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            throw new com.smartscript.platform.content.exception.AppUserWorkUnauthorizedException();
        }
        return userId;
    }
}
