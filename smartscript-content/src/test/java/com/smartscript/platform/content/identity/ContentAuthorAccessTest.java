package com.smartscript.platform.content.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.smartscript.platform.content.exception.ContentAuthorExceptionHandler;
import com.smartscript.platform.content.controller.app.AppWorkManageController;
import com.smartscript.platform.content.controller.app.AppUserWorkController;
import com.smartscript.platform.content.controller.app.AppChapterWriteController;
import com.smartscript.platform.content.service.AppContentFileUploadService;
import com.smartscript.platform.content.service.ContentAuthorAccess;
import com.smartscript.platform.content.service.IAppWorkManageService;
import com.smartscript.platform.content.service.IAppUserWorkService;
import com.smartscript.platform.content.service.IAppChapterAuthorService;
import com.smartscript.platform.identity.IdentityContext;
import com.smartscript.platform.identity.IdentityProvider;

class ContentAuthorAccessTest
{
    @Configuration
    @EnableMethodSecurity
    static class SecurityConfig { }

    @Test
    void liveCapabilityProtectsCreationChapterEditingAndPublishing() throws Exception
    {
        IdentityProvider identities = mock(IdentityProvider.class);
        IAppWorkManageService works = mock(IAppWorkManageService.class);
        IAppUserWorkService userWorks = mock(IAppUserWorkService.class);
        IAppChapterAuthorService chapters = mock(IAppChapterAuthorService.class);
        try (var context = new AnnotationConfigApplicationContext())
        {
            context.register(SecurityConfig.class);
            context.registerBean("contentAuthorAccess", ContentAuthorAccess.class, () -> new ContentAuthorAccess(identities));
            context.registerBean(AppWorkManageController.class, () -> new AppWorkManageController(works, mock(AppContentFileUploadService.class)));
            context.registerBean(AppUserWorkController.class, () -> new AppUserWorkController(userWorks, identities));
            context.registerBean(AppChapterWriteController.class, () -> new AppChapterWriteController(chapters));
            context.refresh();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("app-user", "token", List.of()));
            var controller = context.getBean(AppWorkManageController.class);
            // 即便账号类型为创作者、有 author 角色，未开通或已关闭能力也必须拒绝。
            when(identities.currentIdentity()).thenReturn(identity("02", false));
            assertThrows(AuthorizationDeniedException.class, () -> controller.create(Map.of()));
            verifyNoInteractions(works);
            assertThrows(AuthorizationDeniedException.class, () -> context.getBean(AppUserWorkController.class).submit(1L));
            assertThrows(AuthorizationDeniedException.class, () -> context.getBean(AppChapterWriteController.class).delete(1L));
            verifyNoInteractions(userWorks, chapters);
            MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new ContentAuthorExceptionHandler()).build()
                    .perform(post("/api/v1/content/works").contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("请先申请开通创作者能力"));
            // 普通账号开通能力即可创作；无需改写账号类型。
            when(identities.currentIdentity()).thenReturn(identity("01", true));
            assertTrue(context.getBean(ContentAuthorAccess.class).allowed());
            when(identities.currentIdentity()).thenReturn(identity("01", false));
            assertThrows(AuthorizationDeniedException.class, () -> controller.create(Map.of()));
        }
        finally
        {
            SecurityContextHolder.clearContext();
        }
    }

    private static IdentityContext identity(String type, boolean enabled)
    {
        return IdentityContext.authenticated(1L, type, "0", List.of("author"), List.of(), "APPROVED", enabled);
    }
}
