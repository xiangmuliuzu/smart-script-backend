package com.smartscript.platform.content.dto;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * AI 大纲请求记录条目（App 大纲记录列表元素）。
 *
 * 依据：云端 script_platform_dev 库 sys_ai_request 表（附件5.1 表3-26）。
 *
 * 说明：该表只记录请求元信息（请求编号 / 能力 / 消耗次数 / 状态 / 提供方 / 起止时间），
 * 不存储原始输入（仅 prompt_hash）与大纲结果（result_file_id 可空），
 * 故本条目不含输入 / 输出内容；大纲正文请在创作结果页保存。
 *
 * @author xiangsipeng
 */
public class AppAiOutlineRecordItem
{
    /** 请求ID（sys_ai_request.request_id） */
    private Long requestId;

    /** 请求编号（全局唯一） */
    private String requestNo;

    /** 能力标识（outline） */
    private String capability;

    /** 消耗次数 */
    private Integer quotaCost;

    /** 状态（success / failed） */
    private String status;

    /** AI 服务提供方 */
    private String provider;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startedAt;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishedAt;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    public Long getRequestId()
    {
        return requestId;
    }

    public void setRequestId(Long requestId)
    {
        this.requestId = requestId;
    }

    public String getRequestNo()
    {
        return requestNo;
    }

    public void setRequestNo(String requestNo)
    {
        this.requestNo = requestNo;
    }

    public String getCapability()
    {
        return capability;
    }

    public void setCapability(String capability)
    {
        this.capability = capability;
    }

    public Integer getQuotaCost()
    {
        return quotaCost;
    }

    public void setQuotaCost(Integer quotaCost)
    {
        this.quotaCost = quotaCost;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getProvider()
    {
        return provider;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }

    public Date getStartedAt()
    {
        return startedAt;
    }

    public void setStartedAt(Date startedAt)
    {
        this.startedAt = startedAt;
    }

    public Date getFinishedAt()
    {
        return finishedAt;
    }

    public void setFinishedAt(Date finishedAt)
    {
        this.finishedAt = finishedAt;
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
