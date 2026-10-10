package com.smartscript.platform.content.domain;

import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 外部视频对象 sys_external_drama
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama 表。
 * 表注释：外部短剧表（接入外部渠道的短剧资源，可绑定到本地作品 work_id）。
 *
 * 说明（无文档依据，反推处理点）：
 * 1. created_at/updated_at 由数据库默认值维护，代码不读不写；Entity 只映射若依5通用字段
 *    （create_by/create_time/update_by/update_time/remark，继承 BaseEntity）。
 * 2. status 为 varchar(20)（非 tinyint），Entity 用 String 原样持有；
 *    authorization_status/sync_status/source_type 同样为 varchar，String 原样持有。
 * 3. related_work_id 为可空外键（关联 sys_work.work_id），未绑定时为 NULL。
 * 4. 主键 drama_id 为 bigint，Entity 用 Long。
 * 5. relatedWorkTitle/channelName 为 JOIN 扩展字段
 *    （sys_work.title ON related_work_id=work_id / sys_drama_channel.channel_name ON channel_id）。
 * 6. totalPlayCount/subscribeCount/episodeCount/workTitle 为 Stats 跨表聚合扩展字段
 *    （聚合 sys_episode/sys_subscribe/sys_work）。
 * 7. episodes 为详情接口返回的剧集列表（transient，由 Service 拼装，不入库不映射）。
 * 8. unboundOnly 为 transient 过滤条件（"1"=仅查 related_work_id IS NULL 的未绑定记录）。
 *
 * @author xiangsipeng
 */
public class SysExternalDrama extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 短剧ID */
    private Long dramaId;

    /** 渠道ID（关联 sys_drama_channel.channel_id） */
    private Integer channelId;

    /** 外部内容ID（渠道侧唯一） */
    private String externalContentId;

    /** 标题 */
    private String title;

    /** 封面文件ID */
    private Long coverFileId;

    /** 外部地址 */
    private String externalUrl;

    /** 关联作品ID（可空，关联 sys_work.work_id） */
    private Long relatedWorkId;

    /** 来源类型 */
    private String sourceType;

    /** 授权状态 */
    private String authorizationStatus;

    /** 同步状态 */
    private String syncStatus;

    /** 版权备注 */
    private String copyrightNote;

    /** 状态（varchar(20)，如 on_shelf/off_shelf 等） */
    private String status;

    // ---- JOIN 扩展字段 ----

    /** 关联作品标题（JOIN sys_work.title ON related_work_id=work_id） */
    private String relatedWorkTitle;

    /** 渠道名称（JOIN sys_drama_channel.channel_name ON channel_id） */
    private String channelName;

    // ---- Stats 跨表聚合扩展字段 ----

    /** 总播放数（聚合 sys_episode.play_count WHERE work_id=related_work_id） */
    private Long totalPlayCount;

    /** 订阅数（COUNT sys_subscribe WHERE work_id=related_work_id） */
    private Long subscribeCount;

    /** 剧集数（COUNT sys_episode WHERE work_id=related_work_id） */
    private Integer episodeCount;

    /** 作品标题（Stats 视图下取自 sys_work.title） */
    private String workTitle;

    // ---- 详情接口拼装字段（transient，不映射） ----

    /** 剧集列表（详情接口由 Service 拼装返回，不入库不映射） */
    private List<SysEpisode> episodes;

    // ---- Transient 过滤条件（不映射） ----

    /** 仅查未绑定（"1"=filter related_work_id IS NULL） */
    private String unboundOnly;

    public SysExternalDrama()
    {
    }

    public Long getDramaId()
    {
        return dramaId;
    }

    public void setDramaId(Long dramaId)
    {
        this.dramaId = dramaId;
    }

    public Integer getChannelId()
    {
        return channelId;
    }

    public void setChannelId(Integer channelId)
    {
        this.channelId = channelId;
    }

    public String getExternalContentId()
    {
        return externalContentId;
    }

    public void setExternalContentId(String externalContentId)
    {
        this.externalContentId = externalContentId;
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

    public Long getRelatedWorkId()
    {
        return relatedWorkId;
    }

    public void setRelatedWorkId(Long relatedWorkId)
    {
        this.relatedWorkId = relatedWorkId;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceType(String sourceType)
    {
        this.sourceType = sourceType;
    }

    public String getAuthorizationStatus()
    {
        return authorizationStatus;
    }

    public void setAuthorizationStatus(String authorizationStatus)
    {
        this.authorizationStatus = authorizationStatus;
    }

    public String getSyncStatus()
    {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus)
    {
        this.syncStatus = syncStatus;
    }

    public String getCopyrightNote()
    {
        return copyrightNote;
    }

    public void setCopyrightNote(String copyrightNote)
    {
        this.copyrightNote = copyrightNote;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getRelatedWorkTitle()
    {
        return relatedWorkTitle;
    }

    public void setRelatedWorkTitle(String relatedWorkTitle)
    {
        this.relatedWorkTitle = relatedWorkTitle;
    }

    public String getChannelName()
    {
        return channelName;
    }

    public void setChannelName(String channelName)
    {
        this.channelName = channelName;
    }

    public Long getTotalPlayCount()
    {
        return totalPlayCount;
    }

    public void setTotalPlayCount(Long totalPlayCount)
    {
        this.totalPlayCount = totalPlayCount;
    }

    public Long getSubscribeCount()
    {
        return subscribeCount;
    }

    public void setSubscribeCount(Long subscribeCount)
    {
        this.subscribeCount = subscribeCount;
    }

    public Integer getEpisodeCount()
    {
        return episodeCount;
    }

    public void setEpisodeCount(Integer episodeCount)
    {
        this.episodeCount = episodeCount;
    }

    public String getWorkTitle()
    {
        return workTitle;
    }

    public void setWorkTitle(String workTitle)
    {
        this.workTitle = workTitle;
    }

    public List<SysEpisode> getEpisodes()
    {
        return episodes;
    }

    public void setEpisodes(List<SysEpisode> episodes)
    {
        this.episodes = episodes;
    }

    public String getUnboundOnly()
    {
        return unboundOnly;
    }

    public void setUnboundOnly(String unboundOnly)
    {
        this.unboundOnly = unboundOnly;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("dramaId", getDramaId())
                .append("channelId", getChannelId())
                .append("externalContentId", getExternalContentId())
                .append("title", getTitle())
                .append("relatedWorkId", getRelatedWorkId())
                .append("relatedWorkTitle", getRelatedWorkTitle())
                .append("channelName", getChannelName())
                .append("sourceType", getSourceType())
                .append("authorizationStatus", getAuthorizationStatus())
                .append("syncStatus", getSyncStatus())
                .append("status", getStatus())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
