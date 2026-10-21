package com.smartscript.platform.content.controller.app;

import org.springframework.web.bind.annotation.*;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.copyright.*;
import com.smartscript.platform.content.service.IAppCopyrightService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * App 端版权管理接口（版权申请、证书管理）。
 *
 * 路由前缀：/api/v1/content/copyright
 * 鉴权：App 凭证域私有接口，需登录；用户 ID 只取自 {@link IdentityProvider}。
 * 返回：App 信封 {@link AppApiResponse}（{code, message, data}）。
 *
 * 申请状态机（status）：
 *   pending（待审核）→ approved（待提交）→ submitted（待提交到第三方）
 *   → reviewing（审核中）→ certificated（已发证）或 rejected（已驳回）
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/content/copyright")
public class AppCopyrightController
{
    private static final int CODE_UNAUTHORIZED = 401;
    private static final int CODE_BAD_REQUEST = 400;
    private static final int CODE_NOT_FOUND = 404;
    private static final int CODE_CONFLICT = 409;

    private final IAppCopyrightService copyrightService;
    private final IdentityProvider identityProvider;

    public AppCopyrightController(IAppCopyrightService copyrightService,
            IdentityProvider identityProvider)
    {
        this.copyrightService = copyrightService;
        this.identityProvider = identityProvider;
    }

    // ==================== 版权申请 ====================

    /**
     * 我的版权申请列表（分页）。
     *
     * @param status 状态筛选（可选：pending/approved/submitted/reviewing/certificated/rejected）
     * @param workName 作品名称关键词（可选，模糊搜索）
     * @param pageNum 页码（默认 1）
     * @param pageSize 每页条数（默认 10）
     */
    @GetMapping("/application/list")
    public AppApiResponse<AppPageResult<AppCopyrightApplicationDto>> getMyApplications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String workName,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize)
    {
        Long userId = identityProvider.currentUserId();
        System.out.println("========== App版权申请列表查询 ==========");
        System.out.println("当前用户ID: " + userId);
        System.out.println("查询参数 - status: " + status + ", workName: " + workName + ", pageNum: " + pageNum + ", pageSize: " + pageSize);
        
        if (userId == null)
        {
            System.out.println("用户未登录，返回401");
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }

        AppPageResult<AppCopyrightApplicationDto> result = copyrightService.getMyApplications(
                userId, status, workName, pageNum, pageSize);
        System.out.println("查询结果 - total: " + result.getTotal() + ", list.size: " + result.getList().size());
        System.out.println("==========================================");
        return AppApiResponse.ok(result);
    }

    /**
     * 版权申请详情。
     *
     * @param applyId 申请ID
     */
    @GetMapping("/application/{applyId}")
    public AppApiResponse<AppCopyrightApplicationDto> getApplicationDetail(
            @PathVariable Long applyId)
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }

        AppCopyrightApplicationDto detail = copyrightService.getApplicationDetail(applyId, userId);
        if (detail == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "申请记录不存在");
        }

        return AppApiResponse.ok(detail);
    }

    /**
     * 提交版权申请。
     *
     * @param request 申请信息
     */
    @PostMapping("/application/apply")
    public AppApiResponse<Void> submitApplication(@RequestBody AppCopyrightApplyRequest request)
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }

        try
        {
            copyrightService.submitApplication(userId, request);
            return AppApiResponse.ok();
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (Exception e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "提交申请失败：" + e.getMessage());
        }
    }

    /**
     * 重新提交版权申请（驳回后）。
     *
     * @param request 重新提交信息
     */
    @PostMapping("/application/resubmit")
    public AppApiResponse<Void> resubmitApplication(@RequestBody AppCopyrightResubmitRequest request)
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }

        try
        {
            copyrightService.resubmitApplication(userId, request);
            return AppApiResponse.ok();
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (Exception e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "重新提交失败：" + e.getMessage());
        }
    }

    /**
     * 删除版权申请（仅限pending状态）。
     *
     * @param applyId 申请ID
     */
    @DeleteMapping("/application/{applyId}")
    public AppApiResponse<Void> deleteApplication(@PathVariable Long applyId)
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }

        try
        {
            copyrightService.deleteApplication(applyId, userId);
            return AppApiResponse.ok();
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (Exception e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "删除失败：" + e.getMessage());
        }
    }

    /**
     * 更新版权申请（仅限pending状态）。
     *
     * @param applyId 申请ID
     * @param request 更新信息
     */
    @PutMapping("/application/{applyId}")
    public AppApiResponse<Void> updateApplication(
            @PathVariable Long applyId,
            @RequestBody AppCopyrightApplyRequest request)
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }

        try
        {
            copyrightService.updateApplication(applyId, userId, request);
            return AppApiResponse.ok();
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (Exception e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "更新失败：" + e.getMessage());
        }
    }

    // ==================== 版权证书 ====================

    /**
     * 我的版权证书列表（分页）。
     *
     * @param workName 作品名称关键词（可选，模糊搜索）
     * @param pageNum 页码（默认 1）
     * @param pageSize 每页条数（默认 10）
     */
    @GetMapping("/cert/list")
    public AppApiResponse<AppPageResult<AppCopyrightCertDto>> getMyCertificates(
            @RequestParam(required = false) String workName,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize)
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }

        AppPageResult<AppCopyrightCertDto> result = copyrightService.getMyCertificates(
                userId, workName, pageNum, pageSize);
        return AppApiResponse.ok(result);
    }

    /**
     * 版权证书详情。
     *
     * @param certId 证书ID
     */
    @GetMapping("/cert/{certId}")
    public AppApiResponse<AppCopyrightCertDto> getCertificateDetail(@PathVariable Long certId)
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }

        AppCopyrightCertDto detail = copyrightService.getCertificateDetail(certId, userId);
        if (detail == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "证书不存在");
        }

        return AppApiResponse.ok(detail);
    }

    /**
     * 下载版权证书 PDF。
     *
     * @param certId 证书ID
     */
    @GetMapping("/cert/{certId}/download")
    public AppApiResponse<AppCopyrightCertDownloadDto> downloadCertificate(@PathVariable Long certId)
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }

        try
        {
            AppCopyrightCertDownloadDto download = copyrightService.downloadCertificate(certId, userId);
            if (download == null)
            {
                return AppApiResponse.fail(CODE_NOT_FOUND, "证书不存在");
            }
            return AppApiResponse.ok(download);
        }
        catch (Exception e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "下载失败：" + e.getMessage());
        }
    }

    // ==================== 版权中心配置 ====================

    /**
     * 获取可用的版权中心列表。
     */
    @GetMapping("/config/active")
    public AppApiResponse<java.util.List<AppCopyrightCenterDto>> getActiveCenters()
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }

        java.util.List<AppCopyrightCenterDto> centers = copyrightService.getActiveCenters();
        return AppApiResponse.ok(centers);
    }
}
