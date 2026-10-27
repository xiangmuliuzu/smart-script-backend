package com.smartscript.platform.content.dto;

/**
 * AI 写作 / 润色结果（B 模块 2.9.10 表2-120 / 2.9.11 表2-121）。
 *
 * 依据：接口文档输出参数。契约输出 data = {content, record_id}，
 * 按 B 模块 App 契约的 camelCase 口径下发 content / recordId。
 * truncated：本次生成是否因「单次 AI 调用输出字数上限」被截断（管理员可配置）。
 *
 * @author xiangsipeng
 */
public class AppAiWriteResult
{
    /** 生成内容（写作生成内容 / 润色后内容） */
    private String content;

    /** 写作记录ID（sys_ai_write_record.record_id） */
    private Long recordId;

    /** 是否因字数上限被截断 */
    private boolean truncated;

    public AppAiWriteResult()
    {
    }

    public AppAiWriteResult(String content, Long recordId)
    {
        this(content, recordId, false);
    }

    public AppAiWriteResult(String content, Long recordId, boolean truncated)
    {
        this.content = content;
        this.recordId = recordId;
        this.truncated = truncated;
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

    public boolean isTruncated()
    {
        return truncated;
    }

    public void setTruncated(boolean truncated)
    {
        this.truncated = truncated;
    }
}
