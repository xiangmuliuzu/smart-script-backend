package com.smartscript.platform.user.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.smartscript.platform.user.exception.AppAdminException;

/**
 * {@link MaterialGateway} 的文件系统实现。
 *
 * 目录约定：`app.admin.material-storage-path` 为材料根目录。
 * 引用是相对该根目录的路径（例如 `realname/9101/id-front.png`）。
 *
 * 安全约束：
 *   1. 解析后的真实路径必须仍位于根目录之内，否则按不存在处理（防路径穿越）。
 *   2. 根目录**不得**位于 `RuoYiConfig.getProfile()` 之下——该目录被映射为
 *      公开静态资源。启动时校验并在冲突时直接失败，避免误配置导致材料公开。
 *   3. 只读：本实现不提供任何写入能力。
 */
@Component
public class LocalFileMaterialGateway implements MaterialGateway
{
    private static final Logger log = LoggerFactory.getLogger(LocalFileMaterialGateway.class);

    private final Path root;

    public LocalFileMaterialGateway(
            @Value("${app.admin.material-storage-path:}") String configuredPath,
            @Value("${ruoyi.profile:}") String publicProfilePath)
    {
        if (configuredPath == null || configuredPath.isBlank())
        {
            throw new IllegalStateException(
                    "app.admin.material-storage-path is required for A4 material access");
        }
        this.root = Paths.get(configuredPath).toAbsolutePath().normalize();

        // 关键安全校验：材料根目录绝不能落在公开静态资源目录之下
        if (publicProfilePath != null && !publicProfilePath.isBlank())
        {
            Path publicRoot = Paths.get(publicProfilePath).toAbsolutePath().normalize();
            if (root.startsWith(publicRoot))
            {
                throw new IllegalStateException(
                        "material-storage-path must not be inside ruoyi.profile ("
                        + publicRoot + "), which is served publicly as /profile/**");
            }
        }
        log.info("a4-material gateway root configured (path not logged in full)");
    }

    /** 供测试与排障使用：返回已归一化的根目录。 */
    Path root()
    {
        return root;
    }

    @Override
    public boolean exists(String rawRef)
    {
        Path resolved = resolveSafely(rawRef);
        return resolved != null && Files.isRegularFile(resolved);
    }

    @Override
    public InputStream open(String rawRef) throws IOException
    {
        Path resolved = resolveSafely(rawRef);
        if (resolved == null || !Files.isRegularFile(resolved))
        {
            throw AppAdminException.notFound();
        }
        return Files.newInputStream(resolved);
    }

    @Override
    public long size(String rawRef)
    {
        Path resolved = resolveSafely(rawRef);
        if (resolved == null || !Files.isRegularFile(resolved))
        {
            return -1L;
        }
        try
        {
            return Files.size(resolved);
        }
        catch (IOException e)
        {
            return -1L;
        }
    }

    @Override
    public String contentType(String rawRef)
    {
        Path resolved = resolveSafely(rawRef);
        if (resolved == null)
        {
            return "application/octet-stream";
        }
        try
        {
            String probed = Files.probeContentType(resolved);
            return probed == null ? "application/octet-stream" : probed;
        }
        catch (IOException e)
        {
            return "application/octet-stream";
        }
    }

    /**
     * 解析引用为绝对路径，并确保仍位于根目录之内。
     *
     * 引用为空、含空字节、或归一化后越出根目录时返回 null（按不存在处理），
     * 不区分原因以免泄露存储布局。
     */
    private Path resolveSafely(String rawRef)
    {
        if (rawRef == null || rawRef.isBlank() || rawRef.indexOf('\0') >= 0)
        {
            return null;
        }
        // 统一分隔符：库中可能存 / 或 \，归一后再做越界判断
        String normalizedRef = rawRef.replace('\\', '/');

        // 拒绝绝对路径与显式上跳，避免绕过根目录约束
        if (normalizedRef.startsWith("/") || normalizedRef.contains(".."))
        {
            return null;
        }
        Path resolved = root.resolve(normalizedRef).normalize();
        if (!resolved.startsWith(root))
        {
            return null;
        }
        return resolved;
    }
}
