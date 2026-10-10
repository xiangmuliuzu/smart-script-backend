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
 *   - 其余长度 >= 16 的路径段一律按令牌处理并脱敏；
 *   - 纯数字段达到手机号量级（>= 11 位，H13-REV-04）同样脱敏——A4 写接口的路径变量是
 *     自增主键，客户端可把 11 位手机号样式数字填进 `{userId}`，短于 16 字符不会被上面那条覆盖；
 *   - 路径变量还会作为方法实参进入 `oper_param`（如 `13900001234 {...}`），
 *     故 A4 管理域的 `oper_param` 另做一次长数字串脱敏，并整体保护指纹原子 `fp:…`。
 * 这样即使新增令牌路由、改路由名、换令牌编码或在路径变量里塞数字 PII，都不会重新泄漏——
 * 未登记的新路径段与数字 PII 默认被脱敏，而不是默认放行。
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
     * 纯数字段的脱敏阈值（H13-REV-04）：手机号 11 位、证件号 18 位。
     *
     * <p>A4 写接口的路径变量是自增主键（远短于 11 位），若客户端把 11 位手机号样式数字填进
     * {@code /app-users/{userId}/...}，按 16 字符阈值不会被脱敏，手机号会原样进入
     * {@code oper_url}。故纯数字段达到手机号量级（≥ 11 位）即按数字 PII 处理并脱敏。</p>
     */
    private static final int REDACT_MIN_NUMERIC_LENGTH = 11;

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

    /** A4 PC 管理接口路径前缀（H13-REV-04；与 {@code A4WriteQueryParamFilter} 共用同一口径）。 */
    public static final List<String> A4_ADMIN_PREFIXES = List.of(
            "/api/v1/admin/material-refs/",
            "/api/v1/admin/material/",
            "/api/v1/admin/app-users/",
            "/api/v1/admin/real-name-applications/",
            "/api/v1/admin/author-capabilities/",
            "/api/v1/admin/notifications",
            "/api/v1/admin/feedback/");

    /**
     * 是否属于 A4 PC 管理接口。
     *
     * <p>注意：C 模块 {@code TradeController} 同样挂在 {@code /api/v1/admin} 下，
     * 但其路径为 {@code /api/v1/admin/trade/**}，不在 {@link #A4_ADMIN_PREFIXES} 内，
     * 故不参与 A4 的 oper_param 数字脱敏与查询参数隔离。</p>
     */
    public static boolean isA4AdminPath(String uri)
    {
        if (uri == null)
        {
            return false;
        }
        for (String prefix : A4_ADMIN_PREFIXES)
        {
            if (uri.startsWith(prefix))
            {
                return true;
            }
        }
        return false;
    }

    /** 项目日志指纹原子（{@code fp:} + 12 位十六进制）：数字脱敏时整体保护，避免误伤其数字片段。 */
    private static final Pattern FINGERPRINT_ATOM = Pattern.compile("fp:[0-9a-f]{12}");

    /** 独立数字串：长度达到手机号量级（>= 11 位）。 */
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("(?<!\\d)\\d{11,}(?!\\d)");

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
        String rawUrl = operLog.getOperUrl();
        try
        {
            operLog.setOperUrl(redactUrl(rawUrl));
        }
        catch (RuntimeException ignored)
        {
            // 保守处理：异常时直接清空 URL，宁可少记也不外泄
            operLog.setOperUrl(REDACTED);
        }
        // H13-REV-04：A4 写接口的路径变量会作为方法实参进入 oper_param（如
        // `13900001234 {"status":"1"}`）。客户端可把 11 位手机号样式数字填进 {userId}，
        // 故对 A4 管理域的 oper_param 再做一次数字 PII 脱敏（其它模块不在本轮范围）。
        if (isA4AdminPath(rawUrl))
        {
            try
            {
                operLog.setOperParam(redactNumericPii(operLog.getOperParam()));
            }
            catch (RuntimeException ignored)
            {
                // 保守处理：异常时清空参数，宁可少记也不外泄
                operLog.setOperParam(REDACTED);
            }
        }
    }

    /**
     * 脱敏 oper_param 中的**独立长数字串**（>= 11 位），同时整体保护项目指纹原子，
     * 避免把 {@code fp:123456789012} 这类全数字指纹误伤。
     */
    private static String redactNumericPii(String text)
    {
        if (text == null || text.isEmpty())
        {
            return text;
        }
        Matcher fp = FINGERPRINT_ATOM.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        int last = 0;
        while (fp.find())
        {
            out.append(LONG_DIGIT_RUN.matcher(text.substring(last, fp.start())).replaceAll(REDACTED));
            out.append(fp.group());
            last = fp.end();
        }
        out.append(LONG_DIGIT_RUN.matcher(text.substring(last)).replaceAll(REDACTED));
        return out.toString();
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
            boolean keep = ALLOWED_SEGMENTS.contains(seg.toLowerCase(Locale.ROOT))
                    || (seg.length() < REDACT_MIN_LENGTH && !isNumericPii(seg));
            sb.append(keep ? seg : REDACTED);
            last = m.end();
        }
        sb.append(url, last, url.length());
        return sb.toString();
    }

    /** 纯数字且达到手机号量级（≥ {@value #REDACT_MIN_NUMERIC_LENGTH} 位）——按手机号/证件号等数字 PII 处理。 */
    private static boolean isNumericPii(String seg)
    {
        int n = seg.length();
        if (n < REDACT_MIN_NUMERIC_LENGTH)
        {
            return false;
        }
        for (int i = 0; i < n; i++)
        {
            if (!Character.isDigit(seg.charAt(i)))
            {
                return false;
            }
        }
        return true;
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
            if (ALLOWED_SEGMENTS.contains(seg.toLowerCase(Locale.ROOT)))
            {
                continue;
            }
            if (seg.length() >= REDACT_MIN_LENGTH || isNumericPii(seg))
            {
                return true;
            }
        }
        return false;
    }
}
