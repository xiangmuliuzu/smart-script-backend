package com.ruoyi.framework.aspectj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.ruoyi.system.domain.SysOperLog;

/**
 * 操作日志令牌脱敏回归测试。
 *
 * 背景：A4 连续两轮在操作日志里泄漏了短时令牌——
 * 第一轮是 `/material-refs/{token}`，改用「按路由名匹配」后又漏了
 * 第二轮新增的 `/material/content/{token}`。因此本类改为白名单式默认脱敏，
 * 并用这些用例锁定行为：任何未登记的長路径段都必须被替换。
 */
class OperLogSanitizerTest
{
    @Test
    void contentTokenPathIsRedacted()
    {
        String url = "/api/v1/admin/material/content/AbCdEf123456789012345678";
        String out = OperLogSanitizer.redactUrl(url);
        assertFalse(out.contains("AbCdEf123456789012345678"), "token must be removed: " + out);
        assertTrue(out.endsWith("/material/content/{redacted}"), "path prefix must be kept: " + out);
    }

    @Test
    void redeemTokenPathIsRedacted()
    {
        String url = "/api/v1/admin/material-refs/AbCdEf123456789012345678";
        String out = OperLogSanitizer.redactUrl(url);
        assertFalse(out.contains("AbCdEf123456789012345678"), "token must be removed: " + out);
        assertTrue(out.endsWith("/material-refs/{redacted}"), "path prefix must be kept: " + out);
    }

    /**
     * 全大写或全小写的令牌形态也必须被脱敏。
     * 这正是「按形态判断」方案会漏掉的场景：24 个 A 既是合法令牌，
     * 也可能被当成普通词，形态上无法区分。白名单式默认脱敏可以覆盖。
     */
    @Test
    void uniformCaseTokensAreAlsoRedacted()
    {
        for (String token : new String[] {
                "AAAAAAAAAAAAAAAAAAAAAAAA",
                "abcdefghijklmnopqrstuvwx",
                "123456789012345678901234"
        })
        {
            String out = OperLogSanitizer.redactUrl("/api/v1/admin/material/content/" + token);
            assertFalse(out.contains(token), "token must be removed: " + token + " -> " + out);
        }
    }

    /** 未登记的新令牌路由同样默认脱敏（fail-closed）。 */
    @Test
    void unknownTokenRouteIsRedactedByDefault()
    {
        String out = OperLogSanitizer.redactUrl(
                "/api/v1/admin/some-new-endpoint/Zz9Qq8Ww7Ee6Rr5Tt4Yy3Uu2");
        assertFalse(out.contains("Zz9Qq8Ww7Ee6Rr5Tt4Yy3Uu2"),
                "unregistered long segment must be redacted by default: " + out);
    }

    /**
     * H13-REV-04：A4 写接口路径变量是自增主键，客户端可填入手机号样式数字。
     * 11 位数字短于 16 字符阈值，必须由「纯数字 PII」规则兜住。
     */
    @Test
    void phoneNumberShapedPathSegmentIsRedacted()
    {
        String out = OperLogSanitizer.redactUrl("/api/v1/admin/app-users/13900001234/status");
        assertFalse(out.contains("13900001234"), "phone-like path segment must be redacted: " + out);
        assertEquals("/api/v1/admin/app-users/{redacted}/status", out);
        assertTrue(OperLogSanitizer.containsUnredactedTokenShape(
                "/api/v1/admin/app-users/13900001234/status"));
    }

    @Test
    void idNumberShapedPathSegmentIsRedacted()
    {
        String idNumber = "110101199001011234";
        String out = OperLogSanitizer.redactUrl("/api/v1/admin/author-capabilities/" + idNumber);
        assertFalse(out.contains(idNumber), "id-number-like path segment must be redacted: " + out);
        assertEquals("/api/v1/admin/author-capabilities/{redacted}", out);
    }

    /** 短于手机号量级的自增主键（含 10 位）必须保留，否则日志失去定位价值。 */
    @Test
    void shortNumericIdsAreNotOverRedacted()
    {
        assertEquals("/api/v1/admin/app-users/100/status",
                OperLogSanitizer.redactUrl("/api/v1/admin/app-users/100/status"));
        assertEquals("/api/v1/admin/real-name-applications/9101/decision",
                OperLogSanitizer.redactUrl("/api/v1/admin/real-name-applications/9101/decision"));
        assertEquals("/api/v1/admin/app-users/1234567890/status",
                OperLogSanitizer.redactUrl("/api/v1/admin/app-users/1234567890/status"));
        assertFalse(OperLogSanitizer.containsUnredactedTokenShape(
                "/api/v1/admin/app-users/1234567890/status"));
    }

