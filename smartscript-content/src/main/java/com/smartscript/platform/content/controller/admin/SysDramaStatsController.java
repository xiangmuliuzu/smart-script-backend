package com.smartscript.platform.content.controller.admin;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.smartscript.platform.content.domain.SysExternalDrama;
import com.smartscript.platform.content.domain.SysPlayHistory;
import com.smartscript.platform.content.service.ISysDramaStatsService;

/**
 * 外部视频统计（PC 后台，只读）
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama + sys_episode + sys_subscribe +
 * sys_work + sys_play_history 表 + PC 功能清单（外部视频统计页：聚合列表、详情含剧集、播放历史）。
 * 路由前缀 /api/v1/admin/content/dramaStats（附件6.1 统一版本前缀 /api/v1 + 后台 /admin）。
 * 鉴权：若依 RBAC，@PreAuthorize + Bearer Token。
 * 返回：列表 TableDataInfo，其余 AjaxResult。
 * 反推处理点：stats 跨表聚合（按 related_work_id 聚合 sys_episode/sys_subscribe/sys_work）；
 * 详情接口返回 stats + episodes 剧集列表（由 service 拼装）；history 走 sys_play_history。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/dramaStats")
public class SysDramaStatsController extends BaseController
{
    @Autowired
    private ISysDramaStatsService dramaStatsService;

    /**
     * 获取统计聚合列表（按 related_work_id 聚合：总播放/订阅/剧集数 + 作品标题）
     * 入参（query，均可选）：channelId 精确、relatedWorkId 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:dramastats:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysExternalDrama query)
    {
        startPage();
        List<SysExternalDrama> list = dramaStatsService.selectStatsList(query);
        return getDataTable(list);
    }

    /**
     * 通过作品ID获取统计聚合详情（含 episodes 剧集列表，由 service 拼装）
     */
    @PreAuthorize("@ss.hasPermi('content:dramastats:query')")
    @GetMapping("/{workId}")
    public AjaxResult getInfo(@PathVariable Long workId)
    {
        return success(dramaStatsService.selectStatsById(workId));
    }

    /**
     * 获取播放历史列表（含 episodeTitle，LEFT JOIN sys_episode）
     * 入参（query，均可选）：workId 精确、userId 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:dramastats:list')")
    @GetMapping("/history")
    public TableDataInfo history(SysPlayHistory query)
    {
        startPage();
        List<SysPlayHistory> list = dramaStatsService.selectHistoryList(query);
        return getDataTable(list);
    }
}
