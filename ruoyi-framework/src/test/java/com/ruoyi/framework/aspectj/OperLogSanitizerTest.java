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
