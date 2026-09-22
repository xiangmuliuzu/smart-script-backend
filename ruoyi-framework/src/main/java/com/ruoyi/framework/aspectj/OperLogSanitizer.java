package com.ruoyi.framework.aspectj;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ruoyi.system.domain.SysOperLog;

/**
 * 操作日志脱敏：把 URL 中的短时令牌替换为占位符。
 *
 * 背景：`oper_url` 记录原始 requestURI。任何把令牌放进 URL 路径的接口，
 * 都会把完整令牌写进操作日志。A4 连续两轮都在这里漏过：
 * 先漏 `/material-refs/{token}`，改用形态匹配后又漏了 `/material/content/{token}`。
 *
 * 教训：**按形态判断令牌是不可靠的**。随机令牌只含大写字母或只含数字时，
 * 与普通路径段在形态上不可区分（例如 24 个 A 既是合法令牌，也可能是一个类型名）。
 * 因此本类改为**白名单式、默认脱敏**：
 *   - 已知的业务路径段（allowlist）原样保留，便于定位接口；
 *   - 其余长度 >= 16 的路径段一律按令牌处理并脱敏。
 * 这样即使新增令牌路由、改路由名或换令牌编码，都不会重新泄漏——
 * 未登记的新路径段默认被脱敏，而不是默认放行。
 */
public final class OperLogSanitizer
{
    /** 脱敏占位符。 */
    private static final String REDACTED = "{redacted}";

    /**
     * 长度阈值：短于此值的路径段保留（如数字 ID、users、roles 等），
     * 等于或长于该值且不在白名单内的一律脱敏。
     */
    private static final int REDACT_MIN_LENGTH = 16;

    /**
     * 允许原样记录的路径段白名单。
     *
     * 必须小写比较。新增接口时若某段被误脱敏，把它加进来即可——
     * 误脱敏只影响日志可读性，不构成安全风险；反之则构成泄漏。
     */
    private static final List<String> ALLOWED_SEGMENTS = List.of(
            // A4 管理接口
            "v1", "admin", "app-users", "grantable-roles", "roles", "status",
            "real-name-applications", "decision",
            "author-capabilities", "notifications", "feedback", "handle",
            "material-refs", "material", "redeem", "content",
            // 若依原生
            "system", "monitor", "common", "tool", "login", "logout", "register",
            "captchaimage", "getinfo", "getrouters", "profile", "health",
            "user", "role", "menu", "dept", "dict", "config", "notice", "post",
            "operlog", "logininfor", "online", "job", "druid", "server", "cache",
            "cachelist", "build", "gen", "swagger", "list", "type", "data",
            "treeselect", "rolemenutreeselect", "export", "import", "importdata",
            "importtemplate", "resetpwd", "changeStatus", "authrole", "authuser",
            "unlock", "forceLogout", "batchLogout", "clean", "refresh", "edit",
            "add", "remove", "query", "upload", "download", "preview", "code",
            "dicttype", "optionselect", "depttree", "userprofile", "updatepwd",
            "avatar", "notice", "read", "markread", "sse", "api"
    );

    /** 路径段切分（保留分隔符，便于原样拼接）。 */
    private static final Pattern SEGMENT = Pattern.compile("([A-Za-z0-9_-]+)");

    private OperLogSanitizer()
    {
    }

    /**
     * 就地脱敏。任何一步失败都不影响主流程：日志可以少记，但不能因为脱敏报错而丢日志。
     */
    public static void sanitize(SysOperLog operLog)
    {
        if (operLog == null)
        {
            return;
        }
        try
        {
            operLog.setOperUrl(redactUrl(operLog.getOperUrl()));
        }
        catch (RuntimeException ignored)
        {
            // 保守处理：异常时直接清空 URL，宁可少记也不外泄
            operLog.setOperUrl(REDACTED);
        }
    }

    /**
     * 白名单外的长路径段一律脱敏。
     *
     * 例：
     *   /api/v1/admin/material/content/AbCdEf123456789012345678
     *     -> /api/v1/admin/material/content/{redacted}
     *   /api/v1/admin/real-name-applications/9101/decision   （保留，全部在白名单或过短）
     */
    public static String redactUrl(String url)
    {
        if (url == null || url.isEmpty())
        {
            return url;
        }
        Matcher m = SEGMENT.matcher(url);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find())
        {
            sb.append(url, last, m.start());
            String seg = m.group(1);
            boolean keep = seg.length() < REDACT_MIN_LENGTH
                    || ALLOWED_SEGMENTS.contains(seg.toLowerCase(Locale.ROOT));
            sb.append(keep ? seg : REDACTED);
            last = m.end();
        }
        sb.append(url, last, url.length());
        return sb.toString();
    }

    /**
     * 判断路径中是否仍残留可能为令牌的长片段。供测试与自查使用。
     * 采用与 {@link #redactUrl} 相同的白名单口径。
     */
    public static boolean containsUnredactedTokenShape(String url)
    {
        if (url == null)
        {
            return false;
        }
        Matcher m = SEGMENT.matcher(url);
        while (m.find())
        {
            String seg = m.group(1);
            if (seg.length() >= REDACT_MIN_LENGTH
                    && !ALLOWED_SEGMENTS.contains(seg.toLowerCase(Locale.ROOT)))
            {
                return true;
            }
        }
        return false;
    }
}
