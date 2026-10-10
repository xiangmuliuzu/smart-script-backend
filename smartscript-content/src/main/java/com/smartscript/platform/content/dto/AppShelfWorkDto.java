package com.smartscript.platform.content.dto;

import java.util.Date;
import org.apache.ibatis.type.Alias;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 书架作品（App 书城 2.7.12）：在书城作品字段之上追加本书架的阅读进度。
 *
 * 依据：云端 script_platform_dev 库 sys_bookshelf_record 表（附件5.1 表3-32）——
 * last_read_chapter_id bigint NULL / last_read_at datetime NULL（阅读进度列，2.7.12 补齐）。
 *
 * 与 {@link AppWorkDto} 的关系：书架列表沿用书城作品的全部展示字段，仅多出进度列，
 * 故继承复用，避免同一批字段出现两套映射。从未读到某章时，进度三列均为 null。
 *
 * @author xiangsipeng
 */
@Alias("AppShelfWorkDto")
public class AppShelfWorkDto extends AppWorkDto
{
    /** 最近阅读章节ID（sys_bookshelf_record.last_read_chapter_id，未读为 null） */
    private Long lastReadChapterId;

    /** 最近阅读章节序号（JOIN sys_work_chapter.chapter_no，章节被删或未读时为 null） */
    private Integer lastReadChapterNo;

    /** 最近阅读时间（sys_bookshelf_record.last_read_at，未读为 null） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastReadAt;

    public Long getLastReadChapterId()
    {
        return lastReadChapterId;
    }

    public void setLastReadChapterId(Long lastReadChapterId)
    {
        this.lastReadChapterId = lastReadChapterId;
    }

    public Integer getLastReadChapterNo()
    {
        return lastReadChapterNo;
    }

    public void setLastReadChapterNo(Integer lastReadChapterNo)
    {
        this.lastReadChapterNo = lastReadChapterNo;
    }

    public Date getLastReadAt()
    {
        return lastReadAt;
    }

    public void setLastReadAt(Date lastReadAt)
    {
        this.lastReadAt = lastReadAt;
    }
}