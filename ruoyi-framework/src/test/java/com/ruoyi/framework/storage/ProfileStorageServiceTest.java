package com.ruoyi.framework.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.config.RuoYiConfig;

/**
 * ProfileStorageService 本地模式：不连接 MinIO 时保持历史磁盘行为，
 * 以及各端历史地址到对象 key 的归一规则。
 */
class ProfileStorageServiceTest
{
    private Path root;
    private final ProfileStorageService storage = ProfileStorageService.localOnly();

    @BeforeEach
    void setUp() throws Exception
    {
        root = Files.createTempDirectory("profile-storage");
        new RuoYiConfig().setProfile(root.toString());
    }

    @AfterEach
    void tearDown() throws Exception
    {
        try (var walk = Files.walk(root))
        {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void normalizesLegacyUrlsToObjectKeys()
    {
        assertEquals("upload/2026/11/02/a.jpg", storage.keyOf("/profile/upload/2026/11/02/a.jpg"));
        assertEquals("upload/2026/10/19/a.png",
                storage.keyOf("http://10.0.2.2:8080/profile/upload/2026/10/19/a.png"));
        assertEquals("avatar/2026/11/02/a.png",
                storage.keyOf("https://www.eyhereal.site/prod-api/profile/avatar/2026/11/02/a.png"));
        assertEquals("upload/WPS图片.jpeg",
                storage.keyOf("/profile/upload/WPS%E5%9B%BE%E7%89%87.jpeg"));
        assertEquals("", storage.keyOf("https://example.com/seals/a.png"));
        assertEquals("", storage.keyOf("/profile/../../etc/passwd"));
    }

    @Test
    void writesReadsAndDeletesLocallyWhenMinioDisabled() throws Exception
    {
        assertFalse(storage.remoteEnabled());
        byte[] bytes = "正文".getBytes(StandardCharsets.UTF_8);
        storage.write("upload/2026/11/02/note.txt", bytes, "text/plain");

        File file = storage.materializeUrl("/profile/upload/2026/11/02/note.txt");
        assertTrue(file.isFile());
        assertArrayEquals(bytes, Files.readAllBytes(file.toPath()));
        assertArrayEquals(bytes, storage.readBytes("/profile/upload/2026/11/02/note.txt"));
        assertEquals("text/plain", storage.contentType("upload/2026/11/02/note.txt"));

        storage.deleteUrl("/profile/upload/2026/11/02/note.txt");
        assertFalse(storage.exists("upload/2026/11/02/note.txt"));
    }

    @Test
    void rejectsKeysEscapingProfileRoot() throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> storage.localPath("../outside.txt"));
        assertFalse(storage.exists("/profile/../../outside.txt"));
    }
}
