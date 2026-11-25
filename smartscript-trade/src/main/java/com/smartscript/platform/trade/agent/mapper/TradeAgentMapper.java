package com.smartscript.platform.trade.agent.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.trade.agent.domain.TradeAgentMessage;
import com.smartscript.platform.trade.agent.domain.TradeAgentSession;

public interface TradeAgentMapper
{
    int insertSession(TradeAgentSession session);

    TradeAgentSession findSession(@Param("userId") long userId, @Param("sessionId") long sessionId);

    TradeAgentSession lockSession(@Param("userId") long userId, @Param("sessionId") long sessionId);

    List<TradeAgentSession> listSessions(@Param("userId") long userId);

    Long lockUser(@Param("userId") long userId);

    int countDailyTurns(@Param("userId") long userId, @Param("dayStart") LocalDateTime dayStart,
                        @Param("dayEnd") LocalDateTime dayEnd);

    List<TradeAgentMessage> recentMessages(@Param("userId") long userId, @Param("sessionId") long sessionId);

    List<TradeAgentMessage> displayMessages(@Param("userId") long userId, @Param("sessionId") long sessionId);

    int insertMessage(@Param("userId") long userId, @Param("message") TradeAgentMessage message);

    int updateSession(@Param("userId") long userId, @Param("sessionId") long sessionId,
                      @Param("briefJson") String briefJson, @Param("tokens") int tokens);

    int nameSession(@Param("userId") long userId, @Param("sessionId") long sessionId,
                    @Param("defaultTitle") String defaultTitle, @Param("title") String title);

    int deleteMessages(@Param("userId") long userId, @Param("sessionId") long sessionId);

    int deleteSession(@Param("userId") long userId, @Param("sessionId") long sessionId);
}
