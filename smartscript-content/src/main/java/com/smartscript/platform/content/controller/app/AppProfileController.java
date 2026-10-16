package com.smartscript.platform.content.controller.app;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.service.AppProfileContentService;

/**
 * 个人主页内容统计（B 模块书城侧）。
 *
 * 路径前缀：/api/v1/content/users/{userId}（App 凭证域私有，落入 /api/v1/content/** authenticated）。
 * 作者的上架作品列表走书城分页 {@code /api/v1/content/works?authorId=}，此处只提供获赞统计。
 */
@RestController
@RequestMapping("/api/v1/content/users")
public class AppProfileController
{
    private final AppProfileContentService profileContentService;

    public AppProfileController(AppProfileContentService profileContentService)
    {
        this.profileContentService = profileContentService;
    }

    /** 作者累计获赞数。 */
    @GetMapping("/{userId}/stats")
    public AppApiResponse<Map<String, Object>> stats(@PathVariable Long userId)
    {
        return AppApiResponse.ok(Map.of("likeCount", profileContentService.likeCount(userId)));
    }
}
