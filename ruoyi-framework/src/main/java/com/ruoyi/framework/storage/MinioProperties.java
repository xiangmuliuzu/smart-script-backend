package com.ruoyi.framework.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 对象存储配置。
 *
 * <p>只有 {@code minio.enabled=true} 时才创建客户端；未启用时 /profile 资源继续落在
 * {@code ruoyi.profile} 本地目录，行为与历史版本一致。</p>
 */
@ConfigurationProperties(prefix = "minio")
public class MinioProperties
{
    /** 是否启用 MinIO。 */
    private boolean enabled;

    /** 服务地址，例如 http://127.0.0.1:9000。 */
    private String endpoint;

    /** 访问密钥（只从环境变量注入，禁止提交到仓库）。 */
    private String accessKey;

    /** 私有密钥（只从环境变量注入，禁止提交到仓库）。 */
    private String secretKey;

    /** 公开资源桶：印章图、封面、头像、合同产物等 /profile/** 对象。 */
    private String bucket = "smartscript-profile";

    /** 私有资源桶：实名材料等，不能经 /profile/** 直接访问。 */
    private String privateBucket = "smartscript-private";

    /** 启动时自动创建桶；生产环境可关闭，由运维预先建好。 */
    private boolean autoCreateBucket = true;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getEndpoint()
    {
        return endpoint;
    }

    public void setEndpoint(String endpoint)
    {
        this.endpoint = endpoint;
    }

    public String getAccessKey()
    {
        return accessKey;
    }

    public void setAccessKey(String accessKey)
    {
        this.accessKey = accessKey;
    }

    public String getSecretKey()
    {
        return secretKey;
    }

    public void setSecretKey(String secretKey)
    {
        this.secretKey = secretKey;
    }

    public String getBucket()
    {
        return bucket;
    }

    public void setBucket(String bucket)
    {
        this.bucket = bucket;
    }

    public String getPrivateBucket()
    {
        return privateBucket;
    }

    public void setPrivateBucket(String privateBucket)
    {
        this.privateBucket = privateBucket;
    }

    public boolean isAutoCreateBucket()
    {
        return autoCreateBucket;
    }

    public void setAutoCreateBucket(boolean autoCreateBucket)
    {
        this.autoCreateBucket = autoCreateBucket;
    }
}
