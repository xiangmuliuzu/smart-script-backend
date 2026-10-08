package com.smartscript.platform.content.dto;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 搜索历史条目（App 书城只读下发对象，接口文档 2.7.4 表2-84）。
 *
 * 依据：云端 script_platform_dev 库 sys_search_history 表（附件5.1 表3-30）。
 *
 * 反推处理点：契约只声明 data.list 为「搜索历史列表」，**未定义元素字段**，
 * 故按表实际业务列下发 id / keyword / searchCount / lastSearchAt 四项；
 * created_at 与若依审计列（create_by/create_time/update_by/update_time/remark）不下发。
 *
 * @author xiangsipeng
 */
public class AppSearchHistoryItem
{
    /** 历史记录ID（删除单条时回传） */
    private Long id;

    /** 搜索关键词 */
    private String keyword;

    /** 累计搜索次数 */
    private Integer searchCount;

    /** 最近一次搜索时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastSearchAt;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }

    public Integer getSearchCount()
    {
        return searchCount;
    }

    public void setSearchCount(Integer searchCount)
    {
        this.searchCount = searchCount;
    }

    public Date getLastSearchAt()
    {
        return lastSearchAt;
    }

    public void setLastSearchAt(Date lastSearchAt)
    {
        this.lastSearchAt = lastSearchAt;
    }
}