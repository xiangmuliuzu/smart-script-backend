package com.smartscript.platform.user.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import com.ruoyi.common.enums.BusinessStatus;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.OperatorType;
import com.ruoyi.framework.security.handle.AuthenticationFailureAuditor;
import com.ruoyi.system.domain.SysOperLog;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;

/**
 * 第 11 批：过滤器链层 401 审计口径与「恰好一次」契约测试（H9-LOG-04 残余）。
 * 第 15 批：扩展 App 过滤器 403（凭证域不符 / 账号禁用）的事件类型与防重复记账契约。
 *
 * 不启动 Spring、不连库：直接断言审计行字段、路径范围与请求级一次性标记，
 * 与黑盒脚本 `shared/scripts/h-a-filterchain-401-audit.mjs`、`h-a-filterchain-403-audit.mjs`
 * （真实后端逐请求断言）互补。
 */
class FilterChainAuthFailureAuditorTest
{
    private final FilterChainAuthFailureAuditor auditor = new FilterChainAuthFailureAuditor();

    private static MockHttpServletRequest request(String method, String uri)
    {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr("127.0.0.1");
        return req;
    }

    // ------------------------------------------------------------ 字段口径
    @Test
    void pcAuditRowHasMinimalSafeShape()
    {
        SysOperLog row = auditor.build(AuthenticationFailureAuditor.DOMAIN_PC,
                request("GET", "/api/v1/admin/app-users"), 401, null);
        assertEquals(FilterChainAuthFailureAuditor.TITLE_PC, row.getTitle());
        assertEquals(BusinessType.OTHER.ordinal(), row.getBusinessType());
        assertEquals(OperatorType.MANAGE.ordinal(), row.getOperatorType());
        assertEquals(BusinessStatus.FAIL.ordinal(), row.getStatus());
        assertEquals(FilterChainAuthFailureAuditor.ERROR_PC, row.getErrorMsg());
        assertEquals("", row.getMethod());
        assertEquals("GET", row.getRequestMethod());
        assertEquals("/api/v1/admin/app-users", row.getOperUrl());
        assertEquals(0L, row.getCostTime());
        // 不记录请求参数/响应体/操作人（过滤器层不解析身份）
        assertNull(row.getOperParam());
        assertNull(row.getJsonResult());
        assertNull(row.getOperName());
    }

    @Test
    void appAuditRowUsesMobileActorTypeAndAppTitle()
    {
        SysOperLog row = auditor.build(AuthenticationFailureAuditor.DOMAIN_APP,
                request("POST", "/api/v1/users/me/profile"), 401, null);
        assertEquals(FilterChainAuthFailureAuditor.TITLE_APP, row.getTitle());
        assertEquals(OperatorType.MOBILE.ordinal(), row.getOperatorType());
        assertEquals(FilterChainAuthFailureAuditor.ERROR_APP, row.getErrorMsg());
        assertEquals("POST", row.getRequestMethod());
    }

    // ------------------------------------------------------------ 第 15 批：App 过滤器 403 事件类型可定位
    @Test
    void appForbiddenRowDistinguishesDomainAndDisabledEvents()
    {
        SysOperLog domainRow = auditor.build(AuthenticationFailureAuditor.DOMAIN_APP,
                request("GET", "/api/v1/users/me/profile"), 403, AuthenticationFailureAuditor.REASON_DOMAIN_OR_PERMISSION);
        assertEquals(FilterChainAuthFailureAuditor.TITLE_APP_DOMAIN, domainRow.getTitle());
        assertEquals(FilterChainAuthFailureAuditor.ERROR_APP_DOMAIN, domainRow.getErrorMsg());

        SysOperLog disabledRow = auditor.build(AuthenticationFailureAuditor.DOMAIN_APP,
                request("GET", "/api/v1/users/me/profile"), 403, AuthenticationFailureAuditor.REASON_ACCOUNT_DISABLED);
        assertEquals(FilterChainAuthFailureAuditor.TITLE_APP_DISABLED, disabledRow.getTitle());
        assertEquals(FilterChainAuthFailureAuditor.ERROR_APP_DISABLED, disabledRow.getErrorMsg());

        // 防御深度：未知 403 原因落兜底标题/文案，绝不吞掉事件
        SysOperLog fallbackRow = auditor.build(AuthenticationFailureAuditor.DOMAIN_APP,
                request("GET", "/api/v1/users/me/profile"), 403, null);
        assertEquals(FilterChainAuthFailureAuditor.TITLE_APP_FORBIDDEN, fallbackRow.getTitle());
        assertEquals(FilterChainAuthFailureAuditor.ERROR_APP_FORBIDDEN, fallbackRow.getErrorMsg());

        // 403 行同样是最小口径：结果 FAIL、无参数/响应体、不猜操作人
        assertEquals(BusinessStatus.FAIL.ordinal(), disabledRow.getStatus());
        assertEquals(OperatorType.MOBILE.ordinal(), disabledRow.getOperatorType());
        assertNull(disabledRow.getOperParam());
        assertNull(disabledRow.getJsonResult());
        assertNull(disabledRow.getOperName());
    }

