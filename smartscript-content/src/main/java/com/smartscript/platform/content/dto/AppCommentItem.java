package com.smartscript.platform.content.dto;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 剧集评论条目（B 模块 2.8.10 表2-103）。
 *
 * 依据：云端 script_platform_dev 库 sys_comment + sys_user 表 + 接口文档 2.8.10。
 *
 * 反推处理点：
 * 1. 契约未定义 data.list 元素字段，此处下发评论展示所需列（均为既有表列，非编造）。
 * 2. 含嵌套回复：列表按 episode 平铺下发（parentId=0 为一级，其余挂在对应 parentId 下），
 *    由前端按 parentId 组两层；不在后端裁剪层级，避免分页把回复与其父评论拆到不同页。
 * 3. status 约定：1=正常（可见）、0=隐藏；列表只查 status=1，故本 DTO 不下发 status。
 * 4. nickName/avatar 来自 sys_user（JOIN），用户已注销时为空。
 *
 * @author xiangsipeng
 */
public class AppCommentItem
{
    /** 评论ID */
    private Long commentId;

    /** 剧集ID */
    private Long episodeId;

    /** 评论人ID */
    private Long userId;

    /** 评论人昵称（JOIN sys_user.nick_name） */
    private String nickName;

    /** 评论人头像（JOIN sys_user.avatar） */
    private String avatar;

    /** 评论内容 */
    private String content;

    /** 点赞数 */
    private Integer likeCount;

    /** 回复数 */
    private Integer replyCount;

    /** 父评论ID（0=一级评论） */
    private Long parentId;

    /** 评论时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    public Long getCommentId()
    {
        return commentId;
    }

    public void setCommentId(Long commentId)
    {
        this.commentId = commentId;
    }

    public Long getEpisodeId()
    {
        return episodeId;
    }

    public void setEpisodeId(Long episodeId)
    {
        this.episodeId = episodeId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getNickName()
    {
        return nickName;
    }

    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    public String getAvatar()
    {
        return avatar;
    }

    public void setAvatar(String avatar)
    {
        this.avatar = avatar;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public Integer getLikeCount()
    {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount)
    {
        this.likeCount = likeCount;
    }

    public Integer getReplyCount()
    {
        return replyCount;
    }

    public void setReplyCount(Integer replyCount)
    {
        this.replyCount = replyCount;
    }

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
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