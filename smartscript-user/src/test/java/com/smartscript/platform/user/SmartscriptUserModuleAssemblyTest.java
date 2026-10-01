package com.smartscript.platform.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A3: smartscript-user carries App auth implementation.
 * Must not copy RuoYi PC auth types; App controllers are allowed under controller package.
 */
class SmartscriptUserModuleAssemblyTest
{
    @Test
    void moduleMarkerTypeIsAvailable()
    {
        assertNotNull(SmartscriptUserModule.MODULE_ID);
        assertEquals("smartscript-user", SmartscriptUserModule.MODULE_ID);
    }

    @Test
    void moduleExposesAppDomainControllersOnly() throws Exception
    {
        Path root = moduleMainJava();
        String[] allowedPrefixes = {
                "/api/v1/auth", "/api/v1/users", "/api/v1/messages", "/api/v1/feedback"
        };
        try (Stream<Path> files = Files.walk(root))
        {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try
                {
                    String src = Files.readString(p, StandardCharsets.UTF_8);
                    // 异常处理器虽标注 @RestControllerAdvice（含 @RestController 子串），
                    // 但不是控制器，且它引用的是本模块控制器，故先排除。
                    boolean isAdvice = src.contains("@RestControllerAdvice") || src.contains("@ControllerAdvice");
                    boolean hasRest = !isAdvice
                            && (src.contains("@RestController") || src.contains("@RequestMapping"));
                    if (hasRest)
                    {
                        String path = p.toString().replace('\\', '/');
                        assertTrue(path.contains("/controller/"),
                                "HTTP controllers must live under controller package: " + p);
                        // A3 仅 /api/v1/auth；A5 用户中心按契约增加 users/messages/feedback，
                        // 三者同属 App 凭证域；PC 管理接口不得出现在本模块。
                        boolean allowed = false;
                        for (String prefix : allowedPrefixes)
                        {
                            if (src.contains(prefix))
                            {
                                allowed = true;
                                break;
                            }
                        }
                        assertTrue(allowed,
                                "Only App credential-domain controllers are allowed in this module: " + p);
                        assertFalse(src.contains("/api/v1/admin"),
                                "PC admin endpoints must not live in the App user module: " + p);
                    }
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    @Test
    void moduleDoesNotDuplicateRuoYiUserAuthTypes() throws Exception
    {
        Path root = moduleMainJava();
        try (Stream<Path> files = Files.walk(root))
        {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try
                {
                    String src = Files.readString(p, StandardCharsets.UTF_8);
                    for (String forbidden : new String[] {
                            "class SysUser", "class SysRole", "class SysMenu",
                            "class LoginBody", "class LoginUser", "SysLoginService",
                            "class TokenService", "framework.web.service.TokenService",
                            "SysPermissionService"
                    })
                    {
                        assertFalse(src.contains(forbidden),
                                p + " must not copy RuoYi type/usage: " + forbidden);
                    }
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    @Test
    void appTokenDomainIsIsolatedInSource() throws Exception
    {
        Path root = moduleMainJava();
        try (Stream<Path> files = Files.walk(root))
        {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try
                {
                    String src = Files.readString(p, StandardCharsets.UTF_8);
                    if (src.contains("token_type") || src.contains("TOKEN_TYPE"))
                    {
                        assertTrue(src.contains("app_access"),
                                "App access tokens must carry token_type=app_access: " + p);
                    }
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    private static Path moduleMainJava() throws Exception
    {
        Path p = Paths.get("src/main/java");
        if (!Files.exists(p))
        {
            p = Paths.get("smartscript-user/src/main/java");
        }
        if (!Files.exists(p))
        {
            p = Paths.get("D:/build/smart-script-backend/smartscript-user/src/main/java");
        }
        assertTrue(Files.exists(p), "module main java not found: " + p);
        return p;
    }
}