    @Test
    void tokenBearingPathIsRedactedInOperUrl()
    {
        String token = "h11SynthMaterialTokenAbCdEf0123456789";
        SysOperLog row = auditor.build(AuthenticationFailureAuditor.DOMAIN_PC,
                request("GET", "/api/v1/admin/material/content/" + token), 401, null);
        assertFalse(row.getOperUrl().contains(token), "材料令牌不得进入 oper_url");
        assertTrue(row.getOperUrl().contains("{redacted}"), "长令牌段必须脱敏为 {redacted}");
    }

    @Test
    void appForbidden403WithTokenBearingPathIsRedacted()
    {
        String token = "h15SynthMaterialTokenAbCdEf0123456789";
        SysOperLog row = auditor.build(AuthenticationFailureAuditor.DOMAIN_APP,
                request("GET", "/api/v1/users/material/content/" + token), 403, AuthenticationFailureAuditor.REASON_ACCOUNT_DISABLED);
        assertFalse(String.valueOf(row.getOperUrl()).contains(token), "403 审计行同样必须脱敏路径令牌");
        assertTrue(String.valueOf(row.getOperUrl()).contains("{redacted}"), "403 审计行长令牌段必须为 {redacted}");
    }

    @Test
    void authorizationHeaderIsNeverRecorded()
    {
        MockHttpServletRequest req = request("GET", "/api/v1/users/me/profile");
        req.addHeader("Authorization", "Bearer h11SecretTokenValueAbCdEf0123456789");
        SysOperLog row = auditor.build(AuthenticationFailureAuditor.DOMAIN_APP, req, 401, null);
        String all = String.valueOf(row.getTitle()) + row.getOperUrl() + row.getOperParam() + row.getJsonResult()
                + row.getErrorMsg() + row.getOperName() + row.getMethod();
        assertFalse(all.contains("h11SecretTokenValueAbCdEf0123456789"), "不得记录 Authorization 头");
    }

    @Test
    void appForbidden403NeverRecordsAuthorizationHeader()
    {
        MockHttpServletRequest req = request("GET", "/api/v1/users/me/profile");
        req.addHeader("Authorization", "Bearer h15SecretTokenValueAbCdEf0123456789");
        SysOperLog row = auditor.build(AuthenticationFailureAuditor.DOMAIN_APP, req, 403,
                AuthenticationFailureAuditor.REASON_DOMAIN_OR_PERMISSION);
        String all = String.valueOf(row.getTitle()) + row.getOperUrl() + row.getOperParam() + row.getJsonResult()
                + row.getErrorMsg() + row.getOperName() + row.getMethod();
        assertFalse(all.contains("h15SecretTokenValueAbCdEf0123456789"), "403 审计同样不得记录 Authorization 头");
    }

    // ------------------------------------------------------------ 路径范围
    @Test
    void scopeCoversAModulePathsOnly()
    {
        assertTrue(FilterChainAuthFailureAuditor.inScope("/api/v1/admin/app-users"));
        assertTrue(FilterChainAuthFailureAuditor.inScope("/api/v1/auth/token/refresh"));
        assertTrue(FilterChainAuthFailureAuditor.inScope("/api/v1/users/me/profile"));
        assertTrue(FilterChainAuthFailureAuditor.inScope("/api/v1/messages"));
        assertTrue(FilterChainAuthFailureAuditor.inScope("/api/v1/feedback/1"));
        // C 等业务模块与若依原生路径不在本批范围
        assertFalse(FilterChainAuthFailureAuditor.inScope("/api/v1/content/works"));
        assertFalse(FilterChainAuthFailureAuditor.inScope("/api/v1/content/__probe__"));
        assertFalse(FilterChainAuthFailureAuditor.inScope("/system/user/list"));
        assertFalse(FilterChainAuthFailureAuditor.inScope("/api/v1/adminx"));
        assertFalse(FilterChainAuthFailureAuditor.inScope(null));
    }

