package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class AiServiceClientIsolationTest
{
    private final ObjectMapper json = new ObjectMapper();
    private final List<JsonNode> captured = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private HttpServer server;
    private AiServiceClient client;
    private volatile CountDownLatch bothRequests;

    @BeforeEach
    void start() throws Exception
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/v1/chat/completions", exchange -> {
            try
            {
                JsonNode body = json.readTree(exchange.getRequestBody());
                captured.add(body);
                String content = body.path("messages").path(1).path("content").asText();
                boolean streaming = body.path("stream").asBoolean();
                if (bothRequests != null)
                {
                    bothRequests.countDown();
                    if (!bothRequests.await(5, TimeUnit.SECONDS))
                    {
                        throw new IllegalStateException("Requests did not overlap");
                    }
                }
                String response;
                if (streaming)
                {
                    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                    int middle = content.length() / 2;
                    response = delta(content.substring(0, middle)) + delta(content.substring(middle))
                            + "data: {\"choices\":[{\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
                }
                else
                {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    response = "{\"choices\":[{\"message\":{\"content\":"
                            + json.writeValueAsString(content) + "}}]}";
                }
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            finally
            {
                exchange.close();
            }
        });
        server.start();
        client = new AiServiceClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "synthetic-test-key", "test-model");
    }

    @AfterEach
    void stop()
    {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void subsequentRequestDoesNotContainAnotherRequestsPromptOrResponse()
    {
        String privateStory = "SYNTHETIC-ALPHA-101-secret-story";
        assertEquals(privateStory, client.write(privateStory, null).path("content").asText());
        assertEquals("继续", client.write("继续", null).path("content").asText());
        assertEquals(2, captured.size());
        assertFalse(captured.get(1).toString().contains(privateStory));
        assertEquals(2, captured.get(1).path("messages").size());
    }

    @Test
    void overlappingStreamsKeepTheirRequestAndDeltaBuffersSeparate() throws Exception
    {
        bothRequests = new CountDownLatch(2);
        String privateStory = "SYNTHETIC-ALPHA-101-secret-story";
        StringBuilder firstDeltas = new StringBuilder();
        StringBuilder secondDeltas = new StringBuilder();
        CompletableFuture<String> first = CompletableFuture.supplyAsync(
                () -> client.writeStream(privateStory, null, firstDeltas::append), executor);
        CompletableFuture<String> second = CompletableFuture.supplyAsync(
                () -> client.writeStream("继续", null, secondDeltas::append), executor);

        assertEquals(privateStory, first.get(10, TimeUnit.SECONDS));
        assertEquals("继续", second.get(10, TimeUnit.SECONDS));
        assertEquals(privateStory, firstDeltas.toString());
        assertEquals("继续", secondDeltas.toString());
        assertEquals(2, captured.size());
        assertTrue(captured.stream().allMatch(body -> body.path("messages").size() == 2));
        assertEquals(1, captured.stream().filter(body -> body.toString().contains(privateStory)).count());
    }

    private String delta(String content) throws java.io.IOException
    {
        return "data: {\"choices\":[{\"delta\":{\"content\":"
                + json.writeValueAsString(content) + "}}]}\n\n";
    }
}
