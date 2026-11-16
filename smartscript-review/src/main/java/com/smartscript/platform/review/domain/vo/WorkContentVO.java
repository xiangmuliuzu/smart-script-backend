package com.smartscript.platform.review.domain.vo;

/** 审核用：作品基础内容（简介） */
public class WorkContentVO {
    private Long workId;
    private String title;
    private String workType;
    private String summary;

    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getWorkType() { return workType; }
    public void setWorkType(String workType) { this.workType = workType; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
