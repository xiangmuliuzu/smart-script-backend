package com.smartscript.platform.content.dto;

/**
 * AI 写作记录写入参数（B 模块 2.9.10 / 2.9.11 落库对象）。
 *
 * 依据：云端 script_platform_dev 库 sys_ai_write_record 表（附件5.1 表3-22）。
 *
 * 反推处理点：
 * 1. record_id 为 bigint auto_increment，insert 用 useGeneratedKeys 回填本对象 recordId。
 * 2. 表中 pov / language_style / pace / title / write_type / word_count / ai_model /
 *    tokens_used / status 均为 NOT NULL，契约未提供对应入参，故按下列口径填充（见 AiWriteServiceImpl）：
 *    pov / language_style / pace 填空串，title 取输入前 50 字，tokens_used=0。
 * 3. 本类位于 dto 包（非 domain），typeAliasesPackage 只扫 **.domain，故 XML 用全限定类名。
 *
 * @author xiangsipeng
 */
public class AppAiWriteRecordInsert
{
    /** 记录ID（auto_increment 回填，非写入列） */
    private Long recordId;

    /** 归属用户ID（当前登录身份） */
    private Long userId;

    /** 关联作品ID（可空） */
    private Long workId;

    /** 标题（NOT NULL） */
    private String title;

    /** 写作类型（NOT NULL） */
    private String writeType;

    /** 视角（NOT NULL，填空串） */
    private String pov;

    /** 语言风格（NOT NULL，填空串） */
    private String languageStyle;

    /** 节奏（NOT NULL，填空串） */
    private String pace;

    /** 输入提示词（NOT NULL） */
    private String inputPrompt;

    /** 输出内容（NOT NULL） */
    private String outputContent;

    /** 字数（NOT NULL） */
    private Integer wordCount;

    /** 模型（NOT NULL） */
    private String aiModel;

    /** 消耗 token（NOT NULL，固定 0） */
    private Integer tokensUsed;

    /** 状态（NOT NULL） */
    private String status;

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
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

    public String getPov()
    {
        return pov;
    }

    public void setPov(String pov)
    {
        this.pov = pov;
    }

    public String getLanguageStyle()
    {
        return languageStyle;
    }

    public void setLanguageStyle(String languageStyle)
    {
        this.languageStyle = languageStyle;
    }

    public String getPace()
    {
        return pace;
    }

    public void setPace(String pace)
    {
        this.pace = pace;
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

    public Integer getTokensUsed()
    {
        return tokensUsed;
    }

    public void setTokensUsed(Integer tokensUsed)
    {
        this.tokensUsed = tokensUsed;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}