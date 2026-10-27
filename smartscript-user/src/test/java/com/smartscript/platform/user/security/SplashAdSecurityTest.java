package com.smartscript.platform.user.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import com.smartscript.platform.review.controller.SplashAdController;
import com.smartscript.platform.review.domain.AdConfig;
import com.smartscript.platform.review.mapper.AdConfigMapper;
import com.smartscript.platform.user.config.AppAuthSecurityConfig;

/** Exercises the real App security chain, including both authentication and authorization. */
class SplashAdSecurityTest
{
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private AdConfigMapper mapper;

    @BeforeEach
    void setup()
    {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        mapper = context.getBean(AdConfigMapper.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void cleanup()
    {
        context.close();
    }

    @Test
    void anonymousColdStartCanLoadConfiguredSplash() throws Exception
    {
        AdConfig ad = new AdConfig();
        ad.setImageUrl("/profile/upload/splash.png");
        ad.setDurationSeconds(5);
        when(mapper.selectActiveSplash()).thenReturn(ad);

        mvc.perform(get("/api/v1/ad/splash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.imageUrl").value("/profile/upload/splash.png"));
        verifyNoInteractions(context.getBean(AppRuoYiSessionService.class));
    }

    @Test
    void expiredTokenDoesNotBlockPublicSplash() throws Exception
    {
        mvc.perform(get("/api/v1/ad/splash").header("Authorization", "Bearer expired-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verifyNoInteractions(context.getBean(AppRuoYiSessionService.class));
    }

    @Test
    void splashWritesAndOtherAdPathsStillRequireIdentity() throws Exception
    {
        mvc.perform(post("/api/v1/ad/splash")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ad/splash/private")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ad/reward")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ad/reward")).andExpect(status().isUnauthorized());
        verifyNoInteractions(mapper);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({AppAuthSecurityConfig.class, SplashAdController.class, AppAuthEntryPoint.class})
    static class TestConfig
    {
        @Bean
        AppRuoYiSessionService sessions()
        {
            return mock(AppRuoYiSessionService.class);
        }

        @Bean
        AdConfigMapper ads()
        {
            return mock(AdConfigMapper.class);
        }
    }
}
