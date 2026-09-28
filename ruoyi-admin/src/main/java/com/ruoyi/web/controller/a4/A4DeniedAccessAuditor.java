package com.ruoyi.web.controller.a4;

import java.util.Date;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.enums.BusinessStatus;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.OperatorType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.framework.aspectj.OperLogSanitizer;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.framework.manager.factory.AsyncFactory;
import com.ruoyi.system.domain.SysOperLog;

/**
 * A4 PC 管理域的权限拒绝审计（H9-LOG-04）。
 *
 * 背景：验收标准 §9.3 要求「权限拒绝有安全审计」。第 9 批实测 7 个写接口的 401/403 被拒请求
 * 在 {@code sys_oper_log} 中**零行**——原因是 {@code @PreAuthorize} 由方法安全拦截器在
 * {@code LogAspect} 之前短路，框架日志切面根本不会执行；过滤器链层的 401 更是进不到 MVC。
 * 第 10 批核查确认：现有通道（两个 AuthenticationEntryPoint、App 过滤器、
 * GlobalExceptionHandler 的 slf4j ERROR、A4AdminExceptionHandler、sys_logininfor 的登录类事件）
 * 都不写任何权限拒绝审计行。
 *
 * 位置选择：{@code @PreAuthorize} 拒绝会以 {@code AccessDeniedException} 冒泡到 MVC，
 * 由 {@link A4AdminExceptionHandler#handleAccessDenied} 处理（这正是它返回 403 的原因），
 * 因此该处理器是 A 模块内唯一能确实拿到「已认证但无权限」拒绝事件的位置。
 *
 * 记录口径（刻意最小化，避免引入新的泄漏面）：
 *   - 只记操作人、目标（oper_url）、请求方式、结果（status=FAIL）、安全文案、IP、时间；
 *   - {@code oper_param} 与 {@code json_result} 一律留空——被拒请求的参数不被记录；
 *   - {@code method} 留空：控制器方法在安全拦截之前未知，不臆造；
 *   - {@code oper_url} 经 {@code OperLogSanitizer} 白名单式脱敏（与 {@code LogAspect} 同一钩子），
 *     A4 的 {@code /material/content/{token}} 等带令牌路径不会把令牌写进日志；写入失败的告警
 *     也只输出脱敏后的 URI（H10-REV-01）。
 *
 * 未覆盖：过滤器链层的 401（未登录/凭证域错误）不经过 MVC，本类不涉及；
 * 其它模块（非 A4）的 403 亦不在本类范围。
 */
@Component
public class A4DeniedAccessAuditor
{
    private static final Logger log = LoggerFactory.getLogger(A4DeniedAccessAuditor.class);

    /** 拒绝审计行的模块标题。 */
    public static final String TITLE = "A4-权限拒绝";

    /** 拒绝审计行的安全文案（与 A4AdminExceptionHandler 返回体一致）。 */
    public static final String DENIED_MESSAGE = "权限不足";

    /**
     * 构建拒绝审计行（不落库，便于单测断言字段）。
     *
     * @param requestMethod 请求方法
     * @param requestUri    请求 URI
     * @param operIp        来源 IP
     * @param operName      操作人（已认证账号；取不到时为 null）
     */
    public SysOperLog buildDeniedOperLog(String requestMethod, String requestUri, String operIp, String operName)
    {
        SysOperLog operLog = new SysOperLog();
        operLog.setTitle(TITLE);
        operLog.setBusinessType(BusinessType.OTHER.ordinal());
        operLog.setOperatorType(OperatorType.MANAGE.ordinal());
        // 控制器方法在安全拦截之前未知，留空而不臆造
        operLog.setMethod("");
        operLog.setRequestMethod(StringUtils.substring(requestMethod, 0, 10));
        operLog.setOperName(operName);
        operLog.setOperUrl(StringUtils.substring(requestUri, 0, 255));
        // H10-REV-01：A4 存在把令牌放在路径里的接口（GET /admin/material/content/{token}）。
        // 403 审计必须走与 LogAspect 相同的白名单式脱敏钩子，否则被拒读取会把完整令牌写进 oper_url。
        OperLogSanitizer.sanitize(operLog);
        operLog.setOperIp(operIp);
        operLog.setStatus(BusinessStatus.FAIL.ordinal());
        operLog.setErrorMsg(DENIED_MESSAGE);
        operLog.setCostTime(0L);
        operLog.setOperTime(new Date());
        return operLog;
    }

    /**
     * 记录一次 A4 权限拒绝。任何失败都不得影响拒绝响应的返回
     * （与 {@code LogAspect} 「日志可以少记，但不能让请求失败」同一原则）。
     */
    public void record(HttpServletRequest request)
    {
        try
        {
            if (request == null)
            {
                return;
            }
            String operName = null;
            try
            {
                LoginUser loginUser = SecurityUtils.getLoginUser();
                operName = loginUser == null ? null : loginUser.getUsername();
            }
            catch (Exception ignored)
            {
                // 理论上 A4 403 一定是已认证用户；取不到账号时按匿名记录，不阻断审计
            }
            SysOperLog operLog = buildDeniedOperLog(request.getMethod(), request.getRequestURI(),
                    IpUtils.getIpAddr(request), operName);
            AsyncManager.me().execute(AsyncFactory.recordOper(operLog));
        }
        catch (Exception e)
        {
            // 告警同样只输出脱敏后的 URI：原始 requestURI 可能带材料令牌（H10-REV-01）
            log.warn("A4 权限拒绝审计写入失败 method={} uri={}: {}",
                    request == null ? "-" : request.getMethod(),
                    request == null ? "-" : redactedUri(request.getRequestURI()), e.getMessage());
        }
    }

    /**
     * 供日志告警使用的脱敏 URI。与 {@code oper_url} 同一口径；
     * 任何异常都退回占位符，绝不输出原始 URI。
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
