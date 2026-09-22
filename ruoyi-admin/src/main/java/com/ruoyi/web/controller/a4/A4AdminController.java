package com.ruoyi.web.controller.a4;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.smartscript.platform.user.constant.AppAdminConstants;
import com.smartscript.platform.user.domain.admin.AdminFeedback;
import com.smartscript.platform.user.domain.admin.AdminNotification;
import com.smartscript.platform.user.domain.admin.AppUserDetail;
import com.smartscript.platform.user.domain.admin.AppUserSummary;
import com.smartscript.platform.user.domain.admin.AuthorCapability;
import com.smartscript.platform.user.domain.admin.GrantableRole;
import com.smartscript.platform.user.domain.admin.RealNameApplication;
import com.smartscript.platform.user.exception.AppAdminException;
import com.smartscript.platform.user.service.AppUserAdminService;
import com.smartscript.platform.user.service.AuthorCapabilityService;
import com.smartscript.platform.user.service.MaterialAccessLinkService;
import com.smartscript.platform.user.service.MaterialAccessTokenService;
import com.smartscript.platform.user.service.MaterialGateway;
import com.smartscript.platform.user.service.RealNameReviewService;
import com.smartscript.platform.user.service.UserFeedbackAdminService;
import com.smartscript.platform.user.service.UserNotificationAdminService;

