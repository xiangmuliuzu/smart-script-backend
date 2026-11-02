package com.ruoyi.framework.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

/**
 * MinIO 客户端配置。
 *
 * <p>桶保持私有：Public 读仍由后端 {@code /profile/**} 代理，客户端拿不到 MinIO 凭据，
 * 也不需要开放 CORS 或公网读权限。</p>
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig
{
    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
    public MinioClient minioClient(MinioProperties properties)
    {
        requireText(properties.getEndpoint(), "minio.endpoint");
        requireText(properties.getAccessKey(), "minio.access-key");
        requireText(properties.getSecretKey(), "minio.secret-key");
        requireText(properties.getBucket(), "minio.bucket");
        requireText(properties.getPrivateBucket(), "minio.private-bucket");

        MinioClient client = MinioClient.builder()
                .endpoint(properties.getEndpoint().trim())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();

        if (properties.isAutoCreateBucket())
        {
            ensureBucket(client, properties.getBucket());
            ensureBucket(client, properties.getPrivateBucket());
        }
        log.info("minio storage enabled for profile resources");
        return client;
    }

    private static void ensureBucket(MinioClient client, String bucket)
    {
        try
        {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists)
            {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("minio bucket created: {}", bucket);
            }
        }
        catch (Exception e)
        {
            // 启动即失败，避免运行时才发现所有上传都写不进去
            throw new IllegalStateException("MinIO bucket unavailable: " + bucket, e);
        }
    }

    private static void requireText(String value, String name)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalStateException(name + " is required when minio.enabled=true");
        }
    }
}
