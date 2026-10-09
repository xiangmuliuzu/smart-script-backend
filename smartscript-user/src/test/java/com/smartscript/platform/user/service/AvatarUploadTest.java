package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.framework.config.ServerConfig;
import com.smartscript.platform.user.exception.AppAuthException;

class AvatarUploadTest
{
    @TempDir Path root;

    private byte[] image(String format) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), format, out));
        return out.toByteArray();
    }

    @Test
    void longOriginalNamesProduceShortSaveablePathsAndPreserveBytes() throws Exception
    {
        String previous = RuoYiConfig.getProfile();
        new RuoYiConfig().setProfile(root.toString().replace('\\', '/'));
        try
        {
            ServerConfig server = mock(ServerConfig.class);
            when(server.getUrl()).thenReturn("https://api.example.test");
            AppFileUploadService service = new AppFileUploadService(server);
            for (String format : new String[] { "png", "jpg", "gif", "bmp" })
            {
                byte[] bytes = image(format);
                Map<String, Object> result = service.uploadImage(new MockMultipartFile("file",
                        "a".repeat(95) + "." + format, "image/" + format, bytes));
                String path = (String) result.get("path");
                assertTrue(path.matches("/profile/upload/\\d{4}/\\d{2}/\\d{2}/[a-f0-9]{32}\\." + format));
                assertTrue(path.length() <= 100);
                assertEquals(path, AppUserProfileService.normalizeAvatar((String) result.get("url")));
                assertArrayEquals(bytes, Files.readAllBytes(root.resolve(path.substring("/profile/".length()))));
            }
        }
        finally { new RuoYiConfig().setProfile(previous); }
    }

    @Test
    void legacyPngNamedJpgUsesActualFormat() throws Exception
    {
        String previous = RuoYiConfig.getProfile();
        new RuoYiConfig().setProfile(root.toString().replace('\\', '/'));
        try
        {
            ServerConfig server = mock(ServerConfig.class);
            when(server.getUrl()).thenReturn("http://localhost:8080");
            Map<String, Object> result = new AppFileUploadService(server).uploadImage(
                    new MockMultipartFile("file", "avatar.jpg", "image/jpeg", image("png")));
            assertTrue(((String) result.get("path")).endsWith(".png"));
        }
        finally { new RuoYiConfig().setProfile(previous); }
    }

    @Test
    void validImageWithUnsupportedOriginalExtensionIsRejected() throws Exception
    {
        assertThrows(AppAuthException.class, () -> new AppFileUploadService(mock(ServerConfig.class)).uploadImage(
                new MockMultipartFile("file", "avatar.txt", "image/png", image("png"))));
    }
}
