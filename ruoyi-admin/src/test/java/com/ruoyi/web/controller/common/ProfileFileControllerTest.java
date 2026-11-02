package com.ruoyi.web.controller.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.ruoyi.common.config.RuoYiConfig;

/**
 * 封面上传后按需缩略 + 可缓存下发：直接回原图是列表首屏慢的主因，
 * 这里锁定「大图缩到上限内」「小图/非图片原样回」「带 Last-Modified/Cache-Control」「二次请求 304」四条口径。
 */
class ProfileFileControllerTest
{
    private File root;
    private final ProfileFileController controller = new ProfileFileController();

    @BeforeEach
    void setUp() throws Exception
    {
        root = Files.createTempDirectory("profile-thumb").toFile();
        RuoYiConfig config = new RuoYiConfig();
        config.setProfile(root.getAbsolutePath());
    }

    @AfterEach
    void tearDown()
    {
        delete(root);
    }

    @Test
    void shrinksOversizedPhotoAndCachesIt() throws Exception
    {
        // 与真实上传一致：手机截图 1080 宽，远大于页面上封面的显示尺寸
        writeImage(new File(root, "upload/2026/10/27/big.png"), 1080, 2400, "png");

        MockHttpServletResponse response = get("/profile/upload/2026/10/27/big.png", null);
        assertEquals(200, response.getStatus());
        assertEquals("image/jpeg", response.getContentType());
        assertEquals("public, max-age=2592000", response.getHeader("Cache-Control"));
        BufferedImage served = ImageIO.read(new java.io.ByteArrayInputStream(response.getContentAsByteArray()));
        assertEquals(1280, served.getHeight(), "超过上限的原图必须缩到最长边上限");
        assertEquals(576, served.getWidth());
        assertTrue(response.getContentAsByteArray().length < new File(root, "upload/2026/10/27/big.png").length(),
                "缩略后的字节数必须小于原图");
    }

    @Test
    void returnsNotModifiedWhenClientAlreadyHasIt() throws Exception
    {
        writeImage(new File(root, "upload/2026/10/27/big.png"), 1080, 2400, "png");
        MockHttpServletResponse first = get("/profile/upload/2026/10/27/big.png", null);

        MockHttpServletResponse second = get("/profile/upload/2026/10/27/big.png",
                first.getHeader("Last-Modified"));

        assertEquals(304, second.getStatus());
        assertEquals(0, second.getContentAsByteArray().length, "命中缓存不能再发图片正文");
    }

    @Test
    void leavesSmallImagesAndNonImagesUntouched() throws Exception
    {
        writeImage(new File(root, "upload/2026/10/27/small.png"), 600, 848, "png");
        Files.writeString(new File(root, "upload/2026/10/27/note.txt").toPath(), "正文");

        MockHttpServletResponse image = get("/profile/upload/2026/10/27/small.png", null);
        assertEquals("image/png", image.getContentType());
        assertEquals(new File(root, "upload/2026/10/27/small.png").length(), image.getContentAsByteArray().length,
                "本来就小的图不应被重新编码");

        MockHttpServletResponse text = get("/profile/upload/2026/10/27/note.txt", null);
        assertEquals("正文", text.getContentAsString());
    }

    @Test
    void thumbnailFallsBackWhenSourceIsNotADecodableImage() throws Exception
    {
        File broken = new File(root, "upload/2026/10/27/broken.png");
        Files.createDirectories(broken.getParentFile().toPath());
        // 扩展名是图片、内容不是：必须回落原图而不是报错
        Files.write(broken.toPath(), new byte[] { 1, 2, 3, 4 });
        assertNull(ProfileFileController.thumbnailOf(broken, "upload/2026/10/27/broken.png", "png"));
    }

    private MockHttpServletResponse get(String uri, String ifModifiedSince) throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (ifModifiedSince != null)
        {
            request.addHeader("If-Modified-Since", ifModifiedSince);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.getFile(response, request);
        return response;
    }

    private static void writeImage(File file, int width, int height, String format) throws Exception
    {
        Files.createDirectories(file.getParentFile().toPath());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++)
        {
            for (int y = 0; y < height; y++)
            {
                image.setRGB(x, y, (x * 31 + y * 17) % 0xFFFFFF);
            }
        }
        ImageIO.write(image, format, file);
    }

    private static void delete(File file)
    {
        File[] children = file.listFiles();
        if (children != null)
        {
            for (File child : children)
            {
                delete(child);
            }
        }
        file.delete();
    }
}
