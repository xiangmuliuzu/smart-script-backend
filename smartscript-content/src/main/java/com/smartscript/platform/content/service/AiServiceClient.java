package com.smartscript.platform.content.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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

    /** 流式调用专用 HTTP 客户端（JDK 内置，支持 SSE 逐块读取）。 */
    private final HttpClient streamClient;

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
        this.streamClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
    }

    /**
     * AI 写作：直接产出小说正文。
     *
     * @param skillContent 导演(skill) 指令（可选；命中时追加到 system prompt）
     * @return 结果对象：content=正文全文，totalTokens=本次消耗 token（usage.total_tokens），model=实际模型
     */
    public AiCallResult write(String prompt, String type, String skillContent)
    {
        String typeHint = (type == null || type.isBlank()) ? "" : "\n写作类型：" + type;
        String system = "你是一名资深网络小说写手，擅长创作节奏紧凑、画面感强、对话自然的小说正文。"
                + "请根据用户的要求直接创作小说正文，不要解释、不要标题、不要任何前后缀。";
        return chat(appendSkill(system, skillContent), prompt + typeHint);
    }

    /**
     * AI 润色：保持原意，按指定风格优化。
     *
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=润色全文，totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult polish(String content, String style, String skillContent)
    {
        String styleHint = (style == null || style.isBlank()) ? "自然流畅" : style;
        String system = "你是一名资深文字编辑。请润色用户提供的文本，保持原意、人物关系与关键情节，"
                + "提升表达、节奏与画面感，目标风格：" + styleHint + "。"
                + "直接输出润色后的正文，不要解释、不要标注修改说明。";
        return chat(appendSkill(system, skillContent), content);
    }

    /**
     * AI 剧本（原"大纲"）：面向完全不懂创作的新手，生成结构完整的剧本大纲。
     *
     * 定位：让没有创作经验的人也能得到一份「可直接展开成剧本」的完整大纲，
     * 后续可继续走 AI 分镜做成可直接出售的分镜剧本。
     *
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=模型返回的原始大纲文本（JSON 或兜底文本），
     *                   totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult outline(String inspiration, String genre, String style, String skillContent)
    {
        String genreHint = (genre == null || genre.isBlank()) ? "不限" : genre;
        String styleHint = (style == null || style.isBlank()) ? "不限" : style;
        String insp = (inspiration == null || inspiration.isBlank()) ? "" : inspiration;
        String system = "你是一名资深网文策划编辑，擅长帮完全不懂创作的普通人快速产出专业级剧本大纲。"
                + "请根据用户给出的灵感、题材与风格，生成一份结构完整、可直接展开成剧本的小说大纲；"
                + "要求用通俗易懂的语言，每个分幕写清推进逻辑与关键冲突，让新手照着写也能写成不错的故事。"
                + "必须只输出一个合法 JSON 对象，不要输出任何解释，也不要使用 Markdown 代码块。"
                + "JSON 结构如下："
                + "{\"title\":\"作品名\",\"logline\":\"一句话故事\","
                + "\"characters\":[{\"name\":\"角色名\",\"role\":\"身份或定位\"}],"
                + "\"acts\":[{\"name\":\"第一幕\",\"summary\":\"本幕概述\",\"beats\":[\"关键情节1\",\"关键情节2\"]}]}";
        String user = "故事灵感：" + insp + "\n题材：" + genreHint + "\n风格：" + styleHint;
        return chat(appendSkill(system, skillContent), user);
    }

    /**
     * AI 分镜：把剧本文本拆解为可直接用于拍摄/出售的分镜脚本。
     *
     * 输出为中文可读文本，按场景分镜：每个镜头给出 镜号/景别/运镜/时长/画面内容/台词/音效配乐/转场。
     * 分镜输出规范与「AI Skill 配置」内置 seed skill（悬疑/甜宠导演）中的分镜章节保持一致，
     * 供后续接入视频制作或直接作为成品分镜剧本出售。
     *
     * @param script       剧本文本（必填；可以是 AI 剧本产出）
     * @param style        分镜风格（可选：电影感/短剧竖屏/动漫风等）
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=分镜脚本全文，totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult storyboard(String script, String style, String skillContent)
    {
        String styleHint = (style == null || style.isBlank()) ? "电影感" : style;
        String system = "你是一名资深影视分镜师，擅长把剧本转化为可直接用于拍摄或出售的专业分镜脚本。"
                + "请根据用户提供的剧本文本，按场景拆解为完整分镜；每场戏开头写清场次与场景地点，"
                + "每个镜头依次给出：镜号、景别、运镜、时长、画面内容、台词、音效/配乐、转场。"
                + "画面内容要具体到人物动作、表情与构图；台词直接引用或精简改写剧本对白；"
                + "整体风格：" + styleHint + "。直接用中文输出分镜正文，不要解释、不要前后缀。";
        String user = "剧本文本：\n" + script;
        return chat(appendSkill(system, skillContent), user);
    }

    // ================================================================
    // 流式创作（SSE）：逐块回调内容增量，供 App/PC 端打字机式展示。
    // 返回完整内容与本次 token 消耗（调用方用于落库/按 token 扣积分）；中途失败抛异常，绝不编造。
    // ================================================================

    /**
     * AI 写作（流式）。[onDelta] 每收到一个内容增量回调一次（含空格/换行）。
     *
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=完整正文，totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult writeStream(String prompt, String type, String skillContent, Consumer<String> onDelta)
    {
        String typeHint = (type == null || type.isBlank()) ? "" : "\n写作类型：" + type;
        String system = "你是一名资深网络小说写手，擅长创作节奏紧凑、画面感强、对话自然的小说正文。"
                + "请根据用户的要求直接创作小说正文，不要解释、不要标题、不要任何前后缀。";
        return streamChat(appendSkill(system, skillContent), prompt + typeHint, onDelta);
    }

    /**
     * AI 润色（流式）。[onDelta] 每收到一个内容增量回调一次。
     *
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=完整润色结果，totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult polishStream(String content, String style, String skillContent, Consumer<String> onDelta)
    {
        String styleHint = (style == null || style.isBlank()) ? "自然流畅" : style;
        String system = "你是一名资深文字编辑。请润色用户提供的文本，保持原意、人物关系与关键情节，"
                + "提升表达、节奏与画面感，目标风格：" + styleHint + "。"
                + "直接输出润色后的正文，不要解释、不要标注修改说明。";
        return streamChat(appendSkill(system, skillContent), content, onDelta);
    }

    /**
     * AI 剧本（原"大纲"，流式）。[onDelta] 每收到一个内容增量回调一次（可读剧本大纲文本）。
     *
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=完整可读剧本大纲文本，totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult outlineStream(String inspiration, String genre, String style, String skillContent, Consumer<String> onDelta)
    {
        String genreHint = (genre == null || genre.isBlank()) ? "不限" : genre;
        String styleHint = (style == null || style.isBlank()) ? "不限" : style;
        String insp = (inspiration == null || inspiration.isBlank()) ? "" : inspiration;
        String system = "你是一名资深网文策划编辑，擅长帮完全不懂创作的普通人快速产出专业级剧本大纲。"
                + "请根据故事灵感、题材与风格生成小说剧本大纲，依次包含书名、一句话简介、角色、分幕剧情和关键情节，"
                + "用通俗易懂的语言，让新手照着写也能写成不错的故事。以中文标题分段输出可读文本。"
                + "直接从书名开始，不要解释，不要 JSON，不要 Markdown 代码块。";
        String user = "故事灵感：" + insp + "\n题材：" + genreHint + "\n风格：" + styleHint;
        return streamChat(appendSkill(system, skillContent), user, onDelta);
    }

    /**
     * AI 分镜（流式）。[onDelta] 每收到一个内容增量回调一次（可读分镜文本）。
     *
     * @param skillContent 导演(skill) 指令（可选）
     * @return 结果对象：content=完整分镜脚本，totalTokens=本次消耗 token，model=实际模型
     */
    public AiCallResult storyboardStream(String script, String style, String skillContent, Consumer<String> onDelta)
    {
        String styleHint = (style == null || style.isBlank()) ? "电影感" : style;
        String system = "你是一名资深影视分镜师，擅长把剧本转化为可直接用于拍摄或出售的专业分镜脚本。"
                + "请根据用户提供的剧本文本，按场景拆解为完整分镜；每场戏开头写清场次与场景地点，"
                + "每个镜头依次给出：镜号、景别、运镜、时长、画面内容、台词、音效/配乐、转场。"
                + "画面内容要具体到人物动作、表情与构图；台词直接引用或精简改写剧本对白；"
                + "整体风格：" + styleHint + "。直接用中文输出分镜正文，不要解释、不要前后缀。";
        String user = "剧本文本：\n" + script;
        return streamChat(appendSkill(system, skillContent), user, onDelta);
    }

    /** 导演(skill) 指令追加到 system prompt 末尾（skill 内容非空时生效）。 */
    private static String appendSkill(String system, String skillContent)
    {
        if (skillContent == null || skillContent.isBlank())
        {
            return system;
        }
        return system + "\n\n【导演 skill 指令（最高优先级，必须严格遵守）】\n" + skillContent.trim();
    }

    /**
     * 流式调用 Chat Completions（stream=true），逐块解析 SSE 的 delta.content 并回调 [onDelta]。
     *
     * SSE 约定：每行 {@code data: {...}}，choices[0].delta.content 为增量；{@code data: [DONE]} 结束。
     * 逐行累积完整内容，结束时整体返回；DeepSeek 在最后一个 data 块携带 usage（total_tokens），
     * 逐块捕获，作为本次调用的精确 token 消耗用于按 token 扣积分。
     * 非 2xx / 无有效内容 / 网络异常抛 {@link AiServiceUnavailableException}。
     */
    private AiCallResult streamChat(String system, String user, Consumer<String> onDelta)
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
        body.put("stream", true);
        try
        {
            String payload = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(java.time.Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = streamClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
            {
                throw new AiServiceUnavailableException("AI 服务不可用，请稍后重试 (HTTP " + response.statusCode() + ")");
            }
            StringBuilder full = new StringBuilder();
            boolean completed = false;
            String errorMessage = "AI 未返回有效内容，请稍后重试";
            int totalTokens = 0;
            String modelName = model;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.isBlank())
                    {
                        continue;
                    }
                    if (!line.startsWith("data:"))
                    {
                        continue; // 忽略注释/事件行
                    }
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]"))
                    {
                        completed = true;
                        break;
                    }
                    int usage = parseSseUsage(data);
                    if (usage > 0)
                    {
                        totalTokens = usage; // 最后一个带 usage 的块为准（DeepSeek 流式最后一条）
                    }
                    String m = parseSseModel(data);
                    if (m != null && !m.isBlank())
                    {
                        modelName = m;
                    }
                    String delta = parseSseDelta(data);
                    if (delta != null && !delta.isEmpty())
                    {
                        full.append(delta);
                        onDelta.accept(delta);
                    }
                    if (isSseFinish(data))
                    {
                        completed = true;
                        break;
                    }
                }
            }
            String content = full.toString().trim();
            if (!completed)
            {
                throw new AiServiceUnavailableException("AI 生成中断，请稍后重试");
            }
            if (content.isEmpty())
            {
                throw new AiServiceUnavailableException(errorMessage);
            }
            return new AiCallResult(content, totalTokens, modelName);
        }
        catch (AiServiceUnavailableException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("AI stream chat call failed: {}", e.toString(), e);
            throw new AiServiceUnavailableException("AI 服务不可用，请稍后重试");
        }
    }

    /**
     * 解析单条 SSE data（不含 data: 前缀）的 delta.content。
     * 无法解析的块返回 null（调用方跳过）；无增量内容返回空串。
     */
    static String parseSseDelta(String data)
    {
        if (data == null || data.isEmpty() || data.equals("[DONE]"))
        {
            return "";
        }
        try
        {
            JsonNode root = new ObjectMapper().readTree(data);
            String delta = root.path("choices").path(0).path("delta").path("content").asText(null);
            return delta == null ? "" : delta;
        }
        catch (Exception e)
        {
            return null; // 无法解析的中间块，调用方跳过
        }
    }

    /** 是否 SSE 终止块（finish_reason=stop/length）。 */
    static boolean isSseFinish(String data)
    {
        if (data == null || data.isEmpty())
        {
            return false;
        }
        try
        {
            JsonNode root = new ObjectMapper().readTree(data);
            String reason = root.path("choices").path(0).path("finish_reason").asText("");
            return "stop".equals(reason) || "length".equals(reason);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * 从单条 SSE data 解析本次调用的 usage.total_tokens（DeepSeek 流式在最后一个 data 块携带 usage）。
     * 无 usage / 无法解析返回 0。
     */
    static int parseSseUsage(String data)
    {
        if (data == null || data.isEmpty() || data.equals("[DONE]"))
        {
            return 0;
        }
        try
        {
            JsonNode root = new ObjectMapper().readTree(data);
            int total = root.path("usage").path("total_tokens").asInt(0);
            return total > 0 ? total : 0;
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    /** 从单条 SSE data 解析模型名；缺失/无法解析返回 null。 */
    static String parseSseModel(String data)
    {
        if (data == null || data.isEmpty() || data.equals("[DONE]"))
        {
            return null;
        }
        try
        {
            JsonNode root = new ObjectMapper().readTree(data);
            String m = root.path("model").asText(null);
            return (m == null || m.isBlank()) ? null : m;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 调用 Chat Completions（非流式），返回完整内容与本次 token 消耗。
     */
    private AiCallResult chat(String system, String user)
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
            int totalTokens = root.path("usage").path("total_tokens").asInt(0);
            String modelName = root.path("model").asText(model);
            return new AiCallResult(content.trim(), totalTokens, modelName);
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
