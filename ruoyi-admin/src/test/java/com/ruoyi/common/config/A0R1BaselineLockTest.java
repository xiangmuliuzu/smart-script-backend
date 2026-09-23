package com.ruoyi.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A0-R1：锁定 Spring Boot 4.1.0 / JDBC 8.0.33，且仓库配置不得包含默认密钥/默认口令。
 */
class A0R1BaselineLockTest
{
    @Test
    void springBootVersionIsLockedTo410() throws Exception
    {
        Properties p = loadPomProperties();
        assertEquals("4.1.0", p.getProperty("spring-boot.version"),
                "rules.md requires Spring Boot 4.1.0");
        assertEquals("8.0.33", p.getProperty("mysql.connector.version"),
                "rules.md requires JDBC driver 8.0.33");
        assertEquals("17", p.getProperty("java.version"));
    }

    @Test
    void applicationYmlHasNoDefaultJwtSecret() throws Exception
    {
        String yml = readResource("/application.yml");
        assertFalse(yml.contains("secret: abcdefghijklmnopqrstuvwxyz"),
                "default JWT secret must not be committed");
        assertFalse(yml.contains("dev-only-ruoyi"),
                "placeholder weak token secret must not be committed");
        assertTrue(yml.contains("${TOKEN_SECRET:}") || yml.contains("${TOKEN_SECRET}"),
                "token.secret must reference TOKEN_SECRET env var");
    }

    @Test
    void druidYmlHasNoHardcodedDbPassword() throws Exception
    {
        String yml = readResource("/application-druid.yml");
        assertFalse(yml.contains("password: password"), "hardcoded druid password forbidden");
        assertTrue(yml.contains("${DB_PASSWORD:}") || yml.contains("${DB_PASSWORD}"),
                "DB password must come from environment");
    }

    @Test
    void verifyScriptsMustNotHardcodeDefaultPasswords() throws Exception
    {
        Path scripts = resolveRepoRoot().resolve("scripts");
        if (!Files.isDirectory(scripts))
        {
            return;
        }
        try (Stream<Path> files = Files.list(scripts))
        {
            files.filter(p -> p.getFileName().toString().endsWith(".ps1"))
                    .forEach(p -> {
                        try
                        {
                            String text = Files.readString(p, StandardCharsets.UTF_8);
                            assertFalse(text.contains("\"admin123\"") || text.contains("'admin123'"),
                                    "script " + p + " must not hardcode admin123");
                            assertTrue(text.contains("VERIFY_ADMIN_PASSWORD") || text.contains("Password"),
                                    "script " + p + " must take password from env/param");
                        }
                        catch (Exception e)
                        {
                            throw new IllegalStateException(e);
                        }
                    });
        }
    }

    private static Properties loadPomProperties() throws Exception
    {
        Path pom = resolveRootPom();
        String xml = Files.readString(pom, StandardCharsets.UTF_8);
        Properties p = new Properties();
        p.setProperty("spring-boot.version", require(extract(xml, "spring-boot.version"), "spring-boot.version"));
        p.setProperty("mysql.connector.version", require(extract(xml, "mysql.connector.version"), "mysql.connector.version"));
        p.setProperty("java.version", require(extract(xml, "java.version"), "java.version"));
        return p;
    }

    private static String require(String v, String name)
    {
        if (v == null || v.isEmpty())
        {
            throw new IllegalStateException("missing pom property: " + name);
        }
        return v;
    }

    private static Path resolveRepoRoot()
    {
        Path[] candidates = new Path[] {
                Paths.get("..").toAbsolutePath().normalize(),
                Paths.get(".").toAbsolutePath().normalize(),
                Paths.get("D:/build/smart-script-backend")
        };
        for (Path candidate : candidates)
        {
            if (Files.exists(candidate.resolve("pom.xml"))
                    && Files.exists(candidate.resolve("ruoyi-admin")))
            {
                return candidate;
            }
        }
        throw new IllegalStateException("repo root not found from user.dir=" + Paths.get("").toAbsolutePath());
    }

    private static Path resolveRootPom() throws Exception
    {
        Path root = resolveRepoRoot();
        Path pom = root.resolve("pom.xml");
        String xml = Files.readString(pom, StandardCharsets.UTF_8);
        if (xml.contains("<artifactId>ruoyi</artifactId>"))
        {
            return pom;
        }
        throw new IllegalStateException("root pom.xml not found at " + pom);
    }

    private static String extract(String xml, String tag)
    {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int i = xml.indexOf(open);
        if (i < 0)
        {
            return null;
        }
        int j = xml.indexOf(close, i);
        return xml.substring(i + open.length(), j).trim();
    }

    private static String readResource(String name) throws Exception
    {
        try (InputStream in = A0R1BaselineLockTest.class.getResourceAsStream(name))
        {
            if (in != null)
            {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        Path p = resolveRepoRoot().resolve("ruoyi-admin/src/main/resources" + name);
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}
