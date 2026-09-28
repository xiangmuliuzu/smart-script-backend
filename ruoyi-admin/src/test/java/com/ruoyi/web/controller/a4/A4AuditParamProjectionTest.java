package com.ruoyi.web.controller.a4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.framework.aspectj.LogAspect;
import com.smartscript.platform.user.constant.AppAdminAuditParams;

/**
 * H9-LOG-02（第 12 批）：A4 七写接口请求体的**白名单审计投影**防回退契约。
 *
 * 背景：第 10 批用 {@code excludeParamNames}（已知敏感名清单）收敛，残余是无法穷举任意未知键名与
 * 嵌套结构。本批改为默认拒绝：控制器在业务读取前调用 {@link A4AuditParamProjection#retain}，
 * 请求体只剩契约字段，且值只保留标量/标量数组。
 *
 * 本测试断言四类事实，与黑盒矩阵 {@code h-a-operlog-field-matrix.mjs} 的 W9 段一致：
 *
 * 1. 7 个白名单与《A4-管理接口契约》§5.1–§5.5 的契约字段**逐一相等**（不新增、不改名）；
 * 2. 未知敏感键与嵌套对象/数组收敛后不残留，且用与 {@code LogAspect} 相同的序列化器与排除集
 *    生成的 {@code oper_param} 不含任何注入值；
 * 3. 白名单数组字段内的嵌套结构被逐元素过滤（业务读取到的也只剩标量元素）；
 * 4. 合法契约字段仍出现在 {@code oper_param}（自由文本由既有 excludeParamNames 排除）。
 */
class A4AuditParamProjectionTest
{
    /** 合成标记（非真实凭据），与黑盒矩阵注入值同口径。 */
    private static final String M_TOKEN = "SYNTHETIC_XTOKEN_MARKER";
    private static final String M_PHONE = "13900002222";
    private static final String M_ID = "110101199001018888";
    private static final String M_CODE = "135790";

    /** 一个写接口：方法名（取 @Log） + 白名单 + 契约字段全集。 */
    private record Endpoint(String javaName, String[] whitelist, List<String> contractFields)
    {
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("redeemMaterialRef", A4AuditParamProjection.MATERIAL_REDEEM,
                    List.of(AppAdminAuditParams.TOKEN)),
            new Endpoint("changeStatus", A4AuditParamProjection.APP_USER_STATUS,
                    List.of(AppAdminAuditParams.STATUS, AppAdminAuditParams.REASON)),
            new Endpoint("grantRoles", A4AuditParamProjection.APP_USER_ROLES,
                    List.of(AppAdminAuditParams.ROLE_IDS, AppAdminAuditParams.REASON)),
            new Endpoint("decideRealName", A4AuditParamProjection.REAL_NAME_DECISION,
                    List.of(AppAdminAuditParams.DECISION, AppAdminAuditParams.REJECT_REASON,
                            AppAdminAuditParams.EXPECTED_STATUS)),
            new Endpoint("updateAuthorCapability", A4AuditParamProjection.AUTHOR_CAPABILITY,
                    List.of(AppAdminAuditParams.ENABLED, AppAdminAuditParams.REASON)),
            new Endpoint("createNotification", A4AuditParamProjection.NOTIFICATION_CREATE,
                    List.of(AppAdminAuditParams.REQUEST_ID, AppAdminAuditParams.TYPE, AppAdminAuditParams.TITLE,
                            AppAdminAuditParams.CONTENT, AppAdminAuditParams.BUSINESS_TYPE,
                            AppAdminAuditParams.BUSINESS_ID, AppAdminAuditParams.USER_IDS)),
            new Endpoint("handleFeedback", A4AuditParamProjection.FEEDBACK_HANDLE,
                    List.of(AppAdminAuditParams.ACTION, AppAdminAuditParams.REPLY,
                            AppAdminAuditParams.EXPECTED_STATUS)));

