package com.smartscript.platform.content.dto;

/**
 * AI 写作 / 润色结果（B 模块 2.9.10 表2-120 / 2.9.11 表2-121）。
 *
 * 依据：接口文档输出参数。契约输出 data = {content, record_id}，
 * 按 B 模块 App 契约的 camelCase 口径下发 content / recordId。
 *
 * @author xiangsipeng
 */
public class AppAiWriteResult
{
    /** 生成内容（写作生成内容 / 润色后内容） */
    private String content;

    /** 写作记录ID（sys_ai_write_record.record_id） */
    private Long recordId;

    public AppAiWriteResult()
    {
    }

    public AppAiWriteResult(String content, Long recordId)
    {
        this.content = content;
        this.recordId = recordId;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }
}