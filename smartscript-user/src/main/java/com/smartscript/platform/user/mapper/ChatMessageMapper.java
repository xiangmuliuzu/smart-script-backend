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

    /** 批量标记已读（会话中发给指定用户的消息） */
    int markReadBySession(@Param("sessionId") Long sessionId,
                          @Param("receiverId") Long receiverId,
                          @Param("readTime") Date readTime);
}
