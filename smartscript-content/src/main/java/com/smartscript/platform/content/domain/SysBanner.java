package com.smartscript.platform.content.domain;

import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * Banner对象 sys_banner
 *
 * 依据：云端 script_platform_dev 库 sys_banner 表（附件5.1 表3-88）。
 *
 * 说明（无文档依据，反推处理点）：
 * 1. created_at/updated_at 由数据库默认值维护，代码不读不写；Entity 只映射若依5通用字段
 *    （继承 BaseEntity）。
 * 2. status 为 varchar(20)（非 tinyint），Entity 用 String 原样持有。
 * 3. 主键 banner_id 为 bigint，Entity 用 Long。
 *
 * @author xiangsipeng
 */
public class SysBanner extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** BannerID */
    private Long bannerId;

    /** 标题 */
    private String title;

    /** 图片地址 */
    private String imageUrl;

    /** 链接类型 */
    private String linkType;

    /** 链接目标ID */
    private Long linkId;

    /** 链接地址 */
    private String linkUrl;

    /** 位置 */
    private String position;

    /** 排序 */
    private Integer sortOrder;

    /** 状态（varchar(20)，如 on/off） */
    private String status;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    public SysBanner()
    {
    }

    public Long getBannerId()
    {
        return bannerId;
    }

    public void setBannerId(Long bannerId)
    {
        this.bannerId = bannerId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getImageUrl()
    {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl)
    {
        this.imageUrl = imageUrl;
    }

    public String getLinkType()
    {
        return linkType;
    }

    public void setLinkType(String linkType)
    {
        this.linkType = linkType;
    }

    public Long getLinkId()
    {
        return linkId;
    }

    public void setLinkId(Long linkId)
    {
        this.linkId = linkId;
    }

    public String getLinkUrl()
    {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl)
    {
        this.linkUrl = linkUrl;
    }

    public String getPosition()
    {
        return position;
    }

    public void setPosition(String position)
    {
        this.position = position;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Date getStartTime()
    {
        return startTime;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public Date getEndTime()
    {
        return endTime;
    }

    public void setEndTime(Date endTime)
    {
        this.endTime = endTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("bannerId", getBannerId())
                .append("title", getTitle())
                .append("imageUrl", getImageUrl())
                .append("linkType", getLinkType())
                .append("linkId", getLinkId())
                .append("linkUrl", getLinkUrl())
                .append("position", getPosition())
                .append("sortOrder", getSortOrder())
                .append("status", getStatus())
                .append("startTime", getStartTime())
                .append("endTime", getEndTime())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
