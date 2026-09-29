package com.smartscript.platform.content.controller.admin;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.smartscript.platform.content.service.ISysExternalDramaService;

/**
 * 外部视频内容管理（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama + sys_episode 表 + PC 功能清单
 * （外部视频管理页：列表、详情含剧集、新增、编辑；不包含绑定/状态/统计，各自独立控制器）。
 * 路由前缀 /api/v1/admin/content/drama（附件6.1 统一版本前缀 /api/v1 + 后台 /admin）。
 * 鉴权：若依 RBAC，@PreAuthorize + Bearer Token。
 * 返回：列表 TableDataInfo，其余 AjaxResult。
 * 反推处理点：详情接口返回 drama + episodes 剧集列表（由 service 拼装）。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/drama")
public class SysExternalDramaController extends BaseController
{
    @Autowired
    private ISysExternalDramaService dramaService;

    /**
     * 获取外部视频列表
     * 入参（query，均可选）：channelId 精确、title 模糊、sourceType/authorizationStatus/status/syncStatus 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:drama:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysExternalDrama query)
    {
        startPage();
        List<SysExternalDrama> list = dramaService.selectDramaList(query);
        return getDataTable(list);
    }

    /**
     * 通过短剧ID获取详细信息（含 episodes 剧集列表，由 service 拼装）
     */
    @PreAuthorize("@ss.hasPermi('content:drama:query')")
    @GetMapping("/{dramaId}")
    public AjaxResult getInfo(@PathVariable Long dramaId)
    {
        return success(dramaService.selectDramaById(dramaId));
    }

    /**
     * 新增外部视频
     */
    @PreAuthorize("@ss.hasPermi('content:drama:add')")
    @Log(title = "外部视频", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysExternalDrama drama)
    {
        drama.setCreateBy(getUsername());
        return toAjax(dramaService.insertDrama(drama));
    }

    /**
     * 修改外部视频
     */
    @PreAuthorize("@ss.hasPermi('content:drama:edit')")
    @Log(title = "外部视频", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysExternalDrama drama)
    {
        drama.setUpdateBy(getUsername());
        return toAjax(dramaService.updateDrama(drama));
    }
}
