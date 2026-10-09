package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.NoticeReceipt;

/** 所有接收者读写都以当前身份和启用范围为条件。 */
public interface NoticeRecipientMapper
{
    List<NoticeReceipt> selectReceived(@Param("userId") Long userId, @Param("audience") String audience);
    NoticeReceipt selectDetail(@Param("userId") Long userId, @Param("audience") String audience,
            @Param("noticeId") Long noticeId);
    int countUnread(@Param("userId") Long userId, @Param("audience") String audience);
    int markRead(@Param("userId") Long userId, @Param("audience") String audience,
            @Param("noticeId") Long noticeId);
    int markAllRead(@Param("userId") Long userId, @Param("audience") String audience);
}
