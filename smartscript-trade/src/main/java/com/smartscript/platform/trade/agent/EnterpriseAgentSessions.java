package com.smartscript.platform.trade.agent;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.system.domain.SysConfig;
import com.ruoyi.system.mapper.SysConfigMapper;
import com.smartscript.platform.trade.agent.EnterpriseAgentToolRegistry.Card;
import com.smartscript.platform.trade.agent.domain.TradeAgentMessage;
import com.smartscript.platform.trade.agent.domain.TradeAgentSession;
import com.smartscript.platform.trade.agent.mapper.TradeAgentMapper;

@Service
public class EnterpriseAgentSessions
{
    private static final String DEFAULT_TITLE = "新选本对话";
    private static final String DAILY_LIMIT_KEY = "trade.agent.daily_limit";
    private static final int DEFAULT_DAILY_LIMIT = 20;
    private static final String NOT_FOUND = "选本会话不存在";
    static final String LIMIT_MESSAGE = "今日 AI 选本咨询次数已达上限，请明天再来或直接联系平台客服";

    private final TradeAgentMapper mapper;
    private final SysConfigMapper config;
    private final TransactionTemplate transactions;
    private final ObjectMapper json = new ObjectMapper();

    public EnterpriseAgentSessions(TradeAgentMapper mapper, SysConfigMapper config,
            PlatformTransactionManager transactionManager)
    {
        this.mapper = mapper;
        this.config = config;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public SessionDto create(long userId, String title)
    {
        var session = new TradeAgentSession();
        session.setUserId(userId);
        String normalized = title == null || title.isBlank() ? DEFAULT_TITLE : title.strip();
        if (normalized.codePointCount(0, normalized.length()) > 120)
        {
            throw new AgentException(400, "会话标题最多 120 字");
        }
        session.setTitle(normalized);
        mapper.insertSession(session);
        return SessionDto.from(require(userId, session.getSessionId()));
    }

    public List<SessionDto> list(long userId)
    {
        return mapper.listSessions(userId).stream().map(SessionDto::from).toList();
    }

    public List<MessageViewDto> messages(long userId, long sessionId)
    {
        require(userId, sessionId);
        return mapper.displayMessages(userId, sessionId).stream()
            .map(message -> {
                String cards = message.getStructuredPayloadJson();
                return new MessageViewDto(message.getMessageId(), message.getRole(), message.getContent(),
                    cards == null ? List.of() : readCards(cards), message.getCreatedAt());
            }).toList();
    }

    public TradeAgentSession require(long userId, long sessionId)
    {
        return owned(mapper.findSession(userId, sessionId), userId);
    }

    public void delete(long userId, long sessionId)
    {
        transactions.executeWithoutResult(status -> {
            owned(mapper.lockSession(userId, sessionId), userId);
            mapper.deleteMessages(userId, sessionId);
            mapper.deleteSession(userId, sessionId);
        });
    }

    /**
     * 原子检查当天轮次并写入用户消息。先锁用户行，同一用户跨不同会话的请求也共享额度。
     */
    Turn start(long userId, long sessionId, String content)
    {
        int limit = dailyLimit();
        return transactions.execute(status -> {
            if (mapper.lockUser(userId) == null)
            {
                throw new AgentException(404, NOT_FOUND);
            }
            var session = owned(mapper.lockSession(userId, sessionId), userId);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime today = now.toLocalDate().atStartOfDay();
            if (mapper.countDailyTurns(userId, today, today.plusDays(1)) >= limit)
            {
                throw new AgentException(429, LIMIT_MESSAGE);
            }
            List<TradeAgentMessage> history = mapper.recentMessages(userId, sessionId);
            TradeAgentMessage question = message(sessionId, "user", content);
            question.setCreatedAt(now);
            mapper.insertMessage(userId, question);
            if (history.isEmpty() && DEFAULT_TITLE.equals(session.getTitle()))
            {
                String title = EnterpriseAgentToolRegistry.clip(content.replaceAll("\\s+", " "), 40);
                mapper.nameSession(userId, sessionId, DEFAULT_TITLE, title);
                session.setTitle(title);
            }
            mapper.updateSession(userId, sessionId, null, 0);
            return new Turn(session, question.getMessageId(), history);
        });
    }

    void finish(long userId, Turn turn, List<TradeAgentMessage> messages, String briefJson, int tokens)
    {
        long sessionId = turn.session().getSessionId();
        transactions.executeWithoutResult(status -> {
            owned(mapper.lockSession(userId, sessionId), userId);
            List<TradeAgentMessage> latest = mapper.recentMessages(userId, sessionId);
            if (latest.isEmpty() || !turn.questionId().equals(latest.get(0).getMessageId()))
            {
                throw new AgentException(409, "会话已更新，请重新发送");
            }
            for (TradeAgentMessage message : messages)
            {
                mapper.insertMessage(userId, message);
            }
            mapper.updateSession(userId, sessionId, briefJson, tokens);
        });
    }

    void accountInterruptedTurn(long userId, long sessionId, int tokens)
    {
        if (tokens > 0)
        {
            mapper.updateSession(userId, sessionId, null, tokens);
        }
    }

    static TradeAgentMessage message(long sessionId, String role, String text)
    {
        var message = new TradeAgentMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(text);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private int dailyLimit()
    {
        var query = new SysConfig();
        query.setConfigKey(DAILY_LIMIT_KEY);
        SysConfig row = config.selectConfig(query);
        String value = row == null ? null : row.getConfigValue();
        if (value != null && !value.isBlank())
        {
            try
            {
                int limit = Integer.parseInt(value.strip());
                if (limit > 0)
                {
                    return limit;
                }
            }
            catch (NumberFormatException ignored)
            {
                // 缺失或不合法的配置使用内置默认值。
            }
        }
        return DEFAULT_DAILY_LIMIT;
    }

    private List<Card> readCards(String value)
    {
        try
        {
            return json.readValue(value, new TypeReference<List<Card>>() {});
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalStateException("无法读取选本卡片", e);
        }
    }

    private static TradeAgentSession owned(TradeAgentSession session, long userId)
    {
        if (session == null || !Long.valueOf(userId).equals(session.getUserId()))
        {
            throw new AgentException(404, NOT_FOUND);
        }
        return session;
    }

    record Turn(TradeAgentSession session, Long questionId, List<TradeAgentMessage> history) {}

    public record SessionDto(Long sessionId, String title, LocalDateTime createdAt, LocalDateTime updatedAt)
    {
        static SessionDto from(TradeAgentSession session)
        {
            return new SessionDto(session.getSessionId(), session.getTitle(), session.getCreatedAt(),
                session.getUpdatedAt());
        }
    }

    public record MessageViewDto(Long messageId, String role, String content, List<Card> cards,
                                 LocalDateTime createdAt) {}

    static class AgentException extends RuntimeException
    {
        private final int code;

        AgentException(int code, String message)
        {
            super(message);
            this.code = code;
        }

        int code()
        {
            return code;
        }
    }
}
