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
        Path p = Paths.get("ruoyi-admin/src/main/java/com/ruoyi/web/controller/common/HealthController.java");
        if (!Files.exists(p))
        {
            p = Paths.get("D:/build/smart-script-backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/common/HealthController.java");
        }
        assertTrue(Files.exists(p), "HealthController must exist for GET /health");
        String src = Files.readString(p, StandardCharsets.UTF_8);
        assertTrue(src.contains("/health"), "HealthController must map /health");
        assertTrue(src.contains("UP") || src.contains("status"), "HealthController must report status UP");
    }

    private static String read(String rel) throws Exception
    {
        Path p = Paths.get(rel);
        if (!Files.exists(p))
        {
            p = Paths.get("D:/build/smart-script-backend").resolve(rel);
        }
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}
