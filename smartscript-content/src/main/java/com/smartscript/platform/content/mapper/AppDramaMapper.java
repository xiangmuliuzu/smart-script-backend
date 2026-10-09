package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.dto.AppDramaFeedItem;
import com.smartscript.platform.content.dto.AppExternalDramaDetailDto;

/**
 * 外部视频浏览 数据层（App 2.8.1 短剧信息流 / 2.8.15 外部视频详情 / 2.8.16 找同款剧本）
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama + sys_drama_channel 表 +
 * 接口文档表 2-94 / 2-108 / 2-109。
 *
 * 反推处理点：
 * 1. 信息流只回 status='on_shelf' 的记录（与 sys_work 上下架可见性口径一致）；
 *    sys_external_drama.status 为 varchar(20)。
 * 2. has_related_work 在 SQL 内以 (related_work_id is not null) 计算，口径与 2.8.16 一致。
 * 3. 复用既有 PC 侧 SysExternalDramaMapper 不冲突：本 Mapper 只做 App 侧浏览投影，
 *    不写库、不含审计字段。
 * 4. 分页由调用方 PageHelper 驱动（不在 SQL 内写 limit）。
 *
 * @author xiangsipeng
 */
public interface AppDramaMapper
{
    /**
     * 短剧信息流（仅已上架；分页由调用方 PageHelper 驱动）
     *
     * @return 信息流列表（按 drama_id 倒序）
     */
    public List<AppDramaFeedItem> selectDramaFeed();

    /**
     * 外部视频详情（仅已上架；未上架/不存在返回 null，由控制层按 404 处理）
     *
     * @param dramaId 外部视频ID
     * @return 详情；不存在或未上架为 null
     */
    public AppExternalDramaDetailDto selectDramaDetailById(@Param("dramaId") Long dramaId);
}