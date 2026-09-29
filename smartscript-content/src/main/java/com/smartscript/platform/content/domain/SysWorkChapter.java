package com.smartscript.platform.content.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 作品章节对象 sys_work_chapter
 *
 * 依据：云端 script_platform_dev 库 sys_work_chapter 表（附件5.1 表3-16）。
 * 表注释：作品章节表。
 *
 * 说明（无文档依据，反推处理点）：
 * 1. created_at/updated_at 由数据库默认值维护，代码不读不写；Entity 只映射若依5通用字段
 *    （继承 BaseEntity）。
 * 2. is_free/status 为 tinyint，Entity 用 String（若依字典风格 "0"/"1"）。
 * 3. 主键 chapter_id 为 bigint，Entity 用 Long。
 *
 * @author xiangsipeng
 */
public class SysWorkChapter extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 章节ID */
    private Long chapterId;

    /** 作品ID */
    private Long workId;

    /** 章节序号 */
    private Integer chapterNo;

    /** 章节标题 */
    private String chapterTitle;

    /** 章节内容 */
    private String content;

    /** 字数 */
    private Integer wordCount;

    /** 是否免费（0=否 1=是） */
    private String isFree;

    /** 状态（0=正常 1=停用） */
    private String status;

    public SysWorkChapter()
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

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
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

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("chapterId", getChapterId())
                .append("workId", getWorkId())
                .append("chapterNo", getChapterNo())
                .append("chapterTitle", getChapterTitle())
                .append("wordCount", getWordCount())
                .append("isFree", getIsFree())
                .append("status", getStatus())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
