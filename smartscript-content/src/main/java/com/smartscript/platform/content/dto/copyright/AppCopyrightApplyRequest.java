package com.smartscript.platform.content.dto.copyright;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * App 端版权申请提交请求 DTO。
 */
public class AppCopyrightApplyRequest
{
    /** 作品ID */
    @NotNull(message = "作品ID不能为空")
    private Long workId;

    /** 作品类型：literary/music/art/video/software/other */
    @NotBlank(message = "作品类型不能为空")
    private String workType;

    /** 作品完成时间（yyyy-MM-dd） */
    @NotBlank(message = "完成时间不能为空")
    private String completeTime;

    /** 版权中心代码 */
    @NotBlank(message = "版权中心不能为空")
    private String copyrightCenter;

    /** 申请材料文件URL（兼容旧版本） */
    private String fileUrl;

    /** 原创性证明材料URL（JSON数组字符串） */
    private String originalityProofUrls;

    /** 作者身份证明材料URL（JSON数组字符串） */
    private String authorIdentityDocUrls;

    /** 创作过程材料URL（JSON数组字符串） */
    private String creationProcessUrls;

    /** 其他辅助证明材料URL（JSON数组字符串） */
    private String otherProofUrls;

    /** 备注 */
    private String remark;

    // Getters and Setters

    public Long getWorkId() {
        return workId;
    }

    public void setWorkId(Long workId) {
        this.workId = workId;
    }

    public String getWorkType() {
        return workType;
    }

    public void setWorkType(String workType) {
        this.workType = workType;
    }

    public String getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(String completeTime) {
        this.completeTime = completeTime;
    }

    public String getCopyrightCenter() {
        return copyrightCenter;
    }

    public void setCopyrightCenter(String copyrightCenter) {
        this.copyrightCenter = copyrightCenter;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
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

    public String getCreationProcessUrls() {
        return creationProcessUrls;
    }

    public void setCreationProcessUrls(String creationProcessUrls) {
        this.creationProcessUrls = creationProcessUrls;
    }

    public String getOtherProofUrls() {
        return otherProofUrls;
    }

    public void setOtherProofUrls(String otherProofUrls) {
        this.otherProofUrls = otherProofUrls;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