/**
 * A4 PC 管理接口（契约 A4-PC-ADMIN-CONTRACT-v1，基础路径 /api/v1/admin）。
 *
 * 分域约定：
 *   - 认证域为若依 PC Token；App Token 由安全配置在更外层拒绝（USER-01）。
 *   - 管理员身份只来自若依安全上下文（SecurityUtils），不接受请求体指定操作人。
 *   - 列表返回 TableDataInfo，详情与写操作返回 AjaxResult（若依统一封装）。
 *   - Controller 只做参数校验、权限注解、响应适配与 @Log 审计；
 *     事务、状态机、会话吊销与并发控制在 smartscript-user 的服务层完成。
 *
 * 审计安全约束（一票否决项：Token、材料地址不得进入日志）：
 *   - 敏感详情的响应体含短时令牌，一律 isSaveResponseData = false。
 *   - 令牌走请求体而非 URL 路径，并对该参数 excludeParamNames；
 *     放在路径里会被 oper_url 完整记录。
 *   - 兑换接口只回材料类型，真实地址留在服务端待材料网关换签。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class A4AdminController extends BaseController
{
    private final AppUserAdminService appUserAdminService;
    private final RealNameReviewService realNameReviewService;
    private final AuthorCapabilityService authorCapabilityService;
    private final UserNotificationAdminService notificationService;
    private final UserFeedbackAdminService feedbackService;
    private final MaterialAccessTokenService materialTokenService;
    private final MaterialAccessLinkService materialLinkService;
    private final MaterialGateway materialGateway;

    public A4AdminController(AppUserAdminService appUserAdminService,
            RealNameReviewService realNameReviewService,
            AuthorCapabilityService authorCapabilityService,
            UserNotificationAdminService notificationService,
            UserFeedbackAdminService feedbackService,
            MaterialAccessTokenService materialTokenService,
            MaterialAccessLinkService materialLinkService,
            MaterialGateway materialGateway)
    {
        this.appUserAdminService = appUserAdminService;
        this.realNameReviewService = realNameReviewService;
        this.authorCapabilityService = authorCapabilityService;
        this.notificationService = notificationService;
        this.feedbackService = feedbackService;
        this.materialTokenService = materialTokenService;
        this.materialLinkService = materialLinkService;
        this.materialGateway = materialGateway;
    }

    // ------------------------------------------------------------------
    // 敏感材料令牌兑换
    // ------------------------------------------------------------------

    /**
     * 兑换敏感材料/附件的短时令牌（契约 §3.2 / §3.5 / §7）。
     *
     *   - 令牌有服务端强制的有效期，客户端无法延长；
     *   - 令牌一次性，以 Lua 脚本原子取出+删除，并发只能成功一次；
     *   - 令牌绑定材料类型，兑换时须持有该类型对应的详情权限；
     *   - 每次兑换进入操作日志，但日志不含令牌与真实地址。
     */
    @PreAuthorize("@ss.hasAnyPermi('" + AppAdminConstants.PERM_REALNAME_QUERY + ","
            + AppAdminConstants.PERM_FEEDBACK_QUERY + "')")
    @Log(title = "A4-敏感材料兑换", businessType = BusinessType.OTHER,
         excludeParamNames = { "token", "body" }, isSaveResponseData = false)
    @PostMapping("/material-refs/redeem")
    public AjaxResult redeemMaterialRef(@RequestBody Map<String, Object> body)
    {
        String token = str(body.get("token"));
        if (!MaterialAccessTokenService.looksLikeToken(token))
        {
            throw AppAdminException.notFound();
        }

        // 调用方实际持有的权限集合交给服务层比对；
        // 令牌绑定的材料类型决定需要哪个详情权限，类型不匹配即不可兑换。
        List<String> granted = new ArrayList<>(SecurityUtils.getLoginUser().getPermissions());

        MaterialAccessTokenService.RedeemResult result = materialTokenService.tryRedeem(token, granted);
        if (result == null)
        {
            // 不存在、已消费，或类型与所持权限不匹配：统一按不可兑换处理
            throw AppAdminException.notFound();
        }

        // 回材料类型 + 一次性访问链接。链接本身不含材料引用，
        // 页面请求该链接即可读到真实字节流；链接短时且只能用一次。
        String accessUrl = materialLinkService.issue(result.getKind(), result.getRawRefInternal());
        return AjaxResult.success(Map.of(
                "kind", result.getKind(),
                "accessUrl", accessUrl,
                "expiresInSeconds", MaterialAccessLinkService.LINK_TTL_SECONDS));
    }

    /**
     * 读取材料内容（契约 §7：材料访问必须短时授权、校验详情权限并记录访问审计）。
     *
     * 访问链接一次性：并发请求只有一个能读到字节流，其余 404。
     * 响应为二进制内容，且**禁止写入操作日志**——日志只记访问事实（标题、操作人、时间、结果），
     * 不含链接、引用与内容。
     */
    @PreAuthorize("@ss.hasAnyPermi('" + AppAdminConstants.PERM_REALNAME_QUERY + ","
            + AppAdminConstants.PERM_FEEDBACK_QUERY + "')")
    @Log(title = "A4-材料内容读取", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/material/content/{token}")
    public ResponseEntity<byte[]> readMaterialContent(@PathVariable String token) throws IOException
    {
        MaterialAccessLinkService.LinkPayload payload = materialLinkService.consume(token);
        if (payload == null)
        {
            throw AppAdminException.notFound();
        }

        // 第二段必须复核材料类型对应的权限：接口级 @PreAuthorize 接受
        // 「实名详情 OR 反馈详情」，因此仅持反馈详情权限的账号也能通过接口校验。
        // 若不在此处按令牌绑定的类型再校验，拿着实名链接就能读到实名材料，反之亦然。
        // 用 SecurityUtils.hasPermi（它正确处理 RuoYi 的 *:*:* 通配），
        // 而不是直接对权限集合做字面量比较——后者会把超管误判为无权限。
        String requiredPerm = MaterialAccessTokenService.permissionForKind(payload.getKind());
        if (requiredPerm == null || !SecurityUtils.hasPermi(requiredPerm))
        {
            throw AppAdminException.notFound();
        }

        byte[] content;
        try (InputStream in = materialGateway.open(payload.getRawRefInternal()))
        {
            content = in.readAllBytes();
        }
        catch (IOException e)
        {
            // 网关失败按不存在处理，不把存储细节透给客户端
            throw AppAdminException.notFound();
        }

        // 安全处置：材料可能是用户上传的 HTML/SVG 等可执行内容。
        // 若按原始类型内联返回，浏览器会在同源下渲染并执行其中的脚本，
        // 从而读取管理后台的存储凭证（同源脚本执行）。因此：
        //   1. 一律以 application/octet-stream 返回，强制作为下载/未知类型处理；
        //   2. 附 Content-Disposition: attachment，禁止内联渲染；
        //   3. nosniff 防止浏览器重新嗅探为可执行类型。
        // 真实内容类型仅作为自定义头回传，供调用方选择展示方式。
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(content.length);
        headers.setCacheControl("no-store, no-cache, must-revalidate");
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment");
        headers.add("X-Material-Content-Type",
                materialGateway.contentType(payload.getRawRefInternal()));
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    // ------------------------------------------------------------------
    // App 用户管理
    // ------------------------------------------------------------------

    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_APP_LIST + "')")
    @GetMapping("/app-users")
    public TableDataInfo listAppUsers(@RequestParam Map<String, Object> params)
    {
        startPage(params);
        List<AppUserSummary> rows = appUserAdminService.page(params);
        return getDataTable(rows);
    }

    /**
     * 可授予 App 用户的角色清单。
     * 静态段优先于路径变量，与 /app-users/{userId} 无歧义。
     */
    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_APP_GRANT + "')")
    @GetMapping("/app-users/grantable-roles")
    public AjaxResult grantableRoles()
    {
        List<GrantableRole> roles = appUserAdminService.grantableRoles();
        return AjaxResult.success(roles);
    }

    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_APP_QUERY + "')")
    @GetMapping("/app-users/{userId}")
    public AjaxResult appUserDetail(@PathVariable Long userId)
    {
        AppUserDetail detail = appUserAdminService.detail(userId);
        return AjaxResult.success(detail);
    }

    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_APP_STATUS + "')")
    @Log(title = "A4-App用户状态", businessType = BusinessType.UPDATE, excludeParamNames = { "reason" })
    @PutMapping("/app-users/{userId}/status")
    public AjaxResult changeStatus(@PathVariable Long userId, @RequestBody Map<String, Object> body)
    {
        String status = str(body.get("status"));
        String reason = str(body.get("reason"));
        boolean changed = appUserAdminService.changeStatus(userId, status, reason, SecurityUtils.getUsername());
        return resultWithChange(changed);
    }

    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_APP_GRANT + "')")
    @Log(title = "A4-用户角色授权", businessType = BusinessType.GRANT)
    @PutMapping("/app-users/{userId}/roles")
    public AjaxResult grantRoles(@PathVariable Long userId, @RequestBody Map<String, Object> body)
    {
        String reason = str(body.get("reason"));
        List<Long> roleIds = longList(body.get("roleIds"));
        boolean changed = appUserAdminService.grantRoles(userId, roleIds, reason, SecurityUtils.getUsername());
        return resultWithChange(changed);
    }

    // ------------------------------------------------------------------
    // 实名审核
    // ------------------------------------------------------------------

    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_REALNAME_LIST + "')")
    @GetMapping("/real-name-applications")
    public TableDataInfo listRealNameApplications(@RequestParam Map<String, Object> params)
    {
        startPage(params);
        List<RealNameApplication> rows = realNameReviewService.page(params);
        return getDataTable(rows);
    }

    /**
     * 实名申请详情，需要独立的详情权限（契约 §4）。
     * 响应体含短时材料令牌，因此禁止记录响应数据。
     */
    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_REALNAME_QUERY + "')")
    @Log(title = "A4-实名材料访问", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/real-name-applications/{applicationId}")
    public AjaxResult realNameDetail(@PathVariable Long applicationId)
    {
        RealNameApplication detail = realNameReviewService.detail(applicationId, true);
        return AjaxResult.success(detail);
    }

    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_REALNAME_AUDIT + "')")
    @Log(title = "A4-实名审核", businessType = BusinessType.UPDATE, excludeParamNames = { "rejectReason" })
    @PutMapping("/real-name-applications/{applicationId}/decision")
    public AjaxResult decideRealName(@PathVariable Long applicationId, @RequestBody Map<String, Object> body)
    {
        String decision = str(body.get("decision"));
        String rejectReason = str(body.get("rejectReason"));
        // 客户端 expectedStatus 必须传入并被校验：错误值返回 409，不静默按 PENDING 放行
        String expectedStatus = str(body.get("expectedStatus"));
        RealNameApplication updated = realNameReviewService.decide(applicationId, decision, rejectReason,
                SecurityUtils.getUserId(), expectedStatus);
        return AjaxResult.success(updated);
    }

    // ------------------------------------------------------------------
    // 作者能力
    // ------------------------------------------------------------------

    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_CREATOR_LIST + "')")
    @GetMapping("/author-capabilities")
    public TableDataInfo listAuthorCapabilities(@RequestParam Map<String, Object> params)
    {
        startPage(params);
        List<AuthorCapability> rows = authorCapabilityService.page(params);
        return getDataTable(rows);
    }

    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_CREATOR_UPDATE + "')")
    @Log(title = "A4-作者能力", businessType = BusinessType.UPDATE, excludeParamNames = { "reason" })
    @PutMapping("/author-capabilities/{userId}")
    public AjaxResult updateAuthorCapability(@PathVariable Long userId, @RequestBody Map<String, Object> body)
    {
        boolean enabled = boolValue(body.get("enabled"));
        String reason = str(body.get("reason"));
        boolean changed = authorCapabilityService.setEnabled(userId, enabled, reason,
                SecurityUtils.getUserId(), SecurityUtils.getUsername());
        return resultWithChange(changed);
    }

    // ------------------------------------------------------------------
    // 用户消息
    // ------------------------------------------------------------------

    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_MESSAGE_LIST + "')")
    @GetMapping("/notifications")
    public TableDataInfo listNotifications(@RequestParam Map<String, Object> params)
    {
        startPage(params);
        List<AdminNotification> rows = notificationService.page(params);
        return getDataTable(rows);
    }

    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_MESSAGE_QUERY + "')")
    @GetMapping("/notifications/{notificationId}")
    public AjaxResult notificationDetail(@PathVariable Long notificationId)
    {
        AdminNotification detail = notificationService.detail(notificationId);
        return AjaxResult.success(detail);
    }

    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_MESSAGE_ADD + "')")
    @Log(title = "A4-用户消息", businessType = BusinessType.INSERT)
    @PostMapping("/notifications")
    public AjaxResult createNotification(@RequestBody Map<String, Object> body)
    {
        UserNotificationAdminService.CreateResult result = notificationService.create(
                str(body.get("requestId")),
                str(body.get("type")),
                str(body.get("title")),
                str(body.get("content")),
                str(body.get("businessType")),
                str(body.get("businessId")),
                longList(body.get("userIds")),
                SecurityUtils.getUsername());
        return AjaxResult.success(Map.of(
                "notificationId", result.getNotificationId(),
                "receiverCount", result.getReceiverCount(),
                "created", result.isCreated()));
    }

    // ------------------------------------------------------------------
    // 用户反馈
    // ------------------------------------------------------------------

    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_FEEDBACK_LIST + "')")
    @GetMapping("/feedback")
    public TableDataInfo listFeedback(@RequestParam Map<String, Object> params)
    {
        startPage(params);
        List<AdminFeedback> rows = feedbackService.page(params);
        return getDataTable(rows);
    }

    /**
     * 反馈详情，需要独立的详情权限（契约 §4）。
     * 响应体含附件短时令牌，因此禁止记录响应数据。
     */
    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_FEEDBACK_QUERY + "')")
    @Log(title = "A4-反馈附件访问", businessType = BusinessType.OTHER, isSaveResponseData = false)
    @GetMapping("/feedback/{feedbackId}")
    public AjaxResult feedbackDetail(@PathVariable Long feedbackId)
    {
        AdminFeedback detail = feedbackService.detail(feedbackId, true);
        return AjaxResult.success(detail);
    }

    @PreAuthorize("@ss.hasPermi('" + AppAdminConstants.PERM_FEEDBACK_HANDLE + "')")
    @Log(title = "A4-用户反馈处理", businessType = BusinessType.UPDATE, excludeParamNames = { "reply" })
    @PutMapping("/feedback/{feedbackId}/handle")
    public AjaxResult handleFeedback(@PathVariable Long feedbackId, @RequestBody Map<String, Object> body)
    {
        UserFeedbackAdminService.HandleResult result = feedbackService.handle(
                feedbackId,
                str(body.get("action")),
                str(body.get("reply")),
                str(body.get("expectedStatus")),
                SecurityUtils.getUserId());
        return AjaxResult.success(Map.of(
                "feedbackId", result.getFeedbackId(),
                "status", result.getStatus(),
                "changed", result.isChanged()));
    }

    // ------------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------------

    /**
     * 分页参数净化：pageNum>=1、1<=pageSize<=100（契约 §1）。
     * 越界时收敛到边界值而不是抛错，保持与若依原生分页一致的行为。
     */
    private void startPage(Map<String, Object> params)
    {
        int pageNum = intValue(params.get("pageNum"), 1);
        int pageSize = intValue(params.get("pageSize"), 10);
        if (pageNum < 1)
        {
            pageNum = 1;
        }
        if (pageSize < 1)
        {
            pageSize = 10;
        }
        if (pageSize > AppAdminConstants.PAGE_SIZE_MAX)
        {
            pageSize = AppAdminConstants.PAGE_SIZE_MAX;
        }
        PageHelper.startPage(pageNum, pageSize);
    }

    /** 写操作的幂等反馈：未发生变更时显式标记，便于前端与审计区分。 */
    private AjaxResult resultWithChange(boolean changed)
    {
        return AjaxResult.success(Map.of("changed", changed));
    }

    private String str(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private boolean boolValue(Object value)
    {
        if (value instanceof Boolean b)
        {
            return b;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private int intValue(Object value, int fallback)
    {
        if (value instanceof Number n)
        {
            return n.intValue();
        }
        try
        {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    /** roleIds / userIds 数组解析：忽略空值与非法项，由服务层做去重与边界校验。 */
    private List<Long> longList(Object value)
    {
        List<Long> result = new ArrayList<>();
        if (!(value instanceof List<?> list))
        {
            return result;
        }
        for (Object item : list)
        {
            if (item instanceof Number n)
            {
                result.add(n.longValue());
            }
            else if (item != null)
            {
                try
                {
                    result.add(Long.parseLong(String.valueOf(item)));
                }
                catch (NumberFormatException ignored)
                {
                    // 非法项忽略，最终由服务层的边界校验决定是否拒绝
                }
            }
        }
        return result;
    }
}
