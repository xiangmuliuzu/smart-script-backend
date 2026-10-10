package com.smartscript.platform.content.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.content.exception.AiServiceUnavailableException;

/**
 * 外部 AI 服务客户端（B 模块 2.9.10 / 2.9.11 / 2.9.13 转发）。
 *
 * 依据：接口文档中 AI 写作 / 润色 / 大纲的「实际URL」指向本机 :3000 的 AI 服务
 * （{@code /api/ai/write}、{@code /api/ai/polish}、{@code /api/ai/outline}）。
 * 本类把 Java 后端作为代理转发到该服务，基址由 {@code AI_SERVICE_URL} 配置（默认 http://127.0.0.1:3000）。
 *
 * 反推处理点：
 * 1. 该项目此前无任何 HTTP 客户端依赖，故在内容模块内直接 new RestTemplate（spring-web 已随框架引入），
 *    并设置连接/读取超时：连接 3s、读取 60s（AI 生成耗时较长）。
 * 2. 统一解析 {code, message, data} 信封：HTTP 非 2xx、code != 200 或 data 缺失，
 *    一律抛 {@link AiServiceUnavailableException}，**绝不返回编造内容**，由控制器转 503。
 * 3. 入参 body 使用文档字段名（snake_case work_id 由服务层已转 camelCase 的场景另行处理），
 *    本类只负责透传已组装好的载荷。
 *
 * @author xiangsipeng
 */
@Component
public class AiServiceClient
{
    /** AI 服务信封中代表成功的业务码 */
    private static final int SUCCESS_CODE = 200;

    private final String baseUrl;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiServiceClient(@Value("${AI_SERVICE_URL:http://127.0.0.1:3000}") String baseUrl)
    {
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * AI 写作转发（2.9.10）
     *
     * @param prompt 提示词
     * @param type   写作类型（可空）
     * @return AI 响应信封的 data 节点
     * @throws AiServiceUnavailableException AI 服务不可用或返回异常
     */
    public JsonNode write(String prompt, String type)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", prompt);
        body.put("type", type);
        return invoke("/api/ai/write", body);
    }

    /**
     * AI 润色转发（2.9.11）
     *
     * @param content 待润色内容
     * @param style   润色风格（可空）
     * @return AI 响应信封的 data 节点
     * @throws AiServiceUnavailableException AI 服务不可用或返回异常
     */
    public JsonNode polish(String content, String style)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("style", style);
        return invoke("/api/ai/polish", body);
    }

    /**
     * AI 大纲生成转发（2.9.13）
     *
     * @param inspiration 灵感（可空）
     * @param genre       题材（可空）
     * @param style       风格（可空）
     * @return AI 响应信封的 data 节点
     * @throws AiServiceUnavailableException AI 服务不可用或返回异常
     */
    public JsonNode outline(String inspiration, String genre, String style)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inspiration", inspiration);
        body.put("genre", genre);
        body.put("style", style);
        return invoke("/api/ai/outline", body);
    }

    /**
     * 统一 POST 转发并解析信封，返回 data 节点。
     */
    private JsonNode invoke(String path, Map<String, Object> body)
    {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try
        {
            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + path, entity, String.class);
            String raw = response.getBody();
            if (raw == null || raw.isBlank())
            {
                throw new AiServiceUnavailableException("AI 服务返回为空，请稍后重试");
            }
            JsonNode root = objectMapper.readTree(raw);
            if (!response.getStatusCode().is2xxSuccessful() || root.path("code").asInt(SUCCESS_CODE) != SUCCESS_CODE)
            {
                throw new AiServiceUnavailableException("AI 服务返回异常，请稍后重试");
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull())
            {
                throw new AiServiceUnavailableException("AI 服务返回数据为空，请稍后重试");
            }
            return data;
        }
        catch (AiServiceUnavailableException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            // 连接被拒 / 超时 / 解析失败等：统一收敛为「服务不可用」，不透传底层细节
            throw new AiServiceUnavailableException("AI 服务不可用，请稍后重试");
        }
    }
}