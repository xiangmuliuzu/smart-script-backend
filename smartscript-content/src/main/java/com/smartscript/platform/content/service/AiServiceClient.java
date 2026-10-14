package com.smartscript.platform.content.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartscript.platform.content.exception.AiServiceUnavailableException;

/**
 * AI 大模型客户端（App 端 AI 大纲 / 写作 / 润色）。
 *
 * 直接调用 OpenAI 兼容的 Chat Completions 接口（默认 DeepSeek：https://api.deepseek.com），
 * 由 Java 后端持有密钥并转发，App 端不接触密钥。
 *
 * 配置（均来自环境变量 / 本机私有 .env.local，禁止硬编码或提交密钥）：
 *   AI_BASE_URL      大模型基址（默认 https://api.deepseek.com）
 *   AI_API_KEY       接口密钥（必填，缺失则创作接口返回 503）
 *   AI_SERVICE_MODEL 模型名（默认 deepseek-flash）
 *
 * 失败处理：网络异常、HTTP 非 2xx、content 为空，一律抛
 * {@link AiServiceUnavailableException}，由控制器转 503，**绝不返回编造内容**。
 */
@Component
public class AiServiceClient
{
    private final String baseUrl;

    private final String apiKey;

    private final String model;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiServiceClient.class);

    public AiServiceClient(
            @Value("${AI_BASE_URL:https://api.deepseek.com}") String baseUrl,
            @Value("${AI_API_KEY:}") String apiKey,
            @Value("${AI_SERVICE_MODEL:deepseek-flash}") String model)
    {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(90000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * AI 写作：直接产出小说正文。
     */
    public JsonNode write(String prompt, String type)
    {
        String typeHint = (type == null || type.isBlank()) ? "" : "\n写作类型：" + type;
        String system = "你是一名资深网络小说写手，擅长创作节奏紧凑、画面感强、对话自然的小说正文。"
                + "请根据用户的要求直接创作小说正文，不要解释、不要标题、不要任何前后缀。";
        String content = chat(system, prompt + typeHint);
        ObjectNode out = objectMapper.createObjectNode();
        out.put("content", content);
        out.put("model", model);
        return out;
    }

    /**
     * AI 润色：保持原意，按指定风格优化。
     */
    public JsonNode polish(String content, String style)
    {
        String styleHint = (style == null || style.isBlank()) ? "自然流畅" : style;
        String system = "你是一名资深文字编辑。请润色用户提供的文本，保持原意、人物关系与关键情节，"
                + "提升表达、节奏与画面感，目标风格：" + styleHint + "。"
                + "直接输出润色后的正文，不要解释、不要标注修改说明。";
        String result = chat(system, content);
        ObjectNode out = objectMapper.createObjectNode();
        out.put("content", result);
        out.put("model", model);
        return out;
    }

    /**
     * AI 大纲：生成结构化大纲 JSON。
     */
    public JsonNode outline(String inspiration, String genre, String style)
    {
        String genreHint = (genre == null || genre.isBlank()) ? "不限" : genre;
        String styleHint = (style == null || style.isBlank()) ? "不限" : style;
        String insp = (inspiration == null || inspiration.isBlank()) ? "" : inspiration;
        String system = "你是一名资深网文策划编辑。请根据用户的故事灵感、题材与风格，生成一份结构化的小说大纲。"
                + "必须只输出一个合法 JSON 对象，不要输出任何解释，也不要使用 Markdown 代码块。"
                + "JSON 结构如下："
                + "{\"title\":\"作品名\",\"logline\":\"一句话故事\","
                + "\"characters\":[{\"name\":\"角色名\",\"role\":\"身份或定位\"}],"
                + "\"acts\":[{\"name\":\"第一幕\",\"summary\":\"本幕概述\",\"beats\":[\"关键情节1\",\"关键情节2\"]}]}";
        String user = "故事灵感：" + insp + "\n题材：" + genreHint + "\n风格：" + styleHint;
        String raw = chat(system, user);
        ObjectNode out = objectMapper.createObjectNode();
        out.set("outline", parseJson(raw));
        out.put("model", model);
        return out;
    }

    /**
     * 调用 Chat Completions，返回 message.content。
     */
    private String chat(String system, String user)
    {
        if (apiKey.isBlank())
        {
            throw new AiServiceUnavailableException("AI 服务未配置，请联系管理员");
        }
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", system));
        messages.add(message("user", user));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 4096);
        body.put("temperature", 0.8);
        body.put("stream", false);
        try
        {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/v1/chat/completions",
                    new HttpEntity<>(body, headers), String.class);
            String raw = response.getBody();
            if (raw == null || raw.isBlank())
            {
                throw new AiServiceUnavailableException("AI 返回为空，请稍后重试");
            }
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content == null || content.isBlank())
            {
                throw new AiServiceUnavailableException("AI 未返回有效内容，请稍后重试");
            }
            return content.trim();
        }
        catch (AiServiceUnavailableException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("AI chat call failed: {}", e.toString(), e);
            throw new AiServiceUnavailableException("AI 服务不可用，请稍后重试");
        }
    }

    /**
     * 解析模型返回的 JSON；去除可能的 Markdown 代码块包裹。
     * 若仍不是合法 JSON，兜底把整段文本放入 text，避免编造结构。
     */
    JsonNode parseJson(String raw)
    {
        String s = raw.trim();
        if (s.startsWith("```"))
        {
            int newline = s.indexOf('\n');
            if (newline >= 0)
            {
                s = s.substring(newline + 1);
            }
            if (s.endsWith("```"))
            {
                s = s.substring(0, s.length() - 3);
            }
            s = s.trim();
        }
        try
        {
            return objectMapper.readTree(s);
        }
        catch (Exception e)
        {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("text", raw);
            return fallback;
        }
    }

    private static Map<String, String> message(String role, String content)
    {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static String trimTrailingSlash(String url)
    {
        if (url == null)
        {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
