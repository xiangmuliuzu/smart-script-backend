package com.smartscript.platform.content.controller.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.service.IAppCopyrightSealService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * App 端用户印章接口（用户侧：上传、查看、停用、重新提交）。
 *
 * 路由前缀：/api/v1/content/copyright-seals
 * 鉴权：App 凭证域私有接口，需登录；用户 ID 只取自 {@link IdentityProvider}。
 * 返回：App 信封 {@link AppApiResponse}（{code, message, data}）。
 *
 * 状态机（review_status）：pending（待审核）/ approved（审核通过）/ rejected（审核驳回）
 * 状态机（seal_status）：disabled（停用）/ enabled（启用）/ abnormal（异常）
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/content/copyright-seals")
public class AppCopyrightSealController
{
    private static final int CODE_UNAUTHORIZED = 401;
    private static final int CODE_BAD_REQUEST = 400;
    private static final int CODE_NOT_FOUND = 404;
    private static final int CODE_CONFLICT = 409;

    private final IAppCopyrightSealService sealService;
    private final IdentityProvider identityProvider;

    public AppCopyrightSealController(IAppCopyrightSealService sealService,
            IdentityProvider identityProvider)
    {
        this.sealService = sealService;
        this.identityProvider = identityProvider;
    }

    /**
     * 我的印章列表（通常只有 1 条记录，支持多个历史记录）。
     *
     * GET /api/v1/copyright/seals
     *
     * 返回：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": [
     *     {
     *       "sealId": 1,
     *       "sealName": "张三个人印章",
     *       "sealImageUrl": "https://...",
     *       "reviewStatus": "approved",
     *       "sealStatus": "enabled",
     *       "rejectReason": null,
     *       "createTime": "2024-01-01 10:00:00",
     *       "reviewTime": "2024-01-02 15:30:00"
     *     }
     *   ]
     * }
     */
    @GetMapping
    public AppApiResponse<?> mySeals()
    {
        Long userId = requireLogin();
        List<Map<String, Object>> seals = sealService.mySeals(userId);
        return AppApiResponse.ok(seals);
    }

