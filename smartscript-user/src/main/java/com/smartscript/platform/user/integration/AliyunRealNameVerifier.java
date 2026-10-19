package com.smartscript.platform.user.integration;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.exception.AppAuthException;

/** 云市场 cmapi025518 二要素核验。只在用户提交时调用，不重试，不记录请求或响应明文。 */
@Component
public class AliyunRealNameVerifier
{
    static final String ENDPOINT = "https://idenauthen.market.alicloudapi.com/idenAuthentication";
    private final boolean enabled;
    private final String appCode;
    private final RestTemplate client;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public AliyunRealNameVerifier(
            @Value("${app.real-name.aliyun.enabled:false}") boolean enabled,
            @Value("${app.real-name.aliyun.app-code:}") String appCode)
    {
        this(enabled, appCode, createClient());
    }

    AliyunRealNameVerifier(boolean enabled, String appCode, RestTemplate client)
    {
        this.enabled = enabled;
        this.appCode = appCode == null ? "" : appCode.trim();
        this.client = client;
        if (enabled && this.appCode.isEmpty())
        {
            throw new IllegalStateException("ALIYUN_REAL_NAME_APPCODE is required when real-name verification is enabled");
        }
    }

    private static RestTemplate createClient()
    {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    /** null 表示匹配；明确不匹配返回平台文案，其他结果抛出异常，绝不默认通过。 */
    public String verify(String realName, String idNumber)
    {
        if (!enabled) throw unavailable();
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "APPCODE " + appCode);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("name", realName);
        form.add("idNo", idNumber);
        try
        {
            var response = client.postForEntity(ENDPOINT, new HttpEntity<>(form, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) throw unavailable();
            JsonNode result = json.readTree(response.getBody());
            String code = result == null ? "" : result.path("respCode").asText("");
            return switch (code)
            {
                case "0000" -> null;
                case "0007" -> "身份证号码不存在，请核对后重新提交";
                case "0008" -> "姓名与身份证号码不匹配，请核对后重新提交";
                case "0001", "0002", "0003", "0004" ->
                    throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "姓名或身份证号码有误，请核对后重新提交");
                default -> throw unavailable();
            };
        }
        catch (RestClientException | IOException e)
        {
            // 异常可能携带响应中的姓名、证件号或凭据，不透传、不记录异常内容。
            throw unavailable();
        }
    }

    private static AppAuthException unavailable()
    {
        return new AppAuthException(AppAuthErrorCodes.SYSTEM_ERROR, 503,
                "实名认证服务暂不可用，请稍后再试");
    }
}
