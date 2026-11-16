package com.smartscript.platform.content.dto.copyright;

import jakarta.validation.constraints.NotBlank;

/**
 * 版权第三方代理回调消息请求。
 * 国版 → 代理账号 → 回调本接口 → 平台通知用户。
 */
public class CopyrightThirdPartyMessageRequest
{
    /** 第三方消息幂等 ID（必填） */
    @NotBlank(message = "externalMessageId不能为空")
    private String externalMessageId;

    /** 平台申请 ID（与 externalApplyNo 至少填一个） */
    private Long applyId;

    /** 第三方申请编号 */
    private String externalApplyNo;

    /**
     * supplement_required / notice / certificated / rejected / other
     */
    @NotBlank(message = "messageType不能为空")
    private String messageType;

    private String title;
    private String content;
    private String attachmentUrl;
    /** 要求清单 JSON 字符串（可选） */
    private String requirementJson;
    /** 第三方给出的截止时间 yyyy-MM-dd HH:mm:ss；空则平台按+30天 */
    private String deadlineTime;
    /** 原始载荷（可选，原样落库） */
    private String payloadJson;

    /** 发证时 */
    private String certNo;
    private String certUrl;
    /** 驳回时 */
    private String rejectReason;

    public String getExternalMessageId()
    {
        return externalMessageId;
    }

    public void setExternalMessageId(String externalMessageId)
    {
        this.externalMessageId = externalMessageId;
    }

    public Long getApplyId()
    {
        return applyId;
    }

    public void setApplyId(Long applyId)
    {
        this.applyId = applyId;
    }

    public String getExternalApplyNo()
    {
        return externalApplyNo;
    }

    public void setExternalApplyNo(String externalApplyNo)
    {
        this.externalApplyNo = externalApplyNo;
    }

    public String getMessageType()
    {
        return messageType;
    }

    public void setMessageType(String messageType)
    {
        this.messageType = messageType;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getAttachmentUrl()
    {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl)
    {
        this.attachmentUrl = attachmentUrl;
    }

    public String getRequirementJson()
    {
        return requirementJson;
    }

    public void setRequirementJson(String requirementJson)
    {
        this.requirementJson = requirementJson;
    }

    public String getDeadlineTime()
    {
        return deadlineTime;
    }

    public void setDeadlineTime(String deadlineTime)
    {
        this.deadlineTime = deadlineTime;
    }

    public String getPayloadJson()
    {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson)
    {
        this.payloadJson = payloadJson;
    }

    public String getCertNo()
    {
        return certNo;
    }

    public void setCertNo(String certNo)
    {
        this.certNo = certNo;
    }

    public String getCertUrl()
    {
        return certUrl;
    }

    public void setCertUrl(String certUrl)
    {
        this.certUrl = certUrl;
    }

    public String getRejectReason()
    {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason)
    {
        this.rejectReason = rejectReason;
    }
}
