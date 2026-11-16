package com.smartscript.platform.review.domain.vo;

/** 审核用：章节内容（正文已转纯文本） */
public class WorkChapterVO {
    private Long chapterId;
    private Integer chapterNo;
    private String chapterTitle;
    private String content;

    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Integer getChapterNo() { return chapterNo; }
    public void setChapterNo(Integer chapterNo) { this.chapterNo = chapterNo; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
