package com.smartscript.platform.content.controller.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import com.smartscript.platform.content.dto.AppAiOutlineResult;
import com.smartscript.platform.content.dto.AppAiWriteResult;
import com.smartscript.platform.content.service.IAiWriteService;

class AppAiStreamingControllerTest
{
    @ParameterizedTest
    @ValueSource(strings = {"write", "polish", "outline"})
    void sendsContentBeforeGenerationFinishesAndKeepsRequestIdentity(String type) throws Exception
    {
        IAiWriteService service = mock(IAiWriteService.class);
        CountDownLatch firstChunk = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        org.mockito.stubbing.Answer<Object> generate = call -> {
            assertEquals("stream-user", SecurityContextHolder.getContext().getAuthentication().getName());
            assertEquals("private-request", RequestContextHolder.currentRequestAttributes().getAttribute("owner", 0));
            Consumer<String> delta = call.getArgument(1);
            delta.accept("第一段正文");
            firstChunk.countDown();
            assertTrue(finish.await(5, TimeUnit.SECONDS));
            delta.accept("第二段正文");
            return type.equals("outline") ? new AppAiOutlineResult("完整大纲", "AIR-test", 1)
                    : new AppAiWriteResult("第一段正文第二段正文", 123L);
        };
        when(service.writeStream(any(), any())).thenAnswer(generate);
        when(service.polishStream(any(), any())).thenAnswer(generate);
        when(service.outlineStream(any(), any())).thenAnswer(generate);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AppAiController(service))
                .addFilters(new WebAsyncManagerIntegrationFilter()).build();
        ExecutorService requestThread = Executors.newSingleThreadExecutor();
        try
        {
            var request = requestThread.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated("stream-user", null, List.of()));
                try
                {
                    return mvc.perform(post("/api/v1/content/ai/" + type + "/stream")
                            .requestAttr("owner", "private-request")
                            .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                            .content("{\"prompt\":\"故事\",\"content\":\"原文\",\"inspiration\":\"灵感\"}"))
                            .andReturn();
                }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertTrue(firstChunk.await(3, TimeUnit.SECONDS));
            var response = request.get(1, TimeUnit.SECONDS);
            assertTrue(response.getRequest().isAsyncStarted());
            String partial = response.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertTrue(partial.contains("第一段正文"));
            assertFalse(partial.contains("第二段正文"));
            assertFalse(partial.contains("\"done\":true"));
            assertEquals("no", response.getResponse().getHeader("X-Accel-Buffering"));
            finish.countDown();
            var completed = mvc.perform(asyncDispatch(response)).andReturn();
            String full = completed.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertTrue(full.contains("第二段正文"));
            assertTrue(full.contains("\"done\":true"));
            assertFalse(full.contains("event:error"));
        }
        finally
        {
            finish.countDown();
            requestThread.shutdownNow();
        }
    }
}
