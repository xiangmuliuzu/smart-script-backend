package com.smartscript.platform.user.integration;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
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
    private static final Logger log = LoggerFactory.getLogger(AliyunRealNameVerifier.class);

    /** 云市场接口地址；默认值只在未配置 app.real-name.aliyun.endpoint 时生效。 */
    static final String ENDPOINT = "https://idenauthen.market.alicloudapi.com/idenAuthentication";

    private final boolean enabled;
    private final String appCode;
    private final String endpoint;
    private final RestTemplate client;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public AliyunRealNameVerifier(
            @Value("${app.real-name.aliyun.enabled:false}") boolean enabled,
            @Value("${app.real-name.aliyun.app-code:}") String appCode,
            @Value("${app.real-name.aliyun.endpoint:}") String endpoint)
    {
        this(enabled, appCode, endpoint, createClient());
    }

    AliyunRealNameVerifier(boolean enabled, String appCode, RestTemplate client)
    {
        this(enabled, appCode, ENDPOINT, client);
    }

    AliyunRealNameVerifier(boolean enabled, String appCode, String endpoint, RestTemplate client)
    {
        this.enabled = enabled;
        this.appCode = appCode == null ? "" : appCode.trim();
        this.endpoint = endpoint == null || endpoint.isBlank() ? ENDPOINT : endpoint.trim();
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
            var response = client.postForEntity(endpoint, new HttpEntity<>(form, headers), String.class);
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
                default ->
                {
                    // 只记录响应码，不记录姓名、证件号与响应正文
                    log.warn("real-name verification returned unexpected respCode={}", code);
                    throw unavailable();
                }
            };
        }
        catch (HttpStatusCodeException e)
        {
            // 云市场用 401/403 表达 APPCODE 无效或套餐次数用尽，需要人工处理，重试没有意义
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403)
            {
                log.warn("real-name verification rejected by gateway: status={} (appcode invalid or quota exhausted)", status);
                throw new AppAuthException(AppAuthErrorCodes.SYSTEM_ERROR, 503,
                        "实名认证额度不足或密钥无效，请联系管理员");
            }
            log.warn("real-name verification gateway error: status={}", status);
            throw unavailable();
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
