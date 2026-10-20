package com.smartscript.platform.content.dto.copyright;

/**
 * App 端版权中心配置 DTO。
 */
public class AppCopyrightCenterDto
{
    /** 版权中心代码 */
    private String centerCode;

    /** 版权中心名称 */
    private String centerName;

    public AppCopyrightCenterDto() {
    }

    public AppCopyrightCenterDto(String centerCode, String centerName) {
        this.centerCode = centerCode;
        this.centerName = centerName;
    }

    public String getCenterCode() {
        return centerCode;
    }

    public void setCenterCode(String centerCode) {
        this.centerCode = centerCode;
    }

    public String getCenterName() {
        return centerName;
    }

    public void setCenterName(String centerName) {
        this.centerName = centerName;
    }
}
