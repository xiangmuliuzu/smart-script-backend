package com.ruoyi.web.controller.a4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.enums.BusinessStatus;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.OperatorType;
import com.ruoyi.framework.aspectj.LogAspect;
import com.ruoyi.framework.aspectj.OperLogSanitizer;
import com.ruoyi.system.domain.SysOperLog;
import com.smartscript.platform.user.constant.AppAdminAuditParams;
import com.smartscript.platform.user.domain.admin.RealNameApplication;
import com.smartscript.platform.user.exception.AppAdminException;
import com.smartscript.platform.user.mapper.RealNameAdminMapper;
import com.smartscript.platform.user.service.AppUserAdminService;
import com.smartscript.platform.user.service.AuthorCapabilityService;
import com.smartscript.platform.user.service.RealNameReviewService;
import com.smartscript.platform.user.service.UserFeedbackAdminService;
import com.smartscript.platform.user.service.UserNotificationAdminService;

/**
 * H-04 / H-07：A4 PC 管理写接口的操作日志**字段级口径**防回退契约
 * （第 9 批建立，第 10 批随日志脱敏修复更新）。
 *
 * 断言四类事实，均与黑盒矩阵（{@code h-a-operlog-field-matrix.mjs}）一致：
 *
 * 1. 7 个写接口的 {@code @Log} 注解决定了操作日志的 title / business_type / operator_type /
 *    参数排除集 / 是否记录响应体；business_type 取 {@link BusinessType} 序号，
 *    operator_type 取 {@link OperatorType#MANAGE}。
 *
 * 2. 全部 7 个写接口都必须排除 §9.3 点名的敏感键名（验证码 / Token / 手机号 / 身份证号，
 *    见 {@link AppAdminAuditParams}）。第 9 批实测未声明的 {@code code} / {@code refreshToken}
 *    会原样进入 {@code oper_param}（H9-LOG-02）；第 10 批收敛后固定为不变量。
 *
 * 3. 审核结果不携带材料引用；且日志序列化器（LogAspect 用 fastjson2）遵守 {@code @JsonIgnore}
 *    但**不应用** Jackson 的 {@code @JsonInclude(NON_EMPTY)}（实测会产生空 {@code materialRefs:[]}）。
 *    第 9 批曾据后者推断 {@code @JsonIgnore} 亦不生效（H9-LOG-03）；第 10 批实测更正为：
 *    fastjson2 确实遵守 {@code @JsonIgnore}，该推断不成立、不构成泄漏风险。
 *
 * 4. 权限拒绝审计行（H9-LOG-04）不得携带请求参数与响应体。
 */
class A4AdminOperLogContractTest
{
    /** 单个写接口的日志口径期望。excluded 只做「至少包含」断言，避免限制后续增补。 */
    private record Expect(String title, BusinessType businessType, List<String> excluded, boolean saveResponse)
    {
    }

    /** §9.3 点名的敏感键名：全部 7 个写接口都必须排除。 */
    private static final List<String> SENSITIVE_PARAMS = List.of(
            AppAdminAuditParams.TOKEN, AppAdminAuditParams.ACCESS_TOKEN, AppAdminAuditParams.REFRESH_TOKEN,
            AppAdminAuditParams.CODE, AppAdminAuditParams.SMS_CODE, AppAdminAuditParams.CAPTCHA,
            AppAdminAuditParams.PHONE, AppAdminAuditParams.ID_NUMBER);

    private static final Map<String, Expect> EXPECTED = new LinkedHashMap<>();

    static
    {
        EXPECTED.put("changeStatus", new Expect("A4-App用户状态", BusinessType.UPDATE, List.of(AppAdminAuditParams.REASON), true));
        EXPECTED.put("grantRoles", new Expect("A4-用户角色授权", BusinessType.GRANT, List.of(AppAdminAuditParams.REASON), true));
        // H9-LOG-01：rejectReason 会随响应体进入 json_result，故该接口不记录响应体
        EXPECTED.put("decideRealName", new Expect("A4-实名审核", BusinessType.UPDATE, List.of(AppAdminAuditParams.REJECT_REASON), false));
        EXPECTED.put("updateAuthorCapability", new Expect("A4-作者能力", BusinessType.UPDATE, List.of(AppAdminAuditParams.REASON), true));
        // H10-REV-02：消息正文 title/content 为自由文本，声明排除（正文存业务表）
        EXPECTED.put("createNotification", new Expect("A4-用户消息", BusinessType.INSERT,
                List.of(AppAdminAuditParams.TITLE, AppAdminAuditParams.CONTENT), true));
        EXPECTED.put("handleFeedback", new Expect("A4-用户反馈处理", BusinessType.UPDATE, List.of(AppAdminAuditParams.REPLY), true));
        EXPECTED.put("redeemMaterialRef", new Expect("A4-敏感材料兑换", BusinessType.OTHER,
                List.of(AppAdminAuditParams.TOKEN, AppAdminAuditParams.BODY), false));
    }

