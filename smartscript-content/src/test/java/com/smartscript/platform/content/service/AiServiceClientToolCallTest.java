package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.content.exception.AiServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;

class AiServiceClientToolCallTest
{
    @Test
    void mergesIndexedCallsAndReadsUsageAfterFinish() throws Exception
    {
        List<String> chunks = List.of(
            ": keep-alive\r\n\r\n",
            "data: {\"model\":\"tool-model\",\"choices\":[{\"delta\":{\"content\":\"先检索\",\"tool_calls\":["
                + "{\"index\":1,\"id\":\"call_\",\"function\":{\"name\":\"list_\",\"arguments\":\"{\"}},"
                + "{\"index\":0,\"id\":\"first\",\"function\":{\"name\":\"search_works\",\"arguments\":\"{\\\"limit\\\":\"}}]}}]}\r\n\r\n",
            "data: {\"choices\":[{\"delta\":{\"content\":\"。\",\"tool_calls\":["
                + "{\"index\":0,\"function\":{\"arguments\":\"2}\"}},"
                + "{\"index\":1,\"id\":\"tags\",\"function\":{\"name\":\"tags\",\"arguments\":\"}\"}}]}}]}\r\n\r\n",
            "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}\r\n\r\n",
            "data: {\"choices\":[],\"usage\":{\"total_tokens\":123}}\r\n\r\n",
            "data: [DONE]\r\n\r\n");
        AtomicReference<JsonNode> request = new AtomicReference<>();
        HttpServer server = serve(200, chunks, request);
        try
        {
            var client = client(server, "local-test-key");
            List<String> deltas = new ArrayList<>();
            var result = client.chatWithTools(List.of(Map.of("role", "user", "content", "找短剧")),
                    List.of(Map.of("type", "function", "function", Map.of("name", "search_works"))),
                    "deepseek-flash", deltas::add);
            assertEquals("先检索。", result.content());
            assertEquals(List.of("先检索", "。"), deltas);
            assertEquals(List.of(new AiServiceClient.ToolCall("first", "search_works", "{\"limit\":2}"),
                    new AiServiceClient.ToolCall("call_tags", "list_tags", "{}")), result.toolCalls());
            assertEquals(123, result.totalTokens());
            assertEquals("tool-model", result.model());
            assertEquals("tool_calls", result.finishReason());
            assertEquals("auto", request.get().path("tool_choice").asText());
            assertEquals("deepseek-flash", request.get().path("model").asText());
            assertEquals("disabled", request.get().path("thinking").path("type").asText());
            assertTrue(request.get().path("stream").asBoolean());
            assertTrue(request.get().path("stream_options").path("include_usage").asBoolean());
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void failsWithoutApiKeyOrOnNonSuccessfulHttp() throws Exception
    {
        var missingKey = new AiServiceClient("http://127.0.0.1:1", " ", "tool-model");
        assertThrows(AiServiceUnavailableException.class,
                () -> missingKey.chatWithTools(List.of(), List.of(), null, null));

        AtomicReference<JsonNode> request = new AtomicReference<>();
        HttpServer server = serve(502, List.of("upstream unavailable"), request);
        try
        {
            var client = client(server, "local-test-key");
            assertThrows(AiServiceUnavailableException.class,
                    () -> client.chatWithTools(List.of(), List.of(), null, null));
            assertFalse(request.get().has("tools"));
            assertFalse(request.get().has("tool_choice"));
        }
        finally
        {
            server.stop(0);
        }
    }

    private static AiServiceClient client(HttpServer server, String key)
    {
        return new AiServiceClient("http://127.0.0.1:" + server.getAddress().getPort(), key, "tool-model");
    }

    private static HttpServer serve(int status, List<String> chunks, AtomicReference<JsonNode> request) throws Exception
    {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange ->
        {
            try (exchange)
            {
                request.set(new ObjectMapper().readTree(exchange.getRequestBody()));
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
                exchange.sendResponseHeaders(status, 0);
                for (String chunk : chunks)
                {
                    exchange.getResponseBody().write(chunk.getBytes(StandardCharsets.UTF_8));
                    exchange.getResponseBody().flush();
                }
            }
        });
        server.start();
        return server;
    }
}
