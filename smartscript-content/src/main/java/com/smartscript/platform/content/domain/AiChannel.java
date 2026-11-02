package com.smartscript.platform.content.domain;

import java.util.Date;

/**
 * AI 调用渠道配置（E 模块）
 *
 * 依据：云端 script_platform_dev 库 sys_ai_channel 表。
 * 字段口径：channel_name 唯一；base_url 为 OpenAI 兼容 Chat Completions 基址
 * （如 https://api.deepseek.com）；api_key 为接口密钥（可空，空时该渠道不参与
 * 调用，调用回退默认环境配置）；models 为逗号分隔的可用模型列表；
 * default_model 为默认模型（须属于 models）；status 0 启用 / 1 停用。
 *
 * @author xiangsipeng
 */
public class AiChannel
{
    /** 渠道 ID */
    private Long channelId;

    /** 渠道名称（唯一，如 DeepSeek） */
    private String channelName;

    /** 服务商标识（预留，如 deepseek / openai，默认 openai-compatible） */
    private String provider;

    /** API 基址 */
    private String baseUrl;

    /** 接口密钥（可空，空时不参与调用路由） */
    private String apiKey;

    /** 可用模型列表（逗号分隔，如 deepseek-chat,deepseek-reasoner） */
    private String models;

    /** 默认模型（须属于 models） */
    private String defaultModel;

    /** 状态：0启用 1停用 */
    private String status;

    /** 排序（小者优先，默认渠道） */
    private Integer sort;

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private Date createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private Date updateTime;

    /** 备注 */
    private String remark;

    public Long getChannelId()
    {
        return channelId;
    }

    public void setChannelId(Long channelId)
    {
        this.channelId = channelId;
    }

    public String getChannelName()
    {
        return channelName;
    }

    public void setChannelName(String channelName)
    {
        this.channelName = channelName;
    }

    public String getProvider()
    {
        return provider;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public String getModels()
    {
        return models;
    }

    public void setModels(String models)
    {
        this.models = models;
    }

    public String getDefaultModel()
    {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel)
    {
        this.defaultModel = defaultModel;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getSort()
    {
        return sort;
    }

    public void setSort(Integer sort)
    {
        this.sort = sort;
    }

    public String getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(String createBy)
    {
        this.createBy = createBy;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getUpdateBy()
    {
        return updateBy;
    }

    public void setUpdateBy(String updateBy)
    {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
