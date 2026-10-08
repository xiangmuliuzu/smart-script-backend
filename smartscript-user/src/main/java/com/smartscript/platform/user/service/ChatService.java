package com.smartscript.platform.user.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.domain.ChatMessage;
import com.smartscript.platform.user.domain.ChatSession;
import com.smartscript.platform.user.dto.ChatMessageSendRequest;
import com.smartscript.platform.user.dto.ChatSessionCreateRequest;
import com.smartscript.platform.user.dto.PageResult;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.ChatMessageMapper;
import com.smartscript.platform.user.mapper.ChatSessionMapper;

/**
 * A3 聊天核心服务：会话管理 + 消息收发 + 已读/未读。
 *
 * 约定：
 *   - user1 = App 用户（发起方），user2 = 管理员（接收方）。
 *   - status：0=待处理 1=处理中 2=已结束。
 *   - 创建会话幂等：同一 (user1, user2, businessType, businessId) 只建一条。
 *   - 用户只能查看自己的会话（SQL 层强制）。
 */
@Service
public class ChatService
{
    /** 消息类型：文字 */
    public static final String MSG_TYPE_TEXT = "TEXT";

    /** 会话状态 */
    public static final int STATUS_PENDING    = 0;
    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_CLOSED     = 2;

    /** 默认管理员（暂无分配逻辑时兜底） */
    private static final long DEFAULT_ADMIN_ID = 1L;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    public ChatService(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper)
    {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    /* ========== 任务 2/10：创建会话（幂等） ========== */

    /**
     * 创建或获取已有会话。
     *
     * 唯一键冲突时不报错，直接返回已有会话（任务 10）。
     * userId 为发起方（App 用户），targetAdminId 为接收方（管理员）。
     */
    @Transactional
    public Map<String, Object> createOrGetSession(Long userId, ChatSessionCreateRequest req)
    {
        if (req == null)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "请求体不能为空");
        }
        Long user1Id = userId;
        Long user2Id = req.getTargetAdminId() != null ? req.getTargetAdminId() : DEFAULT_ADMIN_ID;
        String bizType = req.getBusinessType();
        Long bizId = req.getBusinessId();