    @Test
    void whitelistsMatchContractFieldsExactly()
    {
        assertEquals(7, ENDPOINTS.size(), "A4 写接口数量固定为 7；变更需同步契约与第 9/12 批矩阵");
        for (Endpoint e : ENDPOINTS)
        {
            assertEquals(e.contractFields(), List.of(e.whitelist()),
                    e.javaName() + " 的白名单必须与契约字段逐一相等（不得新增或改名）");
        }
    }

    @Test
    void unknownAndNestedKeysNeverReachAuditParam() throws Exception
    {
        for (Endpoint e : ENDPOINTS)
        {
            Map<String, Object> body = contractBody(e.javaName());
            body.putAll(injectUnknownAndNested());

            A4AuditParamProjection.retain(body, e.whitelist());
            String operParam = auditParam(e.javaName(), body);

            for (String injectedKey : List.of("xToken", "legacySecret", "credential", "nested", "deep"))
            {
                assertFalse(body.containsKey(injectedKey), e.javaName() + " 收敛后不得残留注入键 " + injectedKey);
                assertFalse(operParam.contains("\"" + injectedKey + "\""),
                        e.javaName() + " oper_param 不得含注入键 " + injectedKey + "：" + operParam);
            }
            for (String marker : List.of(M_TOKEN, M_PHONE, M_ID, M_CODE))
            {
                assertFalse(operParam.contains(marker),
                        e.javaName() + " oper_param 不得含注入值：" + operParam);
            }
        }
    }

    @Test
    void nestedStructuresInsideContractArraysAreFiltered()
    {
        Map<String, Object> roles = new LinkedHashMap<>();
        roles.put(AppAdminAuditParams.ROLE_IDS, List.of(7, Map.of("xToken", M_TOKEN), "8", Map.of("nested", M_ID)));
        roles.put(AppAdminAuditParams.REASON, "授权");
        A4AuditParamProjection.retain(roles, A4AuditParamProjection.APP_USER_ROLES);
        assertEquals(List.of(7, "8"), roles.get(AppAdminAuditParams.ROLE_IDS),
                "数组内的嵌套结构必须被逐元素过滤，只留标量");

        Map<String, Object> notif = new LinkedHashMap<>();
        notif.put(AppAdminAuditParams.USER_IDS, List.of(5, Map.of("token", M_TOKEN)));
        A4AuditParamProjection.retain(notif, A4AuditParamProjection.NOTIFICATION_CREATE);
        assertEquals(List.of(5), notif.get(AppAdminAuditParams.USER_IDS));
    }

    @Test
    void legalFieldsArePreservedAndFreeTextStillExcluded() throws Exception
    {
        Map<String, Map<String, Object>> expectedVisible = new LinkedHashMap<>();
        expectedVisible.put("changeStatus", Map.of(AppAdminAuditParams.STATUS, "1"));
        expectedVisible.put("grantRoles", Map.of(AppAdminAuditParams.ROLE_IDS, List.of(7)));
        expectedVisible.put("decideRealName", Map.of(AppAdminAuditParams.DECISION, "APPROVE",
                AppAdminAuditParams.EXPECTED_STATUS, "PENDING"));
        expectedVisible.put("updateAuthorCapability", Map.of(AppAdminAuditParams.ENABLED, true));
        expectedVisible.put("createNotification", Map.of(AppAdminAuditParams.REQUEST_ID, "req-1",
                AppAdminAuditParams.TYPE, "SYSTEM", AppAdminAuditParams.USER_IDS, List.of(5)));
        expectedVisible.put("handleFeedback", Map.of(AppAdminAuditParams.ACTION, "ACCEPT",
                AppAdminAuditParams.EXPECTED_STATUS, "SUBMITTED"));
        // 兑换接口只允许 token，而 token 由 @Log 排除，故可见参数为空
        expectedVisible.put("redeemMaterialRef", Map.of());

        for (Endpoint e : ENDPOINTS)
        {
            Map<String, Object> body = contractBody(e.javaName());
            A4AuditParamProjection.retain(body, e.whitelist());

            Map<String, Object> visible = JSON.parseObject(auditParam(e.javaName(), body));
            assertEquals(expectedVisible.get(e.javaName()), visible,
                    e.javaName() + " 的 oper_param 可见字段（合法字段保留、自由文本排除）");
        }
    }

