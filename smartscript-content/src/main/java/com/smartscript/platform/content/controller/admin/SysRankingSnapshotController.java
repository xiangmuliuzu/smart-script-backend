package com.smartscript.platform.content.controller.admin;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.smartscript.platform.content.domain.SysRankingSnapshot;
import com.smartscript.platform.content.service.ISysRankingSnapshotService;

/**
 * 排行榜管理（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_ranking_snapshot 表 + PC 功能清单
 * （排行榜管理页：列表、详情、排名调整、重算）。
 * 路由前缀 /api/v1/admin/content/ranking（附件6.1 统一版本前缀 /api/v1 + 后台 /admin）。
 * 鉴权：若依 RBAC，@PreAuthorize + Bearer Token。
 * 返回：列表 TableDataInfo，其余 AjaxResult。
 * 反推处理点：03 PC 接口文档未列 B 模块排行榜接口，按云端表结构 + PC 功能清单反推；
 * changeRankNo 为局部更新，不做 @Validated 全量校验；recompute 走表单参数。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/ranking")
public class SysRankingSnapshotController extends BaseController
{
    @Autowired
    private ISysRankingSnapshotService rankingService;

    /**
     * 获取榜单列表
     * 入参（query，均可选）：rankingType/periodStart/periodEnd/status 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:ranking:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysRankingSnapshot query)
    {
        startPage();
        List<SysRankingSnapshot> list = rankingService.selectRankingList(query);
        return getDataTable(list);
    }

    /**
     * 通过排行榜ID获取详情
     */
    @PreAuthorize("@ss.hasPermi('content:ranking:query')")
    @GetMapping("/{rankingId}")
    public AjaxResult getInfo(@PathVariable Long rankingId)
    {
        return success(rankingService.selectRankingById(rankingId));
    }

    /**
     * 调整排名（单一职责：只改 rank_no）
     */
    @PreAuthorize("@ss.hasPermi('content:ranking:edit')")
    @Log(title = "排行榜", businessType = BusinessType.UPDATE)
    @PutMapping("/changeRankNo")
    public AjaxResult changeRankNo(@RequestBody SysRankingSnapshot snapshot)
    {
        snapshot.setUpdateBy(getUsername());
        return toAjax(rankingService.updateRankNo(snapshot));
    }

    /**
     * 重算榜单快照
     * 入参（form）：rankingType（view/favorite/sale/rating，指标由类型唯一确定）、periodStart、periodEnd（yyyy-MM-dd）
     */
    @PreAuthorize("@ss.hasPermi('content:ranking:edit')")
    @Log(title = "排行榜", businessType = BusinessType.UPDATE)
    @PostMapping("/recompute")
    public AjaxResult recompute(@RequestParam String rankingType,
            @RequestParam String periodStart,
            @RequestParam String periodEnd)
    {
        return AjaxResult.success(rankingService.recomputeRanking(rankingType, periodStart, periodEnd));
    }
}
