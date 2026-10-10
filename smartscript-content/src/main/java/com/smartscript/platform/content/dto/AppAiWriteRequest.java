package com.smartscript.platform.content.dto;

/**
 * AI 写作请求（B 模块 2.9.10 表2-120）。
 *
 * 依据：接口文档 2.9.10 输入参数。契约入参 {prompt 必填, type 可选, work_id 可选}。
 *
 * 反推处理点：type 为空时写库回退 write_type='write'（见 AiWriteServiceImpl）。
 *
 * @author xiangsipeng
 */
public class AppAiWriteRequest
{
    /** 写作提示词（必填） */
    private String prompt;

    /** 写作类型（可选；落 sys_ai_write_record.write_type） */
    private String type;

    /** 关联作品ID（可选） */
    private Long workId;

    public String getPrompt()
    {
        return prompt;
    }

    public void setPrompt(String prompt)
    {
        this.prompt = prompt;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }
}