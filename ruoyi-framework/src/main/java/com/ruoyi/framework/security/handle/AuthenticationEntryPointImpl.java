package com.ruoyi.framework.security.handle;

import java.io.IOException;
import java.io.Serializable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.aspectj.OperLogSanitizer;

/**
 * 认证失败处理类 返回未授权
 * 
 * @author ruoyi
 */
@Component
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint, Serializable
{
    private static final long serialVersionUID = -8970718410437077606L;

    /**
     * 过滤器链层 401 审计回调（H9-LOG-04 残余）。
     *
     * 可选注入：框架层与单测不依赖具体实现；A 模块（smartscript-user）提供唯一实现在运行期生效。
     * 实现方自行吞异常、保证同请求只记一次。
     */
    @Autowired(required = false)
    private AuthenticationFailureAuditor authenticationFailureAuditor;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException
    {
        int code = HttpStatus.UNAUTHORIZED;
        // H11-REV-01：URI 可能把短时令牌放在路径里（如 /api/v1/admin/material/content/{token}），
        // 错误响应同样不得回显令牌。与操作日志/拒绝审计共用同一白名单式脱敏钩子：
        // 正常路径段原样保留（提示文案不变），仅未登记的长路径段替换为 {redacted}。
        String msg = StringUtils.format("请求访问：{}，认证失败，无法访问系统资源", redactRequestUri(request));
        ServletUtils.renderString(response, JSON.toJSONString(AjaxResult.error(code, msg)));
        // 响应写完之后才回调；回调不得改动响应（见 AuthenticationFailureAuditor 约定）。
        if (authenticationFailureAuditor != null)
        {
            authenticationFailureAuditor.record(request, AuthenticationFailureAuditor.DOMAIN_PC, code);
        }
    }

    /**
     * 错误响应中回显的请求 URI 先经 {@link OperLogSanitizer} 白名单式脱敏。
     * 任何异常或空值都退回占位符，绝不回显原始 URI。
     */
    private static String redactRequestUri(HttpServletRequest request)
    {
        if (request == null)
        {
            return "{redacted}";
        }
        try
        {
            String uri = request.getRequestURI();
            return uri == null || uri.isEmpty() ? "{redacted}" : OperLogSanitizer.redactUrl(uri);
        }
        catch (RuntimeException ignored)
        {
            return "{redacted}";
        }
    }
}
