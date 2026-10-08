package com.smartscript.platform.content.dto;

/**
 * 章节正文（App 侧只读下发对象）。
 *
 * 依据：云端 script_platform_dev 库 sys_work_chapter 表（附件5.1 表3-16）
 *       + 接口文档 2.7.8 免费试读。
 *
 * 可读边界（两条放开分支取或，与目录口径一致）：
 *   1. 在试读范围内（作品试读开关开启且 chapter_no 未超出 preview_episodes）；
 *   2. 当前用户对作品持有生效中的版权授权（sys_copyright_authorization.status='active'）。
 * 两者都不满足时 readable=false 且 content 不下发（保持 null），由控制层按 403 拒绝；
 * 任一满足时 readable=true 并下发 content 全文。
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

    /** 当前身份可否阅读本章（试读范围内 或 已获授权） */
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