package com.smartscript.platform.user.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.ChatSession;

/**
 * A3 聊天会话 Mapper。
 */
public interface ChatSessionMapper
{
    /** 插入会话（唯一键冲突时返回 0） */
    int insertSession(ChatSession session);

    /** 按业务上下文查找已有会话 */
    ChatSession selectByBusiness(@Param("user1Id") Long user1Id,
                                 @Param("user2Id") Long user2Id,
                                 @Param("businessType") String businessType,
                                 @Param("businessId") Long businessId);

    /** 按 session_id 查询（不校验归属，管理端用） */
    ChatSession selectById(@Param("sessionId") Long sessionId);

    /** 用户端：我的会话列表（user1_id 或 user2_id 命中） */
    List<ChatSession> selectMySessions(@Param("userId") Long userId,
                                       @Param("status") Integer status);

    /** 管理端：全部会话列表（分页 + 筛选） */
    List<ChatSession> selectAdminSessions(@Param("status") Integer status,
                                          @Param("businessType") String businessType,
                                          @Param("keyword") String keyword);

    /** 更新最后消息 */
    int updateLastMessage(@Param("sessionId") Long sessionId,
                          @Param("lastMessage") String lastMessage,
                          @Param("lastMessageTime") Date lastMessageTime);

    /** 对方未读 +1（isUser1Receiver 由 Service 判定当前用户是否为 user1） */
    int incrementUnread(@Param("sessionId") Long sessionId,
                        @Param("isUser1Receiver") boolean isUser1Receiver);

    /** 标记当前用户在该会话的未读为 0 */
    int clearUnread(@Param("sessionId") Long sessionId,
                    @Param("isUser1") boolean isUser1);

    /** 当前用户所有会话的未读总数 */
    int sumMyUnread(@Param("userId") Long userId);

    /** 更新会话状态 */
    int updateStatus(@Param("sessionId") Long sessionId,
                     @Param("status") Integer status);

    /** 分配管理员：同步移交会话参与方 user2_id */
    int updateAssignedAdmin(@Param("sessionId") Long sessionId,
                            @Param("adminId") Long adminId);

    /** 查用户显示名（昵称优先，空则用登录名） */
    String selectUserNameById(@Param("userId") Long userId);

    /** 可分配的管理员候选：管理域账号（user_type='00'）且挂有有效角色，排除无角色的测试账号与 App 用户 */
    List<Map<String, Object>> selectAdminCandidates();
}
