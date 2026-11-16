package com.smartscript.platform.content.dto;

/**
 * AI 分镜生成结果（E 模块）。
 *
 * content=可直接使用/出售的分镜脚本文本；requestNo 与 quotaUsed 与大纲口径一致
 * （落 sys_ai_request 与积分流水）。
 *
 * @author xiangsipeng
 */
public class AppAiStoryboardResult
{
    /** 分镜脚本全文（可直接用于拍摄/制作） */
    private final String content;

    /** 本次请求编号（AIR 开头，落 sys_ai_request.request_no） */
    private final String requestNo;

    /** 本次消耗 token（换算积分用） */
    private final int quotaUsed;

    /** 是否因字数上限被截断 */
    private final boolean truncated;

    public AppAiStoryboardResult(String content, String requestNo, int quotaUsed, boolean truncated)
    {
        this.content = content;
        this.requestNo = requestNo;
        this.quotaUsed = quotaUsed;
        this.truncated = truncated;
    }

    public String getContent()
    {
        return content;
    }

    public String getRequestNo()
    {
        return requestNo;
    }

    public int getQuotaUsed()
    {
        return quotaUsed;
    }

    public boolean isTruncated()
    {
        return truncated;
    }
}
