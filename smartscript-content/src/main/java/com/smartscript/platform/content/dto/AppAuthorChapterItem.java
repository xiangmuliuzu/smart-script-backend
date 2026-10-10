package com.smartscript.platform.content.dto;

/**
 * App 作者视角章节条目（接口文档无章节 CRUD 规格，按模块约定补齐）。
 *
 * 与目录接口的 {@link AppChapterDto} 的区别：
 *   1. 作者视角不过滤 status，隐藏章节能被作者看到并恢复，故下发 status；
 *   2. 无 readable（作者对自己作品的阅读可见性由目录接口按试读/授权口径单独判定）；
 *   3. 不下发 content 全文（与目录一致，避免长文本随列表返回）。
 *
 * is_free/status 为 tinyint，沿用 B 模块 App 契约以字符串 "0"/"1" 下发。
 *
 * @author xiangsipeng
 */
public class AppAuthorChapterItem
{
    /** 章节ID */
    private Long chapterId;

    /** 章节序号 */
    private Integer chapterNo;

    /** 章节标题 */
    private String chapterTitle;

    /** 字数 */
    private Integer wordCount;

    /** 是否免费（"0"=否 "1"=是） */
    private String isFree;

    /** 章节状态（"0"=正常 "1"=隐藏） */
    private String status;

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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}