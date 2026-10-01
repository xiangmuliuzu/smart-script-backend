package com.smartscript.platform.content.dto;

/**
 * 作品文件（App 侧只读下发对象，免费试读附件用）。
 *
 * 依据：云端 script_platform_dev 库 sys_work_file 表（附件5.1 表3-15）。
 * 字段裁剪：只下发 App 展示与下载所需字段，不下发 is_preview/sort/若依审计字段。
 *
 * @author xiangsipeng
 */
public class AppWorkFileDto
{
    /** 文件ID */
    private Long fileId;

    /** 文件名称 */
    private String fileName;

    /** 文件URL */
    private String fileUrl;

    /** 文件类型 */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    public AppWorkFileDto()
    {
    }

    public Long getFileId()
    {
        return fileId;
    }

    public void setFileId(Long fileId)
    {
        this.fileId = fileId;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getFileUrl()
    {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl)
    {
        this.fileUrl = fileUrl;
    }

    public String getFileType()
    {
        return fileType;
    }

    public void setFileType(String fileType)
    {
        this.fileType = fileType;
    }

    public Long getFileSize()
    {
        return fileSize;
    }

    public void setFileSize(Long fileSize)
    {
        this.fileSize = fileSize;
    }
}