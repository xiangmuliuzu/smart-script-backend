package com.smartscript.platform.content.dto;

/**
 * 章节正文（App 侧只读下发对象）。
 *
 * 依据：云端 script_platform_dev 库 sys_work_chapter 表（附件5.1 表3-16）
 *       + 接口文档 2.7.8 免费试读。
 *
 * 试读边界：作品试读开关关闭、或 chapter_no 超出 preview_episodes 时，
 * readable=false 且 content 不下发（保持 null），由控制层按 403 拒绝；
 * 试读范围内 readable=true 并下发 content 全文。
 *
 * @author xiangsipeng
 */
public class AppChapterDetailDto
{
    /** 章节ID */
    private Long chapterId;

    /** 作品ID */
    private Long workId;

    /** 章节序号 */
    private Integer chapterNo;

    /** 章节标题 */
    private String chapterTitle;

    /** 字数 */
    private Integer wordCount;

    /** 章节内容（不可读时为 null） */
    private String content;

    /** 游客可否阅读本章（试读范围内为 true） */
    private Boolean readable;

    public AppChapterDetailDto()
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

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
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

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
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