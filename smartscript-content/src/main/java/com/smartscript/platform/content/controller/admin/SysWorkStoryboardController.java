package com.smartscript.platform.content.controller.admin;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.smartscript.platform.content.domain.SysWorkStoryboard;
import com.smartscript.platform.content.service.ISysWorkStoryboardAdminService;

/** 管理端分镜目录、分镜内容和审核接口。 */
@RestController
@RequestMapping("/api/v1/admin/content/storyboard")
public class SysWorkStoryboardController extends BaseController
{
    @Autowired
    private ISysWorkStoryboardAdminService storyboardService;

    /**
     * 分镜管理页和审核工作台共用查询接口：
     * - 分镜管理页使用 content:work:list；
     * - 审核工作台使用 smartscript:copyright:review。
     */
    @PreAuthorize("@ss.hasAnyPermi('content:work:list,smartscript:copyright:review')")
    @GetMapping("/list")
    public TableDataInfo list(SysWorkStoryboard query)
    {
        startPage();
        List<SysWorkStoryboard> list = storyboardService.selectList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasAnyPermi('content:work:query,smartscript:copyright:review')")
    @GetMapping("/{storyboardId}")
    public AjaxResult getInfo(@PathVariable Long storyboardId)
    {
        SysWorkStoryboard storyboard = storyboardService.selectById(storyboardId);
        return storyboard == null ? error("分镜不存在或已删除") : success(storyboard);
    }

    /** 只有审核工作台或作品管理编辑权限可以提交审核结果。 */
    @PreAuthorize("@ss.hasAnyPermi('content:work:edit,smartscript:copyright:review')")
    @Log(title = "分镜审核", businessType = BusinessType.UPDATE)
    @PutMapping("/{storyboardId}/review")
    public AjaxResult review(@PathVariable Long storyboardId,
            @RequestBody Map<String, Object> body)
    {
        String status = text(body, "status");
        if (status.isEmpty())
        {
            String decision = text(body, "decision");
            status = "approve".equalsIgnoreCase(decision) ? "approved"
                    : "reject".equalsIgnoreCase(decision) ? "rejected" : decision;
        }
        String rejectReason = text(body, "rejectReason");
        if (rejectReason.isEmpty())
        {
            rejectReason = text(body, "remark");
        }
        try
        {
            int affected = storyboardService.review(storyboardId, status, rejectReason,
                    getUserId(), getUsername());
            return affected > 0 ? success("分镜审核已提交") : error("分镜状态已变化，请刷新后重试");
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('content:work:edit')")
    @Log(title = "分镜管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{storyboardId}")
    public AjaxResult remove(@PathVariable Long storyboardId)
    {
        return toAjax(storyboardService.delete(storyboardId, getUsername()));
    }

    private String text(Map<String, Object> body, String key)
    {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : value.toString().trim();
    }
}
