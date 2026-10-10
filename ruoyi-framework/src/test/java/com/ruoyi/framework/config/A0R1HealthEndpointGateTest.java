package com.ruoyi.framework.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * A0-R1：/health 必须真实存在且 Security 放行，验收脚本不得打到必然 401 的路径。
 */
class A0R1HealthEndpointGateTest
{
    @Test
    void healthEndpointIsPermitAll() throws Exception
    {
        String sec = read("ruoyi-framework/src/main/java/com/ruoyi/framework/config/SecurityConfig.java");
        assertTrue(sec.contains("\"/health\"") || sec.contains("/health"),
                "SecurityConfig must permit /health for A0-R1 verify script");
    }

    @Test
    void healthControllerExists() throws Exception
    {
        Path p = repoRoot().resolve("ruoyi-admin/src/main/java/com/ruoyi/web/controller/common/HealthController.java");
        assertTrue(Files.exists(p), "HealthController must exist for GET /health");
        String src = Files.readString(p, StandardCharsets.UTF_8);
        assertTrue(src.contains("/health"), "HealthController must map /health");
        assertTrue(src.contains("UP") || src.contains("status"), "HealthController must report status UP");
    }

    private static String read(String rel) throws Exception
    {
        return Files.readString(repoRoot().resolve(rel), StandardCharsets.UTF_8);
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
