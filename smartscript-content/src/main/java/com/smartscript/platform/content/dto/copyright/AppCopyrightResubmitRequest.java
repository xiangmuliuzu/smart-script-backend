package com.smartscript.platform.content.dto.copyright;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * App 端版权申请重新提交请求 DTO。
 */
public class AppCopyrightResubmitRequest
{
    /** 原申请ID */
    @NotNull(message = "原申请ID不能为空")
    private Long originalApplyId;

    /** 作品类型：literary/music/art/video/software/other */
    @NotBlank(message = "作品类型不能为空")
    private String workType;

    /** 作品完成时间（yyyy-MM-dd） */
    @NotBlank(message = "完成时间不能为空")
    private String completeTime;

    /** 申请材料文件URL */
    @NotBlank(message = "申请材料不能为空")
    private String fileUrl;

    /** 说明（必填，说明修改内容） */
    @NotBlank(message = "说明不能为空")
    private String remark;

    // Getters and Setters

    public Long getOriginalApplyId() {
        return originalApplyId;
    }

    public void setOriginalApplyId(Long originalApplyId) {
        this.originalApplyId = originalApplyId;
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

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
