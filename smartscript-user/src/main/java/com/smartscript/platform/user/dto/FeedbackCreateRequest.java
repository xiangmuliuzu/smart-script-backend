package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A5 反馈提交请求（契约 §1.6）。
 *
 * 分类取值与 A4 一致：FEATURE / EXPERIENCE / BUG / COMPLAINT / OTHER。
 * 附件只接受平台上传服务返回的引用，不接受任意外部地址。
 */
public class FeedbackCreateRequest
{
    private String category;

    @NotBlank
    private String content;

    private String attachmentRef;

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

    public String getAttachmentRef()
    {
        return attachmentRef;
    }

    public void setAttachmentRef(String attachmentRef)
    {
        this.attachmentRef = attachmentRef;
    }
}