    @Test
    void projectionIsNullSafeAndDefaultDeny()
    {
        A4AuditParamProjection.retain(null, A4AuditParamProjection.APP_USER_STATUS);
        A4AuditParamProjection.retain(new LinkedHashMap<>(), A4AuditParamProjection.APP_USER_STATUS);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("anything", "value");
        A4AuditParamProjection.retain(body);
        assertTrue(body.isEmpty(), "空白名单必须默认拒绝一切键");
    }

    /**
     * H12-REV-01（第 13 批）：白名单内的客户端可控自由字符串值可为令牌，日志必须只记不可逆指纹。
     */
    @Test
    void fingerprintForLogTurnsClientStringsIntoIrreversibleFingerprints()
    {
        String token = "AbCdEf0123456789AbCdEf0123456789";
        String otherToken = "ZzYyXx9876543210ZzYyXx9876543210";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(AppAdminAuditParams.REQUEST_ID, token);
        body.put(AppAdminAuditParams.TYPE, "SYSTEM");
        body.put(AppAdminAuditParams.BUSINESS_TYPE, "REAL_NAME");
        body.put(AppAdminAuditParams.BUSINESS_ID, otherToken);
        body.put(AppAdminAuditParams.USER_IDS, List.of(5));

        A4AuditParamProjection.fingerprintForLog(body, A4AuditParamProjection.NOTIFICATION_LOG_FINGERPRINT);

        for (String key : A4AuditParamProjection.NOTIFICATION_LOG_FINGERPRINT)
        {
            String value = String.valueOf(body.get(key));
            assertTrue(value.matches("^fp:[0-9a-f]{12}$"), key + " 必须为不可逆指纹，实际 " + value);
        }
        assertNotEquals(body.get(AppAdminAuditParams.REQUEST_ID), body.get(AppAdminAuditParams.BUSINESS_ID),
                "不同取值应得到不同指纹（仍可区分）");
        // 非自由字符串字段保持原值：受控枚举与数字数组结构上不能承载令牌
        assertEquals("SYSTEM", body.get(AppAdminAuditParams.TYPE));
        assertEquals(List.of(5), body.get(AppAdminAuditParams.USER_IDS));
        // 一律指纹化，不看形态：全大写 / 纯数字令牌同样被指纹
        Map<String, Object> edges = new LinkedHashMap<>();
        edges.put(AppAdminAuditParams.REQUEST_ID, "UJFHXJWSLARFYQXUTJZTMSCPFNDHBWYV");
        A4AuditParamProjection.fingerprintForLog(edges, AppAdminAuditParams.REQUEST_ID);
        assertTrue(String.valueOf(edges.get(AppAdminAuditParams.REQUEST_ID)).matches("^fp:[0-9a-f]{12}$"));

        // 确定性：同值同指纹，审计可凭候选值重算核对
        Map<String, Object> again = new LinkedHashMap<>();
        again.put(AppAdminAuditParams.REQUEST_ID, token);
        A4AuditParamProjection.fingerprintForLog(again, AppAdminAuditParams.REQUEST_ID);
        assertEquals(body.get(AppAdminAuditParams.REQUEST_ID), again.get(AppAdminAuditParams.REQUEST_ID),
                "相同取值必须得到相同指纹");
    }

    @Test
    void fingerprintForLogIsNullSafeAndLeavesAbsentKeysUntouched()
    {
        A4AuditParamProjection.fingerprintForLog(null, A4AuditParamProjection.NOTIFICATION_LOG_FINGERPRINT);
        A4AuditParamProjection.fingerprintForLog(new LinkedHashMap<>(), AppAdminAuditParams.REQUEST_ID);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(AppAdminAuditParams.TYPE, "SYSTEM");
        body.put(AppAdminAuditParams.BUSINESS_ID, null);
        A4AuditParamProjection.fingerprintForLog(body, A4AuditParamProjection.NOTIFICATION_LOG_FINGERPRINT);
        assertEquals("SYSTEM", body.get(AppAdminAuditParams.TYPE), "未列入指纹的键不得改动");
        assertNull(body.get(AppAdminAuditParams.BUSINESS_ID), "空值不产生指纹");
    }

