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
import com.smartscript.platform.content.domain.SysWorkStoryboard;
import com.smartscript.platform.content.service.IAppWorkStoryboardService;
import com.smartscript.platform.identity.IdentityProvider;

/** App/PC 创作者端作品分镜目录接口。 */
@RestController
@PreAuthorize("@contentAuthorAccess.allowed()")
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
    public AppApiResponse<List<SysWorkStoryboard>> list(@PathVariable Long workId)
    {
        return AppApiResponse.ok(storyboardService.list(requireLogin(), workId));
    }

    @PostMapping
    public AppApiResponse<SysWorkStoryboard> save(@PathVariable Long workId,
            @RequestBody Map<String, Object> body)
    {
        String title = body == null || body.get("title") == null ? null : body.get("title").toString();
        String content = body == null || body.get("content") == null ? null : body.get("content").toString();
        String source = body == null || body.get("source") == null ? null : body.get("source").toString();
        try
        {
            return AppApiResponse.ok(storyboardService.save(requireLogin(), workId, title, content, source));
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
