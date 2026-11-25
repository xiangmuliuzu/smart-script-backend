package com.smartscript.platform.trade.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.trade.agent.domain.TradeAgentMessage;
import com.smartscript.platform.trade.agent.domain.TradeAgentSession;
import com.smartscript.platform.trade.agent.mapper.TradeAgentMapper;

class TradeAgentMapperTest
{
    @Test
    void mapperBindsAllInterfaceMethods() throws Exception
    {
        Configuration config = new Configuration();
        parse(config);
        assertTrue(config.hasMapper(TradeAgentMapper.class));
        for (var method : TradeAgentMapper.class.getDeclaredMethods())
        {
            assertTrue(config.hasStatement(TradeAgentMapper.class.getName() + "." + method.getName()));
        }
    }

    @Test
    void mysqlRoundTripChecksOwnershipHistoryQuotaAndDelete() throws Exception
    {
        String url = System.getenv("SMARTSCRIPT_AGENT_GATE_JDBC");
        assumeTrue(url != null, "Requires an isolated MySQL fixture with the Agent migration applied");
        var dataSource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, "root", "");
        var config = new Configuration(new Environment("agent-gate", new JdbcTransactionFactory(), dataSource));
        parse(config);
        try (var db = new SqlSessionFactoryBuilder().build(config).openSession())
        {
            var mapper = db.getMapper(TradeAgentMapper.class);
            assertEquals(101L, mapper.lockUser(101L));
            var session = new TradeAgentSession();
            session.setUserId(101L);
            session.setTitle("悬疑短剧");
            assertEquals(1, mapper.insertSession(session));
            long id = session.getSessionId();
            assertEquals("悬疑短剧", mapper.lockSession(101, id).getTitle());
            assertNull(mapper.findSession(202, id));
            assertTrue(mapper.listSessions(202).isEmpty());

            LocalDateTime today = LocalDate.now().atStartOfDay();
            int prior = mapper.countDailyTurns(101, today, today.plusDays(1));
            assertEquals(0, mapper.insertMessage(202, message(id, "user")));
            var question = message(id, "user");
            assertEquals(1, mapper.insertMessage(101, question));
            assertNotNull(question.getMessageId());
            var call = message(id, "assistant");
            call.setToolCallsJson("[{\"id\":\"call_1\"}]");
            assertEquals(1, mapper.insertMessage(101, call));
            assertEquals(1, mapper.insertMessage(101, message(id, "tool")));
            var answer = message(id, "assistant");
            answer.setStructuredPayloadJson("[{\"type\":\"work_list\"}]");
            assertEquals(1, mapper.insertMessage(101, answer));
            assertEquals(List.of("user", "assistant"),
                mapper.displayMessages(101, id).stream().map(TradeAgentMessage::getRole).toList());
            assertEquals(answer.getStructuredPayloadJson(), mapper.displayMessages(101, id).get(1).getStructuredPayloadJson());
            assertEquals(4, mapper.recentMessages(101, id).size());
            assertTrue(mapper.recentMessages(202, id).isEmpty());
            assertEquals(prior + 1, mapper.countDailyTurns(101, today, today.plusDays(1)));
            assertEquals(0, mapper.updateSession(202, id, "{}", 20));
            assertEquals(1, mapper.updateSession(101, id, "{}", 20));
            assertEquals(20, mapper.findSession(101, id).getTotalTokens());
            assertEquals(0, mapper.deleteMessages(202, id));
            assertEquals(0, mapper.deleteSession(202, id));
            assertEquals(4, mapper.deleteMessages(101, id));
            assertEquals(1, mapper.deleteSession(101, id));
            assertNull(mapper.findSession(101, id));
            db.rollback();
        }
    }

    private static TradeAgentMessage message(long id, String role)
    {
        var message = new TradeAgentMessage();
        message.setSessionId(id);
        message.setRole(role);
        message.setContent(role);
        return message;
    }

    private static void parse(Configuration config) throws Exception
    {
        String resource = "mapper/TradeAgentMapper.xml";
        try (InputStream xml = TradeAgentMapperTest.class.getClassLoader().getResourceAsStream(resource))
        {
            assertNotNull(xml);
            new XMLMapperBuilder(xml, config, resource, config.getSqlFragments()).parse();
        }
    }
}
