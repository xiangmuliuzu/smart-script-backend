package com.smartscript.platform.content.controller.app;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.domain.SysWorkReviewRecord;
import com.smartscript.platform.content.service.IAppUserWorkService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * PC 用户端「作品详情与审核流程」（A2，五人分工）。
 *
 * 路由前缀：/api/v1/users/me/works（分工文档锁定，A1 列表页经 workId 跳转进入）。
 * 鉴权：App 凭证域私有接口，需登录；用户 ID 只取自 {@link IdentityProvider}，
 *       不接受请求体传入；作品归属校验在服务层完成。
 * 返回：App 信封 {@link AppApiResponse}（{code, message, data}）。
 *
 * 状态机（小写口径）：draft/reviewing/revision/rejected/published。
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/users/me/works")
public class AppUserWorkController
{
    /** 未登录 */
    private static final int CODE_UNAUTHORIZED = 401;

    /** 参数/状态非法 */
    private static final int CODE_BAD_REQUEST = 400;

    /** 无权限/作品不存在（统一 404，避免探测） */
    private static final int CODE_NOT_FOUND = 404;

    private final IAppUserWorkService userWorkService;
    private final IdentityProvider identityProvider;

    public AppUserWorkController(IAppUserWorkService userWorkService, IdentityProvider identityProvider)
    {
        this.userWorkService = userWorkService;
        this.identityProvider = identityProvider;
    }

    /**
     * 我的作品列表（按审核派生状态过滤）
     *
     * 路径固定为 /works（Spring 优先匹配字面路径，不会与 /{workId} 冲突）。
     * status 取值：all / draft / reviewing / revision / rejected / published。
     */
    @GetMapping
    public AppApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status)
    {
        Long userId = requireLogin();
        try
        {
            return AppApiResponse.ok(userWorkService.listWorks(userId, status));
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 作品详情（含章节目录、最近审核意见）
     */
    @GetMapping("/{workId}")
    public AppApiResponse<Map<String, Object>> detail(@PathVariable Long workId)
    {
        Long userId = requireLogin();
        try
        {
            return AppApiResponse.ok(userWorkService.getWorkDetail(userId, workId));
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (SecurityException e)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, e.getMessage());
        }
    }

    /**
     * 修改作品基本信息（标题/简介/分类）
     */
    @PutMapping("/{workId}")
    public AppApiResponse<Void> update(@PathVariable Long workId, @RequestBody Map<String, Object> body)
    {
        Long userId = requireLogin();
        try
        {
            userWorkService.updateWorkBase(userId, workId,
                    str(body.get("title")), str(body.get("summary")), integer(body.get("genreId")));
            return AppApiResponse.ok();
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (IllegalStateException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (SecurityException e)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, e.getMessage());
        }
    }

    /**
     * 提交审核（draft/revision/rejected → reviewing）
     */
    @PostMapping("/{workId}/submit")
    public AppApiResponse<Void> submit(@PathVariable Long workId)
    {
        Long userId = requireLogin();
        try
        {
            userWorkService.submitReview(userId, workId);
            return AppApiResponse.ok();
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (IllegalStateException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (SecurityException e)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, e.getMessage());
        }
    }

    /**
     * 章节目录（含 content 全文）
     */
    @GetMapping("/{workId}/chapters")
    public AppApiResponse<List<SysWorkChapter>> chapters(@PathVariable Long workId)
    {
        Long userId = requireLogin();
        try
        {
            return AppApiResponse.ok(userWorkService.listChapters(userId, workId));
        }
        catch (SecurityException e)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, e.getMessage());
        }
    }

    /**
     * 新增章节
     */
    @PostMapping("/{workId}/chapters")
    public AppApiResponse<SysWorkChapter> addChapter(@PathVariable Long workId, @RequestBody Map<String, Object> body)
    {
        Long userId = requireLogin();
        try
        {
            return AppApiResponse.ok(userWorkService.addChapter(userId, workId,
                    str(body.get("chapterTitle")), str(body.get("content"))));
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (IllegalStateException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (SecurityException e)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, e.getMessage());
        }
    }

    /**
     * 修改章节
     */
    @PutMapping("/{workId}/chapters/{chapterId}")
    public AppApiResponse<Void> updateChapter(@PathVariable Long workId, @PathVariable Long chapterId,
            @RequestBody Map<String, Object> body)
    {
        Long userId = requireLogin();
        try
        {
            userWorkService.updateChapter(userId, workId, chapterId,
                    str(body.get("chapterTitle")), str(body.get("content")));
            return AppApiResponse.ok();
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (IllegalStateException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (SecurityException e)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, e.getMessage());
        }
    }

    /**
     * 删除章节
     */
    @DeleteMapping("/{workId}/chapters/{chapterId}")
    public AppApiResponse<Void> deleteChapter(@PathVariable Long workId, @PathVariable Long chapterId)
    {
        Long userId = requireLogin();
        try
        {
            userWorkService.deleteChapter(userId, workId, chapterId);
            return AppApiResponse.ok();
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (IllegalStateException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (SecurityException e)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, e.getMessage());
        }
    }

    /**
     * 审核记录（审核历史 + 管理员意见）
     */
    @GetMapping("/{workId}/review-records")
    public AppApiResponse<List<SysWorkReviewRecord>> reviewRecords(@PathVariable Long workId)
    {
        Long userId = requireLogin();
        try
        {
            return AppApiResponse.ok(userWorkService.listReviewRecords(userId, workId));
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (SecurityException e)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 私有辅助
    // ------------------------------------------------------------------

    private Long requireLogin()
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            throw new com.smartscript.platform.content.exception.AppUserWorkUnauthorizedException();
        }
        return userId;
    }

    private String str(Object value)
    {
        return value == null ? null : value.toString();
    }

    private Integer integer(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number n)
        {
            return n.intValue();
        }
        try
        {
            return Integer.parseInt(value.toString());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
