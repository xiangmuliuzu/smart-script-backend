package com.smartscript.platform.content.dto.copyright;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Admin 端版权申请详情 DTO（包含完整作品和材料信息）。
 */
public class AdminCopyrightApplicationDetailDto
{
    // ==================== 申请基本信息 ====================
    
    /** 申请ID */
    private Long applyId;

    /** 申请编号 */
    private String applyNo;

    /** 申请状态 */
    private String status;

    /** 同步状态 */
    private String syncStatus;

    /** 版权中心代码 */
    private String copyrightCenter;

    /** 版权中心名称 */
    private String centerName;

    /** 第三方申请编号 */
    private String externalApplyNo;

    /** 证书编号 */
    private String certNo;

    /** 证书URL */
    private String certUrl;

    /** 驳回原因 */
    private String rejectReason;

    /** 申请时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date submitTime;

    /** 发证时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date certTime;

    /** 备注 */
    private String remark;

    // ==================== 作品信息 ====================

    /** 作品ID */
    private Long workId;

    /** 作品名称 */
    private String workName;

    /** 作品类型 */
    private String workType;

    /** 作品类别 */
    private String workCategory;

    /** 作品简介 */
    private String workSummary;

    /** 作品封面URL */
    private String workCoverUrl;

    /** 作品文件URL */
    private String workFileUrl;

    /** 作品字数 */
    private Integer workWordCount;

    /** 作品时长（秒） */
    private Integer workDuration;

    /** 创作完成日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date workCreationDate;

    /** 首次发表日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date workPublishDate;

    // ==================== 申请人信息 ====================

    /** 申请人用户ID */
    private Long userId;

    /** 申请人姓名 */
    private String userName;

    /** 申请人手机号 */
    private String userPhone;

    /** 申请人邮箱 */
    private String userEmail;

    /** 实名认证状态 */
    private String realNameStatus;

    // ==================== 申请材料 ====================

    /** 原创性证明材料URL（JSON数组或逗号分隔） */
    private String originalityProofUrls;

    /** 作者身份证明材料URL（JSON数组或逗号分隔） */
    private String authorIdentityDocUrls;

    /** 创作过程材料URL（JSON数组或逗号分隔） */
    private String creationProcessUrls;

    /** 其他辅助证明材料URL（JSON数组或逗号分隔） */
    private String otherProofUrls;

    /** 申请材料附件URL（兼容旧版本，逗号分隔或JSON数组） */
    private String attachmentUrl;

    // ==================== Getters and Setters ====================

    public Long getApplyId() {
        return applyId;
    }

    public void setApplyId(Long applyId) {
        this.applyId = applyId;
    }

    public String getApplyNo() {
        return applyNo;
    }

