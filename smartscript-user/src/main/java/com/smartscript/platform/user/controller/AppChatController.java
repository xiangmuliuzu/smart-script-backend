package com.smartscript.platform.user.controller;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.dto.ChatMessageSendRequest;
import com.smartscript.platform.user.dto.ChatSessionCreateRequest;
import com.smartscript.platform.user.dto.PageResult;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.security.AppIdentityContext;
import com.smartscript.platform.user.service.ChatService;

/**
 * A3 用户端聊天接口（App 凭证域鉴权）。
 *
 * 路由前缀 /api/v1/users/me/chat，所有操作以当前登录 App 用户身份执行。
 */
@RestController
@RequestMapping("/api/v1/users/me/chat")
public class AppChatController
{
    private final ChatService chatService;

    public AppChatController(ChatService chatService)
    {
        this.chatService = chatService;
    }

    /** 创建或获取会话（幂等） */
    @PostMapping("/sessions")
    public AppApiResponse<Map<String, Object>> createSession(@RequestBody ChatSessionCreateRequest req)
    {
        return AppApiResponse.ok(chatService.createOrGetSession(currentUserId(), req));
    }

    /** 我的会话列表 */
    @GetMapping("/sessions")
    public AppApiResponse<PageResult<Map<String, Object>>> listSessions(
            @RequestParam(required = false) Integer status)
    {
        return AppApiResponse.ok(chatService.mySessions(currentUserId(), status));
    }

    /** 会话内历史消息 */
    @GetMapping("/sessions/{sessionId}/messages")
    public AppApiResponse<PageResult<Map<String, Object>>> listMessages(
            @PathVariable Long sessionId)
    {
        return AppApiResponse.ok(chatService.messages(sessionId));
    }

    /** 发送文字消息 */
    @PostMapping("/sessions/{sessionId}/messages")
    public AppApiResponse<Map<String, Object>> sendMessage(
            @PathVariable Long sessionId,
            @RequestBody ChatMessageSendRequest req)
    {
        return AppApiResponse.ok(chatService.sendMessage(sessionId, currentUserId(), req));
    }

    /** 标记会话已读 */
    @PutMapping("/sessions/{sessionId}/read")
    public AppApiResponse<Map<String, Object>> markRead(@PathVariable Long sessionId)
    {
        return AppApiResponse.ok(chatService.markSessionRead(sessionId, currentUserId()));
    }

    /** 会话未读总数 */
    @GetMapping("/unread-count")
    public AppApiResponse<Map<String, Object>> unreadCount()
    {
        return AppApiResponse.ok(chatService.chatUnreadCount(currentUserId()));
    }

    private Long currentUserId()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof AppIdentityContext identity)
        {
            return identity.getUserId();
        }
        throw new AppAuthException(AppAuthErrorCodes.UNAUTHORIZED, 401, "unauthorized");
    }
}
