package com.smartscript.platform.content.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 作品文件对象 sys_work_file
 *
 * 依据：云端 script_platform_dev 库 SHOW CREATE TABLE sys_work_file。
 * 表注释：作品文件表（作品上传资料查看页：PC 后台浏览作品相关文件）。
 *
 * 说明（无文档依据，反推处理点）：
 * 1. created_at 由数据库默认值维护，代码不读不写；Entity 只映射若依5通用字段
 *    （create_by/create_time/update_by/update_time/remark，继承 BaseEntity）。
 * 2. is_preview 为 tinyint，Entity 用 String（若依字典风格），JDBC 自动转换。
 * 3. file_id 为 bigint，Entity 用 Long（若依惯例）。
 *
 * @author xiangsipeng
 */
public class SysWorkFile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 文件ID */
    private Long fileId;

    /** 作品ID */
    private Long workId;

    /** 文件名称 */
    private String fileName;

    /** 文件URL */
    private String fileUrl;

    /** 文件类型 */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 是否可预览（0=否 1=是） */
    private String isPreview;

    /** 显示顺序 */
    private Integer sort;

    public SysWorkFile()
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

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
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

    public String getIsPreview()
    {
        return isPreview;
    }

    public void setIsPreview(String isPreview)
    {
        this.isPreview = isPreview;
    }

    public Integer getSort()
    {
        return sort;
    }

    public void setSort(Integer sort)
    {
        this.sort = sort;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("fileId", getFileId())
                .append("workId", getWorkId())
                .append("fileName", getFileName())
                .append("fileUrl", getFileUrl())
                .append("fileType", getFileType())
                .append("fileSize", getFileSize())
                .append("isPreview", getIsPreview())
                .append("sort", getSort())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
