package com.smartscript.platform.content.dto;

import java.util.List;

/**
 * 作品章节目录（App 侧只读下发对象，接口文档 2.7.8 免费试读）。
 *
 * 依据：云端 script_platform_dev 库 sys_work_chapter 表（附件5.1 表3-16）+
 *       sys_copyright_authorization（授权放开全文）。
 *
 * 访问范围 accessScope 取值：
 *   - preview：未获授权，仅能阅读试读范围内的章节（试读开关未开时实际无可读章节）；
 *   - full   ：已获授权，可阅读全部正常章节。
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

    /** 章节总数 */
    private Integer total;

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

    public List<AppChapterDto> getList()
    {
        return list;
    }

    public void setList(List<AppChapterDto> list)
    {
        this.list = list;
    }
}