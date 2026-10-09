package com.smartscript.platform.content.dto;

/**
 * 短剧信息流列表项（B 模块 2.8.1 表 2-94）。
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama 表 + 接口文档表 2-94。
 *
 * 反推处理点：
 * 1. 契约仅定义 data={total,list}，未定义 list 元素字段；此处按表列下发浏览所需子集。
 * 2. cover_file_id 为 bigint，库端无通用文件表可反查 URL（见 AppContentFileUploadService 说明），
 *    故只下发 coverFileId，由客户端按能力兜底占位；不编造 coverUrl。
 * 3. hasRelatedWork 统一口径为 related_work_id 非空（是否绑定原著），与 2.8.16 同一判定依据。
 * 4. create_by/created_at 等审计字段不下发。
 *
 * @author xiangsipeng
 */
public class AppDramaFeedItem
{
    /** 外部视频ID */
    private Long dramaId;

    /** 标题 */
    private String title;

    /** 封面文件ID（可空，库端无 URL 可反查） */
    private Long coverFileId;

    /** 外部播放地址 */
    private String externalUrl;

    /** 来源类型（如 douyin） */
    private String sourceType;

    /** 渠道ID */
    private Integer channelId;

    /** 渠道名称（JOIN sys_drama_channel.channel_name） */
    private String channelName;

    /** 关联作品ID（可空） */
    private Long relatedWorkId;

    /** 是否有关联原著（related_work_id 非空） */
    private Boolean hasRelatedWork;

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
}