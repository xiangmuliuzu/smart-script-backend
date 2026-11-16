package com.smartscript.platform.user.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.framework.storage.ProfileStorageService;
import com.smartscript.platform.user.exception.AppAuthException;

/** Private identity material; never stored under the public profile directory. */
@Service
public class RealNameMaterialStorage {
    /**
     * 审核端下载用的最大长边。手机原图常见 2800px 以上，直接转 PNG 可达 5 MB，
     * 慢网络下后台下载容易中断；1600px 足够辨认证件信息。
     */
    private static final int MAX_IMAGE_SIDE = 1600;

    private final Path root;
    private final ProfileStorageService storage;

    @Autowired
    public RealNameMaterialStorage(@Value("${app.admin.material-storage-path:}") String root,
            @Value("${ruoyi.profile:}") String publicPath, ProfileStorageService storage) {
        this.storage = storage;
        if (root == null || root.isBlank()) {
            // 私有桶已配置时可只走 MinIO，不再要求本地材料目录
            if (!storage.privateRemoteEnabled()) throw new IllegalStateException("material storage path required");
            this.root = null;
            return;
        }
        this.root = Path.of(root).toAbsolutePath().normalize();
        if (publicPath != null && !publicPath.isBlank() && this.root.startsWith(Path.of(publicPath).toAbsolutePath().normalize()))
            throw new IllegalStateException("material storage must be private");
    }

    /** 单元测试保持原构造签名：只写本地私有目录。 */
    public RealNameMaterialStorage(String root, String publicPath) {
        this(root, publicPath, ProfileStorageService.localOnly());
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
                    if (!ImageIO.write(downscale(image), "png", output)) throw new AppAuthException(40000, 400, "图片内容无效");
                    return output.toByteArray();
                } finally { reader.dispose(); }
            }
        } catch (IOException e) { throw new AppAuthException(40000, 400, "图片内容无效"); }
    }

    /** 长边超过 1600px 时等比缩小，避免审核端下载数 MB 的原图。 */
    private static BufferedImage downscale(BufferedImage source) {
        int longestSide = Math.max(source.getWidth(), source.getHeight());
        if (longestSide <= MAX_IMAGE_SIDE) {
            return source;
        }
        double scale = (double) MAX_IMAGE_SIDE / longestSide;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int type = source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    public String save(Long userId, byte[] bytes) {
        String ref = "realname/" + userId + "/" + UUID.randomUUID() + ".png";
        final boolean remote = storage.privateRemoteEnabled();
        final Path file;
        if (remote) {
            try {
                storage.putPrivate(ref, bytes, "image/png");
            } catch (IOException e) {
                throw new AppAuthException(50000, 500, "材料保存失败，请重试");
            }
            file = null;
        } else {
            file = root.resolve(ref).normalize();
            if (!file.startsWith(root)) throw new IllegalStateException("invalid material path");
            try {
                Files.createDirectories(file.getParent());
                Files.write(file, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
            } catch (IOException e) { throw new AppAuthException(50000, 500, "材料保存失败，请重试"); }
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        if (remote) storage.deletePrivate(ref);
                        else try { Files.deleteIfExists(file); } catch (IOException ignored) { }
                    }
                }
            });
        }
        return ref;
    }
}
