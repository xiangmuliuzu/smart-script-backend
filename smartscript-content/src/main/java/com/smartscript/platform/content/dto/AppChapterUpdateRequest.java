package com.smartscript.platform.content.dto;

/**
 * 更新章节请求（App 章节编辑，接口文档无 CRUD 规格，按模块约定补齐）。
 *
 * 全部可选，为 null 的字段不更新；不可修改 work_id / chapter_no。
 * content 更新时后端按内容长度重算 word_count。
 *
 * @author xiangsipeng
 */
public class AppChapterUpdateRequest
{
    /** 章节标题（可选，≤100） */
    private String chapterTitle;

    /** 章节内容（可选；更新时重算 word_count） */
    private String content;

    /** 是否免费（可选 "0"/"1"） */
    private String isFree;

    /** 状态（可选 "0"/"1"） */
    private String status;

    public String getChapterTitle()
    {
        return chapterTitle;
    }

    public void setChapterTitle(String chapterTitle)
    {
        this.chapterTitle = chapterTitle;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getIsFree()
    {
        return isFree;
    }

    public void setIsFree(String isFree)
    {
        this.isFree = isFree;
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