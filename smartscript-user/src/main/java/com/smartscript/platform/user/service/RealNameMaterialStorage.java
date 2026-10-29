package com.smartscript.platform.user.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.smartscript.platform.user.exception.AppAuthException;

/** Private identity material; never stored under the public profile directory. */
@Service
public class RealNameMaterialStorage {
    private final Path root;
    public RealNameMaterialStorage(@Value("${app.admin.material-storage-path:}") String root,
            @Value("${ruoyi.profile:}") String publicPath) {
        if (root == null || root.isBlank()) throw new IllegalStateException("material storage path required");
        this.root = Path.of(root).toAbsolutePath().normalize();
        if (publicPath != null && !publicPath.isBlank() && this.root.startsWith(Path.of(publicPath).toAbsolutePath().normalize()))
            throw new IllegalStateException("material storage must be private");
    }
    public byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new AppAuthException(40000, 400, "请上传身份证正反面");
        if (file.getSize() > 5L * 1024 * 1024) throw new AppAuthException(41300, 413, "图片不能超过 5 MB");
        try {
            byte[] bytes = file.getBytes();
            try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) throw new AppAuthException(40000, 400, "图片格式无效");
                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase();
                    if (!java.util.Set.of("jpeg", "png", "gif", "bmp").contains(format)) throw new AppAuthException(40000, 400, "图片格式无效");
                    reader.setInput(in, true, true);
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > 25000000L)
                        throw new AppAuthException(40000, 400, "图片尺寸或内容无效");
                    var image = reader.read(0);
                    if (image == null) throw new AppAuthException(40000, 400, "图片内容无效");
                    var output = new ByteArrayOutputStream();
                    if (!ImageIO.write(image, "png", output)) throw new AppAuthException(40000, 400, "图片内容无效");
                    return output.toByteArray();
                } finally { reader.dispose(); }
            }
        } catch (IOException e) { throw new AppAuthException(40000, 400, "图片内容无效"); }
    }
    public String save(Long userId, byte[] bytes) {
        String ref = "realname/" + userId + "/" + UUID.randomUUID() + ".png";
        Path file = root.resolve(ref).normalize();
        if (!file.startsWith(root)) throw new IllegalStateException("invalid material path");
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (IOException e) { throw new AppAuthException(50000, 500, "材料保存失败，请重试"); }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) try { Files.deleteIfExists(file); } catch (IOException ignored) { }
                }
            });
        }
        return ref;
    }
}
