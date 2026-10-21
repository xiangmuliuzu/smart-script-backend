package com.smartscript.platform.content.dto.copyright;

/**
 * App 端版权证书下载 DTO。
 */
public class AppCopyrightCertDownloadDto
{
    /** 证书文件下载URL */
    private String url;

    public AppCopyrightCertDownloadDto() {
    }

    public AppCopyrightCertDownloadDto(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
