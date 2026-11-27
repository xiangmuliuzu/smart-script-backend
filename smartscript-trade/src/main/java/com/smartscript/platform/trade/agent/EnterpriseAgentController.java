package com.smartscript.platform.trade.agent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.trade.agent.EnterpriseAgentSessions.AgentException;
import com.smartscript.platform.trade.agent.EnterpriseAgentSessions.MessageViewDto;
import com.smartscript.platform.trade.agent.EnterpriseAgentSessions.SessionDto;

@RestController
@RequestMapping("/api/v1/trade/agent")
public class EnterpriseAgentController
{
    private final EnterpriseAgentSessions sessions;
    private final EnterpriseAgentOrchestrator orchestrator;
    private final ObjectMapper json = new ObjectMapper();

    public EnterpriseAgentController(EnterpriseAgentSessions sessions, EnterpriseAgentOrchestrator orchestrator)
    {
        this.sessions = sessions;
        this.orchestrator = orchestrator;
    }

    @PostMapping("/sessions")
    public AppApiResponse<SessionDto> create(Authentication auth, @RequestBody(required = false) CreateSession body)
    {
        return AppApiResponse.ok(sessions.create(userId(auth), body == null ? null : body.title()));
    }

    @GetMapping("/sessions")
    public AppApiResponse<List<SessionDto>> list(Authentication auth)
    {
        return AppApiResponse.ok(sessions.list(userId(auth)));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public AppApiResponse<List<MessageViewDto>> messages(Authentication auth, @PathVariable long sessionId)
    {
        return AppApiResponse.ok(sessions.messages(userId(auth), sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public AppApiResponse<Void> delete(Authentication auth, @PathVariable long sessionId)
    {
        sessions.delete(userId(auth), sessionId);
        return AppApiResponse.ok();
    }

    @PostMapping(value = "/sessions/{sessionId}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chat(Authentication auth, @PathVariable long sessionId,
            @RequestBody ChatRequest request)
    {
        long currentUser = userId(auth);
        sessions.require(currentUser, sessionId);
        String message = request.message() == null ? "" : request.message().strip();
        if (message.isEmpty() || message.codePointCount(0, message.length()) > 4000)
        {
            throw new AgentException(400, "请输入 1 到 4000 字的采购诉求");
        }
        String model = request.model() == null || request.model().isBlank() ? null : request.model().strip();
        if (model != null && !model.matches("[\\w./:-]{1,120}"))
        {
            throw new AgentException(400, "模型名称无效");
        }
        StreamingResponseBody body = output -> orchestrator.runTurn(currentUser, sessionId, message, model,
            (event, data) -> {
                String frame = "event: " + event + "\ndata: " + json.writeValueAsString(data) + "\n\n";
                output.write(frame.getBytes(StandardCharsets.UTF_8));
                output.flush();
            });
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8))
            .cacheControl(CacheControl.noStore())
            .header("X-Accel-Buffering", "no")
            .body(body);
    }

    @ExceptionHandler(AgentException.class)
    public ResponseEntity<AppApiResponse<Void>> handle(AgentException e)
    {
        return ResponseEntity.status(e.code()).body(AppApiResponse.fail(e.code(), e.getMessage()));
    }

    private static long userId(Authentication auth)
    {
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Long id)
        {
            return id;
        }
        throw new AgentException(401, "请先登录");
    }

    public record CreateSession(String title) {}

    public record ChatRequest(String message, String model) {}
}
