package com.smartscript.platform.content.dto;

/**
 * 文件上传结果（App 接口文档 2.9.1 表 2-111）。
 *
 * 说明：库里无「通用文件表」，且 sys_work_file.work_id 为 NOT NULL（创建作品前无法落关联表），
 * 故 fileId 恒为 null；客户端以 url 为准（封面可直接回填创建/更新作品）。
 *
 * @author xiangsipeng
 */
public class AppUploadResult
{
    /** 文件ID（当前无通用文件表，恒为 null） */
    private Long fileId;

    /** 文件可访问地址 */
    private String url;

    /** 原始文件名 */
    private String fileName;

    public Long getFileId()
    {
        return fileId;
    }

    public void setFileId(Long fileId)
    {
        this.fileId = fileId;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }
}