package com.smartscript.platform.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * A1: prove smartscript-user marker is a Spring bean under component scan,
 * and that the module does not expose production controllers or copy RuoYi auth types.
 */
class SmartscriptUserModuleAssemblyTest
{
    @Test
    void moduleBeanIsRegisteredWhenPackageScanned()
    {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext())
        {
            ctx.scan("com.smartscript.platform.user");
            ctx.refresh();
            SmartscriptUserModule module = ctx.getBean(SmartscriptUserModule.class);
            assertNotNull(module);
            assertEquals("smartscript-user", module.getModuleId());
        }
    }

    @Test
    void moduleSourceHasNoProductionRestController() throws Exception
    {
        Path root = moduleMainJava();
        assertTrueFilesDoNotContainRestApi(root);
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
                            "TokenService", "SysPermissionService"
                    })
                    {
                        org.junit.jupiter.api.Assertions.assertFalse(
                                src.contains(forbidden),
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

    private static void assertTrueFilesDoNotContainRestApi(Path root) throws Exception
    {
        try (Stream<Path> files = Files.walk(root))
        {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try
                {
                    String src = Files.readString(p, StandardCharsets.UTF_8);
                    org.junit.jupiter.api.Assertions.assertFalse(
                            src.contains("@RestController") || src.contains("@RequestMapping"),
                            "A1 module must not expose production HTTP probes: " + p);
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
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(p), "module main java not found: " + p);
        return p;
    }
}
