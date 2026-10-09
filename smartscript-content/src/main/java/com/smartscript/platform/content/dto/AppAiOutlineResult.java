package com.smartscript.platform.content.dto;

/**
 * AI 大纲生成结果（B 模块 2.9.13 表2-123）。
 *
 * 依据：接口文档输出参数。契约输出 data = {outline(对象), request_no, quota_used}，
 * 按 B 模块 App 契约的 camelCase 口径下发 outline / requestNo / quotaUsed。
 *
 * 反推处理点：outline 为对象，直接承载 AI 服务返回的 JSON 节点，不做二次建模，
 * 避免对不同 AI 服务商的大纲结构做无契约来源的假设。
 *
 * @author xiangsipeng
 */
public class AppAiOutlineResult
{
    /** 生成的大纲（AI 服务返回的 JSON 对象） */
    private Object outline;

    /** 请求编号（sys_ai_request.request_no） */
    private String requestNo;

    /** 消耗机会（sys_ai_request.quota_cost） */
    private Integer quotaUsed;

    public AppAiOutlineResult()
    {
    }

    public AppAiOutlineResult(Object outline, String requestNo, Integer quotaUsed)
    {
        this.outline = outline;
        this.requestNo = requestNo;
        this.quotaUsed = quotaUsed;
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
}