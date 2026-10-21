package com.ruoyi.web.controller.common;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;

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

            // 移除 /profile 前缀
            String filePath = requestPath.substring("/profile/".length());
            // 统一分隔符，防止 Windows 下混用 / \
            filePath = filePath.replace('\\', '/');
            
            // 安全检查
            if (!FileUtils.checkAllowDownload(filePath))
            {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "文件访问被拒绝");
                return;
            }
            
            // 构造完整的文件系统路径
            String fullPath = RuoYiConfig.getProfile() + File.separator + filePath.replace('/', File.separatorChar);
            File file = new File(fullPath);
            
            if (!file.exists() || !file.isFile())
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
            
            // 根据文件类型设置Content-Type
            String contentType = getContentType(extension);
            response.setContentType(contentType);
            
            // 对于文本文件，明确设置UTF-8编码
            if (isTextFile(extension)) {
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            }
            
            // 设置文件大小
            response.setContentLengthLong(file.length());
            
            // 读取并写入文件内容
            Files.copy(file.toPath(), response.getOutputStream());
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
