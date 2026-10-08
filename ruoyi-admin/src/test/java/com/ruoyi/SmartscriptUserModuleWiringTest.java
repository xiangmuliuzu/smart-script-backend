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
        Path p = repoRoot().resolve("ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java");
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
        Path p = repoRoot().resolve("ruoyi-admin/pom.xml");
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
        Path moduleJava = repoRoot().resolve("smartscript-user/src/main/java");
        assertTrue(Files.isDirectory(moduleJava), "smartscript-user main java missing");
        boolean hasMapper;
        try (var files = Files.walk(moduleJava))
        {
            hasMapper = files.filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(p -> p.getFileName().toString().contains("Mapper")
                            && p.toString().contains("user"));
        }
        Path cfg = repoRoot().resolve("ruoyi-framework/src/main/java/com/ruoyi/framework/config/ApplicationConfig.java");
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
        Path p = repoRoot().resolve("pom.xml");
        String text = Files.readString(p, StandardCharsets.UTF_8);
        if (text.contains("<modules>") && text.contains("ruoyi-admin"))
        {
            return text;
        }
        throw new IllegalStateException("root aggregator pom.xml not found");
    }

    private static String read(String rel) throws Exception
    {
        Path p = repoRoot().resolve(rel);
        assertTrue(Files.exists(p), "missing file: " + rel);
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /**
     * 仓库根目录：从当前工作目录向上查找聚合 pom（同时含 ruoyi-admin 与 smartscript-content 子模块）。
     * surefire 的工作目录是各模块 basedir，故不能依赖某台机器的绝对路径。
     */
    private static Path repoRoot()
    {
        for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent())
        {
            if (Files.exists(dir.resolve("pom.xml"))
                    && Files.isDirectory(dir.resolve("ruoyi-admin"))
                    && Files.isDirectory(dir.resolve("smartscript-content")))
            {
                return dir;
            }
        }
        throw new IllegalStateException("repo root not found from user.dir=" + Paths.get("").toAbsolutePath());
    }
}
