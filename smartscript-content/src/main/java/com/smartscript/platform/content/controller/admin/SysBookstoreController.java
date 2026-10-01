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
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.service.ISysBookstoreService;

/**
 * 书城作品管理（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_work 表 + PC 功能清单（书城作品管理页：
 * 列表、详情、上下架状态、交易开关、扩展JSON 调整；清单未列删除，故不提供）。
 * 路由前缀 /api/v1/admin/content/bookstore（附件6.1 统一版本前缀 /api/v1 + 后台 /admin）。
 * 鉴权：若依 RBAC，@PreAuthorize + Bearer Token。
 * 返回：列表 TableDataInfo，其余 AjaxResult。
 * 反推处理点：03 PC 接口文档未列 B 模块书城接口，按云端表结构 + PC 功能清单反推；
 * changeStatus/changeTrade/updateExt 均为局部更新，不做 @Validated 全量校验。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/bookstore")
public class SysBookstoreController extends BaseController
{
    @Autowired
    private ISysBookstoreService bookstoreService;

    /**
     * 获取书城作品列表
     * 入参（query，均可选）：title 模糊、recommendStatus 走 JSON_EXTRACT、status/tradeEnabled 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:bookstore:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysWork query)
    {
        startPage();
        List<SysWork> list = bookstoreService.selectBookstoreList(query);
        return getDataTable(list);
    }

    /**
     * 通过作品ID获取书城作品详情（含从 ext_json 解析的 recommendStatus/showScope）
     */
    @PreAuthorize("@ss.hasPermi('content:bookstore:query')")
    @GetMapping("/{workId}")
    public AjaxResult getInfo(@PathVariable Long workId)
    {
        return success(bookstoreService.selectBookstoreById(workId));
    }

    /**
     * 修改作品上下架状态（单一职责：只改 status）
     */
    @PreAuthorize("@ss.hasPermi('content:bookstore:edit')")
    @Log(title = "书城作品", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysWork work)
    {
        work.setUpdateBy(getUsername());
        return toAjax(bookstoreService.updateWorkStatus(work));
    }

    /**
     * 修改作品交易开关（单一职责：只改 trade_enabled）
     */
    @PreAuthorize("@ss.hasPermi('content:bookstore:edit')")
    @Log(title = "书城作品", businessType = BusinessType.UPDATE)
    @PutMapping("/changeTrade")
    public AjaxResult changeTrade(@RequestBody SysWork work)
    {
        work.setUpdateBy(getUsername());
        return toAjax(bookstoreService.updateWorkTradeEnabled(work));
    }

    /**
     * 修改作品扩展JSON（单一职责：整体替换 ext_json）
     */
    @PreAuthorize("@ss.hasPermi('content:bookstore:edit')")
    @Log(title = "书城作品", businessType = BusinessType.UPDATE)
    @PutMapping("/updateExt")
    public AjaxResult updateExt(@RequestBody SysWork work)
    {
        work.setUpdateBy(getUsername());
        return toAjax(bookstoreService.updateWorkExtJson(work));
    }
}
