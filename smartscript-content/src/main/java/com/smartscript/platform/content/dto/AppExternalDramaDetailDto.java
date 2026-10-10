package com.smartscript.platform.content.dto;

/**
 * 外部视频详情（B 模块 2.8.15 表 2-108）。
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama + sys_drama_channel 表 + 接口文档表 2-108。
 *
 * 反推处理点：
 * 1. 契约输出 drama_id / title / external_url；此处补齐详情页需要的 coverFileId / sourceType /
 *    渠道信息 / relatedWorkId / status（均为既有表列，非编造）。
 * 2. cover_file_id 为 bigint，库端无通用文件表可反查 URL，故只下发 coverFileId，不编造 URL。
 * 3. 不下发 authorization_status / copyright_note：属版权内控信息，App 公开接口不投递。
 * 4. hasRelatedWork 口径为 related_work_id 非空（与 2.8.1 / 2.8.16 一致）。
 *
 * @author xiangsipeng
 */
public class AppExternalDramaDetailDto
{
    /** 外部视频ID */
    private Long dramaId;

    /** 标题 */
    private String title;

    /** 封面文件ID（可空） */
    private Long coverFileId;

    /** 外部播放地址 */
    private String externalUrl;

    /** 来源类型 */
    private String sourceType;

    /** 渠道ID */
    private Integer channelId;

    /** 渠道名称（JOIN sys_drama_channel.channel_name） */
    private String channelName;

    /** 平台标识（JOIN sys_drama_channel.platform） */
    private String platform;

    /** 关联作品ID（可空） */
    private Long relatedWorkId;

    /** 是否有关联原著（related_work_id 非空） */
    private Boolean hasRelatedWork;

    /** 上下架状态（varchar，如 on_shelf/off_shelf） */
    private String status;

    public Long getDramaId()
    {
        return dramaId;
    }

    public void setDramaId(Long dramaId)
    {
        this.dramaId = dramaId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Long getCoverFileId()
    {
        return coverFileId;
    }

    public void setCoverFileId(Long coverFileId)
    {
        this.coverFileId = coverFileId;
    }

    public String getExternalUrl()
    {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl)
    {
        this.externalUrl = externalUrl;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceType(String sourceType)
    {
        this.sourceType = sourceType;
    }

    public Integer getChannelId()
    {
        return channelId;
    }

    public void setChannelId(Integer channelId)
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

    public String getPlatform()
    {
        return platform;
    }

    public void setPlatform(String platform)
    {
        this.platform = platform;
    }

    public Long getRelatedWorkId()
    {
        return relatedWorkId;
    }

    public void setRelatedWorkId(Long relatedWorkId)
    {
        this.relatedWorkId = relatedWorkId;
    }

    public Boolean getHasRelatedWork()
    {
        return hasRelatedWork;
    }

    public void setHasRelatedWork(Boolean hasRelatedWork)
    {
        this.hasRelatedWork = hasRelatedWork;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}