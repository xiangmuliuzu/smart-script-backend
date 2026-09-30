package com.smartscript.platform.content.dto;

import java.util.List;

/**
 * 免费试读（App 侧只读下发对象，接口文档 2.7.8）。
 *
 * 依据：sys_work.preview_enabled / preview_episodes（附件5.1 表3-14）+
 *       sys_work_chapter（表3-16）+ sys_work_file.is_preview（表3-15）。
 *
 * 组成：试读开关与集数 + 可读章节子集（目录中 readable=true 的部分）+
 *       标记为可预览的作品文件。开关关闭时后两者均为空集合。
 *
 * @author xiangsipeng
 */
public class AppPreviewDto
{
    /** 作品ID */
    private Long workId;

    /** 是否开启试读（0=否 1=是，库中存储列原样下发） */
    private String previewEnabled;

    /** 试读集数（未配置时为 0） */
    private Integer previewEpisodes;

    /** 可试读章节（目录中 readable=true 的子集，按 chapter_no 升序） */
    private List<AppChapterDto> previewChapters;

    /** 可预览作品文件（is_preview=1，按 sort 升序） */
    private List<AppWorkFileDto> previewFiles;

    public AppPreviewDto()
    {
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public String getPreviewEnabled()
    {
        return previewEnabled;
    }

    public void setPreviewEnabled(String previewEnabled)
    {
        this.previewEnabled = previewEnabled;
    }

    public Integer getPreviewEpisodes()
    {
        return previewEpisodes;
    }

    public void setPreviewEpisodes(Integer previewEpisodes)
    {
        this.previewEpisodes = previewEpisodes;
    }

    public List<AppChapterDto> getPreviewChapters()
    {
        return previewChapters;
    }

    public void setPreviewChapters(List<AppChapterDto> previewChapters)
    {
        this.previewChapters = previewChapters;
    }

    public List<AppWorkFileDto> getPreviewFiles()
    {
        return previewFiles;
    }

    public void setPreviewFiles(List<AppWorkFileDto> previewFiles)
    {
        this.previewFiles = previewFiles;
    }
}