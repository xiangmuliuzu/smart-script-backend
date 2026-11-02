package com.smartscript.platform.user.service;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.ruoyi.framework.storage.ProfileStorageService;
import com.smartscript.platform.user.exception.AppAdminException;

/**
 * {@link MaterialGateway} 的 MinIO 私有桶实现。
 *
 * <p>材料不开放公网访问，仍由后端鉴权后流式下发；引用保持
 * {@code realname/<userId>/<uuid>.png} 这类相对 key 不变。</p>
 */
@Component
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
public class MinioMaterialGateway implements MaterialGateway
{
    private final ProfileStorageService storage;

    public MinioMaterialGateway(ProfileStorageService storage)
    {
        this.storage = storage;
    }

    @Override
    public boolean exists(String rawRef)
    {
        return storage.existsPrivate(rawRef);
    }

    @Override
    public InputStream open(String rawRef) throws IOException
    {
        if (!storage.existsPrivate(rawRef))
        {
            throw AppAdminException.notFound();
        }
        return storage.openPrivate(rawRef);
    }

    @Override
    public long size(String rawRef)
    {
        return storage.sizePrivate(rawRef);
    }

    @Override
    public String contentType(String rawRef)
    {
        return storage.contentTypePrivate(rawRef);
    }
}
