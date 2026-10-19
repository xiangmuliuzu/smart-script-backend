package com.smartscript.platform.content.controller.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("@contentAuthorAccess.allowed()")
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
     * 我的作品列表（A1 工作台「我的作品」页；同时服务 App 端「我的作品」）
     *
     * 路径固定为 /works（Spring 优先匹配字面路径，不会与 /{workId} 冲突）。
     * status：all / draft / reviewing / revision / rejected / published，支持逗号多值
     *         （PC「待修改」传 revision,rejected）。
     * keyword：作品名称模糊；workType：作品类型精确。
     * 分页：不传 pageNum 返回纯数组（App 端契约不变）；传 pageNum 返回
     *       {total, pageNum, pageSize, list}（PC 分页）。
     */
    @GetMapping
    public AppApiResponse<?> list(@RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String workType,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize)
    {
        Long userId = requireLogin();
        try
        {
            List<Map<String, Object>> all = userWorkService.listWorks(userId, status, keyword, workType);
            if (pageNum == null)
            {
                return AppApiResponse.ok(all);
            }

            int page = pageNum < 1 ? 1 : pageNum;
            int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
            int total = all.size();
            int from = Math.min((page - 1) * size, total);
            int to = Math.min(from + size, total);

            Map<String, Object> pageData = new LinkedHashMap<>();
            pageData.put("total", total);
            pageData.put("pageNum", page);
            pageData.put("pageSize", size);
            pageData.put("list", all.subList(from, to));
            return AppApiResponse.ok(pageData);
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 工作台作品统计（A1 工作台首页统计卡片）
     *
     * 返回 {workTotal, draftCount, reviewingCount, revisionCount, publishedCount}；
     * 其中 revisionCount（待修改）= revision + rejected。
     */
    @GetMapping("/statistics")
    public AppApiResponse<Map<String, Object>> statistics()
    {
        Long userId = requireLogin();
        return AppApiResponse.ok(userWorkService.workStatistics(userId));
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
     * 修改作品基本信息（标题/简介/分类/封面/标签）
     *
     * 依据：接口文档 2.3.3 修改剧本信息，入参含 tagIds（Array&lt;Int&gt;，全量覆盖，最多 8 个）。
     */
    @PutMapping("/{workId}")
    public AppApiResponse<Void> update(@PathVariable Long workId, @RequestBody Map<String, Object> body)
    {
        Long userId = requireLogin();
        try
        {
            userWorkService.updateWorkBase(userId, workId,
                    str(body.get("title")), str(body.get("summary")), integer(body.get("genreId")),
                    str(body.get("cover")), integerList(body.get("tagIds")));
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

    /**
     * 解析标签ID数组（接口文档 2.3.3 tagIds）。
     *
     * 字段缺失（null / 非数组）返回 null，表示「不下发 = 不改标签」；
     * 空数组返回空集合，表示「全量覆盖为空 = 清空标签」，二者语义不同不可合并。
     */
    private List<Integer> integerList(Object value)
    {
        if (!(value instanceof Collection<?> raw))
        {
            return null;
        }
        List<Integer> tagIds = new ArrayList<>();
        for (Object item : raw)
        {
            Integer tagId = integer(item);
            if (tagId != null)
            {
                tagIds.add(tagId);
            }
        }
        return tagIds;
    }
}