    /** 指纹字段集必须覆盖消息创建接口全部客户端可控自由字符串（契约 §5.4）。 */
    @Test
    void notificationFingerprintCoversAllClientControlledFreeStringFields()
    {
        assertEquals(List.of(AppAdminAuditParams.REQUEST_ID, AppAdminAuditParams.BUSINESS_TYPE,
                        AppAdminAuditParams.BUSINESS_ID),
                List.of(A4AuditParamProjection.NOTIFICATION_LOG_FINGERPRINT),
                "契约 §5.4 的客户端可控自由字符串为 requestId/businessType/businessId；变更需同步本测试");
        // type 是受控枚举、userIds 是数字数组，不能被指纹化（否则丢失可读定位）
        List<String> fingerprintKeys = List.of(A4AuditParamProjection.NOTIFICATION_LOG_FINGERPRINT);
        assertFalse(fingerprintKeys.contains(AppAdminAuditParams.TYPE));
        assertFalse(fingerprintKeys.contains(AppAdminAuditParams.USER_IDS));
    }

    /**
     * H13-REV-01（第 13 批复核补修）：`type`/`userIds` 的值级默认拒绝。
     * 非法枚举、字符串数组元素、非数组的 `userIds` 都必须指纹化；契约枚举值与数字元素保持可读。
     */
    @Test
    void sanitizeNotificationLogAppliesValueLevelDefaultDeny()
    {
        String token = "AbCdEf0123456789AbCdEf0123456789";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(AppAdminAuditParams.REQUEST_ID, token);
        body.put(AppAdminAuditParams.TYPE, token);
        body.put(AppAdminAuditParams.BUSINESS_TYPE, token);
        body.put(AppAdminAuditParams.BUSINESS_ID, token);
        body.put(AppAdminAuditParams.USER_IDS, List.of(7, token, 8));

        A4AuditParamProjection.sanitizeNotificationLog(body);

        for (String key : List.of(AppAdminAuditParams.REQUEST_ID, AppAdminAuditParams.TYPE,
                AppAdminAuditParams.BUSINESS_TYPE, AppAdminAuditParams.BUSINESS_ID))
        {
            assertTrue(String.valueOf(body.get(key)).matches("^fp:[0-9a-f]{12}$"),
                    key + " 必须指纹化，实际 " + body.get(key));
        }
        List<?> ids = (List<?>) body.get(AppAdminAuditParams.USER_IDS);
        assertEquals(3, ids.size(), "数组结构与条数保留");
        for (Object id : ids)
        {
            assertTrue(String.valueOf(id).matches("^fp:[0-9a-f]{12}$"),
                    "userIds 元素（含数字）必须指纹化，实际 " + id);
        }
        assertFalse(ids.contains(token), "userIds 内不得残留令牌原值");
    }

