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
import com.smartscript.platform.content.domain.SysDramaChannel;
import com.smartscript.platform.content.service.ISysDramaChannelService;

/**
 * 外部视频渠道管理（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_drama_channel 表 + PC 功能清单
 * （渠道管理页：列表、详情、新增、编辑、启用/停用）。
 * 路由前缀 /api/v1/admin/content/dramaChannel（附件6.1 统一版本前缀 /api/v1 + 后台 /admin；
 * 反推处理点：dramaChannel 原路径 /api/v1/admin/dramaChannel 统一加 /content 前缀归入内容模块）。
 * 鉴权：若依 RBAC，@PreAuthorize + Bearer Token。
 * 返回：列表 TableDataInfo，其余 AjaxResult。
 * 反推处理点：03 PC 接口文档未列 B 模块渠道接口，按云端表结构 + PC 功能清单反推。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/dramaChannel")
public class SysDramaChannelController extends BaseController
{
    @Autowired
    private ISysDramaChannelService channelService;

    /**
     * 获取外部视频渠道列表
     * 入参（query，均可选）：channelName 模糊、platform 精确、status 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:channel:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysDramaChannel query)
    {
        startPage();
        List<SysDramaChannel> list = channelService.selectChannelList(query);
        return getDataTable(list);
    }

    /**
     * 通过渠道ID获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('content:channel:query')")
    @GetMapping("/{channelId}")
    public AjaxResult getInfo(@PathVariable Long channelId)
    {
        return success(channelService.selectChannelById(channelId));
    }

    /**
     * 新增外部视频渠道
     */
    @PreAuthorize("@ss.hasPermi('content:channel:add')")
    @Log(title = "外部视频渠道", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysDramaChannel channel)
    {
        channel.setCreateBy(getUsername());
        return toAjax(channelService.insertChannel(channel));
    }

    /**
     * 修改外部视频渠道
     */
    @PreAuthorize("@ss.hasPermi('content:channel:edit')")
    @Log(title = "外部视频渠道", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysDramaChannel channel)
    {
        channel.setUpdateBy(getUsername());
        return toAjax(channelService.updateChannel(channel));
    }

    /**
     * 启用/停用外部视频渠道（单一职责：只改 status）
     */
    @PreAuthorize("@ss.hasPermi('content:channel:edit')")
    @Log(title = "外部视频渠道", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysDramaChannel channel)
    {
        channel.setUpdateBy(getUsername());
        return toAjax(channelService.updateChannelStatus(channel));
    }
}
