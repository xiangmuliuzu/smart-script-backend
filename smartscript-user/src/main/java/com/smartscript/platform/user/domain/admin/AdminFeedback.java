package com.smartscript.platform.user.domain.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A4 PC 管理域：用户反馈（契约 §3.5 AdminFeedback）。
 *
 * 映射自 user_feedback。状态机（契约 §5.5，与 DB CHECK 约束一致）：
 *   SUBMITTED -> PROCESSING -> REPLIED/CLOSED
 *   REPLIED   -> CLOSED
 * A4 迁移已把存量 OPEN 归一为 SUBMITTED。
 *
 * 列表不返回附件短时地址；详情由有 query 权限者按短时授权获取。
 * 处理人来自 PC 安全上下文，不接受请求体指定。
 */
public class AdminFeedback
{
    private Long feedbackId;
    private Long userId;
    private String nickname;
    private String phoneMasked;
    private String category;
    private String content;
    private String status;
    private String reply;
    private String handlerName;
    private Date submittedAt;
    private Date handledAt;

    /**
     * 附件短时授权引用；列表不返回。
     * NON_EMPTY：空集合时不出现在响应中。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> attachmentRefs = new ArrayList<>();

    /**
     * 数据库中的单值附件列（user_feedback.attachment_ref）。
     *
     * 仅作为 Mapper 的接收字段存在，不参与 JSON 序列化；服务层据此按
     * 裁决 GAP-5 生成零或单元素 attachmentRefs，禁止按分隔符拆分。
     */
    @JsonIgnore
    private String attachmentRef;

    public Long getFeedbackId()
    {
        return feedbackId;
    }

    public void setFeedbackId(Long feedbackId)
    {
        this.feedbackId = feedbackId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getNickname()
    {
        return nickname;
    }

    public void setNickname(String nickname)
    {
        this.nickname = nickname;
    }

    public String getPhoneMasked()
    {
        return phoneMasked;
    }

    public void setPhoneMasked(String phoneMasked)
    {
        this.phoneMasked = phoneMasked;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getReply()
    {
        return reply;
    }

    public void setReply(String reply)
    {
        this.reply = reply;
    }

    public String getHandlerName()
    {
        return handlerName;
    }

    public void setHandlerName(String handlerName)
    {
        this.handlerName = handlerName;
    }

    public Date getSubmittedAt()
    {
        return submittedAt;
    }

    public void setSubmittedAt(Date submittedAt)
    {
        this.submittedAt = submittedAt;
    }

    public Date getHandledAt()
    {
        return handledAt;
    }

    public void setHandledAt(Date handledAt)
    {
        this.handledAt = handledAt;
    }

    public List<String> getAttachmentRefs()
    {
        return attachmentRefs;
    }

    public void setAttachmentRefs(List<String> attachmentRefs)
    {
        this.attachmentRefs = attachmentRefs == null ? new ArrayList<>() : attachmentRefs;
    }

    public String getAttachmentRef()
    {
        return attachmentRef;
    }

    public void setAttachmentRef(String attachmentRef)
    {
        this.attachmentRef = attachmentRef;
    }
}