    /**
     * 印章详情（含材料 URL、审核状态、驳回原因、操作日志）。
     *
     * GET /api/v1/copyright/seals/{sealId}
     *
     * 返回：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "sealId": 1,
     *     "sealName": "张三个人印章",
     *     "sealImageUrl": "https://...",
     *     "materialUrls": "https://...,https://...",
     *     "reviewStatus": "approved",
     *     "sealStatus": "enabled",
     *     "rejectReason": null,
     *     "createTime": "2024-01-01 10:00:00",
     *     "reviewTime": "2024-01-02 15:30:00",
     *     "logs": [
     *       {
     *         "logId": 1,
     *         "operationType": "review_approve",
     *         "fromStatus": "pending",
     *         "toStatus": "approved",
     *         "operatorName": "管理员",
     *         "createTime": "2024-01-02 15:30:00"
     *       }
     *     ]
     *   }
     * }
     */
    @GetMapping("/{sealId}")
    public AppApiResponse<?> sealDetail(@PathVariable Long sealId)
    {
        Long userId = requireLogin();
        Map<String, Object> detail = sealService.sealDetail(userId, sealId);
        if (detail == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "印章不存在或无权限访问");
        }
        return AppApiResponse.ok(detail);
    }

    /**
     * 上传印章图片（首次提交）。
     *
     * POST /api/v1/copyright/seals
     *
     * 请求体：
     * {
     *   "sealName": "张三个人印章",
     *   "sealImageUrl": "https://...",
     *   "materialUrls": "https://...,https://..."
     * }
     *
     * 返回：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {"sealId": 1}
     * }
     */
    @PostMapping
    public AppApiResponse<?> submitSeal(@RequestBody Map<String, String> request)
    {
        Long userId = requireLogin();
        String sealName = request.get("sealName");
        String sealImageUrl = request.get("sealImageUrl");
        String materialUrls = request.get("materialUrls");

        try
        {
            Long sealId = sealService.submitSeal(userId, sealName, sealImageUrl, materialUrls);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sealId", sealId);
            return AppApiResponse.ok(result);
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (IllegalStateException e)
        {
            return AppApiResponse.fail(CODE_CONFLICT, e.getMessage());
        }
    }

    /**
     * 补充审核材料（印章已存在时追加材料）。
     *
     * POST /api/v1/copyright/seals/{sealId}/materials
     *
     * 请求体：
     * {
     *   "additionalMaterialUrls": "https://...,https://..."
     * }
     *
     * 返回：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": null
     * }
     */
    @PostMapping("/{sealId}/materials")
    public AppApiResponse<?> addMaterials(@PathVariable Long sealId,
            @RequestBody Map<String, String> request)
    {
        Long userId = requireLogin();
        String additionalMaterialUrls = request.get("additionalMaterialUrls");

        boolean success = sealService.addMaterials(userId, sealId, additionalMaterialUrls);
        if (!success)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "补充材料失败，请检查印章状态");
        }
        return AppApiResponse.ok(null);
    }

    /**
     * 重新提交印章（驳回后重新上传）。
     *
     * PUT /api/v1/copyright/seals/{sealId}/resubmit
     *
     * 请求体：
     * {
     *   "sealImageUrl": "https://...",
     *   "materialUrls": "https://...,https://..."
     * }
     *
     * 返回：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": null
     * }
     */
    @PutMapping("/{sealId}/resubmit")
    public AppApiResponse<?> resubmitSeal(@PathVariable Long sealId,
            @RequestBody Map<String, String> request)
    {
        Long userId = requireLogin();
        String sealImageUrl = request.get("sealImageUrl");
        String materialUrls = request.get("materialUrls");

        boolean success = sealService.resubmitSeal(userId, sealId, sealImageUrl, materialUrls);
        if (!success)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "重新提交失败，只有驳回状态才能重新提交");
        }
        return AppApiResponse.ok(null);
    }

    /**
     * 停用印章（用户主动停用，仅限已审核通过的印章）。
     *
     * PUT /api/v1/copyright/seals/{sealId}/disable
     *
     * 返回：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": null
     * }
     */
    @PutMapping("/{sealId}/disable")
    public AppApiResponse<?> disableSeal(@PathVariable Long sealId)
    {
        Long userId = requireLogin();
        boolean success = sealService.disableSeal(userId, sealId);
        if (!success)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "停用失败，只有已审核通过且启用状态的印章才能停用");
        }
        return AppApiResponse.ok(null);
    }

    /**
     * 更新印章信息（编辑印章名称、图片、材料，修改后重置审核状态为 pending）。
     *
     * PUT /api/v1/content/copyright-seals/{sealId}
     *
     * 请求体：
     * {
     *   "sealName": "新印章名称",        // 可选
     *   "sealImageUrl": "https://...",  // 可选
     *   "materialUrls": "https://..."   // 可选
     * }
     *
     * 返回：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": null
     * }
     */
    @PutMapping("/{sealId}")
    public AppApiResponse<?> updateSeal(@PathVariable Long sealId,
            @RequestBody Map<String, String> request)
    {
        Long userId = requireLogin();
        String sealName = request.get("sealName");
        String sealImageUrl = request.get("sealImageUrl");
        String materialUrls = request.get("materialUrls");

        try
        {
            boolean success = sealService.updateSeal(userId, sealId, sealName, sealImageUrl, materialUrls);
            if (!success)
            {
                return AppApiResponse.fail(CODE_NOT_FOUND, "更新失败，印章不存在或无权限访问");
            }
            return AppApiResponse.ok(null);
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * 删除印章（软删除）。
     *
     * DELETE /api/v1/content/copyright-seals/{sealId}
     *
     * 返回：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": null
     * }
     */
    @DeleteMapping("/{sealId}")
    public AppApiResponse<?> deleteSeal(@PathVariable Long sealId)
    {
        Long userId = requireLogin();
        boolean success = sealService.deleteSeal(userId, sealId);
        if (!success)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "删除失败，印章不存在或无权限访问");
        }
        return AppApiResponse.ok(null);
    }

    /**
     * 获取当前登录用户 ID（未登录抛异常）。
     */
    private Long requireLogin()
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            throw new IllegalStateException("用户未登录");
        }
        return userId;
    }
}
