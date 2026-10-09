package com.smartscript.platform.content.dto;

/**
 * 评论落库入参（内部使用，非接口出入参）。
 *
 * 说明：为什么要单独一个对象——MyBatis 回填自增主键需要 keyProperty 指向一个可写属性，
 * 用多个 @Param 拼参数无法回填，故沿用本模块既有 AppAiWriteRecordInsert 的做法，
 * 用一个承载字段的插入对象接住 useGeneratedKeys 回填的 commentId。
 *
 * @author xiangsipeng
 */
public class AppCommentInsert
{
    /** 评论ID（自增回填） */
    private Long commentId;

    /** 剧集ID */
    private Long episodeId;

    /** 评论人ID（当前登录身份） */
    private Long userId;

    /** 父评论ID（0=一级评论） */
    private Long parentId;

    /** 评论内容 */
    private String content;

    /** 创建人（落 create_by = 当前用户ID 字符串） */
    private String createBy;

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

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(String createBy)
    {
        this.createBy = createBy;
    }
}