        // 先查已有
        ChatSession existing = sessionMapper.selectByBusiness(user1Id, user2Id, bizType, bizId);
        if (existing != null)
        {
            return toSessionVo(existing, userId);
        }
        // 新建
        ChatSession session = new ChatSession();
        session.setSessionType("CHAT");
        session.setUser1Id(user1Id);
        session.setUser2Id(user2Id);
        session.setBusinessType(bizType);
        session.setBusinessId(bizId);
        session.setBusinessName(req.getBusinessName());
        session.setStatus(STATUS_PENDING);
        session.setAssignedAdminId(user2Id);
        try
        {
            sessionMapper.insertSession(session);
        }
        catch (Exception e)
        {
            // 唯一键冲突 → 再查一次
            existing = sessionMapper.selectByBusiness(user1Id, user2Id, bizType, bizId);
            if (existing != null)
            {
                return toSessionVo(existing, userId);
            }
            throw e;
        }
        // 重新查一次拿联表字段
        ChatSession created = sessionMapper.selectById(session.getSessionId());
        return toSessionVo(created != null ? created : session, userId);
    }

    /* ========== 任务 3/11：会话列表 ========== */

    /** 用户端：我的会话列表。SQL 层强制 user1_id 或 user2_id = userId（任务 11）。 */
    public PageResult<Map<String, Object>> mySessions(Long userId, Integer status)
    {
        List<ChatSession> rows = sessionMapper.selectMySessions(userId, status);
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChatSession s : rows)
        {
            list.add(toSessionVo(s, userId));
        }
        return PageResult.of(list.size(), list);
    }

    /** 管理端：全部会话列表（PageHelper 驱动，Controller 用 getDataTable 取分页）。 */
    public List<Map<String, Object>> adminSessions(Integer status, String businessType, String keyword)
    {
        List<ChatSession> rows = sessionMapper.selectAdminSessions(status, businessType, keyword);
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChatSession s : rows)
        {
            list.add(toAdminSessionVo(s));
        }
        return list;
    }

    /** 管理端：会话详情（含用户信息）。 */
    public Map<String, Object> adminSessionDetail(Long sessionId)
    {
        ChatSession s = sessionMapper.selectById(sessionId);
        if (s == null)
        {
            throw new AppAuthException(AppUserErrorCodes.RESOURCE_NOT_FOUND, 404, "会话不存在");
        }
        return toAdminSessionVo(s);
    }

    /* ========== 任务 4：历史消息 ========== */

    /** 会话内消息列表（时间正序）。 */
    public PageResult<Map<String, Object>> messages(Long sessionId)
    {
        List<ChatMessage> rows = messageMapper.selectBySession(sessionId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChatMessage m : rows)
        {
            list.add(toMessageVo(m));
        }
        return PageResult.of(list.size(), list);
    }

    /* ========== 任务 5：发送消息 ========== */

    /**
     * 发送文字消息。
     *
     * 原子操作：插入消息 → 更新会话最后消息 → 对方未读 +1。
     * senderId 为当前用户；receiverId 为会话另一方。
     */
    @Transactional
    public Map<String, Object> sendMessage(Long sessionId, Long senderId,
                                            ChatMessageSendRequest req)
    {
        if (req == null || req.getContent() == null || req.getContent().isBlank())
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "消息内容不能为空");
        }
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null)
        {
            throw new AppAuthException(AppUserErrorCodes.RESOURCE_NOT_FOUND, 404, "会话不存在");
        }
        // 已结束会话不允许发消息
        if (session.getStatus() != null && session.getStatus() == STATUS_CLOSED)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "会话已结束，无法发送消息");
        }
        Long receiverId = session.getUser1Id().equals(senderId) ? session.getUser2Id() : session.getUser1Id();
        String msgType = (req.getMsgType() != null && !req.getMsgType().isBlank()) ? req.getMsgType() : MSG_TYPE_TEXT;

        // 插入消息
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setSenderId(senderId);
        msg.setReceiverId(receiverId);
        msg.setMsgType(msgType);
        msg.setContent(req.getContent());
        messageMapper.insertMessage(msg);

        // 更新会话最后消息
        String preview = req.getContent().length() > 100
                ? req.getContent().substring(0, 100) + "…" : req.getContent();
        Date now = new Date();
        sessionMapper.updateLastMessage(sessionId, preview, now);

        // 对方未读 +1
        boolean isUser1Receiver = receiverId.equals(session.getUser1Id());
        sessionMapper.incrementUnread(sessionId, isUser1Receiver);

        // 查回完整消息（含 sender_name）
        ChatMessage full = messageMapper.selectBySession(sessionId).stream()
                .filter(m -> m.getMessageId().equals(msg.getMessageId()))
                .findFirst().orElse(msg);
        return toMessageVo(full);
    }

    /* ========== 任务 6：未读统计 ========== */

    /** 当前用户所有会话的未读总数。 */
    public Map<String, Object> chatUnreadCount(Long userId)
    {
        int total = sessionMapper.sumMyUnread(userId);
        return Map.of("chatUnread", total);
    }

    /* ========== 任务 7：标记已读 ========== */

    /** 标记会话中当前用户收到的所有消息为已读，并清除会话未读计数。 */
    @Transactional
    public Map<String, Object> markSessionRead(Long sessionId, Long userId)
    {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null)
        {
            throw new AppAuthException(AppUserErrorCodes.RESOURCE_NOT_FOUND, 404, "会话不存在");
        }
        Date now = new Date();
        int msgUpdated = messageMapper.markReadBySession(sessionId, userId, now);
        boolean isUser1 = userId.equals(session.getUser1Id());
        sessionMapper.clearUnread(sessionId, isUser1);
        return Map.of("changed", msgUpdated > 0);
    }

    /* ========== 任务 8：分配管理员 ========== */

    @Transactional
    public Map<String, Object> assignAdmin(Long sessionId, Long adminId)
    {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null)
        {
            throw new AppAuthException(AppUserErrorCodes.RESOURCE_NOT_FOUND, 404, "会话不存在");
        }
        sessionMapper.updateAssignedAdmin(sessionId, adminId);
        return Map.of("assignedAdminId", adminId);
    }

    /* ========== 任务 9：状态管理 ========== */

    /**
     * 变更会话状态。
     *
     * @param action "processing" | "close" | "reopen"
     */
    @Transactional
    public Map<String, Object> changeStatus(Long sessionId, String action)
    {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null)
        {
            throw new AppAuthException(AppUserErrorCodes.RESOURCE_NOT_FOUND, 404, "会话不存在");
        }
        int newStatus;
        switch (action)
        {
            case "processing":
                newStatus = STATUS_PROCESSING;
                break;
            case "close":
                newStatus = STATUS_CLOSED;
                break;
            case "reopen":
                newStatus = STATUS_PROCESSING;
                break;
            default:
                throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "无效操作：" + action);
        }
        sessionMapper.updateStatus(sessionId, newStatus);
        return Map.of("sessionId", sessionId, "status", newStatus);
    }

    /* ========== VO 转换 ========== */

    private Map<String, Object> toSessionVo(ChatSession s, Long currentUserId)
    {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("sessionId", s.getSessionId());
        vo.put("businessType", s.getBusinessType());
        vo.put("businessId", s.getBusinessId());
        vo.put("businessName", s.getBusinessName());
        vo.put("status", s.getStatus());
        vo.put("lastMessage", s.getLastMessage());
        vo.put("lastMessageTime", s.getLastMessageTime());
        vo.put("createdAt", s.getCreatedAt());
        // 未读：当前用户那一侧的值
        int unread = currentUserId.equals(s.getUser1Id())
                ? (s.getUser1Unread() != null ? s.getUser1Unread() : 0)
                : (s.getUser2Unread() != null ? s.getUser2Unread() : 0);
        vo.put("unread", unread);
        // 对方信息
        boolean isUser1 = currentUserId.equals(s.getUser1Id());
        vo.put("peerId", isUser1 ? s.getUser2Id() : s.getUser1Id());
        vo.put("peerName", isUser1 ? s.getUser2Name() : s.getUser1Name());
        vo.put("peerAvatar", isUser1 ? s.getUser2Avatar() : s.getUser1Avatar());
        return vo;
    }

    private Map<String, Object> toAdminSessionVo(ChatSession s)
    {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("sessionId", s.getSessionId());
        vo.put("sessionType", s.getSessionType());
        vo.put("businessType", s.getBusinessType());
        vo.put("businessId", s.getBusinessId());
        vo.put("businessName", s.getBusinessName());
        vo.put("status", s.getStatus());
        vo.put("assignedAdminId", s.getAssignedAdminId());
        vo.put("lastMessage", s.getLastMessage());
        vo.put("lastMessageTime", s.getLastMessageTime());
        vo.put("createdAt", s.getCreatedAt());
        vo.put("updatedAt", s.getUpdatedAt());
        // 双方信息
        vo.put("user1Id", s.getUser1Id());
        vo.put("user1Name", s.getUser1Name());
        vo.put("user1Avatar", s.getUser1Avatar());
        vo.put("user1Unread", s.getUser1Unread());
        vo.put("user2Id", s.getUser2Id());
        vo.put("user2Name", s.getUser2Name());
        vo.put("user2Avatar", s.getUser2Avatar());
        vo.put("user2Unread", s.getUser2Unread());
        return vo;
    }

    private Map<String, Object> toMessageVo(ChatMessage m)
    {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("messageId", m.getMessageId());
        vo.put("sessionId", m.getSessionId());
        vo.put("senderId", m.getSenderId());
        vo.put("senderName", m.getSenderName());
        vo.put("senderAvatar", m.getSenderAvatar());
        vo.put("msgType", m.getMsgType());
        vo.put("content", m.getContent());
        vo.put("isRead", m.getIsRead());
        vo.put("createdAt", m.getCreatedAt());
        return vo;
    }
}
