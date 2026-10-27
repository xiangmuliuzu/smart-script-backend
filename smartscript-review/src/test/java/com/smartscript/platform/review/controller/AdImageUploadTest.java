package com.smartscript.platform.review.controller;

import com.ruoyi.common.exception.file.InvalidExtensionException;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class AdImageUploadTest {
    @Test void optimizedSplashImagePassesCommonAndImageUploadValidation() {
        var webp = new MockMultipartFile("file", "splash.webp", "image/webp", new byte[] {1});
        assertDoesNotThrow(() -> FileUploadUtils.assertAllowed(webp, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION));
        assertDoesNotThrow(() -> FileUploadUtils.assertAllowed(webp, MimeTypeUtils.IMAGE_EXTENSION));
        var unnamed = new MockMultipartFile("file", "splash", "image/webp", new byte[] {1});
        assertEquals("webp", FileUploadUtils.getExtension(unnamed));
    }

    @Test void executableUploadsRemainRejected() {
        var executable = new MockMultipartFile("file", "splash.exe", "application/octet-stream", new byte[] {1});
        assertThrows(InvalidExtensionException.class,
                () -> FileUploadUtils.assertAllowed(executable, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION));
    }
}
