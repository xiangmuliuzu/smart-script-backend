package com.smartscript.platform.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A6 示例模块边界与依赖方向约束。
 *
 * 目的：把「下游模块不得依赖 smartscript-user、不得直接访问 A 的认证表/Token 表/
 * 实名材料表」这条规则做成可执行检查（规格 §10），而不是只写在文档里。
 */
class ContentModuleBoundaryTest
{
    private static Path moduleRoot() throws Exception
    {
        Path p = Paths.get(".");
        if (Files.exists(p.resolve("src/main/java"))) return p;
        p = Paths.get("smartscript-content");
        if (Files.exists(p.resolve("src/main/java"))) return p;
        p = Paths.get("D:/build/smart-script-backend/smartscript-content");
        assertTrue(Files.exists(p.resolve("src/main/java")), "module root not found");
        return p;
    }

    private static List<String> sourceFiles() throws Exception
    {
        Path root = moduleRoot().resolve("src/main/java");
        List<String> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root))
        {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> files.add(p.toString().replace('\\', '/')));
        }
        return files;
    }

    @Test
    void moduleMarkerIsStable()
    {
        assertNotNull(ContentModule.MODULE_ID);
        assertEquals("smartscript-content", ContentModule.MODULE_ID);
    }

    @Test
    void moduleDoesNotDependOnUserModule() throws Exception
    {
        // 依赖方向单向：content -> (ruoyi-common/framework)，不得编译期依赖 smartscript-user
        String pom = Files.readString(moduleRoot().resolve("pom.xml"), StandardCharsets.UTF_8);
        assertFalse(pom.contains("<artifactId>smartscript-user</artifactId>"),
                "示例业务模块不得依赖 smartscript-user（会形成业务模块间反向耦合）");
        for (String file : sourceFiles())
        {
            String src = Files.readString(Paths.get(file), StandardCharsets.UTF_8);
            assertFalse(src.contains("com.smartscript.platform.user."),
                    "业务模块不得引用 A 模块实现类: " + file);
        }
    }

    @Test
    void moduleDoesNotTouchAuthOrRealNameTables() throws Exception
    {
        // 下游不得直接操作 A 的认证表、Token 表、实名材料表（规格 §10）
        String[] forbidden = {
                "app_sms_code", "app_refresh_session", "app_user_consent", "app_user_oauth",
                "user_real_name_auth", "user_phone_change_log", "user_author_capability",
                "sys_user_role", "sys_role_menu", "mapper/user/"
        };
        for (String file : sourceFiles())
        {
            String src = Files.readString(Paths.get(file), StandardCharsets.UTF_8);
            for (String token : forbidden)
            {
                assertFalse(src.contains(token),
                        file + " 不得直接访问 A 模块的表或 Mapper: " + token);
            }
        }
    }

    @Test
    void moduleControllersLiveUnderControllerPackageAndUseAppPrefix() throws Exception
    {
        for (String file : sourceFiles())
        {
            String src = Files.readString(Paths.get(file), StandardCharsets.UTF_8);
            boolean hasRest = src.contains("@RestController") || src.contains("@RequestMapping");
            if (hasRest)
            {
                assertTrue(file.contains("/controller/"), "控制器必须位于 controller 包: " + file);
                assertTrue(src.contains("/api/v1/content"),
                        "业务模块控制器必须挂在 /api/v1/content 之下: " + file);
            }
        }
    }

    @Test
    void sampleDataIsMarkedAsTemporary() throws Exception
    {
        // 示例内存仓储是明确的临时实现，必须留下替换标记，避免被误当正式仓储
        Path repo = moduleRoot().resolve(
                "src/main/java/com/smartscript/platform/content/repository/SampleWorkRepository.java");
        String src = Files.readString(repo, StandardCharsets.UTF_8);
        assertTrue(src.contains("临时实现") || src.contains("示例"),
                "示例数据必须标注为临时实现并写明替换条件");
        assertTrue(src.contains("A6-Q1") || src.contains("加固清单"),
                "示例数据必须引用待确认项或加固清单编号");
    }
}
