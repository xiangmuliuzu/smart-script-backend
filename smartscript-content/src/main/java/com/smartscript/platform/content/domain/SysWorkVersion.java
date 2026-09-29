package com.smartscript.platform.content.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 作品版本对象 sys_work_version
 *
 * 依据：云端 script_platform_dev 库 SHOW CREATE TABLE sys_work_version。
 * 表注释：作品版本表（作品上传资料查看页：PC 后台查看作品历史版本与变更日志）。
 *
 * 说明（无文档依据，反推处理点）：
 * 1. created_at 由数据库默认值维护，代码不读不写；Entity 只映射若依5通用字段
 *    （create_by/create_time/update_by/update_time/remark，继承 BaseEntity）。
 * 2. is_current 为 tinyint，Entity 用 String（若依字典风格），JDBC 自动转换。
 * 3. version_id 为 int，Entity 用 Long（若依惯例，与 category_id 等保持一致）。
 * 4. status 已为 varchar，与若依字典风格天然契合，直接 String。
 * 5. creatorName 为 JOIN 扩展字段：left join sys_user u on u.user_id = v.creator_id，
 *    取 u.nickname as creator_name；非表原生列，仅用于列表/详情展示。
 *
 * @author xiangsipeng
 */
public class SysWorkVersion extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 版本ID */
    private Long versionId;

    /** 作品ID */
    private Long workId;

    /** 版本号 */
    private String versionNo;

    /** 版本标题 */
    private String title;

    /** 版本内容 */
    private String content;

    /** 版本文件URL */
    private String fileUrl;

    /** 变更日志 */
    private String changeLog;

    /** 创建人ID */
    private Long creatorId;

    /** 状态 */
    private String status;

    /** 是否为当前版本（0=否 1=是） */
    private String isCurrent;

    /** 创建人名称（JOIN sys_user.nickname 扩展字段） */
    private String creatorName;

    public SysWorkVersion()
    {
    }

    public Long getVersionId()
    {
        return versionId;
    }

    public void setVersionId(Long versionId)
    {
        this.versionId = versionId;
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public String getVersionNo()
    {
        return versionNo;
    }

    public void setVersionNo(String versionNo)
    {
        this.versionNo = versionNo;
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

    public String getFileUrl()
    {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl)
    {
        this.fileUrl = fileUrl;
    }

    public String getChangeLog()
    {
        return changeLog;
    }

    public void setChangeLog(String changeLog)
    {
        this.changeLog = changeLog;
    }

    public Long getCreatorId()
    {
        return creatorId;
    }

    public void setCreatorId(Long creatorId)
    {
        this.creatorId = creatorId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getIsCurrent()
    {
        return isCurrent;
    }

    public void setIsCurrent(String isCurrent)
    {
        this.isCurrent = isCurrent;
    }

    public String getCreatorName()
    {
        return creatorName;
    }

    public void setCreatorName(String creatorName)
    {
        this.creatorName = creatorName;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("versionId", getVersionId())
                .append("workId", getWorkId())
                .append("versionNo", getVersionNo())
                .append("title", getTitle())
                .append("content", getContent())
                .append("fileUrl", getFileUrl())
                .append("changeLog", getChangeLog())
                .append("creatorId", getCreatorId())
                .append("status", getStatus())
                .append("isCurrent", getIsCurrent())
                .append("creatorName", getCreatorName())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
