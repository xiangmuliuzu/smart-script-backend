package com.smartscript.platform.content.controller.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppAiOutlineRequest;
import com.smartscript.platform.content.dto.AppAiOutlineRecordItem;
import com.smartscript.platform.content.dto.AppAiOutlineResult;
import com.smartscript.platform.content.dto.AppAiPolishRequest;
import com.smartscript.platform.content.dto.AppAiWriteRecordItem;
import com.smartscript.platform.content.dto.AppAiWriteRequest;
import com.smartscript.platform.content.dto.AppAiWriteResult;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.exception.AiQuotaInsufficientException;
import com.smartscript.platform.content.exception.AiServiceUnavailableException;
import com.smartscript.platform.content.service.IAiWriteService;

/**
 * App AI 辅助创作（B 模块，接口文档 2.9.10~2.9.13 表2-120 ~ 表2-123）。
 *
 * 鉴权：全部为 App 私有接口，需 App Access Token。四路径均落在
 * {@code /api/v1/content/ai/**}，未登记 App 凭证域白名单，自动走 authenticated，
 * 因此**无需改动白名单**，与书城公开 GET 也不冲突。
 *
 * 路径映射：文档写的是 /api/ai/write、/api/ai/polish、/api/ai/write-records、/api/ai/outline，
 * B 模块既有接口统一收拢在 App 凭证域 /api/v1/content/** 下（见 AppAuthSecurityConfig.APP_PATH_PREFIXES），
 * 故落此前缀：
 *   2.9.10 POST /api/v1/content/ai/write
 *   2.9.11 POST /api/v1/content/ai/polish
 *   2.9.12 GET  /api/v1/content/ai/write-records
 *   2.9.13 POST /api/v1/content/ai/outline
 *
 * 降级策略：生成内容一律来自外部 AI 服务，服务不可用时返回 **HTTP 200 + code 503 + 明确文案**，
 * 与本模块其它接口统一走 App 信封，**绝不返回编造内容**。
 *
 * 字段口径：入参兼容文档的 snake_case（work_id）与模块内既有 camelCase（workId）两种写法；
 * 出参沿用 B 模块 App 契约的 camelCase（content / recordId / requestNo / quotaUsed）。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content/ai")
public class AppAiController
{
    /** 入参非法 */
    private static final int CODE_BAD_REQUEST = 400;

    /** AI 服务不可用 */
    private static final int CODE_SERVICE_UNAVAILABLE = 503;

    private final IAiWriteService aiWriteService;

    public AppAiController(IAiWriteService aiWriteService)
    {
        this.aiWriteService = aiWriteService;
    }

    /**
     * AI 写作（2.9.10）
     *
     * @param body 请求体 {prompt 必填, type 可选, work_id 可选}
     * @return data = {content, recordId}；prompt 为空 400；AI 服务不可用 503
     */
    @PostMapping("/write")
    public AppApiResponse<AppAiWriteResult> write(@RequestBody(required = false) Map<String, Object> body)
    {
        String prompt = trimToNull(asString(body, "prompt"));
        if (prompt == null)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "prompt 不能为空");
        }
        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt(prompt);
        request.setType(trimToNull(asString(body, "type")));
        request.setWorkId(asLong(body, "work_id", "workId"));
        try
        {
            return AppApiResponse.ok(aiWriteService.write(request));
        }
        catch (AiQuotaInsufficientException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (AiServiceUnavailableException e)
        {
            return AppApiResponse.fail(CODE_SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    /**
     * AI 润色（2.9.11）
     *
     * @param body 请求体 {content 必填, style 可选}
     * @return data = {content, recordId}；content 为空 400；AI 服务不可用 503
     */
    @PostMapping("/polish")
    public AppApiResponse<AppAiWriteResult> polish(@RequestBody(required = false) Map<String, Object> body)
    {
        String content = trimToNull(asString(body, "content"));
        if (content == null)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "content 不能为空");
        }
        AppAiPolishRequest request = new AppAiPolishRequest();
        request.setContent(content);
        request.setStyle(trimToNull(asString(body, "style")));
        try
        {
            return AppApiResponse.ok(aiWriteService.polish(request));
        }
        catch (AiQuotaInsufficientException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (AiServiceUnavailableException e)
        {
            return AppApiResponse.fail(CODE_SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    /**
     * AI 写作记录（2.9.12，分页）
     *
     * @param page     页码（可选，默认 1）
     * @param pageSize 每页条数（可选，默认 20，上限 50）
     * @return data = {total, list}；仅当前登录身份的记录
     */
    @GetMapping("/write-records")
    public AppApiResponse<AppPageResult<AppAiWriteRecordItem>> writeRecords(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize)
    {
        int pageNum = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        return AppApiResponse.ok(aiWriteService.pageWriteRecords(pageNum, size));
    }

    /**
     * AI 大纲请求记录（分页）
     *
     * @param page     页码（可选，默认 1）
     * @param pageSize 每页条数（可选，默认 20，上限 50）
     * @return data = {total, list}；仅当前登录身份的大纲请求记录
     */
    @GetMapping("/outline-records")
    public AppApiResponse<AppPageResult<AppAiOutlineRecordItem>> outlineRecords(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize)
    {
        int pageNum = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        return AppApiResponse.ok(aiWriteService.pageOutlineRecords(pageNum, size));
    }

    /**
     * AI 写作（流式，SSE 打字机输出）。
     *
     * 协议：text/event-stream，逐块发送 {@code data: {"delta":"..."}}，
     * 结束时发送 {@code data: {"done":true,"recordId":..}}；中途失败发送
     * {@code data: {"error":"..."}}（不落库、不扣次数）。次数不足在握手期
     * 直接返回 App 信封 400，不进入流。
     *
     * @param body 请求体 {prompt 必填, type 可选, work_id 可选}
     */
    @PostMapping(value = "/write/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter writeStream(@RequestBody(required = false) Map<String, Object> body)
    {
        String prompt = trimToNull(asString(body, "prompt"));
        if (prompt == null)
        {
            return errorEmitter(CODE_BAD_REQUEST, "prompt 不能为空");
        }
        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt(prompt);
        request.setType(trimToNull(asString(body, "type")));
        request.setWorkId(asLong(body, "work_id", "workId"));
        SseEmitter emitter = new SseEmitter(120_000L);
        try
        {
            Consumer<String> delta = d -> sendData(emitter, "{\"delta\":" + jsonEscape(d) + "}");
            AppAiWriteResult result = aiWriteService.writeStream(request, delta);
            sendData(emitter, "{\"done\":true,\"recordId\":" + result.getRecordId() + "}");
            emitter.complete();
        }
        catch (AiQuotaInsufficientException e)
        {
            completeWithError(emitter, CODE_BAD_REQUEST, e.getMessage());
        }
        catch (AiServiceUnavailableException e)
        {
            completeWithError(emitter, CODE_SERVICE_UNAVAILABLE, e.getMessage());
        }
        catch (Exception e)
        {
            completeWithError(emitter, CODE_SERVICE_UNAVAILABLE, "AI 服务不可用，请稍后重试");
        }
        return emitter;
    }

    /**
     * AI 润色（流式，SSE 打字机输出）。
     *
     * @param body 请求体 {content 必填, style 可选}
     */
    @PostMapping(value = "/polish/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter polishStream(@RequestBody(required = false) Map<String, Object> body)
    {
        String content = trimToNull(asString(body, "content"));
        if (content == null)
        {
            return errorEmitter(CODE_BAD_REQUEST, "content 不能为空");
        }
        AppAiPolishRequest request = new AppAiPolishRequest();
        request.setContent(content);
        request.setStyle(trimToNull(asString(body, "style")));
        SseEmitter emitter = new SseEmitter(120_000L);
        try
        {
            Consumer<String> delta = d -> sendData(emitter, "{\"delta\":" + jsonEscape(d) + "}");
            AppAiWriteResult result = aiWriteService.polishStream(request, delta);
            sendData(emitter, "{\"done\":true,\"recordId\":" + result.getRecordId() + "}");
            emitter.complete();
        }
        catch (AiQuotaInsufficientException e)
        {
            completeWithError(emitter, CODE_BAD_REQUEST, e.getMessage());
        }
        catch (AiServiceUnavailableException e)
        {
            completeWithError(emitter, CODE_SERVICE_UNAVAILABLE, e.getMessage());
        }
        catch (Exception e)
        {
            completeWithError(emitter, CODE_SERVICE_UNAVAILABLE, "AI 服务不可用，请稍后重试");
        }
        return emitter;
    }

    /**
     * AI 大纲（流式，SSE 打字机输出）。
     *
     * @param body 请求体 {inspiration, genre, style, work_id} 均可选
     */
    @PostMapping(value = "/outline/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter outlineStream(@RequestBody(required = false) Map<String, Object> body)
    {
        AppAiOutlineRequest request = new AppAiOutlineRequest();
        request.setInspiration(trimToNull(asString(body, "inspiration")));
        request.setGenre(trimToNull(asString(body, "genre")));
        request.setStyle(trimToNull(asString(body, "style")));
        request.setWorkId(asLong(body, "work_id", "workId"));
        SseEmitter emitter = new SseEmitter(120_000L);
        try
        {
            Consumer<String> delta = d -> sendData(emitter, "{\"delta\":" + jsonEscape(d) + "}");
            AppAiOutlineResult result = aiWriteService.outlineStream(request, delta);
            sendData(emitter, "{\"done\":true,\"quotaUsed\":" + result.getQuotaUsed() + ",\"requestNo\":"
                    + jsonEscape(result.getRequestNo() == null ? "" : result.getRequestNo()) + "}");
            emitter.complete();
        }
        catch (AiQuotaInsufficientException e)
        {
            completeWithError(emitter, CODE_BAD_REQUEST, e.getMessage());
        }
        catch (AiServiceUnavailableException e)
        {
            completeWithError(emitter, CODE_SERVICE_UNAVAILABLE, e.getMessage());
        }
        catch (Exception e)
        {
            completeWithError(emitter, CODE_SERVICE_UNAVAILABLE, "AI 服务不可用，请稍后重试");
        }
        return emitter;
    }

    /** 直接构造一个只带错误事件的 SseEmitter（握手期参数错误用）。 */
    private static SseEmitter errorEmitter(int code, String message)
    {
        SseEmitter emitter = new SseEmitter(120_000L);
        completeWithError(emitter, code, message);
        return emitter;
    }

    /** 发送 SSE data 块；发送失败时抛运行时异常由上层 catch 收敛。 */
    private static void sendData(SseEmitter emitter, String json)
    {
        try
        {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(json, MediaType.APPLICATION_JSON));
        }
        catch (IOException | IllegalStateException e)
        {
            throw new IllegalStateException("SSE 发送中断", e);
        }
    }

    /** 发送错误事件并关闭流。 */
    private static void completeWithError(SseEmitter emitter, int code, String message)
    {
        try
        {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"code\":" + code + ",\"message\":" + jsonEscape(message) + "}",
                            MediaType.APPLICATION_JSON));
        }
        catch (IOException | IllegalStateException ignored)
        {
            // 客户端已断开，无需再写
        }
        emitter.complete();
    }

    /** JSON 字符串转义（用于把增量文本嵌入 data 块）。 */
    private static String jsonEscape(String value)
    {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            switch (c)
            {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20)
                    {
                        sb.append(String.format("\\u%04x", (int) c));
                    }
                    else
                    {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * AI 大纲生成（2.9.13）
     *
     * @param body 请求体 {inspiration, genre, style, work_id} 均可选
     * @return data = {outline, requestNo, quotaUsed}；AI 服务不可用 503
     */
    @PostMapping("/outline")
    public AppApiResponse<AppAiOutlineResult> outline(@RequestBody(required = false) Map<String, Object> body)
    {
        AppAiOutlineRequest request = new AppAiOutlineRequest();
        request.setInspiration(trimToNull(asString(body, "inspiration")));
        request.setGenre(trimToNull(asString(body, "genre")));
        request.setStyle(trimToNull(asString(body, "style")));
        request.setWorkId(asLong(body, "work_id", "workId"));
        try
        {
            return AppApiResponse.ok(aiWriteService.outline(request));
        }
        catch (AiQuotaInsufficientException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
        }
        catch (AiServiceUnavailableException e)
        {
            return AppApiResponse.fail(CODE_SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    /** 从请求体取字符串值（请求体可为 null）。 */
    private static String asString(Map<String, Object> body, String key)
    {
        if (body == null)
        {
            return null;
        }
        Object raw = body.get(key);
        return raw instanceof String text ? text : (raw == null ? null : String.valueOf(raw));
    }

    /** 从请求体取整数型ID，按 keys 顺序尝试（兼容 snake_case 与 camelCase）。 */
    private static Long asLong(Map<String, Object> body, String... keys)
    {
        if (body == null)
        {
            return null;
        }
        for (String key : keys)
        {
            Object raw = body.get(key);
            if (raw instanceof Number number)
            {
                return number.longValue();
            }
            if (raw instanceof String text && !text.isBlank())
            {
                try
                {
                    return Long.valueOf(text.trim());
                }
                catch (NumberFormatException ignored)
                {
                    return null;
                }
            }
        }
        return null;
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}