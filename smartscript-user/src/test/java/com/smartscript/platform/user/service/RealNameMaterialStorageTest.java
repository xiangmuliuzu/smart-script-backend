package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import com.smartscript.platform.user.exception.AppAuthException;

class RealNameMaterialStorageTest {
    @TempDir Path root;
    @Test void privateImagesAreValidatedAndStoredUnderTheOwner() throws Exception {
        var storage = new RealNameMaterialStorage(root.toString(), root.resolveSibling("public").toString());
        var png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", png);
        var bytes = storage.validate(new MockMultipartFile("front", png.toByteArray()));
        String ref = storage.save(42L, bytes);
        assertTrue(ref.startsWith("realname/42/"));
        assertTrue(Files.exists(root.resolve(ref)));
        assertThrows(AppAuthException.class, () -> storage.validate(new MockMultipartFile("front", new byte[]{1, 2, 3})));
        assertThrows(AppAuthException.class, () -> storage.validate(null));
        assertThrows(IllegalStateException.class, () -> new RealNameMaterialStorage(root.toString(), root.toString()));
    }
}
