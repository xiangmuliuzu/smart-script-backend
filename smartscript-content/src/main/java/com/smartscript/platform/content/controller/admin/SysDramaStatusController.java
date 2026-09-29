package com.smartscript.platform.content.controller.admin;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.smartscript.platform.content.domain.SysExternalDrama;
import com.smartscript.platform.content.service.ISysDramaStatusService;

/**
 * 外部视频状态管理（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama 表（status/sync_status 字段）+ PC 功能清单
 * （外部视频状态页：列表、改状态、批量同步）。
 * 路由前缀 /api/v1/admin/content/dramaStatus（附件6.1 统一版本前缀 /api/v1 + 后台 /admin）。
 * 鉴权：若依 RBAC，@PreAuthorize + Bearer Token。
 * 返回：列表 TableDataInfo，其余 AjaxResult。
 * 反推处理点：批量同步将所选记录 sync_status 置为 syncing；改状态单一职责只改 status。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/dramaStatus")
public class SysDramaStatusController extends BaseController
{
    @Autowired
    private ISysDramaStatusService dramaStatusService;

    /**
     * 获取外部视频状态列表（含 relatedWorkTitle/channelName）
     * 入参（query，均可选）：channelId 精确、title 模糊、status/syncStatus 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:dramastatus:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysExternalDrama query)
    {
        startPage();
        List<SysExternalDrama> list = dramaStatusService.selectStatusList(query);
        return getDataTable(list);
    }

    /**
     * 修改外部视频状态（单一职责：只改 status）
     * 入参：dramaId + status
     */
    @PreAuthorize("@ss.hasPermi('content:dramastatus:edit')")
    @Log(title = "外部视频状态", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysExternalDrama drama)
    {
        drama.setUpdateBy(getUsername());
        return toAjax(dramaStatusService.changeStatus(drama));
    }

    /**
     * 批量同步（将所选记录 sync_status 置为 syncing）
     * 入参：dramaIds 数组（@RequestBody Long[]）
     */
    @PreAuthorize("@ss.hasPermi('content:dramastatus:edit')")
    @Log(title = "外部视频状态", businessType = BusinessType.UPDATE)
    @PutMapping("/sync")
    public AjaxResult sync(@RequestBody Long[] dramaIds)
    {
        return toAjax(dramaStatusService.syncDramas(dramaIds, getUsername()));
    }
}
