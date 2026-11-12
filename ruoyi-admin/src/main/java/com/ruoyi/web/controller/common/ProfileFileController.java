package com.ruoyi.web.controller.common;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.framework.storage.ProfileAccessSigner;
import com.ruoyi.framework.storage.ProfileStorageService;

/**
 * Profile文件访问控制器
 * 处理 /profile/** 路径的文件访问，设置正确的Content-Type和字符编码
 * 
 * @author smartscript
 */
@RestController
@RequestMapping("/profile")
public class ProfileFileController
{
    private static final Logger log = LoggerFactory.getLogger(ProfileFileController.class);

    private final ProfileStorageService storage;

    private final ProfileAccessSigner accessSigner;

    /** 单测/无 Spring 环境：只读本地 profile 目录。 */
    public ProfileFileController()
    {
        this(ProfileStorageService.localOnly(), new ProfileAccessSigner("profile-file-controller-test-secret"));
    }

    @Autowired
    public ProfileFileController(ProfileStorageService storage, ProfileAccessSigner accessSigner)
    {
        this.storage = storage;
        this.accessSigner = accessSigner;
    }

    /**
     * 访问上传的文件
     * 路径示例: /profile/upload/2026/10/20/file.txt
     */
    @GetMapping("/**")
    public void getFile(HttpServletResponse response, HttpServletRequest request) throws IOException
    {
        try
        {
            // Servlet getRequestURI 可能仍是百分号编码；中文文件名必须 UTF-8 解码后再拼磁盘路径，
            // 否则 File 找不到 → sendError → 转发 /error → 对外表现为 401。
            String requestPath = resolveProfileRequestPath(request);
            if (StringUtils.isEmpty(requestPath) || !requestPath.startsWith("/profile/"))
            {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
                return;
            }

            // 统一为对象 key（upload/2026/...）；MinIO 开启时本地缺失会按需回源。
            String filePath = storage.keyOf(requestPath);
            if (StringUtils.isEmpty(filePath))
            {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
                return;
            }

            // 安全检查
            if (!FileUtils.checkAllowDownload(filePath))
            {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "文件访问被拒绝");
                return;
            }

            // 合同材料是私有件（双方真名 + 成交金额），而 /profile/** 在安全配置里匿名可访问：
            // 没有未过期的签名就不给读，地址被转发或被爬虫顺走也不会长期有效。
            boolean guarded = ProfileAccessSigner.isGuarded(filePath);
            if (guarded && !accessSigner.verified(filePath,
                    request.getParameter(ProfileAccessSigner.PARAM_EXPIRE),
                    request.getParameter(ProfileAccessSigner.PARAM_SIGNATURE)))
            {
                // 直接置状态码，不能用 sendError：sendError 会转发到 /error，被安全入口改写成
                // HTTP 200 + {"code":401}，前端与爬虫都分不出「被拒」和「拿到文件」
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentLength(0);
                return;
            }

            File file;
            try
            {
                file = storage.materialize(filePath);
            }
            catch (IOException e)
            {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
                return;
            }

            // 获取文件扩展名
            String fileName = file.getName();
            String extension = "";
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                extension = fileName.substring(dotIndex + 1).toLowerCase();
            }

            // 没有 Cache-Control 时浏览器每次进列表都要重新拉整张原图。
            // 只给图片加长缓存：同目录还有合同 PDF 等私有件，不该被中间缓存留存。
            long lastModified = file.lastModified() / 1000L * 1000L;
            response.setDateHeader("Last-Modified", lastModified);
            if (guarded)
            {
                // 鉴权链接每次都带新的过期时间，中间缓存留着旧副本等于绕过有效期
                response.setHeader("Cache-Control", "private, no-store");
            }
            else if (isThumbnailSource(extension))
            {
                response.setHeader("Cache-Control", "public, max-age=2592000");
            }
            if (notModified(request, lastModified))
            {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }

            // 列表/卡片位置只需几百像素，直接回原图（手机截图常见 1080x2400、数 MB）是首屏慢的主因。
            // 超过上限边长的图片按需生成缩略图并落盘复用；其它文件、解码失败、本来就小的图走原文件。
            File served = thumbnailOf(file, filePath, extension);
            if (served == null)
            {
                served = file;
            }