    @Test
    void everyWriteEndpointDeclaresExpectedLogFields() throws Exception
    {
        assertEquals(7, EXPECTED.size(), "A4 写接口数量固定为 7；变更需同步本契约与第 9 批矩阵");

        for (Map.Entry<String, Expect> e : EXPECTED.entrySet())
        {
            Method method = findWriteMethod(e.getKey());
            Log log = method.getAnnotation(Log.class);
            assertNotNull(log, e.getKey() + " 必须声明 @Log（§5.3 要求写操作进入操作日志）");

            Expect exp = e.getValue();
            assertEquals(exp.title(), log.title(), e.getKey() + " 的 oper_title 口径");
            assertEquals(exp.businessType().ordinal(), log.businessType().ordinal(),
                    e.getKey() + " 的 business_type 取 BusinessType 序号");
            assertEquals(OperatorType.MANAGE.ordinal(), log.operatorType().ordinal(),
                    e.getKey() + " 的 operator_type 应为后台用户");
            assertEquals(exp.saveResponse(), log.isSaveResponseData(),
                    e.getKey() + " 的 json_result 是否记录响应体");

            List<String> excludes = List.of(log.excludeParamNames());
            assertTrue(excludes.containsAll(exp.excluded()),
                    e.getKey() + " 声明的参数排除项发生回退：期望至少 " + exp.excluded() + "，实际 " + excludes);
        }
    }

    @Test
    void everyWriteEndpointExcludesSensitiveParamNames() throws Exception
    {
        for (String name : EXPECTED.keySet())
        {
            Log log = findWriteMethod(name).getAnnotation(Log.class);
            assertNotNull(log, name + " 必须声明 @Log");
            List<String> excludes = List.of(log.excludeParamNames());
            assertTrue(excludes.containsAll(SENSITIVE_PARAMS),
                    name + " 未排除全部 §9.3 敏感键名（H9-LOG-02）：缺少 "
                            + SENSITIVE_PARAMS.stream().filter((p) -> !excludes.contains(p)).toList());
        }
    }

    @Test
    void decideServiceResponseDoesNotCarryMaterialReferences()
    {
        RealNameAdminMapper mapper = mock(RealNameAdminMapper.class);
        when(mapper.selectApplicationStatus(9101L)).thenReturn("PENDING");
        when(mapper.decideIfPending(9101L, "PENDING", "REJECTED", 9L, "材料不清晰"))
                .thenReturn(1);
        RealNameReviewService service = new RealNameReviewService(mapper, null);

        RealNameApplication response = service.decide(9101L, "REJECT", "材料不清晰", 9L, "PENDING");

        assertNull(response.getMaterialRef(), "审核结果不得携带永久材料引用");
        assertTrue(response.getMaterialRefs().isEmpty(), "审核结果不得携带短时材料引用");
    }

    @Test
    void fastjsonHonorsMaterialRefIgnoreButWritesEmptyMaterialRefs()
    {
        RealNameApplication response = new RealNameApplication();
        response.setMaterialRef("SYNTHETIC_MATERIAL_REF");

        String json = JSON.toJSONString(response);

        assertFalse(json.contains("SYNTHETIC_MATERIAL_REF"),
                "fastjson2 应遵守 materialRef 上的 @JsonIgnore");
        assertTrue(json.contains("\"materialRefs\":[]"),
                "fastjson2 当前不应用 materialRefs 上的 @JsonInclude(NON_EMPTY)");
    }

