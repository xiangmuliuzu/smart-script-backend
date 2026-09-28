package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.system.domain.SysLogininfor;
import com.smartscript.platform.identity.IdentityContext;
import com.smartscript.platform.identity.IdentityProvider;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.domain.AppRefreshSession;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.dto.PasswordLoginRequest;
import com.smartscript.platform.user.dto.SmsLoginRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppRefreshSessionMapper;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.security.AppAccessTokenService.IssuedAccessToken;
import com.smartscript.platform.user.service.AppSessionRevocationService;
import com.smartscript.platform.user.service.RefreshSessionService.IssuedSession;

/**
 * H15-REV-03（验收标准 §9.3）：登录成功/失败、禁用账号登录、Refresh Token 重放必须留可查询安全审计。
 *
 * 不启动 Spring：Mockito 构造服务依赖，捕获 AppSecurityEventRecorder 收到的行，
 * 与黑盒脚本 `shared/scripts/h-a-login-replay-audit.mjs`（真实后端逐请求断言 sys_logininfor）互补。
 */
class AppLoginAuditEventTest
{
    private static final String PHONE = "13900000007";
    private static final String MASK = "139****0007";
    private static final String PASSWORD = "Passw0rdX";
    private static final String IP = "127.0.0.1";

    /** 捕获型审计器：记录 (event, status, actor, detail)。 */
    private static class CapturingRecorder extends AppSecurityEventRecorder
    {
        final List<String> rows = new java.util.ArrayList<>();

        CapturingRecorder()
        {
            super(null);
        }

        @Override
        public void record(String actor, String ip, String event, String detail)
        {
            rows.add(event + "|1|" + actor + "|" + detail);
        }

        @Override
        public void recordSuccess(String actor, String ip, String event, String detail)
        {
            rows.add(event + "|0|" + actor + "|" + detail);
        }
    }

    private AppAuthenticationService service(CapturingRecorder recorder, AppUserMapper userMapper,
            SmsCodeService smsCodeService, RefreshSessionService refreshSessionService)
    {
        return new AppAuthenticationService(userMapper, smsCodeService, mock(AgreementService.class),
                refreshSessionService, mock(AppSessionRevocationService.class),
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(),
                identityProvider(), recorder);
    }

    private IdentityProvider identityProvider()
    {
        IdentityProvider provider = mock(IdentityProvider.class);
        when(provider.currentIdentity()).thenReturn(IdentityContext.guest());
        return provider;
    }

