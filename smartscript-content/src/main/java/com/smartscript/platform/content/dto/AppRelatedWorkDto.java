package com.smartscript.platform.content.dto;

/**
 * 找同款剧本（B 模块 2.8.16 表 2-109）。
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama 表 + sys_work 表 + 接口文档表 2-109。
 *
 * 反推处理点：
 * 1. 契约输出 drama_id / has_related_work / work(Object)；work 复用 2.7.2 的
 *    {@link AppWorkDto}，确保跳转后的作品信息与书城详情同一口径。
 * 2. hasRelatedWork 口径为 related_work_id 非空（与 2.8.1 / 2.8.15 一致）。
 * 3. work 仅在该关联作品「未删除且已上架」时非空（复用 2.7.2 可见性口径）；
 *    related_work_id 存在但作品不可见时，work 为 null，由客户端提示「原著暂不可见」。
 *
 * @author xiangsipeng
 */
public class AppRelatedWorkDto
{
    /** 外部视频ID */
    private Long dramaId;

    /** 是否有关联原著（related_work_id 非空） */
    private Boolean hasRelatedWork;

    /** 关联剧本信息（不可见时为 null，见类注释第 3 点） */
    private AppWorkDto work;

    public Long getDramaId()
    {
        return dramaId;
    }

    public void setDramaId(Long dramaId)
    {
        this.dramaId = dramaId;
    }

    public Boolean getHasRelatedWork()
    {
        return hasRelatedWork;
    }

    public void setHasRelatedWork(Boolean hasRelatedWork)
    {
        this.hasRelatedWork = hasRelatedWork;
    }

    public AppWorkDto getWork()
    {
        return work;
    }

    public void setWork(AppWorkDto work)
    {
        this.work = work;
    }
}