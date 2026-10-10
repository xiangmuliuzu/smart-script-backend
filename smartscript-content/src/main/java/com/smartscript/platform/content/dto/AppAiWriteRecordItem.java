package com.smartscript.platform.content.dto;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * AI 写作记录条目（App 2.9.12 表2-122 列表元素）。
 *
 * 依据：云端 script_platform_dev 库 sys_ai_write_record 表（附件5.1 表3-22）。
 *
 * 反推处理点：契约只声明 data.list 为「记录列表」，**未定义元素字段**，
 * 故按表业务列下发记录ID / 标题 / 写作类型 / 输入 / 输出 / 字数 / 模型 / 状态 / 时间；
 * user_id 与若依审计列（create_by/create_time/update_by/update_time/remark）不下发。
 *
 * @author xiangsipeng
 */
public class AppAiWriteRecordItem
{
    /** 记录ID（sys_ai_write_record.record_id） */
    private Long recordId;

    /** 标题（落库时取输入前若干字符） */
    private String title;

    /** 写作类型（write / polish 等） */
    private String writeType;

    /** 输入提示词（写作取 prompt，润色取待润色原文） */
    private String inputPrompt;

    /** 生成内容 */
    private String outputContent;

    /** 字数 */
    private Integer wordCount;

    /** 使用的模型 */
    private String aiModel;

    /** 状态 */
    private String status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getWriteType()
    {
        return writeType;
    }

    public void setWriteType(String writeType)
    {
        this.writeType = writeType;
    }

    public String getInputPrompt()
    {
        return inputPrompt;
    }

    public void setInputPrompt(String inputPrompt)
    {
        this.inputPrompt = inputPrompt;
    }

    public String getOutputContent()
    {
        return outputContent;
    }

    public void setOutputContent(String outputContent)
    {
        this.outputContent = outputContent;
    }

    public Integer getWordCount()
    {
        return wordCount;
    }

    public void setWordCount(Integer wordCount)
    {
        this.wordCount = wordCount;
    }

    public String getAiModel()
    {
        return aiModel;
    }

    public void setAiModel(String aiModel)
    {
        this.aiModel = aiModel;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }
}