    @Test
    void sanitizeNotificationLogKeepsOnlyContractEnums()
    {
        for (String knownType : List.of("SYSTEM", "REVIEW", "TRANSACTION", "BENEFIT"))
        {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(AppAdminAuditParams.TYPE, knownType);
            body.put(AppAdminAuditParams.USER_IDS, List.of(5, 6));
            A4AuditParamProjection.sanitizeNotificationLog(body);
            assertEquals(knownType, body.get(AppAdminAuditParams.TYPE), "契约枚举值应保持可读");
            List<?> ids = (List<?>) body.get(AppAdminAuditParams.USER_IDS);
            for (Object id : ids)
            {
                assertTrue(String.valueOf(id).matches("^fp:[0-9a-f]{12}$"),
                        "userIds 数字元素亦须指纹（H13-REV-03），实际 " + id);
            }
        }
        // 非法枚举（非契约常量）指纹
        Map<String, Object> badType = new LinkedHashMap<>();
        badType.put(AppAdminAuditParams.TYPE, "NOT_A_TYPE");
        A4AuditParamProjection.sanitizeNotificationLog(badType);
        assertTrue(String.valueOf(badType.get(AppAdminAuditParams.TYPE)).matches("^fp:[0-9a-f]{12}$"),
                "非法枚举必须指纹化");
        // 非数组 userIds（非契约类型）整体指纹
        Map<String, Object> scalarIds = new LinkedHashMap<>();
        scalarIds.put(AppAdminAuditParams.USER_IDS, "AbCdEf0123456789");
        A4AuditParamProjection.sanitizeNotificationLog(scalarIds);
        assertTrue(String.valueOf(scalarIds.get(AppAdminAuditParams.USER_IDS)).matches("^fp:[0-9a-f]{12}$"),
                "非数组 userIds 必须整体指纹化");
        // 空值安全
        A4AuditParamProjection.sanitizeNotificationLog(null);
        A4AuditParamProjection.sanitizeNotificationLog(new LinkedHashMap<>());
    }

    /**
     * H13-REV-03（第 13 批再次复核补修）：**数字元素**同样不可信。
     * 32 位纯数字令牌作为 JSON 数字（BigInteger）进入 `userIds` / `roleIds` 时必须指纹化。
     */
    @Test
    void sanitizeNotificationLogFingerprintsNumericIdArrayTokens()
    {
        BigInteger numericToken = new BigInteger("86717387668337570408300487642632");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(AppAdminAuditParams.USER_IDS, List.of(72076, numericToken));

        A4AuditParamProjection.sanitizeNotificationLog(body);

        List<?> ids = (List<?>) body.get(AppAdminAuditParams.USER_IDS);
        assertEquals(2, ids.size());
        assertFalse(String.valueOf(ids).contains("86717387668337570408300487642632"),
                "数字令牌不得残留在投影中");
        for (Object id : ids)
        {
            assertTrue(String.valueOf(id).matches("^fp:[0-9a-f]{12}$"), "数字元素必须指纹化，实际 " + id);
        }
    }

    @Test
    void sanitizeRoleGrantLogFingerprintsAllRoleIdElements()
    {
        BigInteger numericToken = new BigInteger("29328717781361740397874589457042");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(AppAdminAuditParams.ROLE_IDS, List.of(2, numericToken));

        A4AuditParamProjection.sanitizeRoleGrantLog(body);

        List<?> ids = (List<?>) body.get(AppAdminAuditParams.ROLE_IDS);
        assertEquals(2, ids.size());
        assertFalse(String.valueOf(ids).contains("29328717781361740397874589457042"));
        for (Object id : ids)
        {
            assertTrue(String.valueOf(id).matches("^fp:[0-9a-f]{12}$"), "roleIds 元素必须指纹化，实际 " + id);
        }
        A4AuditParamProjection.sanitizeRoleGrantLog(null);
        A4AuditParamProjection.sanitizeRoleGrantLog(new LinkedHashMap<>());
    }

