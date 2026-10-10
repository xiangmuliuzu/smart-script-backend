package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.exception.AppAuthException;

/**
 * P09 / 缺陷 F01：头像上传内容校验。
 *
 * 扩展名与 MIME 由客户端可控；损坏或伪装成图片的内容必须在落盘前拒绝。
 * 合法四格式（JPG/PNG/GIF/BMP）在内容校验处放行（扩展名检查由
 * FileUploadUtils 另行执行，不在本用例范围）。
 */
class AppFileUploadServiceContentTest
{
    private static byte[] image(String format) throws IOException
    {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    void textDisguisedAsPngIsRejected()
    {
        byte[] corrupt = "this is definitely not an image".getBytes(StandardCharsets.UTF_8);
        AppAuthException e = assertThrows(AppAuthException.class,
                () -> AppFileUploadService.validateImageContent(corrupt));
        assertEquals(AppUserErrorCodes.PARAM, e.getCode());
        assertEquals(400, e.getHttpStatus());
    }

    @Test
    void unknownBinaryIsRejected()
    {
        byte[] bytes = new byte[512];
        for (int i = 0; i < bytes.length; i++)
        {
            bytes[i] = (byte) i;
        }
        assertThrows(AppAuthException.class, () -> AppFileUploadService.validateImageContent(bytes));
    }

    @Test
    void emptyContentIsRejected()
    {
        assertThrows(AppAuthException.class, () -> AppFileUploadService.validateImageContent(new byte[0]));
    }

    @Test
    void validPngJpgGifBmpPassContentValidation() throws IOException
    {
        assertDoesNotThrow(() -> AppFileUploadService.validateImageContent(image("png")));
        assertDoesNotThrow(() -> AppFileUploadService.validateImageContent(image("jpg")));
        assertDoesNotThrow(() -> AppFileUploadService.validateImageContent(image("gif")));
        assertDoesNotThrow(() -> AppFileUploadService.validateImageContent(image("bmp")));
    }

    @Test
    void truncatedPngBodyIsRejected() throws IOException
    {
        // 头部合法但正文截断：整体解码阶段必须拒绝，不能落盘后显示破图
        byte[] full = image("png");
        byte[] truncated = new byte[full.length / 2];
        System.arraycopy(full, 0, truncated, 0, truncated.length);
        assertThrows(AppAuthException.class, () -> AppFileUploadService.validateImageContent(truncated));
    }
}
