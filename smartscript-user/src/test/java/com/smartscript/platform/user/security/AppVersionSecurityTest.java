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
import com.ruoyi.system.service.ISysConfigService;
import com.smartscript.platform.user.config.AppAuthSecurityConfig;
import com.smartscript.platform.user.controller.AppVersionController;

/** 版本检查是冷启动匿名接口：不依赖 App Token，配置全部来自 sys_config。 */
class AppVersionSecurityTest
{
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ISysConfigService configs;

    @BeforeEach
    void setup()
    {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        configs = context.getBean(ISysConfigService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void cleanup()
    {
        context.close();
    }

    @Test
    void anonymousColdStartCanReadConfiguredVersion() throws Exception
    {
        when(configs.selectConfigByKey("app.version.android.versionCode")).thenReturn("2");
        when(configs.selectConfigByKey("app.version.android.versionName")).thenReturn("1.0.1");
        when(configs.selectConfigByKey("app.version.android.minVersionCode")).thenReturn("1");
        when(configs.selectConfigByKey("app.version.android.url"))
                .thenReturn("https://www.eyhereal.site/profile/apk/script-app-1.0.1.apk");
        when(configs.selectConfigByKey("app.version.android.size")).thenReturn("31457280");
        when(configs.selectConfigByKey("app.version.android.notes")).thenReturn("修复若干问题");

        mvc.perform(get("/api/v1/app/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.versionCode").value(2))
                .andExpect(jsonPath("$.data.versionName").value("1.0.1"))
                .andExpect(jsonPath("$.data.minVersionCode").value(1))
                .andExpect(jsonPath("$.data.size").value(31457280))
                .andExpect(jsonPath("$.data.notes").value("修复若干问题"))
                .andExpect(jsonPath("$.data.url")
                        .value("https://www.eyhereal.site/profile/apk/script-app-1.0.1.apk"));
        verifyNoInteractions(context.getBean(AppRuoYiSessionService.class));
    }

    @Test
    void expiredTokenDoesNotBlockVersionCheck() throws Exception
    {
        mvc.perform(get("/api/v1/app/version").header("Authorization", "Bearer expired-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verifyNoInteractions(context.getBean(AppRuoYiSessionService.class));
    }

    @Test
    void missingConfigFallsBackToNoUpdate() throws Exception
    {
        mvc.perform(get("/api/v1/app/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionCode").value(1))
                .andExpect(jsonPath("$.data.versionName").value("1.0.0"))
                .andExpect(jsonPath("$.data.url").value(""));
    }

    @Test
    void disabledSwitchMasksConfiguredUpdate() throws Exception
    {
        when(configs.selectConfigByKey("app.version.android.enabled")).thenReturn("false");
        when(configs.selectConfigByKey("app.version.android.versionCode")).thenReturn("9");

        mvc.perform(get("/api/v1/app/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionCode").value(1))
                .andExpect(jsonPath("$.data.url").value(""));
    }

    @Test
    void writeMethodsStillRequireIdentity() throws Exception
    {
        mvc.perform(post("/api/v1/app/version")).andExpect(status().isUnauthorized());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import({AppAuthSecurityConfig.class, AppVersionController.class, AppAuthEntryPoint.class})
    static class TestConfig
    {
        @Bean
        AppRuoYiSessionService sessions()
        {
            return mock(AppRuoYiSessionService.class);
        }

        @Bean
        ISysConfigService configs()
        {
            return mock(ISysConfigService.class);
        }
    }
}
