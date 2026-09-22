package com.ruoyi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * A1: prove ruoyi-admin assembles smartscript-user (Maven + component scan)
 * without copying RuoYi auth implementations.
 */
class SmartscriptUserModuleWiringTest
{
    @Test
    void applicationScansSmartscriptPlatformPackage() throws Exception
    {
        Path p = Paths.get("src/main/java/com/ruoyi/RuoYiApplication.java");
        if (!Files.exists(p))
        {
            p = Paths.get("D:/build/smart-script-backend/ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java");
        }
        assertTrue(Files.exists(p), "RuoYiApplication.java not found");
        String src = Files.readString(p, StandardCharsets.UTF_8);
        assertTrue(src.contains("com.smartscript.platform"),
                "RuoYiApplication must component-scan com.smartscript.platform");
        assertTrue(src.contains("com.ruoyi"),
                "scanBasePackages must keep com.ruoyi when extending scan");
    }

    @Test
    void adminPomDependsOnSmartscriptUser() throws Exception
    {
        Path p = Paths.get("pom.xml");
        if (!Files.exists(p))
        {
            p = Paths.get("D:/build/smart-script-backend/ruoyi-admin/pom.xml");
        }
        String pom = Files.readString(p, StandardCharsets.UTF_8);
        assertTrue(pom.contains("smartscript-user"),
                "ruoyi-admin must depend on smartscript-user");
        assertTrue(pom.contains("com.smartscript"),
                "dependency groupId must be com.smartscript");
    }

    @Test
    void moduleClassIsOnAdminTestClasspath()
    {
        Class<?> clazz = assertDoesNotThrow(
                () -> Class.forName("com.smartscript.platform.user.SmartscriptUserModule"));
        assertTrue(clazz.getName().equals("com.smartscript.platform.user.SmartscriptUserModule"));
        assertTrue(java.util.Arrays.stream(clazz.getAnnotations())
                        .anyMatch(a -> a.annotationType().getName().endsWith("Component")),
                "SmartscriptUserModule must be a Spring @Component for scan assembly");
    }

    @Test
    void mapperScanCoversSmartscriptWhenMappersExist() throws Exception
    {
        Path moduleJava = Paths.get("smartscript-user/src/main/java");
        if (!Files.exists(moduleJava))
        {
            moduleJava = Paths.get("D:/build/smart-script-backend/smartscript-user/src/main/java");
        }
        assertTrue(Files.isDirectory(moduleJava), "smartscript-user main java missing");
        boolean hasMapper;
        try (var files = Files.walk(moduleJava))
        {
            hasMapper = files.filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(p -> p.getFileName().toString().contains("Mapper")
                            && p.toString().contains("user"));
        }
        Path cfg = Paths.get("../ruoyi-framework/src/main/java/com/ruoyi/framework/config/ApplicationConfig.java")
                .toAbsolutePath().normalize();
        if (!Files.exists(cfg))
        {
            cfg = Paths.get("D:/build/smart-script-backend/ruoyi-framework/src/main/java/com/ruoyi/framework/config/ApplicationConfig.java");
        }
        String appCfg = Files.readString(cfg, StandardCharsets.UTF_8);
        if (hasMapper)
        {
            assertTrue(appCfg.contains("com.smartscript.platform"),
                    "ApplicationConfig MapperScan must cover smartscript mappers after A3");
        }
        else
        {
            assertTrue(!appCfg.contains("com.smartscript"),
                    "ApplicationConfig MapperScan must not expand until Mappers exist");
        }
    }

    private static String readRootPom() throws Exception
    {
        Path[] candidates = new Path[] {
                Paths.get("..", "pom.xml").toAbsolutePath().normalize(),
                Paths.get("pom.xml").toAbsolutePath().normalize(),
                Paths.get("D:/build/smart-script-backend/pom.xml")
        };
        for (Path p : candidates)
        {
            if (Files.exists(p))
            {
                String text = Files.readString(p, StandardCharsets.UTF_8);
                if (text.contains("<modules>") && text.contains("ruoyi-admin"))
                {
                    return text;
                }
            }
        }
        throw new IllegalStateException("root aggregator pom.xml not found");
    }

    private static String read(String rel) throws Exception
    {
        Path p = Paths.get("..").resolve(rel).toAbsolutePath().normalize();
        if (!Files.exists(p))
        {
            p = Paths.get(rel);
        }
        if (!Files.exists(p))
        {
            p = Paths.get("D:/build/smart-script-backend").resolve(rel);
        }
        assertTrue(Files.exists(p), "missing file: " + rel);
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}
