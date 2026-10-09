package com.smartscript.platform.content.service;

import com.smartscript.platform.content.dto.AppDramaFeedItem;
import com.smartscript.platform.content.dto.AppExternalDramaDetailDto;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppRelatedWorkDto;

/**
 * 外部视频浏览 服务（App 2.8.1 短剧信息流 / 2.8.15 外部视频详情 / 2.8.16 找同款剧本）
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama + sys_drama_channel 表 +
 * 接口文档表 2-94 / 2-108 / 2-109。
 *
 * 边界：三个接口均为公开只读（游客可读），不涉及归属与写操作；
 * 可见性只回 status='on_shelf' 的记录，未上架/不存在按 404 处理（与书城作品口径一致）。
 *
 * @author xiangsipeng
 */
public interface IAppDramaService
{
    /**
     * 短剧信息流（接口 2.8.1，分页）
     *
     * @param pageNum  页码（从 1 起）
     * @param pageSize 每页条数
     * @return 分页结果（total + list）
     */
    public AppPageResult<AppDramaFeedItem> pageFeed(int pageNum, int pageSize);

    /**
     * 外部视频详情（接口 2.8.15）
     *
     * @param dramaId 外部视频ID
     * @return 详情；不存在或未上架为 null，由控制层按 404 处理
     */
    public AppExternalDramaDetailDto getDramaDetail(Long dramaId);

    /**
     * 找同款剧本（接口 2.8.16）
     *
     * @param dramaId 外部视频ID
     * @return {dramaId, hasRelatedWork, work}；外部视频不存在或未上架为 null，由控制层按 404 处理
     */
    public AppRelatedWorkDto getRelatedWork(Long dramaId);
}