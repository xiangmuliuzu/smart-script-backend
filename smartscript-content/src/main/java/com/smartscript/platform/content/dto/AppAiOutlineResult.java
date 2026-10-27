package com.smartscript.platform.content.dto;

/**
 * AI 大纲生成结果（B 模块 2.9.13 表2-123）。
 *
 * 依据：接口文档输出参数。契约输出 data = {outline(对象), request_no, quota_used}，
 * 按 B 模块 App 契约的 camelCase 口径下发 outline / requestNo / quotaUsed。
 *
 * 反推处理点：outline 为普通 Java 对象（Map/List），承载 AI 返回的大纲结构。
 * 本项目响应 ObjectMapper 无法正确序列化 Jackson 的 JsonNode（会被当作普通 Bean
 * 输出 isArray()/isObject() 等方法名），因此服务层在落结果前用
 * ObjectMapper.convertValue(node, Object.class) 把 JsonNode 转成 Map，再放入本字段。
 *
 * @author xiangsipeng
 */
public class AppAiOutlineResult
{
    /** 生成的大纲（AI 服务返回的结构，Map/List） */
    private Object outline;

    /** 请求编号（sys_ai_request.request_no） */
    private String requestNo;

    /** 消耗机会（sys_ai_request.quota_cost） */
    private Integer quotaUsed;

    /** 是否因字数上限被截断 */
    private boolean truncated;

    public AppAiOutlineResult()
    {
    }

    public AppAiOutlineResult(Object outline, String requestNo, Integer quotaUsed)
    {
        this(outline, requestNo, quotaUsed, false);
    }

    public AppAiOutlineResult(Object outline, String requestNo, Integer quotaUsed, boolean truncated)
    {
        this.outline = outline;
        this.requestNo = requestNo;
        this.quotaUsed = quotaUsed;
        this.truncated = truncated;
    }

    public Object getOutline()
    {
        return outline;
    }

    public void setOutline(Object outline)
    {
        this.outline = outline;
    }

    public String getRequestNo()
    {
        return requestNo;
    }

    public void setRequestNo(String requestNo)
    {
        this.requestNo = requestNo;
    }

    public Integer getQuotaUsed()
    {
        return quotaUsed;
    }

    public void setQuotaUsed(Integer quotaUsed)
    {
        this.quotaUsed = quotaUsed;
    }

    public boolean isTruncated()
    {
        return truncated;
    }

    public void setTruncated(boolean truncated)
    {
        this.truncated = truncated;
    }
}