    // ------------------------------------------------------------ 恰好一次
    @Test
    void claimSucceedsOncePerRequestAndRejectsOutOfContractCases()
    {
        MockHttpServletRequest req = request("GET", "/api/v1/admin/app-users");
        assertTrue(FilterChainAuthFailureAuditor.claim(req, AuthenticationFailureAuditor.DOMAIN_PC, 401),
                "首次应认领成功");
        assertFalse(FilterChainAuthFailureAuditor.claim(req, AuthenticationFailureAuditor.DOMAIN_PC, 401),
                "同一请求第二次必须拒绝，保证恰好一条审计行");

        // PC 403 不在本通道（A4 @PreAuthorize 403 由 A4DeniedAccessAuditor 处置，避免重复记账）
        assertFalse(FilterChainAuthFailureAuditor.claim(request("GET", "/api/v1/admin/app-users"),
                AuthenticationFailureAuditor.DOMAIN_PC, 403));
        // C 模块路径不记录（401 与 403 均不记录）
        assertFalse(FilterChainAuthFailureAuditor.claim(request("GET", "/api/v1/content/works"),
                AuthenticationFailureAuditor.DOMAIN_APP, 401));
        assertFalse(FilterChainAuthFailureAuditor.claim(request("GET", "/api/v1/content/works"),
                AuthenticationFailureAuditor.DOMAIN_APP, 403));
        // 空请求
        assertFalse(FilterChainAuthFailureAuditor.claim(null, AuthenticationFailureAuditor.DOMAIN_PC, 401));
    }

    @Test
    void appForbidden403ClaimsExactlyOncePerRequest()
    {
        // 第 15 批：App 过滤器 403 进入本通道，但同一请求仍恰好一条
        MockHttpServletRequest req = request("GET", "/api/v1/users/me/profile");
        assertTrue(FilterChainAuthFailureAuditor.claim(req, AuthenticationFailureAuditor.DOMAIN_APP, 403),
                "App 403 首次应认领成功");
        assertFalse(FilterChainAuthFailureAuditor.claim(req, AuthenticationFailureAuditor.DOMAIN_APP, 403),
                "同一请求第二次 403 回调必须拒绝");
        assertFalse(FilterChainAuthFailureAuditor.claim(req, AuthenticationFailureAuditor.DOMAIN_APP, 401),
                "403 与 401 回调共享一次性标记，混合回调也不得重复记账");
    }

    @Test
    void distinctRequestsEachClaimOwnSlot()
    {
        MockHttpServletRequest a = request("GET", "/api/v1/admin/app-users");
        MockHttpServletRequest b = request("GET", "/api/v1/admin/feedback");
        assertTrue(FilterChainAuthFailureAuditor.claim(a, AuthenticationFailureAuditor.DOMAIN_PC, 401));
        assertTrue(FilterChainAuthFailureAuditor.claim(b, AuthenticationFailureAuditor.DOMAIN_PC, 401));
        assertNotEquals(a.getAttribute(FilterChainAuthFailureAuditor.AUDITED_MARKER),
                b.getAttribute(FilterChainAuthFailureAuditor.AUDITED_MARKER) == null);
    }

    @Test
    void appAuditReasonMapsErrorCodeToStableEventType()
    {
        assertEquals(AuthenticationFailureAuditor.REASON_DOMAIN_OR_PERMISSION,
                AppAuthAuthenticationFilter.auditReason(AppAuthErrorCodes.DOMAIN_OR_PERMISSION));
        assertEquals(AuthenticationFailureAuditor.REASON_ACCOUNT_DISABLED,
                AppAuthAuthenticationFilter.auditReason(AppAuthErrorCodes.ACCOUNT_DISABLED));
        // 401 类不携带 reason（沿用第 11 批口径）
        assertNull(AppAuthAuthenticationFilter.auditReason(AppAuthErrorCodes.UNAUTHORIZED));
        assertNull(AppAuthAuthenticationFilter.auditReason(AppAuthErrorCodes.ACCESS_EXPIRED));
    }
}
