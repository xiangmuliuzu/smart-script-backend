package com.smartscript.platform.content.dto;

/**
 * 新增章节请求（App 章节编辑，接口文档无 CRUD 规格，按模块约定补齐）。
 *
 * 入参：chapterNo 必填正整数（作品内唯一，落 sys_work_chapter.chapter_no）；
 * chapterTitle 必填且 ≤100（列 varchar(100)）；content 可选（落 longtext）；
 * isFree 可选 "0"/"1"（缺省 "0"）。
 *
 * @author xiangsipeng
 */
public class AppChapterCreateRequest
{
    /** 章节序号（必填，作品内唯一） */
    private Integer chapterNo;

    /** 章节标题（必填，≤100） */
    private String chapterTitle;

    /** 章节内容（可选） */
    private String content;

    /** 是否免费（可选 "0"/"1"，缺省 "0"） */
    private String isFree;

    public Integer getChapterNo()
    {
        return chapterNo;
    }

    public void setChapterNo(Integer chapterNo)
    {
        this.chapterNo = chapterNo;
    }

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
}