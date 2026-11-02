package com.ruoyi.framework.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import io.minio.MinioClient;

/**
 * MinIO 开关与 ProfileStorageService 装配：关闭时不创建客户端，开启时可取得客户端。
 * 不访问真实 MinIO（auto-create-bucket=false，构造客户端不发起网络请求）。
 */
class MinioWiringTest
{
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MinioConfig.class, ProfileStorageService.class);

    @Test
    void disabledByDefaultKeepsLocalOnly()
    {
        runner.run(context -> {
            assertFalse(context.containsBean("minioClient"));
            assertFalse(context.getBean(ProfileStorageService.class).remoteEnabled());
        });
    }

    @Test
    void enabledCreatesClientWithoutConnecting()
    {
        runner.withPropertyValues(
                "minio.enabled=true",
                "minio.endpoint=http://127.0.0.1:9000",
                "minio.access-key=test-access",
                "minio.secret-key=test-secret",
                "minio.bucket=test-profile",
                "minio.private-bucket=test-private",
                "minio.auto-create-bucket=false")
                .run(context -> {
                    assertTrue(context.containsBean("minioClient"));
                    assertEquals(MinioClient.class, context.getBean("minioClient").getClass());
                    assertTrue(context.getBean(ProfileStorageService.class).remoteEnabled());
                });
    }
}
