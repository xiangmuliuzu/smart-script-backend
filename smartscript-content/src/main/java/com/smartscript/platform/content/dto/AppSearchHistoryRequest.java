package com.smartscript.platform.content.dto;

/**
 * 记录搜索历史请求体（文档未定义，按 sys_search_history 表结构补充）。
 *
 * 只接收 keyword：user_id 一律由 App Token 身份解析，不接受入参传入，避免越权写他人历史。
 *
 * @author xiangsipeng
 */
public class AppSearchHistoryRequest
{
    /** 搜索关键词 */
    private String keyword;

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }
}