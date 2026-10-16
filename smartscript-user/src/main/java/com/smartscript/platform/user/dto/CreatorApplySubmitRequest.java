package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A5 创作者申请提交请求（契约 §1.6）。
 *
 * 只承载申请人填写的字段；不包含 userId（数据归属由服务端按当前身份取）。
 * 长度上限与 user_creator_application 列宽一致，超限由 @Size 与服务层双重拦截。
 */
public class CreatorApplySubmitRequest
{
    @NotBlank
    @Size(max = 50)
    private String penName;

    @NotBlank
    @Size(max = 200)
    private String direction;

    /** 代表作品：可留空。 */
    @Size(max = 500)
    private String portfolio;

    @NotBlank
    @Size(max = 500)
    private String intro;

    @NotBlank
    @Size(max = 100)
    private String contact;

    public String getPenName()
    {
        return penName;
    }

    public void setPenName(String penName)
    {
        this.penName = penName;
    }

    public String getDirection()
    {
        return direction;
    }

    public void setDirection(String direction)
    {
        this.direction = direction;
    }

    public String getPortfolio()
    {
        return portfolio;
    }

    public void setPortfolio(String portfolio)
    {
        this.portfolio = portfolio;
    }

    public String getIntro()
    {
        return intro;
    }

    public void setIntro(String intro)
    {
        this.intro = intro;
    }

    public String getContact()
    {
        return contact;
    }

    public void setContact(String contact)
    {
        this.contact = contact;
    }
}
