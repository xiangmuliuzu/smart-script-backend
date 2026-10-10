package com.smartscript.platform.content.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 外部视频渠道对象 sys_drama_channel
 *
 * 依据：云端 script_platform_dev 库 sys_drama_channel 表。
 * 表注释：外部短剧渠道表（用于接入外部短剧源：抖音/快手/微信小程序等）。
 *
 * 说明（无文档依据，反推处理点）：
 * 1. created_at/updated_at 由数据库默认值维护，代码不读不写；Entity 只映射若依5通用字段
 *    （create_by/create_time/update_by/update_time/remark，继承 BaseEntity）。
 * 2. is_auto_distribute/status 为 tinyint，Entity 用 String（若依字典风格 "0"/"1"）。
 * 3. api_config 为 json 类型，Entity 用 String 持有原文本，mapper xml 透传。
 * 4. 主键 channel_id 为 int，Entity 用 Long（若依惯例）。
 *
 * @author xiangsipeng
 */
public class SysDramaChannel extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 渠道ID */
    private Long channelId;

    /** 渠道名称 */
    private String channelName;

    /** 渠道编码（唯一） */
    private String channelCode;

    /** 平台（抖音/快手/微信小程序等） */
    private String platform;

    /** 账号名称 */
    private String accountName;

    /** 账号ID */
    private String accountId;

    /** API 配置（JSON 原文本透传） */
    private String apiConfig;

    /** 是否自动分发（0=否 1=是） */
    private String isAutoDistribute;

    /** 状态（0=正常 1=停用） */
    private String status;

    /** 显示顺序 */
    private Integer sort;

    public SysDramaChannel()
    {
    }

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

    public String getChannelCode()
    {
        return channelCode;
    }

    public void setChannelCode(String channelCode)
    {
        this.channelCode = channelCode;
    }

    public String getPlatform()
    {
        return platform;
    }

    public void setPlatform(String platform)
    {
        this.platform = platform;
    }

    public String getAccountName()
    {
        return accountName;
    }

    public void setAccountName(String accountName)
    {
        this.accountName = accountName;
    }

    public String getAccountId()
    {
        return accountId;
    }

    public void setAccountId(String accountId)
    {
        this.accountId = accountId;
    }

    public String getApiConfig()
    {
        return apiConfig;
    }

    public void setApiConfig(String apiConfig)
    {
        this.apiConfig = apiConfig;
    }

    public String getIsAutoDistribute()
    {
        return isAutoDistribute;
    }

    public void setIsAutoDistribute(String isAutoDistribute)
    {
        this.isAutoDistribute = isAutoDistribute;
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

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("channelId", getChannelId())
                .append("channelName", getChannelName())
                .append("channelCode", getChannelCode())
                .append("platform", getPlatform())
                .append("accountName", getAccountName())
                .append("accountId", getAccountId())
                .append("isAutoDistribute", getIsAutoDistribute())
                .append("status", getStatus())
                .append("sort", getSort())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
