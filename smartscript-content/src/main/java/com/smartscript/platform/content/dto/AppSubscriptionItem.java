package com.smartscript.platform.content.dto;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 我的追更列表项（B 模块 2.8.14 表 2-107）。
 *
 * 依据：云端 script_platform_dev 库 sys_subscribe + sys_work + sys_user 表 + 接口文档表 2-107。
 *
 * 反推处理点：
 * 1. 契约仅定义 data={total,list}，未定义元素字段；此处下发追更页展示所需的作品信息
 *    （均为既有表列，非编造）。
 * 2. notify_enabled 为 tinyint，以字符串 "0"/"1" 下发（与 B 模块 tinyint 口径一致）。
 * 3. createdAt 取自 sys_subscribe.created_at（订阅时间），按它倒序分页。
 *
 * @author xiangsipeng
 */
public class AppSubscriptionItem
{
    /** 作品ID */
    private Long workId;

    /** 作品标题 */
    private String title;

    /** 作品封面 */
    private String cover;

    /** 作者昵称（JOIN sys_user.nick_name） */
    private String authorName;

    /** 集数（sys_work.episode_count，可空） */
    private Integer episodeCount;

    /** 价格 */
    private BigDecimal price;

    /** 是否开启更新提醒（"0"=否 "1"=是） */
    private String notifyEnabled;

    /** 订阅时间 */
    private Date createdAt;

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getCover()
    {
        return cover;
    }

    public void setCover(String cover)
    {
        this.cover = cover;
    }

    public String getAuthorName()
    {
        return authorName;
    }

    public void setAuthorName(String authorName)
    {
        this.authorName = authorName;
    }

    public Integer getEpisodeCount()
    {
        return episodeCount;
    }

    public void setEpisodeCount(Integer episodeCount)
    {
        this.episodeCount = episodeCount;
    }

    public BigDecimal getPrice()
    {
        return price;
    }

    public void setPrice(BigDecimal price)
    {
        this.price = price;
    }

    public String getNotifyEnabled()
    {
        return notifyEnabled;
    }

    public void setNotifyEnabled(String notifyEnabled)
    {
        this.notifyEnabled = notifyEnabled;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }
}