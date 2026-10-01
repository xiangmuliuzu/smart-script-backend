package com.smartscript.platform.content.controller.admin;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.smartscript.platform.content.service.ISysDramaBindService;

/**
 * 外部视频绑定管理（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama 表（related_work_id 字段）+ PC 功能清单
 * （外部视频绑定页：列表含 relatedWorkTitle、绑定前的 drama 信息回显、绑定/解绑作品）。
 * 路由前缀 /api/v1/admin/content/dramaBind（附件6.1 统一版本前缀 /api/v1 + 后台 /admin）。
 * 鉴权：若依 RBAC，@PreAuthorize + Bearer Token。
 * 返回：列表 TableDataInfo，其余 AjaxResult。
 * 反推处理点：绑定/解绑各为独立接口；解绑时 service 内构造 drama 置 relatedWorkId=null，
 * XML SET related_work_id = #{relatedWorkId} 会将其置为 NULL。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/dramaBind")
public class SysDramaBindController extends BaseController
{
    @Autowired
    private ISysDramaBindService dramaBindService;

    /**
     * 获取外部视频绑定列表（含 relatedWorkTitle；可设 unboundOnly="1" 仅查未绑定记录）
     * 入参（query，均可选）：channelId 精确、title 模糊、unboundOnly="1" 仅未绑定；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:dramabind:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysExternalDrama query)
    {
        startPage();
        List<SysExternalDrama> list = dramaBindService.selectBindList(query);
        return getDataTable(list);
    }

    /**
     * 通过短剧ID获取详情（用于绑定前的 drama 信息回显）
     */
    @PreAuthorize("@ss.hasPermi('content:dramabind:query')")
    @GetMapping("/{dramaId}")
    public AjaxResult getInfo(@PathVariable Long dramaId)
    {
        return success(dramaBindService.selectDramaById(dramaId));
    }

    /**
     * 绑定作品（单一职责：只改 related_work_id）
     * 入参：dramaId + relatedWorkId
     */
    @PreAuthorize("@ss.hasPermi('content:dramabind:edit')")
    @Log(title = "外部视频绑定", businessType = BusinessType.UPDATE)
    @PutMapping("/bind")
    public AjaxResult bind(@RequestBody SysExternalDrama drama)
    {
        drama.setUpdateBy(getUsername());
        return toAjax(dramaBindService.bindWork(drama));
    }

    /**
     * 解绑作品（将 related_work_id 置为 NULL）
     * 入参：dramaId（updateBy 由 controller 设置）
     */
    @PreAuthorize("@ss.hasPermi('content:dramabind:edit')")
    @Log(title = "外部视频绑定", businessType = BusinessType.UPDATE)
    @PutMapping("/unbind")
    public AjaxResult unbind(@RequestBody SysExternalDrama drama)
    {
        drama.setUpdateBy(getUsername());
        return toAjax(dramaBindService.unbindWork(drama));
    }
}
