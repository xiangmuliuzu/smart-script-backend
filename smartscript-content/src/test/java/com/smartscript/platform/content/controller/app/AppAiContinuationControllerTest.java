package com.smartscript.platform.content.controller.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.content.service.IAiWriteService;
import com.smartscript.platform.content.dto.AppAiWriteResult;

class AppAiContinuationControllerTest
{
    private static final String MESSAGE = "请提供需要续写的正文";
    private final IAiWriteService service = mock(IAiWriteService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AppAiController(service)).build();

    @Test
    void synchronousValidationReturnsTheApp400Envelope() throws Exception
    {
        when(service.write(any())).thenThrow(new IllegalArgumentException(MESSAGE));
        MvcResult response = mvc.perform(post("/api/v1/content/ai/write")
                .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"继续\"}"))
                .andReturn();
        JsonNode body = new ObjectMapper().readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(400, body.path("code").asInt());
        assertEquals(MESSAGE, body.path("message").asText());
    }

    @Test
    void streamingValidationReturns400WithoutAnyContentDelta() throws Exception
    {
        when(service.writeStream(any(), any())).thenThrow(new IllegalArgumentException(MESSAGE));
        MvcResult response = mvc.perform(post("/api/v1/content/ai/write/stream")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"prompt\":\"继续\"}"))
                .andReturn();
        if (response.getRequest().isAsyncStarted())
        {
            response = mvc.perform(asyncDispatch(response)).andReturn();
        }
        String body = response.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("event:error"));
        String data = body.lines().filter(line -> line.startsWith("data:"))
                .map(line -> line.substring(5).trim()).findFirst().orElseThrow();
        JsonNode error = new ObjectMapper().readTree(data);
        assertEquals(400, error.path("code").asInt());
        assertEquals(MESSAGE, error.path("message").asText());
        assertFalse(body.contains("\"delta\""));
    }

    @Test
    void normalStreamPreservesChineseContentAndCompletion() throws Exception
    {
        when(service.writeStream(any(), any())).thenAnswer(call -> {
            java.util.function.Consumer<String> delta = call.getArgument(1);
            delta.accept("合成测试正文");
            return new AppAiWriteResult("合成测试正文", 123L);
        });
        MvcResult response = mvc.perform(post("/api/v1/content/ai/write/stream")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                .content("{\"prompt\":\"合成故事正文，续写测试\"}"))
                .andReturn();
        if (response.getRequest().isAsyncStarted())
        {
            response = mvc.perform(asyncDispatch(response)).andReturn();
        }
        String body = response.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("合成测试正文"));
        assertTrue(body.contains("\"done\":true,\"recordId\":123"));
        assertFalse(body.contains("event:error"));
    }
}
