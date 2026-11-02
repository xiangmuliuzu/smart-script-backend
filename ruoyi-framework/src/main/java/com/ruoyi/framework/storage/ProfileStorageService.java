package com.ruoyi.framework.storage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import com.ruoyi.common.config.RuoYiConfig;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.Result;
import io.minio.messages.Item;

/**
 * /profile 资源存储：本地磁盘 + 可选 MinIO 同步。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>数据库仍然只保存 {@code /profile/<key>} 相对地址，App/网页端无需改动；</li>
 *   <li>上传先落本地（保留文件锁、PDF 渲染等现有实现），随后同步到 MinIO；</li>
 *   <li>读取时本地优先；本机没有时从 MinIO 拉取并落本地缓存，解决“文件只在上传者机器”；</li>
 *   <li>MinIO 未启用时所有方法退化为纯本地磁盘，行为与历史版本一致。</li>
 * </ul>
 */
@Service
public class ProfileStorageService
{
    private static final Logger log = LoggerFactory.getLogger(ProfileStorageService.class);

    private static final String PROFILE_PREFIX = "/profile/";

    /** 本地与 MinIO 的修改时间允许的偏差（上传落盘与对象写入之间有几毫秒差）。 */
    private static final long FRESH_TOLERANCE_MS = 2000L;

    private final ObjectProvider<MinioClient> minioClientProvider;

    private final MinioProperties properties;

    public ProfileStorageService(ObjectProvider<MinioClient> minioClientProvider,
            MinioProperties properties)
    {
        this.minioClientProvider = minioClientProvider;
        this.properties = properties;
    }

    /** 单测/无 Spring 环境使用：只读写本地目录。 */
    public static ProfileStorageService localOnly()
    {
        return new ProfileStorageService(null, new MinioProperties());
    }

    /** MinIO 是否真正可用。 */
    public boolean remoteEnabled()
    {
        return minioClientProvider != null && minioClientProvider.getIfAvailable() != null;
    }

    /** 私有材料是否走 MinIO。 */
    public boolean privateRemoteEnabled()
    {
        return remoteEnabled() && notBlank(properties.getPrivateBucket());
    }

    // ==================== 路径与键 ====================