    /** 正常业务路由不得被误脱敏，否则日志失去定位价值。 */
    @Test
    void normalRoutesArePreserved()
    {
        String[] keep = {
            "/api/v1/admin/app-users",
            "/api/v1/admin/app-users/grantable-roles",
            "/api/v1/admin/app-users/100/roles",
            "/api/v1/admin/real-name-applications",
            "/api/v1/admin/real-name-applications/9101",
            "/api/v1/admin/real-name-applications/9101/decision",
            "/api/v1/admin/author-capabilities/950",
            "/api/v1/admin/notifications",
            "/api/v1/admin/feedback/9201/handle",
            "/api/v1/admin/material-refs/redeem",
            "/system/role/list",
            "/system/user",
            "/monitor/operlog/list",
            "/monitor/logininfor/list",
            "/getRouters",
            "/getInfo"
        };
        for (String url : keep)
        {
            assertEquals(url, OperLogSanitizer.redactUrl(url), "must not redact: " + url);
        }
    }

    /** 脱敏结果本身不应再被当成令牌（幂等）。 */
    @Test
    void redactionIsIdempotent()
    {
        String once = OperLogSanitizer.redactUrl(
                "/api/v1/admin/material/content/AbCdEf123456789012345678");
        assertEquals(once, OperLogSanitizer.redactUrl(once));
    }

    /** sanitize 直接作用于 SysOperLog，并处理 null。 */
    @Test
    void sanitizeAppliesToOperLog()
    {
        SysOperLog log = new SysOperLog();
        log.setOperUrl("/api/v1/admin/material/content/AbCdEf123456789012345678");
        OperLogSanitizer.sanitize(log);
        assertFalse(String.valueOf(log.getOperUrl()).contains("AbCdEf123456789012345678"));

        OperLogSanitizer.sanitize(null);
        SysOperLog blank = new SysOperLog();
        OperLogSanitizer.sanitize(blank);
    }

    /**
     * H13-REV-04：路径变量还会作为方法实参进入 oper_param（`13900001234 {"status":"1"}`），
     * 故 A4 管理域的 oper_param 也须脱敏长数字串；短自增 id 保留可读。
     */
    @Test
    void pathVariableNumericPiiInA4OperParamIsRedacted()
    {
        SysOperLog log = new SysOperLog();
        log.setOperUrl("/api/v1/admin/app-users/{redacted}/status");
        log.setOperParam("13900001234 {\"status\":\"1\"} ");
        OperLogSanitizer.sanitize(log);
        assertFalse(String.valueOf(log.getOperParam()).contains("13900001234"),
                "phone-like path variable must be redacted in oper_param: " + log.getOperParam());
        assertTrue(String.valueOf(log.getOperParam()).contains("{redacted}"));

        SysOperLog shortId = new SysOperLog();
        shortId.setOperUrl("/api/v1/admin/app-users/100/status");
        shortId.setOperParam("100 {\"status\":\"1\"} ");
        OperLogSanitizer.sanitize(shortId);
        assertTrue(String.valueOf(shortId.getOperParam()).contains("100 "),
                "short self-increment id must stay readable: " + shortId.getOperParam());
    }

    /** 项目指纹原子在全数字时也不得被数字脱敏误伤。 */
    @Test
    void fingerprintAtomsAreProtectedFromDigitRedaction()
    {
        SysOperLog log = new SysOperLog();
        log.setOperUrl("/api/v1/admin/notifications");
        log.setOperParam("{\"userIds\":[\"fp:123456789012\"],\"requestId\":\"fp:000000000001\"}");
        OperLogSanitizer.sanitize(log);
        String out = String.valueOf(log.getOperParam());
        assertTrue(out.contains("fp:123456789012"), "fingerprint must be preserved: " + out);
        assertTrue(out.contains("fp:000000000001"), "fingerprint must be preserved: " + out);
    }

    /** 非 A4 管理域（如 C 模块）的 oper_param 不在本轮范围，不得被改动。 */
    @Test
    void nonA4OperParamIsNotDigitRedacted()
    {
        SysOperLog log = new SysOperLog();
        log.setOperUrl("/api/v1/admin/trade/works");
        log.setOperParam("{\"ref\":\"13900001234\"}");
        OperLogSanitizer.sanitize(log);
        assertTrue(String.valueOf(log.getOperParam()).contains("13900001234"),
                "C module oper_param must be untouched this round: " + log.getOperParam());
    }

    @Test
    void tokenShapeDetectorFlagsUnredactedSegments()
    {
        assertTrue(OperLogSanitizer.containsUnredactedTokenShape(
                "/api/v1/admin/material/content/AbCdEf123456789012345678"));
        assertFalse(OperLogSanitizer.containsUnredactedTokenShape(
                "/api/v1/admin/material/content/{redacted}"));
        assertFalse(OperLogSanitizer.containsUnredactedTokenShape(
                "/api/v1/admin/real-name-applications/9101"));
    }
}
