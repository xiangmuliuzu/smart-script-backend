package com.smartscript.platform.content.controller.app;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
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
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, e.getMessage());
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
     * {@code event:error} 携带 code/message；次数不足同样以错误事件返回。
     *
     * @param body 请求体 {prompt 必填, type 可选, work_id 可选}
     */
    @PostMapping(value = "/write/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> writeStream(
            @RequestBody(required = false) Map<String, Object> body, HttpServletRequest servletRequest)
    {
        String prompt = trimToNull(asString(body, "prompt"));
        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt(prompt);
        request.setType(trimToNull(asString(body, "type")));
        request.setWorkId(asLong(body, "work_id", "workId"));
        return streamResponse(servletRequest, delta -> {
            if (prompt == null) throw new IllegalArgumentException("prompt 不能为空");
            AppAiWriteResult result = aiWriteService.writeStream(request, delta);
            return "{\"done\":true,\"recordId\":" + result.getRecordId() + "}";
        });
    }

    @PostMapping(value = "/polish/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> polishStream(
            @RequestBody(required = false) Map<String, Object> body, HttpServletRequest servletRequest)
    {
        String content = trimToNull(asString(body, "content"));
        AppAiPolishRequest request = new AppAiPolishRequest();
        request.setContent(content);
        request.setStyle(trimToNull(asString(body, "style")));
        return streamResponse(servletRequest, delta -> {
            if (content == null) throw new IllegalArgumentException("content 不能为空");
            AppAiWriteResult result = aiWriteService.polishStream(request, delta);
            return "{\"done\":true,\"recordId\":" + result.getRecordId() + "}";
        });
    }

    @PostMapping(value = "/outline/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> outlineStream(
            @RequestBody(required = false) Map<String, Object> body, HttpServletRequest servletRequest)
    {
        AppAiOutlineRequest request = new AppAiOutlineRequest();
        request.setInspiration(trimToNull(asString(body, "inspiration")));
        request.setGenre(trimToNull(asString(body, "genre")));
        request.setStyle(trimToNull(asString(body, "style")));
        request.setWorkId(asLong(body, "work_id", "workId"));
        return streamResponse(servletRequest, delta -> {
            AppAiOutlineResult result = aiWriteService.outlineStream(request, delta);
            return "{\"done\":true,\"quotaUsed\":" + result.getQuotaUsed() + ",\"requestNo\":"
                    + jsonEscape(result.getRequestNo() == null ? "" : result.getRequestNo()) + "}";
        });
    }

    /** MVC 在异步任务中执行生成，并传递请求和安全上下文；每个事件立即 flush。 */
    private ResponseEntity<StreamingResponseBody> streamResponse(HttpServletRequest request,
            Function<Consumer<String>, String> generate)
    {
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(output -> {
                    request.getAsyncContext().setTimeout(120_000L);
                    output.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    try
                    {
                        String done = generate.apply(delta -> sendEvent(output, "message",
                                "{\"delta\":" + jsonEscape(delta) + "}"));
                        sendEvent(output, "message", done);
                    }
                    catch (AiQuotaInsufficientException | IllegalArgumentException e)
                    {
                        sendError(output, CODE_BAD_REQUEST, e.getMessage());
                    }
                    catch (AiServiceUnavailableException e)
                    {
                        sendError(output, CODE_SERVICE_UNAVAILABLE, e.getMessage());
                    }
                    catch (UncheckedIOException e) { throw e.getCause(); }
                    catch (Exception e)
                    {
                        sendError(output, CODE_SERVICE_UNAVAILABLE, "AI 服务不可用，请稍后重试");
                    }
                });
    }

    private static void sendError(OutputStream output, int code, String message)
    {
        sendEvent(output, "error", "{\"code\":" + code + ",\"message\":" + jsonEscape(message) + "}");
    }

    private static void sendEvent(OutputStream output, String event, String json)
    {
        try
        {
            output.write(("event:" + event + "\ndata:" + json + "\n\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
        catch (IOException e) { throw new UncheckedIOException(e); }
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