    /**
     * 把数据库/HTML 中的地址归一为对象 key。
     *
     * <p>支持 {@code /profile/upload/...}、{@code https://host/prod-api/profile/...}、
     * 历史 {@code http://10.0.2.2:8080/profile/...}；外部 CDN 地址返回空串，表示不归本存储管理。</p>
     */
    public String keyOf(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        String value = raw.trim();
        if (value.isEmpty())
        {
            return "";
        }
        int query = value.indexOf('?');
        if (query >= 0)
        {
            value = value.substring(0, query);
        }
        int fragment = value.indexOf('#');
        if (fragment >= 0)
        {
            value = value.substring(0, fragment);
        }

        int profileIndex = value.indexOf(PROFILE_PREFIX);
        if (profileIndex >= 0)
        {
            value = value.substring(profileIndex + PROFILE_PREFIX.length());
        }
        else if (value.startsWith("profile/"))
        {
            value = value.substring("profile/".length());
        }
        else if (value.startsWith("/"))
        {
            value = value.substring(1);
        }
        else if (value.contains("://"))
        {
            // 外部地址，例如 https://example.com/a.png
            return "";
        }

        try
        {
            value = UriUtils.decode(value, StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException ignored)
        {
            // 已经解码过或包含非法百分号时按原文处理
        }
        value = value.replace('\\', '/');
        return normalizeKey(value);
    }

    /** 对象 key 转数据库/HTML 使用的相对地址。 */
    public String urlOf(String key)
    {
        String normalized = normalizeKey(key);
        return normalized.isEmpty() ? "" : PROFILE_PREFIX + normalized;
    }

    /** 本地文件路径；拒绝越出 ruoyi.profile 的键。 */
    public Path localPath(String key)
    {
        String normalized = normalizeKey(key);
        if (normalized.isEmpty())
        {
            throw new IllegalArgumentException("storage key is empty");
        }
        Path root = Paths.get(RuoYiConfig.getProfile()).toAbsolutePath().normalize();
        Path resolved = root.resolve(normalized.replace('/', File.separatorChar)).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root))
        {
            throw new IllegalArgumentException("storage key escapes profile root");
        }
        return resolved;
    }

    // ==================== 写入 ====================

    /** 上传完成后调用：把刚写入本地 profile 的文件同步到 MinIO。 */
    public void publishUrl(String url) throws IOException
    {
        String key = keyOf(url);
        if (!key.isEmpty())
        {
            publishKey(key);
        }
    }

    public void publishKey(String key) throws IOException
    {
        if (!remoteEnabled())
        {
            return;
        }
        Path local = localPath(key);
        if (!Files.isRegularFile(local))
        {
            throw new IOException("uploaded file not found: " + key);
        }
        upload(local.toFile(), key, contentType(key));
        // 以同步完成时间作为本地缓存版本，避免下一次读取误判为过期而重复下载
        touchQuietly(local);
    }

    /** 写本地并同步 MinIO（合同 PDF、分页图、签名图等多处直接写文件的场景）。 */
    public void write(String key, byte[] bytes, String contentType) throws IOException
    {
        String normalized = normalizeKey(key);
        Path local = localPath(normalized);
        Files.createDirectories(local.getParent());
        Path temp = Files.createTempFile(local.getParent(), ".write-", ".tmp");
        try
        {
            Files.write(temp, bytes);
            Files.move(temp, local, StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(local, FileTime.fromMillis(System.currentTimeMillis()));
        }
        finally
        {
            Files.deleteIfExists(temp);
        }
        if (remoteEnabled())
        {
            upload(new ByteArrayInputStream(bytes), normalized, bytes.length,
                    contentType == null ? contentType(normalized) : contentType);
            touchQuietly(local);
        }
    }

    // ==================== 读取 ====================

    /**
     * 返回本地可读文件：本地存在且不比 MinIO 旧时直接复用，否则从 MinIO 拉取。
     *
     * <p>MinIO 不可达但本地有缓存时降级使用本地副本，避免对象存储抖动导致全站破图。</p>
     */
    public File materialize(String key) throws IOException
    {
        Path local = localPath(key);
        if (!remoteEnabled())
        {
            if (!Files.isRegularFile(local))
            {
                throw new IOException("file not found: " + key);
            }
            return local.toFile();
        }

        // 上传目录/头像按 Seq/UUID 命名，key 不复用，本地存在即为有效缓存，无需每次 HEAD。
        if (isImmutableKey(key) && Files.isRegularFile(local))
        {
            return local.toFile();
        }

        MinioClient client = client();
        try
        {
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket()).object(key).build());
            long remoteMillis = stat.lastModified() == null
                    ? 0L : stat.lastModified().toInstant().toEpochMilli();
            if (Files.isRegularFile(local)
                    && Files.getLastModifiedTime(local).toMillis() + FRESH_TOLERANCE_MS >= remoteMillis)
            {
                return local.toFile();
            }
            download(client, key, local, remoteMillis);
            return local.toFile();
        }
        catch (Exception e)
        {
            if (Files.isRegularFile(local))
            {
                log.warn("minio read failed, fallback to local cache: {}", key);
                return local.toFile();
            }
            throw new IOException("object not found: " + key, e);
        }
    }

    public File materializeUrl(String url) throws IOException
    {
        String key = keyOf(url);
        if (key.isEmpty())
        {
            throw new IOException("external resource is not managed by profile storage: " + url);
        }
        return materialize(key);
    }

    public byte[] readBytes(String url) throws IOException
    {
        return Files.readAllBytes(materializeUrl(url).toPath());
    }

    public InputStream open(String key) throws IOException
    {
        return new FileInputStream(materialize(key));
    }

    public boolean exists(String key)
    {
        try
        {
            if (Files.isRegularFile(localPath(key)))
            {
                return true;
            }
        }
        catch (RuntimeException ignored)
        {
            return false;
        }
        if (!remoteEnabled())
        {
            return false;
        }
        try
        {
            client().statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket()).object(key).build());
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public String contentType(String key)
    {
        return MediaTypeFactory.getMediaType(key)
                .map(MediaType::toString)
                .orElse("application/octet-stream");
    }

    // ==================== 删除 ====================

    public void deleteUrl(String url)
    {
        String key = keyOf(url);
        if (!key.isEmpty())
        {
            delete(key);
        }
    }

    public void delete(String key)
    {
        try
        {
            Files.deleteIfExists(localPath(key));
        }
        catch (Exception e)
        {
            log.warn("local delete failed: {}", key);
        }
        if (!remoteEnabled())
        {
            return;
        }
        try
        {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket()).object(key).build());
        }
        catch (Exception e)
        {
            log.warn("minio delete failed: {}", key);
        }
    }

    /**
     * 删除指定前缀的对象（合同重新上传时清理 page_/source_ 产物）。
     *
     * @param prefix 形如 {@code contract/1/page_}；最后一段为文件名前缀
     */
    public void deletePrefix(String prefix)
    {
        String normalized = normalizeKey(prefix);
        int slash = normalized.lastIndexOf('/');
        String parent = slash < 0 ? "" : normalized.substring(0, slash);
        String leaf = slash < 0 ? normalized : normalized.substring(slash + 1);
        if (!parent.isEmpty())
        {
            try
            {
                Path dir = localPath(parent);
                if (Files.isDirectory(dir))
                {
                    try (Stream<Path> children = Files.list(dir))
                    {
                        List<Path> matches = children
                                .filter(p -> p.getFileName().toString().startsWith(leaf))
                                .toList();
                        for (Path match : matches)
                        {
                            Files.deleteIfExists(match);
                        }
                    }
                }
            }
            catch (Exception e)
            {
                log.warn("local prefix delete failed: {}", prefix);
            }
        }
        if (!remoteEnabled())
        {
            return;
        }
        try
        {
            for (String key : listKeys(normalized))
            {
                client().removeObject(RemoveObjectArgs.builder()
                        .bucket(properties.getBucket()).object(key).build());
            }
        }
        catch (Exception e)
        {
            log.warn("minio prefix delete failed: {}", prefix);
        }
    }

    // ==================== 私有材料 ====================

    public InputStream openPrivate(String ref) throws IOException
    {
        requirePrivate();
        try
        {
            GetObjectResponse response = client().getObject(GetObjectArgs.builder()
                    .bucket(properties.getPrivateBucket()).object(privateKey(ref)).build());
            return response;
        }
        catch (Exception e)
        {
            throw new IOException("private object not found", e);
        }
    }

    public boolean existsPrivate(String ref)
    {
        if (!privateRemoteEnabled())
        {
            return false;
        }
        try
        {
            client().statObject(StatObjectArgs.builder()
                    .bucket(properties.getPrivateBucket()).object(privateKey(ref)).build());
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public long sizePrivate(String ref)
    {
        if (!privateRemoteEnabled())
        {
            return -1L;
        }
        try
        {
            return client().statObject(StatObjectArgs.builder()
                    .bucket(properties.getPrivateBucket()).object(privateKey(ref)).build()).size();
        }
        catch (Exception e)
        {
            return -1L;
        }
    }

    public String contentTypePrivate(String ref)
    {
        if (!privateRemoteEnabled())
        {
            return "application/octet-stream";
        }
        try
        {
            String type = client().statObject(StatObjectArgs.builder()
                    .bucket(properties.getPrivateBucket()).object(privateKey(ref)).build()).contentType();
            return type == null || type.isBlank() ? "application/octet-stream" : type;
        }
        catch (Exception e)
        {
            return "application/octet-stream";
        }
    }

    public void putPrivate(String ref, byte[] bytes, String contentType) throws IOException
    {
        requirePrivate();
        try
        {
            client().putObject(PutObjectArgs.builder()
                    .bucket(properties.getPrivateBucket())
                    .object(privateKey(ref))
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        }
        catch (Exception e)
        {
            throw new IOException("private object upload failed", e);
        }
    }

    public void deletePrivate(String ref)
    {
        if (!privateRemoteEnabled())
        {
            return;
        }
        try
        {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getPrivateBucket()).object(privateKey(ref)).build());
        }
        catch (Exception e)
        {
            log.warn("minio private delete failed");
        }
    }

    // ==================== 内部 ====================

    private MinioClient client()
    {
        MinioClient client = minioClientProvider == null ? null : minioClientProvider.getIfAvailable();
        if (client == null)
        {
            throw new IllegalStateException("minio client is not configured");
        }
        return client;
    }

    private void upload(File local, String key, String contentType) throws IOException
    {
        try (InputStream in = Files.newInputStream(local.toPath()))
        {
            upload(in, key, Files.size(local.toPath()), contentType);
        }
    }

    private void upload(InputStream in, String key, long size, String contentType) throws IOException
    {
        try
        {
            client().putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .stream(in, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        }
        catch (Exception e)
        {
            throw new IOException("minio upload failed: " + key, e);
        }
    }

    private void download(MinioClient client, String key, Path local, long remoteMillis) throws Exception
    {        Files.createDirectories(local.getParent());
        Path temp = Files.createTempFile(local.getParent(), ".read-", ".tmp");
        try
        {
            try (GetObjectResponse in = client.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket()).object(key).build()))
            {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temp, local, StandardCopyOption.REPLACE_EXISTING);
            if (remoteMillis > 0)
            {
                Files.setLastModifiedTime(local, FileTime.fromMillis(remoteMillis));
            }
        }
        finally
        {
            Files.deleteIfExists(temp);
        }
    }

    private static boolean isImmutableKey(String key)
    {
        return key.startsWith("upload/") || key.startsWith("avatar/");
    }

    private static void touchQuietly(Path file)
    {
        try
        {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        }
        catch (IOException e)
        {
            log.warn("local cache touch failed: {}", file.getFileName());
        }
    }

    private List<String> listKeys(String prefix)
    {
        List<String> keys = new ArrayList<>();
        try
        {
            Iterable<Result<Item>> results = client().listObjects(ListObjectsArgs.builder()
                    .bucket(properties.getBucket()).prefix(prefix).recursive(true).build());
            for (Result<Item> result : results)
            {
                keys.add(result.get().objectName());
            }
        }
        catch (Exception e)
        {
            log.warn("minio list failed for prefix");
        }
        return keys;
    }

    private void requirePrivate()
    {
        if (!privateRemoteEnabled())
        {
            throw new IllegalStateException("minio private bucket is not configured");
        }
    }

    /** 私有材料引用校验：只允许相对 key，不接受绝对路径和上跳。 */
    private static String privateKey(String ref)
    {
        String normalized = normalizeKey(ref);
        if (normalized.isEmpty())
        {
            throw new IllegalArgumentException("private storage key is empty");
        }
        return normalized;
    }

    private static String normalizeKey(String rawKey)
    {
        if (rawKey == null)
        {
            return "";
        }
        String value = rawKey.trim().replace('\\', '/');
        if (value.isEmpty() || value.startsWith("/"))
        {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String part : value.split("/"))
        {
            if (part.isEmpty() || ".".equals(part))
            {
                continue;
            }
            if ("..".equals(part) || part.indexOf('\0') >= 0)
            {
                return "";
            }
            parts.add(part);
        }
        return String.join("/", parts);
    }

    private static boolean notBlank(String value)
    {
        return value != null && !value.isBlank();
    }
}