    @Test
    void deniedAccessAuditRowCarriesNoRequestContent()
    {
        SysOperLog row = new A4DeniedAccessAuditor()
                .buildDeniedOperLog("PUT", "/api/v1/admin/app-users/100/status", "127.0.0.1", "h8_normal");

        assertEquals("A4-权限拒绝", row.getTitle());
        assertEquals(BusinessType.OTHER.ordinal(), row.getBusinessType());
        assertEquals(OperatorType.MANAGE.ordinal(), row.getOperatorType());
        assertEquals(BusinessStatus.FAIL.ordinal(), row.getStatus());
        assertEquals("权限不足", row.getErrorMsg());
        assertEquals("/api/v1/admin/app-users/100/status", row.getOperUrl());
        assertEquals("PUT", row.getRequestMethod());
        assertEquals("h8_normal", row.getOperName());
        assertEquals("127.0.0.1", row.getOperIp());
        assertEquals(0L, row.getCostTime());
        assertNotNull(row.getOperTime());
        // 被拒请求的参数与响应一律不记录，避免引入新的泄漏面
        assertNull(row.getOperParam(), "拒绝审计行不得记录请求参数");
        assertNull(row.getJsonResult(), "拒绝审计行不得记录响应体");
    }

    /**
     * H10-REV-01：A4 有把令牌放在路径里的接口（{@code GET /admin/material/content/{token}}）。
     * 403 审计行必须与 {@code LogAspect} 走同一脱敏钩子，不得把令牌写进 {@code oper_url}。
     */
    @Test
    void deniedAccessAuditRedactsMaterialTokenInUrl()
    {
        String token = "AbCdEf0123456789AbCdEf0123456789AbCdEf";
        SysOperLog row = new A4DeniedAccessAuditor().buildDeniedOperLog(
                "GET", "/api/v1/admin/material/content/" + token, "127.0.0.1", "h06_partial");

        assertFalse(row.getOperUrl().contains(token), "拒绝审计行不得保留材料令牌（H10-REV-01）");
        assertTrue(row.getOperUrl().contains("{redacted}"), "材料令牌路径段应被脱敏为占位符");
        assertFalse(OperLogSanitizer.containsUnredactedTokenShape(row.getOperUrl()),
                "oper_url 不得残留白名单外的长路径段");
        // 无令牌的写接口路径不应被过度脱敏，仍可定位接口
        assertTrue(row.getOperUrl().startsWith("/api/v1/admin/material/content/"),
                "业务路径段应保留，只脱敏令牌段");
    }

    /**
     * H10-REV-02：{@code createNotification} 的 {@code title}/{@code content} 是自由文本正文，
     * 可含手机号、身份证号或 Token，必须声明排除，不得写入 {@code oper_param}。
     *
     * 正文已持久化在业务表；日志保留 {@code requestId}/{@code type}/{@code userIds} 等定位字段
     * 即可还原「谁在何时向谁发布了哪条消息」，故排除正文不损失审计可追溯性。
     */
    @Test
    void notificationTextIsNotLogged() throws Exception
    {
        Log log = findWriteMethod("createNotification").getAnnotation(Log.class);
        List<String> excludes = List.of(log.excludeParamNames());

        assertTrue(excludes.contains(AppAdminAuditParams.TITLE), "消息标题必须排除（H10-REV-02）");
        assertTrue(excludes.contains(AppAdminAuditParams.CONTENT), "消息正文必须排除（H10-REV-02）");
        assertTrue(excludes.containsAll(SENSITIVE_PARAMS), "消息日志仍须排除 §9.3 敏感键名");
        // 定位操作所需的关键字段不受影响
        assertFalse(excludes.contains("requestId"), "requestId 是审计关联键，不得排除");
        assertFalse(excludes.contains("userIds"), "userIds 是收件人目标，不得排除");
    }

