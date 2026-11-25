package com.smartscript.platform.trade.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.system.mapper.SysConfigMapper;
import com.smartscript.platform.content.service.AiServiceClient;
import com.smartscript.platform.content.service.AiServiceClient.AiToolChatResult;
import com.smartscript.platform.content.service.AiServiceClient.ToolCall;
import com.smartscript.platform.trade.agent.EnterpriseAgentSessions.AgentException;
import com.smartscript.platform.trade.agent.EnterpriseAgentToolRegistry.Card;
import com.smartscript.platform.trade.agent.EnterpriseAgentToolRegistry.ToolResult;
import com.smartscript.platform.trade.agent.domain.TradeAgentMessage;
import com.smartscript.platform.trade.agent.domain.TradeAgentSession;
import com.smartscript.platform.trade.agent.mapper.TradeAgentMapper;

class EnterpriseAgentOrchestratorTest
{
    private final AiServiceClient ai = mock(AiServiceClient.class);
    private final EnterpriseAgentToolRegistry tools = mock(EnterpriseAgentToolRegistry.class);
    private final TradeAgentMapper mapper = mock(TradeAgentMapper.class);
    private final SysConfigMapper config = mock(SysConfigMapper.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final EnterpriseAgentSessions sessions = new EnterpriseAgentSessions(mapper, config, tx);
    private final EnterpriseAgentOrchestrator agent = new EnterpriseAgentOrchestrator(ai, tools, sessions);
    private final EnterpriseAgentController controller = new EnterpriseAgentController(sessions, agent);
    private final List<String> events = new ArrayList<>();
    private final List<Object> payloads = new ArrayList<>();
    private final List<TradeAgentMessage> saved = new ArrayList<>();

    @BeforeEach
    void setup()
    {
        var session = new TradeAgentSession();
        session.setSessionId(8L);
        session.setUserId(3L);
        session.setTitle("采购对话");
        when(tx.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(mapper.findSession(3, 8)).thenReturn(session);
        when(mapper.listSessions(3)).thenReturn(List.of(session));
        when(mapper.lockSession(3, 8)).thenReturn(session);
        when(mapper.lockUser(3)).thenReturn(3L);
        when(mapper.recentMessages(3, 8)).thenAnswer(call -> saved.isEmpty() ? List.of()
            : List.of(saved.get(saved.size() - 1)));
        AtomicLong ids = new AtomicLong(50);
        when(mapper.insertMessage(eq(3L), any())).thenAnswer(call -> {
            TradeAgentMessage message = call.getArgument(1);
            message.setMessageId(ids.getAndIncrement());
            saved.add(message);
            return 1;
        });
        when(tools.definitions()).thenReturn(List.of(Map.of("type", "function")));
        when(tools.label(anyString())).thenReturn("查询");
        when(tools.argsSummary(anyString(), anyString())).thenReturn("");
    }

    @Test
    void toolThenAnswerSavesCardsBriefAndRealTokens() throws Exception
    {
        var brief = new Card("demand_brief", Map.of("title", "悬疑"));
        when(tools.execute("draft_procurement_brief", "{}", 3)).thenReturn(
            new ToolResult(brief.payload(), "已生成草案", brief));
        when(ai.chatWithTools(anyList(), anyList(), isNull(), isNull()))
            .thenReturn(call("draft_procurement_brief", 12), answer(8));

        agent.runTurn(3, 8, "帮我起草采购需求", null, this::receive);

        assertEquals(List.of("step", "tool_call", "tool_result", "card", "step", "delta", "done"), events);
        assertEquals(brief, payloads.get(3));
        assertEquals(Map.of("messageId", 53L, "sessionTitle", "采购对话", "tokens", 20), payloads.get(6));
        assertEquals(List.of("user", "assistant", "tool", "assistant"),
            saved.stream().map(TradeAgentMessage::getRole).toList());
        assertEquals("call_1", saved.get(2).getToolCallId());
        assertTrue(saved.get(3).getStructuredPayloadJson().contains("demand_brief"));
        verify(mapper).updateSession(3, 8, "{\"title\":\"悬疑\"}", 20);
        verify(ai).chatWithTools(argThat(rows -> rows.get(rows.size() - 1).get("role").equals("tool")),
            anyList(), isNull(), isNull());
    }

    @Test
    void sixthStepForcesTextAndDisablesTools() throws Exception
    {
        when(tools.execute("list_tags", "{}", 3)).thenReturn(new ToolResult(List.of(), "无标签", null));
        when(ai.chatWithTools(anyList(), argThat(list -> list != null && !list.isEmpty()), isNull(), isNull()))
            .thenReturn(call("list_tags", 3));
        when(ai.chatWithTools(anyList(), eq(List.of()), isNull(), any())).thenAnswer(invocation -> {
            List<Map<String, Object>> messages = invocation.getArgument(0);
            assertTrue(messages.get(0).get("content").toString().contains("工具阶段已结束"));
            Consumer<String> delta = invocation.getArgument(3);
            delta.accept("已有信息");
            return answer(2);
        });
        agent.runTurn(3, 8, "找悬疑剧", null, this::receive);
        assertEquals(6, events.stream().filter("step"::equals).count());
        assertEquals(5, events.stream().filter("tool_call"::equals).count());
        assertEquals("done", events.get(events.size() - 1));
        verify(tools, times(5)).execute("list_tags", "{}", 3);
    }

    @Test
    void crossUserIs404EvenIfMapperReturnsMismatchedOwner()
    {
        var ownedByOtherUser = mapper.findSession(3, 8);
        when(mapper.findSession(4, 8)).thenReturn(ownedByOtherUser);
        var e = assertThrows(AgentException.class, () -> controller.messages(auth(4), 8));
        assertEquals(404, controller.handle(e).getStatusCode().value());
        assertEquals(404, controller.handle(e).getBody().getCode());
        verify(mapper, never()).displayMessages(4, 8);
        verifyNoInteractions(ai);
    }

    @Test
    void dailyQuotaEmits429AndSessionResponseContainsNoQuotaFields() throws Exception
    {
        when(mapper.countDailyTurns(eq(3L), any(), any())).thenReturn(20);
        var response = controller.chat(auth(3), 8, new EnterpriseAgentController.ChatRequest("找剧本", null));
        var output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);
        String sse = output.toString(StandardCharsets.UTF_8);
        assertTrue(sse.startsWith("event: error\n"));
        assertTrue(sse.contains("\"code\":429"));
        assertTrue(sse.contains(EnterpriseAgentSessions.LIMIT_MESSAGE));
        assertTrue(saved.isEmpty());
        verifyNoInteractions(ai);

        var data = new ObjectMapper().valueToTree(controller.list(auth(3))).path("data").path(0);
        assertEquals(4, data.size());
        assertTrue(data.has("sessionId"));
        assertTrue(data.has("title"));
        assertTrue(data.has("createdAt"));
        assertTrue(data.has("updatedAt"));
    }

    @Test
    void ignoresOrphanedToolsWhenReplayingHistory() throws Exception
    {
        var orphan = EnterpriseAgentSessions.message(8, "tool", "old");
        orphan.setToolCallId("out_of_window");
        var incomplete = EnterpriseAgentSessions.message(8, "assistant", "");
        incomplete.setToolCallsJson("[{\"id\":\"missing\"}]");
        var question = EnterpriseAgentSessions.message(8, "user", "继续比较");
        var complete = EnterpriseAgentSessions.message(8, "assistant", "");
        complete.setToolCallsJson("[{\"id\":\"ok\"}]");
        var tool = EnterpriseAgentSessions.message(8, "tool", "{\"result\":1}");
        tool.setToolCallId("ok");
        assertEquals(List.of("user", "assistant", "tool"),
            agent.history(List.of(tool, complete, question, incomplete, orphan)).stream()
                .map(message -> message.get("role")).toList());
    }

    @Test
    void stripsDsmlToolCallMarkupFromStreamAndSavedAnswer() throws Exception
    {
        String leaked = "为您找到一部候选作品。\n<｜DSML｜function_calls>\n"
            + "<｜DSML｜invoke name=\"get_work_detail\">\n"
            + "<｜DSML｜parameter name=\"workId\" string=\"false\">129</｜DSML｜parameter>\n"
            + "</｜DSML｜invoke>\n</｜DSML｜function_calls>";
        when(ai.chatWithTools(anyList(), anyList(), isNull(), isNull()))
            .thenReturn(new AiToolChatResult(leaked, List.of(), 10, "test", "stop"));

        agent.runTurn(3, 8, "找悬疑剧", null, this::receive);

        assertEquals("为您找到一部候选作品。", saved.get(1).getContent());
        assertFalse(payloads.toString().contains("DSML"));
        assertFalse(payloads.toString().contains("get_work_detail"));
    }

    private void receive(String event, Object data)
    {
        events.add(event);
        payloads.add(data);
    }

    private static UsernamePasswordAuthenticationToken auth(long userId)
    {
        return UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of());
    }

    private static AiToolChatResult call(String name, int tokens)
    {
        return new AiToolChatResult("", List.of(new ToolCall("call_1", name, "{}")), tokens, "test", "tool_calls");
    }

    private static AiToolChatResult answer(int tokens)
    {
        return new AiToolChatResult("已有信息", List.of(), tokens, "test", "stop");
    }
}