    /**
     * 第 14 批（H-07 值级残余）：用户状态接口 `status` 的值级默认拒绝。
     * 契约枚举 "0"/"1" 保持可读；令牌字符串、数字、数组等其余取值一律指纹。
     */
    @Test
    void sanitizeStatusChangeLogKeepsOnlyContractEnumValues()
    {
        for (String legal : List.of("0", "1"))
        {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(AppAdminAuditParams.STATUS, legal);
            A4AuditParamProjection.sanitizeStatusChangeLog(body);
            assertEquals(legal, body.get(AppAdminAuditParams.STATUS), "契约枚举值应保持可读");
        }
        // 非法自由字符串（令牌形态）指纹
        Map<String, Object> token = new LinkedHashMap<>();
        token.put(AppAdminAuditParams.STATUS, "AbCdEf0123456789AbCdEf0123456789");
        A4AuditParamProjection.sanitizeStatusChangeLog(token);
        assertTrue(String.valueOf(token.get(AppAdminAuditParams.STATUS)).matches("^fp:[0-9a-f]{12}$"),
                "非法枚举字符串必须指纹化");
        // 非法自由字符串（合成手机号形态）指纹
        Map<String, Object> phone = new LinkedHashMap<>();
        phone.put(AppAdminAuditParams.STATUS, "13900002222");
        A4AuditParamProjection.sanitizeStatusChangeLog(phone);
        assertTrue(String.valueOf(phone.get(AppAdminAuditParams.STATUS)).matches("^fp:[0-9a-f]{12}$"),
                "手机号形态字符串必须指纹化");
        // 数字与数组等非契约类型同样指纹（不原样保留）
        Map<String, Object> numeric = new LinkedHashMap<>();
        numeric.put(AppAdminAuditParams.STATUS, 9);
        A4AuditParamProjection.sanitizeStatusChangeLog(numeric);
        assertTrue(String.valueOf(numeric.get(AppAdminAuditParams.STATUS)).matches("^fp:[0-9a-f]{12}$"),
                "数字取值必须指纹化");
        // 空值安全
        A4AuditParamProjection.sanitizeStatusChangeLog(null);
        A4AuditParamProjection.sanitizeStatusChangeLog(new LinkedHashMap<>());
        Map<String, Object> absent = new LinkedHashMap<>();
        absent.put("other", "x");
        A4AuditParamProjection.sanitizeStatusChangeLog(absent);
        assertEquals("x", absent.get("other"), "未列入策略的键不得改动");
    }

    /**
     * 第 14 批：实名审核接口 `decision`/`expectedStatus` 的值级默认拒绝。
     * 仅保留 APPROVE/REJECT 与固定的 PENDING；其余取值（含令牌、数字）一律指纹。
     */
    @Test
    void sanitizeRealNameDecisionLogKeepsOnlyContractEnums()
    {
        for (String legalDecision : List.of("APPROVE", "REJECT"))
        {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(AppAdminAuditParams.DECISION, legalDecision);
            body.put(AppAdminAuditParams.EXPECTED_STATUS, "PENDING");
            A4AuditParamProjection.sanitizeRealNameDecisionLog(body);
            assertEquals(legalDecision, body.get(AppAdminAuditParams.DECISION), "契约枚举值应保持可读");
            assertEquals("PENDING", body.get(AppAdminAuditParams.EXPECTED_STATUS), "固定期望状态应保持可读");
        }
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put(AppAdminAuditParams.DECISION, "MAYBE");
        bad.put(AppAdminAuditParams.EXPECTED_STATUS, "APPROVED");
        A4AuditParamProjection.sanitizeRealNameDecisionLog(bad);
        assertTrue(String.valueOf(bad.get(AppAdminAuditParams.DECISION)).matches("^fp:[0-9a-f]{12}$"),
                "非法 decision 必须指纹化");
        assertTrue(String.valueOf(bad.get(AppAdminAuditParams.EXPECTED_STATUS)).matches("^fp:[0-9a-f]{12}$"),
                "非固定取值的 expectedStatus 必须指纹化");
        // 数字取值同样指纹
        Map<String, Object> numeric = new LinkedHashMap<>();
        numeric.put(AppAdminAuditParams.DECISION, 1);
        A4AuditParamProjection.sanitizeRealNameDecisionLog(numeric);
        assertTrue(String.valueOf(numeric.get(AppAdminAuditParams.DECISION)).matches("^fp:[0-9a-f]{12}$"),
                "数字取值必须指纹化");
        A4AuditParamProjection.sanitizeRealNameDecisionLog(null);
        A4AuditParamProjection.sanitizeRealNameDecisionLog(new LinkedHashMap<>());
    }

