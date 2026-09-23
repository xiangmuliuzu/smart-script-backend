package com.smartscript.platform.content.domain;

/**
 * 作品摘要（A6 示例数据模型）。
 *
 * 说明：字段命名与内容表结构尚未经团队确认（见 A6-Q1），因此本对象只用于
 * `smartscript-content` 的示例只读列表，**不代表**最终内容领域模型。
 * 内容表建立后由该模块替换仓储实现，本对象可随之调整为映射对象或直接替换。
 */
public class WorkSummary
{
    private Long workId;
    private String title;
    private String authorName;
    private String category;
    private int wordCount;
    private boolean freeToRead;

    public WorkSummary()
    {
    }

    public WorkSummary(Long workId, String title, String authorName, String category, int wordCount,
            boolean freeToRead)
    {
        this.workId = workId;
        this.title = title;
        this.authorName = authorName;
        this.category = category;
        this.wordCount = wordCount;
        this.freeToRead = freeToRead;
    }

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

    public String getAuthorName()
    {
        return authorName;
    }

    public void setAuthorName(String authorName)
    {
        this.authorName = authorName;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public int getWordCount()
    {
        return wordCount;
    }

    public void setWordCount(int wordCount)
    {
        this.wordCount = wordCount;
    }

    public boolean isFreeToRead()
    {
        return freeToRead;
    }

    public void setFreeToRead(boolean freeToRead)
    {
        this.freeToRead = freeToRead;
    }
}
