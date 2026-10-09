package com.smartscript.platform.content.dto;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 作品版本（App 接口文档 2.9.7 版本列表 / 2.9.9 版本详情）。
 *
 * 列表不下发 content 全文（长文本），content 为 null；详情下发 versionId/versionNo/content。
 *
 * @author xiangsipeng
 */
public class AppWorkVersionDto
{
    /** 版本ID */
    private Long versionId;

    /** 版本号 */
    private String versionNo;

    /** 版本内容（仅详情下发） */
    private String content;

    /** 变更说明（落库 change_log） */
    private String changeLog;

    /** 是否为当前版本（0=否 1=是） */
    private String isCurrent;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getVersionId()
    {
        return versionId;
    }

    public void setVersionId(Long versionId)
    {
        this.versionId = versionId;
    }

    public String getVersionNo()
    {
        return versionNo;
    }

    public void setVersionNo(String versionNo)
    {
        this.versionNo = versionNo;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getChangeLog()
    {
        return changeLog;
    }

    public void setChangeLog(String changeLog)
    {
        this.changeLog = changeLog;
    }

    public String getIsCurrent()
    {
        return isCurrent;
    }

    public void setIsCurrent(String isCurrent)
    {
        this.isCurrent = isCurrent;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}