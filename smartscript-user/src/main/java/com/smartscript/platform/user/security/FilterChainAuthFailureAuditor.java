package com.smartscript.platform.user.security;

import java.util.Date;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ruoyi.common.enums.BusinessStatus;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.OperatorType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.framework.aspectj.OperLogSanitizer;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.framework.manager.factory.AsyncFactory;
import com.ruoyi.framework.security.handle.AuthenticationFailureAuditor;
import com.ruoyi.system.domain.SysOperLog;

/**
 * A 模块过滤器链层 401/403 审计实现（H9-LOG-04 残余，第 11 批；第 15 批扩展 App 过滤器 403）。
 *
 * 背景：验收标准 §9.3 要求「权限拒绝有安全审计」。第 10 批已补 A4 {@code @PreAuthorize} 403 审计；
 * 第 11 批补了过滤器链层 401（PC 走 {@code AuthenticationEntryPointImpl}、App 走
 * {@code AppAuthAuthenticationFilter}）；第 15 批按清单 §3.14 负责人边界决策补齐
 * App 过滤器产生的 403（{@code DOMAIN_OR_PERMISSION} 凭证域不符 / {@code ACCOUNT_DISABLED} 账号禁用）——
 * 这些决定都在 {@code DispatcherServlet} 之前做出，任何 MVC 层日志通道都拿不到。
 *
 * 记录口径（刻意最小化，不引入新的泄漏面，且不改变响应）：
 *   - 只记操作人（401/403 在过滤器层不解析身份，恒为空）、目标（脱敏 oper_url）、请求方式、
 *     结果（FAIL）、按事件类型区分的安全文案、IP、时间；
 *   - App 403 按事件类型用不同 title 可定位：凭证域不符 → {@link #TITLE_APP_DOMAIN}、
 *     账号禁用 → {@link #TITLE_APP_DISABLED}（同一请求恰好一条，不向 {@code sys_logininfor} 重复记账）；
 *   - {@code oper_param}/{@code json_result} 恒为空，不读取请求体；
 *   - 不读取 {@code Authorization} 头，不记录查询串（{@code getRequestURI()} 不含 query），
 *     {@code oper_name} 不解析令牌去猜；
 *   - {@code oper_url} 走与 {@code LogAspect}/第 10 批 403 审计同一 {@link OperLogSanitizer} 白名单式脱敏；
 *   - 只覆盖 A 模块路径（{@link #inScope}）：PC {@code /api/v1/admin/**} 与 App
 *     {@code /api/v1/{auth,users,messages,feedback}/**}；{@code /api/v1/content/**}（C 等模块）
 *     与若依原生路径一律跳过（含其 403）；
 *   - 同一请求只记一次（请求级一次性标记），任何异常都被吞掉，不影响被拒响应。
 *
 * 未覆盖：其它模块的 403（A4 {@code @PreAuthorize} 403 由 {@code A4DeniedAccessAuditor} 处置）。
 */
@Component
public class FilterChainAuthFailureAuditor implements AuthenticationFailureAuditor
{
    private static final Logger log = LoggerFactory.getLogger(FilterChainAuthFailureAuditor.class);

    /** PC 域拒绝审计行标题。 */
    public static final String TITLE_PC = "A4-PC认证拒绝";

    /** App 域拒绝审计行标题。 */
    public static final String TITLE_APP = "App-认证拒绝";

    /** App 域 403（凭证域/权限不符）审计行标题（第 15 批）。 */
    public static final String TITLE_APP_DOMAIN = "App-凭证域拒绝";

    /** App 域 403（账号禁用）审计行标题（第 15 批）。 */
    public static final String TITLE_APP_DISABLED = "App-账号禁用";

    /** App 域 403 未知原因的兜底标题（防御深度，正常不可达）。 */
    public static final String TITLE_APP_FORBIDDEN = "App-权限拒绝";

    /** PC 域安全文案。 */
    public static final String ERROR_PC = "认证失败";

    /** App 域安全文案。 */
    public static final String ERROR_APP = "未认证";

    /** App 域 403（凭证域/权限不符）安全文案（第 15 批）。 */
    public static final String ERROR_APP_DOMAIN = "凭证域不匹配";

    /** App 域 403（账号禁用）安全文案（第 15 批）。 */
    public static final String ERROR_APP_DISABLED = "账号禁用";

    /** App 域 403 未知原因的兜底安全文案。 */
    public static final String ERROR_APP_FORBIDDEN = "权限拒绝";

    /** 请求级一次性标记，保证同一请求恰好一条审计行。 */
    static final String AUDITED_MARKER = FilterChainAuthFailureAuditor.class.getName() + ".AUDITED";

    /** A 模块路径白名单前缀（不含 C 等业务模块与若依原生路径）。 */
    private static final String[] A_SCOPE_PREFIXES = {
            "/api/v1/admin", "/api/v1/auth", "/api/v1/users", "/api/v1/messages", "/api/v1/feedback"
    };

