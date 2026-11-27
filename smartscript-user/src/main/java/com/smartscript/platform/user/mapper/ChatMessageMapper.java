package com.smartscript.platform.user.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.ChatMessage;

/**
 * A3 聊天消息 Mapper。
 */
public interface ChatMessageMapper
{
    /** 插入消息 */
    int insertMessage(ChatMessage message);

    /** 会话内消息列表（按时间正序，分页） */
    List<ChatMessage> selectBySession(@Param("sessionId") Long sessionId);

    /** 单条消息（撤回前置校验用，不带联表字段） */
    ChatMessage selectMessageById(@Param("messageId") Long messageId);

    /** 条件撤回：本人 + TEXT + 未撤回 + 时间窗口内，返回 0 表示未生效 */
    int recallMessage(@Param("messageId") Long messageId,
                      @Param("sessionId") Long sessionId,
                      @Param("senderId") Long senderId,
                      @Param("windowStart") java.util.Date windowStart,
                      @Param("placeholder") String placeholder);

    /** 批量标记已读（会话中发给指定用户的消息） */
    int markReadBySession(@Param("sessionId") Long sessionId,
                          @Param("receiverId") Long receiverId,
                          @Param("readTime") Date readTime);

    /** 管理员读到的用户消息，兼容曾投递给其他管理员的历史消息。 */
    int markAdminReadThrough(@Param("sessionId") Long sessionId,
                             @Param("userId") Long userId,
                             @Param("throughMessageId") Long throughMessageId,
                             @Param("readTime") Date readTime);
}