    /**
     * 第 14 批：作者能力接口 `enabled` 的值级默认拒绝。
     * H14-REV-02 复核收紧：**仅布尔**保持可读（与契约可接受集一致）；
     * 令牌字符串、"true"/"false" 字符串、数字一律指纹（这些取值在业务侧同样被 400 拒绝）。
     */
    @Test
    void sanitizeAuthorCapabilityLogKeepsOnlyBooleanValues()
    {
        Map<String, Object> legal = new LinkedHashMap<>();
        legal.put(AppAdminAuditParams.ENABLED, true);
        A4AuditParamProjection.sanitizeAuthorCapabilityLog(legal);
        assertEquals(true, legal.get(AppAdminAuditParams.ENABLED), "布尔取值应保持可读");

        for (Object rejected : List.of("true", "TRUE", "False", "false", "AbCdEf0123456789AbCdEf0123456789",
                13900002222L, 1))
        {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(AppAdminAuditParams.ENABLED, rejected);
            A4AuditParamProjection.sanitizeAuthorCapabilityLog(body);
            assertTrue(String.valueOf(body.get(AppAdminAuditParams.ENABLED)).matches("^fp:[0-9a-f]{12}$"),
                    "非布尔取值必须指纹化（业务侧亦 400 拒绝）：" + rejected);
        }
        A4AuditParamProjection.sanitizeAuthorCapabilityLog(null);
        A4AuditParamProjection.sanitizeAuthorCapabilityLog(new LinkedHashMap<>());
    }

    /**
     * 第 14 批：反馈处理接口 `action`/`expectedStatus` 的值级默认拒绝。
     * 仅保留 ACCEPT/REPLY/CLOSE 与反馈状态机四态；其余取值一律指纹。
     */
    @Test
    void sanitizeFeedbackHandleLogKeepsOnlyContractEnums()
    {
        for (String legalAction : List.of("ACCEPT", "REPLY", "CLOSE"))
        {
            for (String legalExpected : List.of("SUBMITTED", "PROCESSING", "REPLIED", "CLOSED"))
            {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put(AppAdminAuditParams.ACTION, legalAction);
                body.put(AppAdminAuditParams.EXPECTED_STATUS, legalExpected);
                A4AuditParamProjection.sanitizeFeedbackHandleLog(body);
                assertEquals(legalAction, body.get(AppAdminAuditParams.ACTION), "契约枚举值应保持可读");
                assertEquals(legalExpected, body.get(AppAdminAuditParams.EXPECTED_STATUS), "状态机期望值应保持可读");
            }
        }
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put(AppAdminAuditParams.ACTION, "NOPE");
        bad.put(AppAdminAuditParams.EXPECTED_STATUS, "AbCdEf0123456789AbCdEf0123456789");
        A4AuditParamProjection.sanitizeFeedbackHandleLog(bad);
        assertTrue(String.valueOf(bad.get(AppAdminAuditParams.ACTION)).matches("^fp:[0-9a-f]{12}$"),
                "非法 action 必须指纹化");
        assertTrue(String.valueOf(bad.get(AppAdminAuditParams.EXPECTED_STATUS)).matches("^fp:[0-9a-f]{12}$"),
                "非状态机取值的 expectedStatus 必须指纹化");
        A4AuditParamProjection.sanitizeFeedbackHandleLog(null);
        A4AuditParamProjection.sanitizeFeedbackHandleLog(new LinkedHashMap<>());
    }

    /** 第 14 批：四个新值级策略的字段集必须与契约字段一致（防漂移）。 */
    @Test
    void valueLevelStrategyFieldSetsMatchContract()
    {
        assertEquals(List.of(AppAdminAuditParams.STATUS), List.of(A4AuditParamProjection.STATUS_LOG_ENUM));
        assertEquals(List.of(AppAdminAuditParams.DECISION), List.of(A4AuditParamProjection.DECISION_LOG_ENUM));
        assertEquals(List.of(AppAdminAuditParams.EXPECTED_STATUS),
                List.of(A4AuditParamProjection.REAL_NAME_EXPECTED_LOG_ENUM));
        assertEquals(List.of(AppAdminAuditParams.ACTION), List.of(A4AuditParamProjection.FEEDBACK_ACTION_LOG_ENUM));
        assertEquals(List.of(AppAdminAuditParams.EXPECTED_STATUS),
                List.of(A4AuditParamProjection.FEEDBACK_EXPECTED_LOG_ENUM));
        assertEquals(List.of(AppAdminAuditParams.ENABLED), List.of(A4AuditParamProjection.AUTHOR_CAPABILITY_LOG_BOOL));
    }

