package com.smartscript.platform.user.dto;

/** 创建扫码登录票据的请求体。创作者端固定 creator，企业端传 enterprise。 */
public class QrLoginCreateRequest
{
    private String portal;

    public String getPortal()
    {
        return portal;
    }

    public void setPortal(String portal)
    {
        this.portal = portal;
    }
}