    @Override
    public void record(HttpServletRequest request, String domain, int httpStatus)
    {
        record(request, domain, httpStatus, null);
    }

    @Override
    public void record(HttpServletRequest request, String domain, int httpStatus, String reason)
    {
        if (!claim(request, domain, httpStatus))
        {
            return;
        }
        try
        {
            SysOperLog operLog = build(domain, request, httpStatus, reason);
            AsyncManager.me().execute(AsyncFactory.recordOper(operLog));
        }
        catch (Exception e)
        {
            // 告警只输出脱敏后的 URI：原始 requestURI 可能带令牌（与第 10 批 H10-REV-01 同口径）
            log.warn("过滤器链拒绝审计写入失败 domain={} uri={}: {}", domain,
                    request == null ? "-" : redactedUri(request.getRequestURI()), e.getMessage());
        }
    }

    /**
     * 判定并占用「本请求的审计名额」：状态码、路径范围、一次性标记三重检查。
     * 先判后置，保证并发/重复回调下同一请求只产生一条审计行。
     * 401 两域均可；403 仅限 App 过滤器（PC {@code @PreAuthorize} 403 由既有 A4 通道处置，避免重复记账）。
     */
    static boolean claim(HttpServletRequest request, String domain, int httpStatus)
    {
        if (request == null || (httpStatus != 401 && !(httpStatus == 403 && DOMAIN_APP.equals(domain))))
        {
            return false;
        }
        if (!inScope(request.getRequestURI()))
        {
            return false;
        }
        if (request.getAttribute(AUDITED_MARKER) != null)
        {
            return false;
        }
        request.setAttribute(AUDITED_MARKER, Boolean.TRUE);
        return true;
    }

    /**
     * 是否属于本批覆盖的 A 模块路径。401 语义下不存在已认证身份，故不解析令牌。
     */
    static boolean inScope(String uri)
    {
        if (uri == null || uri.isEmpty())
        {
            return false;
        }
        for (String prefix : A_SCOPE_PREFIXES)
        {
            if (uri.equals(prefix) || uri.startsWith(prefix + "/"))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建拒绝审计行（不落库，便于单测断言字段）。401 沿用第 11 批口径；App 403 按事件类型区分 title/文案。
     */
    SysOperLog build(String domain, HttpServletRequest request, int httpStatus, String reason)
    {
        boolean pc = DOMAIN_PC.equals(domain);
        SysOperLog operLog = new SysOperLog();
        operLog.setTitle(title(domain, httpStatus, reason));
        operLog.setBusinessType(BusinessType.OTHER.ordinal());
        // PC 后台用户 / App 手机端用户；过滤器层不解析身份，只标凭证域
        operLog.setOperatorType(pc ? OperatorType.MANAGE.ordinal() : OperatorType.MOBILE.ordinal());
        // 控制器方法在安全拦截之前未知，留空而不臆造
        operLog.setMethod("");
        operLog.setRequestMethod(StringUtils.substring(request.getMethod(), 0, 10));
        // 过滤器层无已认证身份；不读取 Authorization，也不落任何令牌
        operLog.setOperName(null);
        operLog.setOperUrl(StringUtils.substring(request.getRequestURI(), 0, 255));
        OperLogSanitizer.sanitize(operLog);
        operLog.setOperIp(IpUtils.getIpAddr(request));
        operLog.setStatus(BusinessStatus.FAIL.ordinal());
        operLog.setErrorMsg(errorMsg(domain, httpStatus, reason));
        operLog.setCostTime(0L);
        operLog.setOperTime(new Date());
        return operLog;
    }

    private static String title(String domain, int httpStatus, String reason)
    {
        if (DOMAIN_PC.equals(domain))
        {
            return TITLE_PC;
        }
        if (httpStatus == 403)
        {
            if (REASON_ACCOUNT_DISABLED.equals(reason))
            {
                return TITLE_APP_DISABLED;
            }
            if (REASON_DOMAIN_OR_PERMISSION.equals(reason))
            {
                return TITLE_APP_DOMAIN;
            }
            return TITLE_APP_FORBIDDEN;
        }
        return TITLE_APP;
    }

    private static String errorMsg(String domain, int httpStatus, String reason)
    {
        if (DOMAIN_PC.equals(domain))
        {
            return ERROR_PC;
        }
        if (httpStatus == 403)
        {
            if (REASON_ACCOUNT_DISABLED.equals(reason))
            {
                return ERROR_APP_DISABLED;
            }
            if (REASON_DOMAIN_OR_PERMISSION.equals(reason))
            {
                return ERROR_APP_DOMAIN;
            }
            return ERROR_APP_FORBIDDEN;
        }
        return ERROR_APP;
    }

    /**
     * 供日志告警使用的脱敏 URI；任何异常都退回占位符，绝不输出原始 URI。
     */
    private static String redactedUri(String uri)
    {
        try
        {
            return OperLogSanitizer.redactUrl(StringUtils.substring(uri, 0, 255));
        }
        catch (RuntimeException ignored)
        {
            return "{redacted}";
        }
    }
}