    /** 与黑盒矩阵一致：未知名的敏感键 + 嵌套对象 + 深层嵌套。 */
    private static Map<String, Object> injectUnknownAndNested()
    {
        Map<String, Object> injected = new LinkedHashMap<>();
        injected.put("xToken", M_TOKEN);
        injected.put("legacySecret", M_TOKEN);
        injected.put("credential", M_CODE);
        injected.put("nested", Map.of("accessToken", M_TOKEN, "phone", M_PHONE, "idNumber", M_ID));
        injected.put("deep", Map.of("l1", Map.of("l2", Map.of("idNumber", M_ID, "refreshToken", M_TOKEN))));
        return injected;
    }

    /** 契约字段的合法取值为代表（类型与契约一致）。 */
    private static Map<String, Object> contractBody(String javaName)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        switch (javaName)
        {
            case "redeemMaterialRef" -> body.put(AppAdminAuditParams.TOKEN, "AbCdEf0123456789AbCdEf0123456789");
            case "changeStatus" ->
            {
                body.put(AppAdminAuditParams.STATUS, "1");
                body.put(AppAdminAuditParams.REASON, "正常路径");
            }
            case "grantRoles" ->
            {
                body.put(AppAdminAuditParams.ROLE_IDS, List.of(7));
                body.put(AppAdminAuditParams.REASON, "授权");
            }
            case "decideRealName" ->
            {
                body.put(AppAdminAuditParams.DECISION, "APPROVE");
                body.put(AppAdminAuditParams.REJECT_REASON, "材料模糊");
                body.put(AppAdminAuditParams.EXPECTED_STATUS, "PENDING");
            }
            case "updateAuthorCapability" ->
            {
                body.put(AppAdminAuditParams.ENABLED, true);
                body.put(AppAdminAuditParams.REASON, "开通");
            }
            case "createNotification" ->
            {
                body.put(AppAdminAuditParams.REQUEST_ID, "req-1");
                body.put(AppAdminAuditParams.TYPE, "SYSTEM");
                body.put(AppAdminAuditParams.TITLE, "标题");
                body.put(AppAdminAuditParams.CONTENT, "正文");
                body.put(AppAdminAuditParams.USER_IDS, List.of(5));
            }
            case "handleFeedback" ->
            {
                body.put(AppAdminAuditParams.ACTION, "ACCEPT");
                body.put(AppAdminAuditParams.REPLY, "已受理");
                body.put(AppAdminAuditParams.EXPECTED_STATUS, "SUBMITTED");
            }
            default -> throw new IllegalArgumentException(javaName);
        }
        return body;
    }

    /**
     * 用与 {@code LogAspect.setRequestValue} 相同的序列化器与排除集（框架密码名 + 接口声明）
     * 还原该接口会写入 {@code oper_param} 的内容。
     */
    private static String auditParam(String javaName, Map<String, Object> body) throws Exception
    {
        Log log = findWriteMethod(javaName).getAnnotation(Log.class);
        assertNotNull(log, javaName + " 必须声明 @Log");
        return JSON.toJSONString(body, new LogAspect().excludePropertyPreFilter(log.excludeParamNames()));
    }

    private static Method findWriteMethod(String name) throws NoSuchMethodException
    {
        return "createNotification".equals(name) || "redeemMaterialRef".equals(name)
                ? A4AdminController.class.getDeclaredMethod(name, Map.class)
                : A4AdminController.class.getDeclaredMethod(name, Long.class, Map.class);
    }
}
