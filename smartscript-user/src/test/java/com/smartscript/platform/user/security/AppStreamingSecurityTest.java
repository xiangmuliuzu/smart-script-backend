package com.smartscript.platform.user.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import com.smartscript.platform.user.config.AppAuthSecurityConfig;

class AppStreamingSecurityTest
{
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup()
    {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        AppRuoYiSessionService sessions = context.getBean(AppRuoYiSessionService.class);
        when(sessions.parseAndValidate("valid")).thenReturn(new AppIdentityContext(7L, "01", "0", null, null, null, null, 1L, "jti", "valid"));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        TestController.identity.set(null);
    }

    @AfterEach
    void cleanup() { context.close(); }

    @Test
    void authenticatedStreamCompletesAndRetainsIdentity() throws Exception
    {
        var response = mvc.perform(post("/api/v1/content/ai/outline/stream").header("Authorization", "Bearer valid"))
                .andReturn();
        var result = mvc.perform(asyncDispatch(response)).andExpect(status().isOk()).andReturn();
        assertEquals("7", TestController.identity.get());
        assertTrue(result.getResponse().getContentAsString().contains("\"done\":true"));
    }

    @Test
    void anonymousInitialRequestIsRejected() throws Exception
    {
        mvc.perform(post("/api/v1/content/ai/outline/stream")).andExpect(status().isUnauthorized());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({AppAuthSecurityConfig.class, AppAuthEntryPoint.class, TestController.class})
    static class TestConfig
    {
        @Bean AppRuoYiSessionService sessions() { return mock(AppRuoYiSessionService.class); }
    }

    @RestController
    static class TestController
    {
        static final AtomicReference<String> identity = new AtomicReference<>();
        @PostMapping(value = "/api/v1/content/ai/outline/stream", produces = "text/event-stream")
        StreamingResponseBody stream()
        {
            return output -> {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                identity.set(auth == null ? null : auth.getName());
                output.write("data:{\"delta\":\"test\"}\n\ndata:{\"done\":true}\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
            };
        }
    }
}
