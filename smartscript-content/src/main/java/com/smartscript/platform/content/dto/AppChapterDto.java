package com.smartscript.platform.content.dto;

/**
 * 作品章节摘要（App 侧只读下发对象，章节目录用）。
 *
 * 依据：云端 script_platform_dev 库 sys_work_chapter 表（附件5.1 表3-16）。
 * 字段裁剪：目录不下发 content 全文，只下发展示与试读判定所需字段。
 *
 * 口径说明：
 *   - isFree 为 tinyint 存储列，沿用既有 App 契约以字符串 "0"/"1" 下发（与 AppWorkDto 一致）；
 *   - readable 为服务端按作品试读开关计算的可读标识（preview_enabled='1'
 *     且 preview_episodes>0 且 chapter_no<=preview_episodes），非库中列。
 *
 * @author xiangsipeng
 */
public class AppChapterDto
{
    /** 章节ID */
    private Long chapterId;

    /** 章节序号 */
    private Integer chapterNo;

    /** 章节标题 */
    private String chapterTitle;

    /** 字数 */
    private Integer wordCount;

    /** 是否免费（0=否 1=是） */
    private String isFree;

    /** 游客可否阅读本章（试读范围内为 true） */
    private Boolean readable;

    public AppChapterDto()
    {
    }

    public Long getChapterId()
    {
        return chapterId;
    }

    public void setChapterId(Long chapterId)
    {
        this.chapterId = chapterId;
    }

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

    public Integer getWordCount()
    {
        return wordCount;
    }

    public void setWordCount(Integer wordCount)
    {
        this.wordCount = wordCount;
    }

    public String getIsFree()
    {
        return isFree;
    }

    public void setIsFree(String isFree)
    {
        this.isFree = isFree;
    }

    public Boolean getReadable()
    {
        return readable;
    }

    public void setReadable(Boolean readable)
    {
        this.readable = readable;
    }
}