            // 根据文件类型设置Content-Type
            String contentType = served == file ? getContentType(extension) : MediaType.IMAGE_JPEG_VALUE;
            response.setContentType(contentType);

            // 对于文本文件，明确设置UTF-8编码
            if (isTextFile(extension)) {
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            }

            // 设置文件大小
            response.setContentLengthLong(served.length());

            // 读取并写入文件内容
            Files.copy(served.toPath(), response.getOutputStream());
            response.getOutputStream().flush();
        }
        catch (Exception e)
        {
            log.error("访问文件失败: {}", request.getRequestURI(), e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "文件访问失败");
            }
        }
    }
    
    /** 缩略图目录：与 uploadPath 同级，不进版本库（原图仍在 profile 根下）。 */
    private static final String THUMB_DIR = ".thumbnails";

    /**
     * 下发图片的最长边上限（px）。
     *
     * 页面上封面/头像最大也就 200 CSS px（约 400 物理像素），1280 足够且留了放大余地；
     * 再大的原图在下发时缩到这个尺寸，体积通常降到 1/5 ~ 1/30。
     */
    private static final int MAX_SERVE_EDGE = 1280;

    /** 小于该体积的原图不值得再压一遍。 */
    private static final int THUMB_MIN_SOURCE_BYTES = 40 * 1024;

    /**
     * 超过该像素数（约 5000 万）的原图不做缩略，直接回原文件。
     *
     * 解码一张图要 width*height*4 字节堆内存，封面目录没有像素上限校验，
     * 任由超大图解码会把共享 JVM 拖进 OOM——那比慢更糟。
     */
    private static final long THUMB_MAX_SOURCE_PIXELS = 50_000_000L;

    /**
     * 缩略图：首次按需生成并落盘，后续直接复用同一文件。
     *
     * 只对 jpg/jpeg/png/webp/bmp 且体积超过 {@link #THUMB_MIN_SOURCE_BYTES} 的原图生效；
     * 生成失败（损坏文件、扩展名与实际格式不符）返回 null，调用方回落原图，不对外报错。
     * 未缩小的图片（本来就比上限小）同样返回 null。
     *
     * @return 可直接输出的 JPEG 文件，null 表示走原图
     */
    static File thumbnailOf(File source, String relativePath, String extension)
    {
        if (source.length() <= THUMB_MIN_SOURCE_BYTES || !isThumbnailSource(extension))
        {
            return null;
        }
        File target = new File(new File(RuoYiConfig.getProfile(), THUMB_DIR),
                relativePath.replace('/', File.separatorChar) + ".w" + MAX_SERVE_EDGE + ".jpg");
        // 原图被重新上传覆盖（同路径不同内容）时按修改时间失效，避免发出过期缩略图
        if (target.isFile() && target.lastModified() >= source.lastModified())
        {
            return target;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
        {
            return null;
        }
        File temp = new File(parent, target.getName() + "." + Thread.currentThread().getId() + ".tmp");
        try
        {
            if (!writeThumbnail(source, temp, MAX_SERVE_EDGE))
            {
                return null;
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            target.setLastModified(source.lastModified());
            return target;
        }
        catch (IOException e)
        {
            log.warn("生成缩略图失败: {}", source.getName());
            return null;
        }
        finally
        {
            if (temp.exists() && !temp.delete())
            {
                log.debug("临时缩略图清理失败: {}", temp.getName());
            }
        }
    }

    private static boolean isThumbnailSource(String extension)
    {
        return "jpg".equals(extension) || "jpeg".equals(extension) || "png".equals(extension)
                || "webp".equals(extension) || "bmp".equals(extension);
    }

    /**
     * 等比缩放并编码为 JPEG 写入目标文件；源图无法解码时返回 false。
     */
    private static boolean writeThumbnail(File source, File target, int width) throws IOException
    {
        BufferedImage decoded;
        try (ImageInputStream imageIn = ImageIO.createImageInputStream(source))
        {
            if (imageIn == null)
            {
                return false;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageIn);
            if (!readers.hasNext())
            {
                return false;
            }
            ImageReader reader = readers.next();
            try
            {
                reader.setInput(imageIn, true, true);
                // 先只取尺寸：超大图不解码，避免一张图吃掉整块堆
                if ((long) reader.getWidth(0) * reader.getHeight(0) > THUMB_MAX_SOURCE_PIXELS)
                {
                    return false;
                }
                decoded = reader.read(0);
            }
            finally
            {
                reader.dispose();
            }
        }
        if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0)
        {
            return false;
        }
        // 按最长边收敛：竖版手机截图宽 1080 但高 2400，只比宽度会漏掉
        double scale = Math.min(1d, width / (double) Math.max(decoded.getWidth(), decoded.getHeight()));
        if (scale >= 1d)
        {
            // 原图本来就在上限内，再缩只会变大
            return false;
        }
        int targetWidth = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
        // 统一转 RGB：PNG 的 alpha 直接写 JPEG 会变黑底
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(decoded, 0, 0, targetWidth, targetHeight, null);
        }
        finally
        {
            graphics.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(target))
        {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.82f);
            writer.write(null, new IIOImage(scaled, null, null), param);
        }
        finally
        {
            writer.dispose();
        }
        return target.isFile() && target.length() > 0;
    }

    /**
     * If-Modified-Since 命中判断（秒级精度，与 Last-Modified 对齐）。
     */
    private static boolean notModified(HttpServletRequest request, long lastModified)
    {
        long since = request.getDateHeader("If-Modified-Since");
        return since > 0 && lastModified <= since;
    }

    /**
     * 解析 /profile/** 请求路径：去掉 contextPath，并按 UTF-8 解码百分号编码。
     */
    private static String resolveProfileRequestPath(HttpServletRequest request)
    {
        String uri = request.getRequestURI();
        if (uri == null)
        {
            return "";
        }
        String contextPath = request.getContextPath();
        if (StringUtils.isNotEmpty(contextPath) && uri.startsWith(contextPath))
        {
            uri = uri.substring(contextPath.length());
        }
        // 已是解码后的中文时，UriUtils.decode 会原样返回；仍是 %XX 时按 UTF-8 还原
        return UriUtils.decode(uri, StandardCharsets.UTF_8);
    }

    /**
     * 判断是否为文本文件
     */
    private boolean isTextFile(String extension)
    {
        return "txt".equals(extension) || 
               "json".equals(extension) || 
               "xml".equals(extension) || 
               "html".equals(extension) ||
               "htm".equals(extension) ||
               "css".equals(extension) ||
               "js".equals(extension) ||
               "md".equals(extension) ||
               "log".equals(extension);
    }
    
    /**
     * 根据文件扩展名获取Content-Type
     */
    private String getContentType(String extension)
    {
        switch (extension.toLowerCase())
        {
            // 文本文件
            case "txt":
                return "text/plain;charset=UTF-8";
            case "html":
            case "htm":
                return "text/html;charset=UTF-8";
            case "css":
                return "text/css;charset=UTF-8";
            case "js":
                return "application/javascript;charset=UTF-8";
            case "json":
                return "application/json;charset=UTF-8";
            case "xml":
                return "application/xml;charset=UTF-8";
            case "md":
                return "text/markdown;charset=UTF-8";
            
            // 图片文件
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "webp":
                return "image/webp";
            case "svg":
                return "image/svg+xml";
            case "ico":
                return "image/x-icon";
            
            // 文档文件
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            
            // 压缩文件
            case "zip":
                return "application/zip";
            case "rar":
                return "application/x-rar-compressed";
            case "7z":
                return "application/x-7z-compressed";
            case "tar":
                return "application/x-tar";
            case "gz":
                return "application/gzip";
            
            // 音视频文件
            case "mp3":
                return "audio/mpeg";
            case "mp4":
                return "video/mp4";
            case "avi":
                return "video/x-msvideo";
            case "mov":
                return "video/quicktime";
            
            // 默认
            default:
                return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}
