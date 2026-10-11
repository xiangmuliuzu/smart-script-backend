package com.smartscript.platform.content.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 版权申请对象 sys_copyright_application
 * 
 * @author SmartScript Team
 * @date 2024-01
 */
public class SysCopyrightApplication extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 申请ID */
    private Long applyId;

    /** 作品ID */
    @Excel(name = "作品ID")
    private Long workId;

    /** 用户ID */
    @Excel(name = "用户ID")
    private Long userId;

    /** 申请类型 */
    @Excel(name = "申请类型")
    private String applyType;

    /** 申请状态 */
    @Excel(name = "申请状态")
    private String status;

    /** 版权中心外部编号 */
    @Excel(name = "外部编号")
    private String externalNo;

    /** 证书编号 */
    @Excel(name = "证书编号")
    private String certNo;

    /** 证书URL */
    private String certUrl;

    /** 证书颁发时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "证书颁发时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date certTime;

    /** 审核意见 */
    @Excel(name = "审核意见")
    private String reviewComment;

    /** 拒绝原因 */
    @Excel(name = "拒绝原因")
    private String rejectReason;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "提交时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date submitTime;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "审核时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date reviewTime;

    /** 审核人ID */
    private Long reviewerId;

    /** 配置ID */
    private Long configId;

    /** 作品名称 */
    @Excel(name = "作品名称")
    private String workTitle;

    /** 作品类型 */
    @Excel(name = "作品类型")
    private String workType;

    /** 作者名称 */
    @Excel(name = "作者名称")
    private String authorName;

    /** 创作日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "创作日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date creationDate;

    /** 发表日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "发表日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date publicationDate;

    /** 作品描述 */
    @Excel(name = "作品描述")
    private String workDescription;

    /** 附件URL */
    @Excel(name = "附件URL")
    private String attachmentUrl;

    /** 作品名称（关联字段） */
    private String workName;

    /** 用户名称（关联字段） */
    private String userName;

    /** 审核人名称（关联字段） */
    private String reviewerName;

    /** 申请编号（扩展字段，用于前端显示） */
    private String applyNo;

    /** 版权中心代码（扩展字段，从configId关联获取） */
    private String copyrightCenter;

    /** 版权中心名称（扩展字段，从configId关联获取） */
    private String centerName;

    /** 外部申请号（扩展字段，映射externalNo用于前端显示） */
    private String externalApplyNo;

    /** 状态中文（扩展字段，用于前端显示） */
    private String statusText;

    public void setApplyId(Long applyId) 
    {
        this.applyId = applyId;
    }

    public Long getApplyId() 
    {
        return applyId;
    }

    public void setWorkId(Long workId) 
    {
        this.workId = workId;
    }

    public Long getWorkId() 
    {
        return workId;
    }

    public void setUserId(Long userId) 
    {
        this.userId = userId;
    }

    public Long getUserId() 
    {
        return userId;
    }

    public void setApplyType(String applyType) 
    {
        this.applyType = applyType;
    }

    public String getApplyType() 
    {
        return applyType;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    public void setExternalNo(String externalNo) 
    {
        this.externalNo = externalNo;
    }

    public String getExternalNo() 
    {
        return externalNo;
    }

    public void setCertNo(String certNo) 
    {
        this.certNo = certNo;
    }

    public String getCertNo() 
    {
        return certNo;
    }

    public void setCertUrl(String certUrl) 
    {
        this.certUrl = certUrl;
    }

    public String getCertUrl() 
    {
        return certUrl;
    }

    public void setCertTime(Date certTime) 
    {
        this.certTime = certTime;
    }

    public Date getCertTime() 
    {
        return certTime;
    }

    public void setReviewComment(String reviewComment) 
    {
        this.reviewComment = reviewComment;
    }

    public String getReviewComment() 
    {
        return reviewComment;
    }

    public void setRejectReason(String rejectReason) 
    {
        this.rejectReason = rejectReason;
    }

    public String getRejectReason() 
    {
        return rejectReason;
    }

    public void setSubmitTime(Date submitTime) 
    {
        this.submitTime = submitTime;
    }

    public Date getSubmitTime() 
    {
        return submitTime;
    }

    public void setReviewTime(Date reviewTime) 
    {
        this.reviewTime = reviewTime;
    }

    public Date getReviewTime() 
    {
        return reviewTime;
    }

    public void setReviewerId(Long reviewerId) 
    {
        this.reviewerId = reviewerId;
    }

    public Long getReviewerId() 
    {
        return reviewerId;
    }

    public void setConfigId(Long configId) 
    {
        this.configId = configId;
    }

    public Long getConfigId() 
    {
        return configId;
    }

    public void setWorkTitle(String workTitle) 
    {
        this.workTitle = workTitle;
    }

    public String getWorkTitle() 
    {
        return workTitle;
    }

    public void setWorkType(String workType) 
    {
        this.workType = workType;
    }

    public String getWorkType() 
    {
        return workType;
    }

    public void setAuthorName(String authorName) 
    {
        this.authorName = authorName;
    }

    public String getAuthorName() 
    {
        return authorName;
    }

    public void setCreationDate(Date creationDate) 
    {
        this.creationDate = creationDate;
    }

    public Date getCreationDate() 
    {
        return creationDate;
    }

    public void setPublicationDate(Date publicationDate) 
    {
        this.publicationDate = publicationDate;
    }

    public Date getPublicationDate() 
    {
        return publicationDate;
    }

    public void setWorkDescription(String workDescription) 
    {
        this.workDescription = workDescription;
    }

    public String getWorkDescription() 
    {
        return workDescription;
    }

    public void setAttachmentUrl(String attachmentUrl) 
    {
        this.attachmentUrl = attachmentUrl;
    }

    public String getAttachmentUrl() 
    {
        return attachmentUrl;
    }

    public String getWorkName() 
    {
        return workName;
    }

    public void setWorkName(String workName) 
    {
        this.workName = workName;
    }

    public String getUserName() 
    {
        return userName;
    }

    public void setUserName(String userName) 
    {
        this.userName = userName;
    }

    public String getReviewerName() 
    {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) 
    {
        this.reviewerName = reviewerName;
    }

    public String getApplyNo() 
    {
        return applyNo;
    }

    public void setApplyNo(String applyNo) 
    {
        this.applyNo = applyNo;
    }

    public String getCopyrightCenter() 
    {
        return copyrightCenter;
    }

    public void setCopyrightCenter(String copyrightCenter) 
    {
        this.copyrightCenter = copyrightCenter;
    }

    public String getCenterName() 
    {
        return centerName;
    }

    public void setCenterName(String centerName) 
    {
        this.centerName = centerName;
    }

    public String getExternalApplyNo() 
    {
        return externalApplyNo;
    }

    public void setExternalApplyNo(String externalApplyNo) 
    {
        this.externalApplyNo = externalApplyNo;
    }

    public String getStatusText() 
    {
        return statusText;
    }

    public void setStatusText(String statusText) 
    {
        this.statusText = statusText;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("applyId", getApplyId())
            .append("workId", getWorkId())
            .append("userId", getUserId())
            .append("workTitle", getWorkTitle())
            .append("workType", getWorkType())
            .append("authorName", getAuthorName())
            .append("creationDate", getCreationDate())
            .append("publicationDate", getPublicationDate())
            .append("workDescription", getWorkDescription())
            .append("attachmentUrl", getAttachmentUrl())
            .append("applyType", getApplyType())
            .append("status", getStatus())
            .append("externalNo", getExternalNo())
            .append("certNo", getCertNo())
            .append("certUrl", getCertUrl())
            .append("certTime", getCertTime())
            .append("reviewComment", getReviewComment())
            .append("rejectReason", getRejectReason())
            .append("submitTime", getSubmitTime())
            .append("reviewTime", getReviewTime())
            .append("reviewerId", getReviewerId())
            .append("configId", getConfigId())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