    /**
     * H12-REV-01（第 13 批）：`createNotification` 的 `requestId`/`businessType`/`businessId` 是
     * 客户端可控自由字符串，其**值**可承载材料令牌。修复口径是「只脱敏、不删除」——
     * 服务层仍收到原值（幂等键与业务引用不变），而 `@Log` 序列化的同一个 Map 只含不可逆指纹。
     */
    @Test
    void notificationFingerprintsLogValuesButServiceReceivesRawValues() throws Exception
    {
        String token = "AbCdEf0123456789AbCdEf0123456789";
        UserNotificationAdminService service = mock(UserNotificationAdminService.class);
        when(service.create(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new UserNotificationAdminService.CreateResult(77L, 1, true));
        A4AdminController controller = new A4AdminController(null, null, null, service, null, null, null, null);

        SysUser sysUser = new SysUser();
        sysUser.setUserName("h8_normal");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(1L, null, sysUser, Set.of()), null, List.of()));
        try
        {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(AppAdminAuditParams.REQUEST_ID, token);
            body.put(AppAdminAuditParams.TYPE, "SYSTEM");
            body.put(AppAdminAuditParams.TITLE, "标题");
            body.put(AppAdminAuditParams.CONTENT, "正文");
            body.put(AppAdminAuditParams.BUSINESS_TYPE, "REAL_NAME");
            body.put(AppAdminAuditParams.BUSINESS_ID, token);
            body.put(AppAdminAuditParams.USER_IDS, List.of(5));

            AjaxResult result = controller.createNotification(body);
            assertNotNull(result, "创建消息必须返回统一响应");

            // 1) 业务入参为原值：幂等键与业务引用不得被脱敏影响
            verify(service).create(eq(token), eq("SYSTEM"), eq("标题"), eq("正文"),
                    eq("REAL_NAME"), eq(token), eq(List.of(5L)), eq("h8_normal"));

            // 2) 日志投影（@Log 序列化同一 Map）只含指纹，且保留结构供审计关联
            Log log = findWriteMethod("createNotification").getAnnotation(Log.class);
            String operParam = JSON.toJSONString(body, new LogAspect().excludePropertyPreFilter(log.excludeParamNames()));
            assertFalse(operParam.contains(token), "oper_param 不得含令牌原值：" + operParam);
            assertTrue(operParam.matches("(?s).*\"requestId\":\"fp:[0-9a-f]{12}\".*"),
                    "requestId 须以不可逆指纹记录且字段保留：" + operParam);
            assertTrue(operParam.contains("\"type\":\"SYSTEM\""), "受控枚举 type 保持原值");
            assertTrue(operParam.matches("(?s).*\"userIds\":\\[\"fp:[0-9a-f]{12}\"\\].*"),
                    "userIds 元素（含数字）须一律指纹化：" + operParam);
            assertFalse(operParam.contains("\"title\""), "自由文本 title 仍被排除（H10-REV-02）");
            assertFalse(operParam.contains("\"content\""), "自由文本 content 仍被排除（H10-REV-02）");
        }
        finally
        {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * H13-REV-01（第 13 批复核补修）：`type` 非法枚举与 `userIds` 字符串元素的值级默认拒绝。
     * 服务层仍收到原值（枚举校验、收件人解析不变），日志投影把这些值指纹化。
     */
    @Test
    void notificationLogProjectionIsDefaultDenyForEnumAndUserIdsValues() throws Exception
    {
        String token = "AbCdEf0123456789AbCdEf0123456789";
        UserNotificationAdminService service = mock(UserNotificationAdminService.class);
        when(service.create(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new UserNotificationAdminService.CreateResult(88L, 1, true));
        A4AdminController controller = new A4AdminController(null, null, null, service, null, null, null, null);

        authenticate("h8_normal");
        try
        {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(AppAdminAuditParams.REQUEST_ID, "req-ok");
            body.put(AppAdminAuditParams.TYPE, token);
            body.put(AppAdminAuditParams.TITLE, "标题");
            body.put(AppAdminAuditParams.CONTENT, "正文");
            body.put(AppAdminAuditParams.USER_IDS, List.of(5, token));

            controller.createNotification(body);

            // 服务层仍取原值：非法枚举与含字符串的 userIds 由服务层按契约校验/解析
            verify(service).create(eq("req-ok"), eq(token), eq("标题"), eq("正文"),
                    any(), any(), eq(List.of(5L)), eq("h8_normal"));

            Log log = findWriteMethod("createNotification").getAnnotation(Log.class);
            String operParam = JSON.toJSONString(body, new LogAspect().excludePropertyPreFilter(log.excludeParamNames()));
            assertFalse(operParam.contains(token), "oper_param 不得含令牌原值：" + operParam);
            assertTrue(operParam.matches("(?s).*\"type\":\"fp:[0-9a-f]{12}\".*"),
                    "非法枚举 type 须指纹化：" + operParam);
            assertTrue(operParam.matches("(?s).*\"userIds\":\\[\"fp:[0-9a-f]{12}\",\"fp:[0-9a-f]{12}\"\\].*"),
                    "userIds 元素（含数字）须一律指纹化：" + operParam);
        }
        finally
        {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 第 14 批（H-07 值级残余）：其余写接口的非枚举字段（`status`/`decision`/
     * `expectedStatus`/`action`/`enabled`）按值级默认拒绝收敛——契约枚举/布尔保持可读，
     * 其余取值（可承载令牌/PII）在日志投影中指纹化；服务层仍收到**原值**，
     * 业务校验与状态机不变（「只脱敏、不删除」）。
     */
    @Test
    void remainingWriteEndpointsFingerprintLogValuesButServicesReceiveRawValues() throws Exception
    {
        String token = "AbCdEf0123456789AbCdEf0123456789";
        AppUserAdminService appUserAdminService = mock(AppUserAdminService.class);
        when(appUserAdminService.changeStatus(any(), any(), any(), any())).thenReturn(true);
        RealNameReviewService realNameReviewService = mock(RealNameReviewService.class);
        when(realNameReviewService.decide(any(), any(), any(), any(), any()))
                .thenReturn(new RealNameApplication());
        AuthorCapabilityService authorCapabilityService = mock(AuthorCapabilityService.class);
        when(authorCapabilityService.setEnabled(any(), anyBoolean(), any(), any(), any())).thenReturn(true);
        UserFeedbackAdminService feedbackService = mock(UserFeedbackAdminService.class);
        when(feedbackService.handle(any(), any(), any(), any(), any()))
                .thenReturn(new UserFeedbackAdminService.HandleResult(5L, "SUBMITTED", true));
        A4AdminController controller = new A4AdminController(appUserAdminService, realNameReviewService,
                authorCapabilityService, null, feedbackService, null, null, null);

        authenticate("h8_normal");
        try
        {
            // 1) 用户状态：status 非法取值指纹化，服务层仍收到原值
            Map<String, Object> statusBody = new LinkedHashMap<>();
            statusBody.put(AppAdminAuditParams.STATUS, token);
            statusBody.put(AppAdminAuditParams.REASON, "状态原因");
            controller.changeStatus(9L, statusBody);
            verify(appUserAdminService).changeStatus(eq(9L), eq(token), eq("状态原因"), eq("h8_normal"));
            assertFingerprinted(statusBody, "changeStatus", AppAdminAuditParams.STATUS, token);

            // 2) 实名审核：decision/expectedStatus 非法取值指纹化，服务层仍收到原值
            Map<String, Object> decisionBody = new LinkedHashMap<>();
            decisionBody.put(AppAdminAuditParams.DECISION, token);
            decisionBody.put(AppAdminAuditParams.EXPECTED_STATUS, "NOT_PENDING");
            controller.decideRealName(9L, decisionBody);
            verify(realNameReviewService).decide(eq(9L), eq(token), isNull(), any(), eq("NOT_PENDING"));
            assertFingerprinted(decisionBody, "decideRealName", AppAdminAuditParams.DECISION, token);
            assertFingerprinted(decisionBody, "decideRealName", AppAdminAuditParams.EXPECTED_STATUS, "NOT_PENDING");

            // 3) 作者能力：enabled 非布尔取值按契约拒绝（H14-REV-02），服务层不被调用；
            //    日志投影仍先把该值指纹化（失败请求同样经 @AfterThrowing 序列化请求体）
            Map<String, Object> enabledBody = new LinkedHashMap<>();
            enabledBody.put(AppAdminAuditParams.ENABLED, token);
            enabledBody.put(AppAdminAuditParams.REASON, "能力原因");
            AppAdminException rejected = assertThrows(AppAdminException.class,
                    () -> controller.updateAuthorCapability(9L, enabledBody),
                    "非布尔 enabled 必须被契约校验拒绝");
            assertEquals("请求参数无效", rejected.getMessage());
            verifyNoInteractions(authorCapabilityService);
            assertFingerprinted(enabledBody, "updateAuthorCapability", AppAdminAuditParams.ENABLED, token);

            // 3b) 合法布尔与可解析布尔字符串保持既有行为（契约 §5.3）
            Map<String, Object> legalBool = new LinkedHashMap<>();
            legalBool.put(AppAdminAuditParams.ENABLED, Boolean.TRUE);
            legalBool.put(AppAdminAuditParams.REASON, "能力原因");
            controller.updateAuthorCapability(9L, legalBool);
            // 仅 JSON 布尔到达服务层（H14-REV-02 复核收紧：字符串不再兼容）
            verify(authorCapabilityService).setEnabled(eq(9L), eq(true), eq("能力原因"), any(), eq("h8_normal"));

            // 复核收紧：字符串 "true"/"false"（任意大小写）与数字同样被契约拒绝，服务层零调用
            for (Object rejectedValue : List.of("true", "TRUE", "false", 1))
            {
                Map<String, Object> nonBool = new LinkedHashMap<>();
                nonBool.put(AppAdminAuditParams.ENABLED, rejectedValue);
                nonBool.put(AppAdminAuditParams.REASON, "能力原因");
                AppAdminException rejectedEx = assertThrows(AppAdminException.class,
                        () -> controller.updateAuthorCapability(9L, nonBool),
                        "非 JSON 布尔 enabled 必须被拒绝：" + rejectedValue);
                assertEquals("请求参数无效", rejectedEx.getMessage());
            }
            verify(authorCapabilityService, times(1)).setEnabled(any(), anyBoolean(), any(), any(), any());

            // 4) 反馈处理：action/expectedStatus 非法取值指纹化，服务层仍收到原值
            Map<String, Object> feedbackBody = new LinkedHashMap<>();
            feedbackBody.put(AppAdminAuditParams.ACTION, token);
            feedbackBody.put(AppAdminAuditParams.EXPECTED_STATUS, "NOT_A_STATE");
            controller.handleFeedback(5L, feedbackBody);
            verify(feedbackService).handle(eq(5L), eq(token), isNull(), eq("NOT_A_STATE"), any());
            assertFingerprinted(feedbackBody, "handleFeedback", AppAdminAuditParams.ACTION, token);
            assertFingerprinted(feedbackBody, "handleFeedback", AppAdminAuditParams.EXPECTED_STATUS, "NOT_A_STATE");

            // 5) 合法契约枚举值保持可读（受控枚举不得被误指纹化）
            Map<String, Object> legal = new LinkedHashMap<>();
            legal.put(AppAdminAuditParams.STATUS, "1");
            A4AuditParamProjection.sanitizeStatusChangeLog(legal);
            assertEquals("1", legal.get(AppAdminAuditParams.STATUS));
            Map<String, Object> legalFeedback = new LinkedHashMap<>();
            legalFeedback.put(AppAdminAuditParams.ACTION, "ACCEPT");
            legalFeedback.put(AppAdminAuditParams.EXPECTED_STATUS, "SUBMITTED");
            A4AuditParamProjection.sanitizeFeedbackHandleLog(legalFeedback);
            assertEquals("ACCEPT", legalFeedback.get(AppAdminAuditParams.ACTION));
            assertEquals("SUBMITTED", legalFeedback.get(AppAdminAuditParams.EXPECTED_STATUS));
        }
        finally
        {
            SecurityContextHolder.clearContext();
        }
    }

    /** 断言该键在日志投影中只剩不可逆指纹且不含原值（与 @Log 序列化同一口径）。 */
    private static void assertFingerprinted(Map<String, Object> body, String endpoint, String key, String raw)
            throws Exception
    {
        Log log = findWriteMethod(endpoint).getAnnotation(Log.class);
        String operParam = JSON.toJSONString(body, new LogAspect().excludePropertyPreFilter(log.excludeParamNames()));
        assertFalse(operParam.contains("\"" + key + "\":\"" + raw + "\""),
                endpoint + " 的 " + key + " 不得以原值进入 oper_param：" + operParam);
        assertTrue(operParam.matches("(?s).*\"" + key + "\":\"fp:[0-9a-f]{12}\".*"),
                endpoint + " 的 " + key + " 须以不可逆指纹记录且字段保留：" + operParam);
    }

    private static void authenticate(String userName)
    {
        SysUser sysUser = new SysUser();
        sysUser.setUserName(userName);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(1L, null, sysUser, Set.of()), null, List.of()));
    }

    /** 按参数形状解析 7 个写接口方法，避免依赖方法重载顺序。 */
    private static Method findWriteMethod(String name) throws NoSuchMethodException
    {
        Class<?> c = A4AdminController.class;
        return switch (name)
        {
            case "changeStatus" -> c.getDeclaredMethod(name, Long.class, Map.class);
            case "grantRoles" -> c.getDeclaredMethod(name, Long.class, Map.class);
            case "decideRealName" -> c.getDeclaredMethod(name, Long.class, Map.class);
            case "updateAuthorCapability" -> c.getDeclaredMethod(name, Long.class, Map.class);
            case "createNotification" -> c.getDeclaredMethod(name, Map.class);
            case "handleFeedback" -> c.getDeclaredMethod(name, Long.class, Map.class);
            case "redeemMaterialRef" -> c.getDeclaredMethod(name, Map.class);
            default -> throw new NoSuchMethodException(name);
        };
    }
}