    private AppUserRecord usableUser()
    {
        AppUserRecord user = new AppUserRecord();
        user.setUserId(901L);
        user.setNickName(MASK);
        user.setPhonenumber(PHONE);
        user.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(PASSWORD));
        user.setUserType("01");
        user.setAvatar("");
        user.setStatus("0");
        user.setDelFlag("0");
        return user;
    }

    private AppUserRecord disabledUser()
    {
        AppUserRecord user = usableUser();
        user.setStatus("1");
        return user;
    }

    @SuppressWarnings("unchecked")
    private RefreshSessionService refreshSessionService(CapturingRecorder recorder, AppRefreshSessionMapper mapper)
    {
        return new RefreshSessionService(new com.smartscript.platform.user.config.AppAuthProperties(),
                mapper, null, mock(AppSessionRevocationService.class), recorder);
    }

    // ------------------------------------------------------------ 密码登录
    @Test
    void passwordLoginFailureRecordsLoginFailWithoutPassword()
    {
        CapturingRecorder recorder = new CapturingRecorder();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        when(userMapper.selectByPhone(PHONE)).thenReturn(usableUser());
        AppAuthenticationService service = service(recorder, userMapper, mock(SmsCodeService.class),
                refreshSessionService(recorder, mock(AppRefreshSessionMapper.class)));

        PasswordLoginRequest request = new PasswordLoginRequest();
        request.setPhone(PHONE);
        request.setPassword("TotallyWrong1");
        AppAuthException ex = assertThrows(AppAuthException.class, () -> service.passwordLogin(request, IP));
        assertEquals(400, ex.getHttpStatus());

        assertEquals(1, recorder.rows.size());
        String row = recorder.rows.get(0);
        assertTrue(row.startsWith("APP_LOGIN_FAIL|1|" + MASK + "|"), "事件/失败状态/掩码 actor：" + row);
        assertTrue(row.contains("scene=PASSWORD"), row);
        assertTrue(!row.contains("TotallyWrong1") && !row.contains(PHONE), "口令与原始手机号不得入审计：" + row);
    }

    @Test
    void disabledPasswordLoginRecordsDisabledEvent()
    {
        CapturingRecorder recorder = new CapturingRecorder();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        when(userMapper.selectByPhone(PHONE)).thenReturn(disabledUser());
        AppAuthenticationService service = service(recorder, userMapper, mock(SmsCodeService.class),
                refreshSessionService(recorder, mock(AppRefreshSessionMapper.class)));

        PasswordLoginRequest request = new PasswordLoginRequest();
        request.setPhone(PHONE);
        request.setPassword(PASSWORD);
        AppAuthException ex = assertThrows(AppAuthException.class, () -> service.passwordLogin(request, IP));
        assertEquals(AppAuthErrorCodes.ACCOUNT_DISABLED, ex.getCode());

        assertEquals(1, recorder.rows.size());
        String row = recorder.rows.get(0);
        assertTrue(row.startsWith("APP_LOGIN_DISABLED|1|" + MASK + "|"), row);
        assertTrue(row.contains("userId=901"), row);
    }

    @Test
    void successfulPasswordLoginRecordsSuccessEvent()
    {
        CapturingRecorder recorder = new CapturingRecorder();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        when(userMapper.selectByPhone(PHONE)).thenReturn(usableUser());

        RefreshSessionService rss = org.mockito.Mockito.mock(RefreshSessionService.class);
        AppRefreshSession session = new AppRefreshSession();
        session.setId(11L);
        session.setUserId(901L);
        when(rss.createSession(eq(901L), any(), any())).thenReturn(
                new IssuedSession(session, "raw-refresh", new IssuedAccessToken("access", "jti", 11L, 300), 600));
        AppAuthenticationService service = service(recorder, userMapper, mock(SmsCodeService.class), rss);

        PasswordLoginRequest request = new PasswordLoginRequest();
        request.setPhone(PHONE);
        request.setPassword(PASSWORD);
        service.passwordLogin(request, IP);

        assertEquals(1, recorder.rows.size());
        String row = recorder.rows.get(0);
        assertTrue(row.startsWith("APP_LOGIN_SUCCESS|0|" + MASK + "|"), "成功事件 status=0：" + row);
        assertTrue(row.contains("scene=PASSWORD") && row.contains("userId=901"), row);
        assertTrue(!row.contains("raw-refresh") && !row.contains("access"), "令牌不得入审计：" + row);
    }

    // ------------------------------------------------------------ 短信登录
    @Test
    void smsCodeFailureRecordsLoginFailAndRethrows()
    {
        CapturingRecorder recorder = new CapturingRecorder();
        SmsCodeService sms = mock(SmsCodeService.class);
        org.mockito.Mockito.doThrow(new AppAuthException(AppAuthErrorCodes.SMS_CODE_INVALID, 400, "sms code invalid"))
                .when(sms).consumeOnce(PHONE, "LOGIN", "000000");
        AppAuthenticationService service = service(recorder, mock(AppUserMapper.class), sms,
                refreshSessionService(recorder, mock(AppRefreshSessionMapper.class)));

        SmsLoginRequest request = new SmsLoginRequest();
        request.setPhone(PHONE);
        request.setCode("000000");
        AppAuthException ex = assertThrows(AppAuthException.class, () -> service.smsLogin(request, IP));
        assertEquals(AppAuthErrorCodes.SMS_CODE_INVALID, ex.getCode());

        assertEquals(1, recorder.rows.size());
        String row = recorder.rows.get(0);
        assertTrue(row.startsWith("APP_LOGIN_FAIL|1|" + MASK + "|"), row);
        assertTrue(row.contains("scene=LOGIN reason=" + AppAuthErrorCodes.SMS_CODE_INVALID), row);
        assertTrue(!row.contains("000000|"), "验证码原文不得入审计：" + row);
    }

    @Test
    void smsLoginDisabledAndSuccessRecordDistinctEvents()
    {
        CapturingRecorder recorder = new CapturingRecorder();
        SmsCodeService sms = mock(SmsCodeService.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        when(userMapper.selectByPhone(PHONE)).thenReturn(disabledUser());
        AppAuthenticationService service = service(recorder, userMapper, sms,
                refreshSessionService(recorder, mock(AppRefreshSessionMapper.class)));

        SmsLoginRequest request = new SmsLoginRequest();
        request.setPhone(PHONE);
        request.setCode("123456");
        AppAuthException ex = assertThrows(AppAuthException.class, () -> service.smsLogin(request, IP));
        assertEquals(AppAuthErrorCodes.ACCOUNT_DISABLED, ex.getCode());
        assertTrue(recorder.rows.get(0).startsWith("APP_LOGIN_DISABLED|1|" + MASK + "|"), recorder.rows.get(0));

        // 成功路径（含首次短信登录自动建号）
        CapturingRecorder okRecorder = new CapturingRecorder();
        AppUserMapper okMapper = mock(AppUserMapper.class);
        when(okMapper.selectByPhone(PHONE)).thenReturn(null, usableUser());
        RefreshSessionService rss = org.mockito.Mockito.mock(RefreshSessionService.class);
        AppRefreshSession session = new AppRefreshSession();
        session.setId(12L);
        session.setUserId(901L);
        when(rss.createSession(any(), any(), any())).thenReturn(
                new IssuedSession(session, "raw-refresh", new IssuedAccessToken("access", "jti", 12L, 300), 600));
        AppAuthenticationService okService = new AppAuthenticationService(okMapper, sms,
                mock(AgreementService.class), rss, mock(AppSessionRevocationService.class),
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(),
                identityProvider(), okRecorder);
        okService.smsLogin(request, IP);
        assertEquals(1, okRecorder.rows.size());
        assertTrue(okRecorder.rows.get(0).startsWith("APP_LOGIN_SUCCESS|0|" + MASK + "|"), okRecorder.rows.get(0));
    }

    // ------------------------------------------------------------ Refresh 重放
    @Test
    void refreshReplayRecordsSecurityEventAndRevokesFamily()
    {
        CapturingRecorder recorder = new CapturingRecorder();
        AppRefreshSessionMapper mapper = mock(AppRefreshSessionMapper.class);
        AppRefreshSession revoked = new AppRefreshSession();
        revoked.setId(21L);
        revoked.setUserId(902L);
        revoked.setFamilyId("family-abc");
        revoked.setRevokedAt(new Date());
        revoked.setRevokedReason("ROTATED");
        when(mapper.selectByTokenHashForUpdate(anyString())).thenReturn(revoked);
        RefreshSessionService rss = refreshSessionService(recorder, mapper);

        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> rss.rotate("stolen-refresh-token", null, null, IP));
        assertEquals(AppAuthErrorCodes.REFRESH_REPLAY, ex.getCode());

        verify(mapper).revokeFamily(eq("family-abc"), eq("REPLAY"), any(Date.class));
        assertEquals(1, recorder.rows.size());
        String row = recorder.rows.get(0);
        assertTrue(row.startsWith("APP_REFRESH_REPLAY|1|user-902|"), "重放事件/失败状态/内部标识 actor：" + row);
        assertTrue(row.contains("family=family-abc"), row);
        assertTrue(!row.contains("stolen-refresh-token"), "令牌原文不得入审计：" + row);
    }

    // ------------------------------------------------------------ Recorder 行口径
    @Test
    void recorderWritesSuccessAndFailStatusLikeRuoYi() throws Exception
    {
        List<SysLogininfor> sink = new java.util.ArrayList<>();
        com.ruoyi.system.service.ISysLogininforService svc = mock(com.ruoyi.system.service.ISysLogininforService.class);
        org.mockito.Mockito.doAnswer(inv -> {
            sink.add(inv.getArgument(0));
            return null;
        }).when(svc).insertLogininfor(any(SysLogininfor.class));
        AppSecurityEventRecorder recorder = new AppSecurityEventRecorder(svc);

        recorder.record(MASK, IP, "APP_LOGIN_FAIL", "scene=PASSWORD reason=BAD_CREDENTIAL");
        recorder.recordSuccess(MASK, IP, "APP_LOGIN_SUCCESS", "scene=PASSWORD userId=901");

        // 插入经 AsyncManager 异步队列落库（与业务事务解耦，H15-REV-03），轮询等待最多 2s
        long deadline = System.currentTimeMillis() + 2000;
        while (sink.size() < 2 && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(20);
        }

        assertEquals(2, sink.size());
        assertEquals(Constants.FAIL, sink.get(0).getStatus());
        assertEquals("APP_LOGIN_FAIL scene=PASSWORD reason=BAD_CREDENTIAL", sink.get(0).getMsg());
        assertEquals(Constants.SUCCESS, sink.get(1).getStatus());
        assertEquals(MASK, sink.get(1).getUserName());
        assertEquals(IP, sink.get(1).getIpaddr());
        ArgumentCaptor<SysLogininfor> captor = ArgumentCaptor.forClass(SysLogininfor.class);
        org.mockito.Mockito.verify(svc, org.mockito.Mockito.times(2)).insertLogininfor(captor.capture());
        assertEquals(2, captor.getAllValues().size());
    }

    // ------------------------------------------------------------ H15-REV-05：成功事件必须对应已提交的会话
    private void withActiveSynchronization(Runnable body)
    {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try
        {
            body.run();
        }
        finally
        {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void successEventOnlyFiresAfterTransactionCommit()
    {
        CapturingRecorder recorder = new CapturingRecorder();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        when(userMapper.selectByPhone(PHONE)).thenReturn(usableUser());
        RefreshSessionService rss = org.mockito.Mockito.mock(RefreshSessionService.class);
        AppRefreshSession session = new AppRefreshSession();
        session.setId(31L);
        session.setUserId(901L);
        when(rss.createSession(any(), any(), any())).thenReturn(
                new IssuedSession(session, "raw-refresh", new IssuedAccessToken("access", "jti", 31L, 300), 600));
        AppAuthenticationService service = service(recorder, userMapper, mock(SmsCodeService.class), rss);

        PasswordLoginRequest request = new PasswordLoginRequest();
        request.setPhone(PHONE);
        request.setPassword(PASSWORD);

        withActiveSynchronization(() -> {
            service.passwordLogin(request, IP);
            // 事务尚未提交：不得提前写成功行
            assertEquals(0, recorder.rows.size(), "提交前不得出现 APP_LOGIN_SUCCESS");
            // 模拟事务提交：afterCommit 触发后恰好一条成功行
            for (org.springframework.transaction.support.TransactionSynchronization s
                    : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations())
            {
                s.afterCommit();
            }
            assertEquals(1, recorder.rows.size());
            assertTrue(recorder.rows.get(0).startsWith("APP_LOGIN_SUCCESS|0|" + MASK + "|"), recorder.rows.get(0));
        });
    }

    @Test
    void sessionIssuanceFailureLeavesNoSuccessRow()
    {
        CapturingRecorder recorder = new CapturingRecorder();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        when(userMapper.selectByPhone(PHONE)).thenReturn(usableUser());
        RefreshSessionService rss = org.mockito.Mockito.mock(RefreshSessionService.class);
        when(rss.createSession(any(), any(), any())).thenThrow(new RuntimeException("h15-rev05: session insert failed"));
        AppAuthenticationService service = service(recorder, userMapper, mock(SmsCodeService.class), rss);

        PasswordLoginRequest request = new PasswordLoginRequest();
        request.setPhone(PHONE);
        request.setPassword(PASSWORD);

        withActiveSynchronization(() -> {
            // 会话签发失败 → 事务回滚 → afterCommit 不触发 → 不得有虚假成功行
            assertThrows(RuntimeException.class, () -> service.passwordLogin(request, IP));
        });
        assertTrue(recorder.rows.isEmpty(), "签发失败/回滚时不得留下成功审计行：" + recorder.rows);
    }

    @Test
    void failureEventsRemainImmediateEvenInsideTransaction()
    {
        CapturingRecorder recorder = new CapturingRecorder();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        when(userMapper.selectByPhone(PHONE)).thenReturn(disabledUser());
        AppAuthenticationService service = service(recorder, userMapper, mock(SmsCodeService.class),
                refreshSessionService(recorder, mock(AppRefreshSessionMapper.class)));

        PasswordLoginRequest request = new PasswordLoginRequest();
        request.setPhone(PHONE);
        request.setPassword(PASSWORD);

        withActiveSynchronization(() -> {
            // 失败事件立即落行（异步落库后不受回滚影响）——失败尝试本身必须留痕
            assertThrows(AppAuthException.class, () -> service.passwordLogin(request, IP));
            assertEquals(1, recorder.rows.size());
            assertTrue(recorder.rows.get(0).startsWith("APP_LOGIN_DISABLED|1|" + MASK + "|"), recorder.rows.get(0));
        });
    }
}
