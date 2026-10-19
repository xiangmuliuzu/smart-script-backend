package com.smartscript.platform.user.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import com.smartscript.platform.user.exception.AppAuthException;

/** 全部请求由 MockRestServiceServer 截获，不联网、不使用真实 AppCode、不消耗额度。 */
class AliyunRealNameVerifierTest
{
    private MockRestServiceServer server;
    private AliyunRealNameVerifier verifier;

    @BeforeEach
    void setup()
    {
        var client = new RestTemplate();
        server = MockRestServiceServer.bindTo(client).build();
        verifier = new AliyunRealNameVerifier(true, "offline-test-code", client);
    }

    @Test
    void postsUtf8FormWithHeaderAuthenticationAndAcceptsOnlyExplicitMatch()
    {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("name", "测试用户");
        form.add("idNo", "000000200001010000");
        server.expect(requestTo(AliyunRealNameVerifier.ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "APPCODE offline-test-code"))
                .andExpect(content().formData(form))
                .andRespond(withSuccess("{\"respCode\":\"0000\"}", MediaType.APPLICATION_JSON));
        assertNull(verifier.verify("测试用户", "000000200001010000"));
        server.verify();
    }

    @Test
    void mismatchAndMissingIdentityHaveFixedNonSensitiveReasons()
    {
        for (String code : new String[] { "0007", "0008" })
        {
            server.reset();
            server.expect(requestTo(AliyunRealNameVerifier.ENDPOINT))
                    .andRespond(withSuccess("{\"respCode\":\"" + code + "\",\"respMessage\":\"private data\"}",
                            MediaType.APPLICATION_JSON));
            String reason = verifier.verify("测试用户", "000000200001010000");
            assertNotNull(reason);
            assertFalse(reason.contains("private data"));
            server.verify();
        }
    }

    @Test
    void maintenanceUnknownEmptyAndMalformedResponsesNeverPassOrRetry()
    {
        for (String body : new String[] { "{\"respCode\":\"0010\"}", "{\"respCode\":\"9999\"}", "{}", "null", "bad json" })
        {
            server.reset();
            server.expect(requestTo(AliyunRealNameVerifier.ENDPOINT)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            var error = assertThrows(AppAuthException.class, () -> verifier.verify("测试用户", "000000200001010000"));
            assertEquals(503, error.getHttpStatus());
            server.verify();
        }
    }

    @Test
    void gatewayAndNetworkFailuresNeverLeakResponseOrRetry()
    {
        server.expect(requestTo(AliyunRealNameVerifier.ENDPOINT))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("private credentials and identity"));
        var error = assertThrows(AppAuthException.class, () -> verifier.verify("测试用户", "000000200001010000"));
        assertEquals(503, error.getHttpStatus());
        // 403/401 是额度用尽或 APPCODE 失效，必须给出可操作的提示而不是笼统的「暂不可用」
        assertTrue(error.getMessage().contains("额度"));
        assertFalse(error.getMessage().contains("private"));
        server.verify();
        server.reset();
        server.expect(requestTo(AliyunRealNameVerifier.ENDPOINT)).andRespond(withException(new IOException("private")));
        var networkError = assertThrows(AppAuthException.class, () -> verifier.verify("测试用户", "000000200001010000"));
        assertEquals("实名认证服务暂不可用，请稍后再试", networkError.getMessage());
        server.verify();
    }

    @Test
    void endpointOverrideKeepsRequestShapeAndLeavesProductionUrlUntouched()
    {
        var client = new RestTemplate();
        var localServer = MockRestServiceServer.bindTo(client).build();
        var localVerifier = new AliyunRealNameVerifier(true, "offline-test-code",
                "http://127.0.0.1:18080/idenAuthentication", client);
        localServer.expect(requestTo("http://127.0.0.1:18080/idenAuthentication"))
                .andExpect(header("Authorization", "APPCODE offline-test-code"))
                .andRespond(withSuccess("{\"respCode\":\"0008\"}", MediaType.APPLICATION_JSON));
        assertNotNull(localVerifier.verify("测试用户", "000000200001010000"));
        localServer.verify();
        assertEquals("https://idenauthen.market.alicloudapi.com/idenAuthentication", AliyunRealNameVerifier.ENDPOINT);
    }

    @Test
    void missingCredentialsFailStartupAndDisabledModeCannotMakeRequests()
    {
        assertThrows(IllegalStateException.class, () -> new AliyunRealNameVerifier(true, "", new RestTemplate()));
        var disabled = new AliyunRealNameVerifier(false, "", new RestTemplate());
        assertThrows(AppAuthException.class, () -> disabled.verify("测试用户", "000000200001010000"));
    }
}