    public void setApplyNo(String applyNo) {
        this.applyNo = applyNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getCopyrightCenter() {
        return copyrightCenter;
    }

    public void setCopyrightCenter(String copyrightCenter) {
        this.copyrightCenter = copyrightCenter;
    }

    public String getCenterName() {
        return centerName;
    }

    public void setCenterName(String centerName) {
        this.centerName = centerName;
    }

    public String getExternalApplyNo() {
        return externalApplyNo;
    }

    public void setExternalApplyNo(String externalApplyNo) {
        this.externalApplyNo = externalApplyNo;
    }

    public String getCertNo() {
        return certNo;
    }

    public void setCertNo(String certNo) {
        this.certNo = certNo;
    }

    public String getCertUrl() {
        return certUrl;
    }

    public void setCertUrl(String certUrl) {
        this.certUrl = certUrl;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(Date submitTime) {
        this.submitTime = submitTime;
    }

    public Date getCertTime() {
        return certTime;
    }

    public void setCertTime(Date certTime) {
        this.certTime = certTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getWorkId() {
        return workId;
    }

    public void setWorkId(Long workId) {
        this.workId = workId;
    }

    public String getWorkName() {
        return workName;
    }

    public void setWorkName(String workName) {
        this.workName = workName;
    }

    public String getWorkType() {
        return workType;
    }

    public void setWorkType(String workType) {
        this.workType = workType;
    }

    public String getWorkCategory() {
        return workCategory;
    }

    public void setWorkCategory(String workCategory) {
        this.workCategory = workCategory;
    }

    public String getWorkIntro() {
        return workSummary;
    }

    public void setWorkIntro(String workIntro) {
        this.workSummary = workIntro;
    }

    public String getWorkSummary() {
        return workSummary;
    }

    public void setWorkSummary(String workSummary) {
        this.workSummary = workSummary;
    }

    public String getWorkCoverUrl() {
        return workCoverUrl;
    }

    public void setWorkCoverUrl(String workCoverUrl) {
        this.workCoverUrl = workCoverUrl;
    }

    public String getWorkFileUrl() {
        return workFileUrl;
    }

    public void setWorkFileUrl(String workFileUrl) {
        this.workFileUrl = workFileUrl;
    }

    public Integer getWorkWordCount() {
        return workWordCount;
    }

    public void setWorkWordCount(Integer workWordCount) {
        this.workWordCount = workWordCount;
    }

    public Integer getWorkDuration() {
        return workDuration;
    }

    public void setWorkDuration(Integer workDuration) {
        this.workDuration = workDuration;
    }

    public Date getWorkCompletionDate() {
        return workCreationDate;
    }

    public void setWorkCompletionDate(Date workCompletionDate) {
        this.workCreationDate = workCompletionDate;
    }

    public Date getWorkCreationDate() {
        return workCreationDate;
    }

    public void setWorkCreationDate(Date workCreationDate) {
        this.workCreationDate = workCreationDate;
    }

    public Date getWorkPublishDate() {
        return workPublishDate;
    }

    public void setWorkPublishDate(Date workPublishDate) {
        this.workPublishDate = workPublishDate;
    }

    public String getWorkSize() {
        if (workWordCount != null && workWordCount > 0) {
            return workWordCount + "字";
        }
        if (workDuration != null && workDuration > 0) {
            return (workDuration / 60) + "分钟";
        }
        return null;
    }

    public void setWorkSize(String workSize) {
        // 兼容旧接口，不做实际赋值
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPhone() {
        return userPhone;
    }

    public void setUserPhone(String userPhone) {
        this.userPhone = userPhone;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getRealNameStatus() {
        return realNameStatus;
    }

    public void setRealNameStatus(String realNameStatus) {
        this.realNameStatus = realNameStatus;
    }

    public String getIdentityProofUrl() {
        return authorIdentityDocUrls;
    }

    public void setIdentityProofUrl(String identityProofUrl) {
        this.authorIdentityDocUrls = identityProofUrl;
    }

    public String getOriginalityProofUrls() {
        return originalityProofUrls;
    }

    public void setOriginalityProofUrls(String originalityProofUrls) {
        this.originalityProofUrls = originalityProofUrls;
    }

    public String getAuthorIdentityDocUrls() {
        return authorIdentityDocUrls;
    }

    public void setAuthorIdentityDocUrls(String authorIdentityDocUrls) {
        this.authorIdentityDocUrls = authorIdentityDocUrls;
    }

    public String getCreationProofUrl() {
        return creationProcessUrls;
    }

    public void setCreationProofUrl(String creationProofUrl) {
        this.creationProcessUrls = creationProofUrl;
    }

    public String getCreationProcessUrls() {
        return creationProcessUrls;
    }

    public void setCreationProcessUrls(String creationProcessUrls) {
        this.creationProcessUrls = creationProcessUrls;
    }

    public String getOtherMaterialsUrls() {
        return otherProofUrls;
    }

    public void setOtherMaterialsUrls(String otherMaterialsUrls) {
        this.otherProofUrls = otherMaterialsUrls;
    }

    public String getOtherProofUrls() {
        return otherProofUrls;
    }

    public void setOtherProofUrls(String otherProofUrls) {
        this.otherProofUrls = otherProofUrls;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
    }
}
