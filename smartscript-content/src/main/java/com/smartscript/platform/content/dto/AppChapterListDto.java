package com.smartscript.platform.content.dto;

import java.util.List;

/**
 * 作品章节目录（App 侧只读下发对象，接口文档 2.7.8 免费试读）。
 *
 * 依据：云端 script_platform_dev 库 sys_work_chapter 表（附件5.1 表3-16）+
 *       sys_copyright_authorization（授权放开全文）。
 *
 * 访问范围 accessScope 取值：
 *   - preview：未获授权，目录仅展示可试读章节（试读范围 + 作者标记的免费章节），
 *              已发布但未开放试读的付费章节不下发；
 *   - full   ：已获授权，展示并可阅读全部已发布章节。
 * unlocked 为 accessScope 的布尔等价形态，便于客户端直接判断是否需要展示「未授权」提示。
 *
 * @author xiangsipeng
 */
public class AppChapterListDto
{
    /** 作品ID */
    private Long workId;

    /** 是否开启试读（0=否 1=是，库中存储列原样下发） */
    private String previewEnabled;

    /** 试读集数（未配置时为 0） */
    private Integer previewEpisodes;

    /** 访问范围：preview / full */
    private String accessScope;

    /** 是否已获授权（等价于 accessScope=='full'） */
    private Boolean unlocked;

    /** 章节总数（当前身份可见的章节数） */
    private Integer total;

    /** 已发布章节总数（含当前身份不可见的付费章节，用于展示「共 N 章」） */
    private Integer publishedTotal;

    /** 章节摘要集合（按 chapter_no 升序，不下发 content 全文） */
    private List<AppChapterDto> list;

    public AppChapterListDto()
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

    public String getAccessScope()
    {
        return accessScope;
    }

    public void setAccessScope(String accessScope)
    {
        this.accessScope = accessScope;
    }

    public Boolean getUnlocked()
    {
        return unlocked;
    }

    public void setUnlocked(Boolean unlocked)
    {
        this.unlocked = unlocked;
    }

    public Integer getTotal()
    {
        return total;
    }

    public void setTotal(Integer total)
    {
        this.total = total;
    }

    public Integer getPublishedTotal()
    {
        return publishedTotal;
    }

    public void setPublishedTotal(Integer publishedTotal)
    {
        this.publishedTotal = publishedTotal;
    }

    public List<AppChapterDto> getList()
    {
        return list;
    }

    public void setList(List<AppChapterDto> list)
    {
        this.list = list;
    }
}