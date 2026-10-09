package com.smartscript.platform.content.dto;

/**
 * 发表评论请求（B 模块 2.8.11 表2-104）。
 *
 * 入参：content 必填且不超过 500 字（落库 sys_comment.content varchar(500)）；
 * parentId 可选，非空且 &gt;0 时表示回复某条一级评论（落库 parent_id，须属于同一剧集）。
 *
 * @author xiangsipeng
 */
public class AppCommentCreateRequest
{
    /** 评论内容（必填，≤500） */
    private String content;

    /** 父评论ID（可选，0 或缺省为一级评论） */
    private Long parentId;

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
    }
}