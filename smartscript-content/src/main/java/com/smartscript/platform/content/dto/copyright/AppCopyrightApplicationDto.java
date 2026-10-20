package com.smartscript.platform.content.dto.copyright;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * App 端版权申请 DTO。
 */
public class AppCopyrightApplicationDto
{
    /** 申请ID */
    private Long applyId;

    /** 申请编号 */
    private String applyNo;

    /** 作品ID */
    private Long workId;

    /** 作品名称 */
    private String workName;

    /** 作品类型 */
    private String workType;

    /** 作品类别（文字/美术/摄影/音乐/视频等） */
    private String workCategory;

    /** 作品简介 */
    private String workIntro;

    /** 作品摘要/简介（别名，映射到summary字段） */
    private String workSummary;

    /** 作品封面URL */
    private String workCoverUrl;

    /** 作品创作完成日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date workCompletionDate;

    /** 作品首次发表日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date workPublishDate;

    /** 作品文件URL（申请材料） */
    private String workFileUrl;

    /** 身份证明材料URL */
    private String identityProofUrl;

    /** 创作说明材料URL */
    private String creationProofUrl;

    /** 其他辅助材料URL列表（JSON数组字符串或逗号分隔） */
    private String otherMaterialsUrls;

    /** 原创性证明材料URL（JSON数组） */
    private String originalityProof;

    /** 作者身份证明材料URL（JSON数组） */
    private String authorIdentityDoc;

    /** 创作过程材料URL（JSON数组） */
    private String creationProcess;

    /** 其他辅助证明材料URL（JSON数组） */
    private String otherProof;

    /** 申请人用户ID */
    private Long userId;

    /** 申请人姓名 */
    private String userName;

    /** 版权中心代码 */
    private String copyrightCenter;

    /** 版权中心名称 */
    private String centerName;

    /** 第三方申请编号 */
    private String externalApplyNo;

    /** 状态：pending/approved/submitted/reviewing/certificated/rejected */
    private String status;

    /** 证书编号 */
    private String certNo;

    /** 证书ID */
    private Long certId;

    /** 证书文件URL */
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

    // Getters and Setters

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
        return workIntro;
    }

    public void setWorkIntro(String workIntro) {
        this.workIntro = workIntro;
    }

    public String getWorkCoverUrl() {
        return workCoverUrl;
    }

    public void setWorkCoverUrl(String workCoverUrl) {
        this.workCoverUrl = workCoverUrl;
    }

    public Date getWorkCompletionDate() {
        return workCompletionDate;
    }

    public void setWorkCompletionDate(Date workCompletionDate) {
        this.workCompletionDate = workCompletionDate;
    }

    public Date getWorkPublishDate() {
        return workPublishDate;
    }

    public void setWorkPublishDate(Date workPublishDate) {
        this.workPublishDate = workPublishDate;
    }

    public String getWorkFileUrl() {
        return workFileUrl;
    }

    public void setWorkFileUrl(String workFileUrl) {
        this.workFileUrl = workFileUrl;
    }

    public String getIdentityProofUrl() {
        return identityProofUrl;
    }

    public void setIdentityProofUrl(String identityProofUrl) {
        this.identityProofUrl = identityProofUrl;
    }

    public String getCreationProofUrl() {
        return creationProofUrl;
    }

    public void setCreationProofUrl(String creationProofUrl) {
        this.creationProofUrl = creationProofUrl;
    }

    public String getOtherMaterialsUrls() {
        return otherMaterialsUrls;
    }

    public void setOtherMaterialsUrls(String otherMaterialsUrls) {
        this.otherMaterialsUrls = otherMaterialsUrls;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCertNo() {
        return certNo;
    }

    public void setCertNo(String certNo) {
        this.certNo = certNo;
    }

    public Long getCertId() {
        return certId;
    }

    public void setCertId(Long certId) {
        this.certId = certId;
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

    public String getWorkSummary() {
        return workSummary;
    }

    public void setWorkSummary(String workSummary) {
        this.workSummary = workSummary;
    }

    public String getOriginalityProof() {
        return originalityProof;
    }

    public void setOriginalityProof(String originalityProof) {
        this.originalityProof = originalityProof;
    }

    public String getAuthorIdentityDoc() {
        return authorIdentityDoc;
    }

    public void setAuthorIdentityDoc(String authorIdentityDoc) {
        this.authorIdentityDoc = authorIdentityDoc;
    }

    public String getCreationProcess() {
        return creationProcess;
    }

    public void setCreationProcess(String creationProcess) {
        this.creationProcess = creationProcess;
    }

    public String getOtherProof() {
        return otherProof;
    }

    public void setOtherProof(String otherProof) {
        this.otherProof = otherProof;
    }
}
