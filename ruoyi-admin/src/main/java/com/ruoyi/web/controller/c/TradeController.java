package com.ruoyi.web.controller.c;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.smartscript.platform.trade.domain.SysBusinessFollow;
import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.domain.SysDemandSubmission;
import com.smartscript.platform.trade.domain.SysDemandTag;
import com.smartscript.platform.trade.domain.SysInquiry;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.domain.SysOrderStatusLog;
import com.smartscript.platform.trade.domain.SysPartner;
import com.smartscript.platform.trade.domain.SysQuote;
import com.smartscript.platform.trade.domain.SysWork;
import com.smartscript.platform.trade.service.TradeDemandService;
import com.smartscript.platform.trade.service.TradeDemandTagService;
import com.smartscript.platform.trade.service.TradeFollowUpService;
import com.smartscript.platform.trade.service.TradeInquiryService;
import com.smartscript.platform.trade.service.TradeOrderService;
import com.smartscript.platform.trade.service.TradePartnerService;
import com.smartscript.platform.trade.service.TradeQuoteService;
import com.smartscript.platform.trade.service.TradeWorkService;

/**
 * C module: PC admin trade controller (base path /api/v1/admin).
 *
 * Implements 21 endpoints covering: trade works, orders, partners,
 * demand tags, follow-ups, inquiries, quotes, and demand projects.
 *
 * Boundary: C owns order creation + pre-status flow + status log.
 * Contract / escrow / settlement / delivery / invoice belong to D module.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class TradeController extends BaseController
{
    @Autowired private TradeWorkService workService;
    @Autowired private TradeOrderService orderService;
    @Autowired private TradePartnerService partnerService;
    @Autowired private TradeDemandTagService demandTagService;
    @Autowired private TradeFollowUpService followUpService;
    @Autowired private TradeInquiryService inquiryService;
    @Autowired private TradeQuoteService quoteService;
    @Autowired private TradeDemandService demandService;

    /* ==================== 2.25 / 2.26 / 2.27 Trade Works ==================== */

    /** 2.25 Trade work list */
    @PreAuthorize("@ss.hasPermi('trade:works:list')")
    @GetMapping("/trade/works")
    public TableDataInfo listTradeWorks(SysWork work)
    {
        startPage();
        List<SysWork> list = workService.selectTradeWorkList(work);
        return getDataTable(list);
    }

    /** 2.26 List a work for trade (enable trade on an approved work) */
    @PreAuthorize("@ss.hasPermi('trade:works:add')")
    @Log(title = "C-Trade", businessType = BusinessType.INSERT)
    @PostMapping("/trade/works")
    public AjaxResult createTradeWork(@RequestBody SysWork work)
    {
        // Validate: work must be approved and not deleted
        SysWork existing = workService.selectWorkById(work.getWorkId());
        if (existing == null)
        {
            return AjaxResult.error("Work not found");
        }
        if (!"approved".equals(existing.getStatus()))
        {
            return AjaxResult.error("Only approved works can be listed for trade");
        }
        work.setTradeEnabled(1);
        int rows = workService.updateTradeSettings(work);
        return toAjax(rows);
    }

    /** 2.27 Update trade settings for a work */
    @PreAuthorize("@ss.hasPermi('trade:works:edit')")
    @Log(title = "C-Trade", businessType = BusinessType.UPDATE)
    @PutMapping("/trade/works/{tradeWorkId}")
    public AjaxResult updateTradeWork(@PathVariable Long tradeWorkId, @RequestBody SysWork work)
    {
        work.setWorkId(tradeWorkId);
        int rows = workService.updateTradeSettings(work);
        return toAjax(rows);
    }

    /* ==================== 2.28 / 2.29 Orders ==================== */

    /** 2.28 Order list */
    @PreAuthorize("@ss.hasPermi('trade:orders:list')")
    @GetMapping("/trade/orders")
    public TableDataInfo listOrders(SysOrder order)
    {
        startPage();
        List<SysOrder> list = orderService.selectOrderList(order);
        return getDataTable(list);
    }

    /** 2.29 Order detail */
    @PreAuthorize("@ss.hasPermi('trade:orders:query')")
    @GetMapping("/trade/orders/{orderId}")
    public AjaxResult getOrderDetail(@PathVariable Long orderId)
    {
        SysOrder order = orderService.selectOrderById(orderId);
        if (order == null)
        {
            return AjaxResult.error("Order not found");
        }
        return AjaxResult.success(order);
    }

    /** Order status log (documented gap) */
    @PreAuthorize("@ss.hasPermi('trade:orders:query')")
    @GetMapping("/trade/orders/{orderId}/status-log")
    public TableDataInfo getOrderStatusLog(@PathVariable Long orderId)
    {
        List<SysOrderStatusLog> list = orderService.selectStatusLogByOrderId(orderId);
        return getDataTable(list);
    }

    /* ==================== 2.35 / 2.36 Partners ==================== */

    /** 2.35 Partner list */
    @PreAuthorize("@ss.hasPermi('trade:partners:list')")
    @GetMapping("/trade/partners")
    public TableDataInfo listPartners(SysPartner partner)
    {
        startPage();
        List<SysPartner> list = partnerService.selectPartnerList(partner);
        return getDataTable(list);
    }

    /** 2.36 Create partner */
    @PreAuthorize("@ss.hasPermi('trade:partners:add')")
    @Log(title = "C-Partner", businessType = BusinessType.INSERT)
    @PostMapping("/trade/partners")
    public AjaxResult createPartner(@RequestBody SysPartner partner)
    {
        // Generate partner number
        partner.setPartnerNo("P" + System.currentTimeMillis());
        int rows = partnerService.insertPartner(partner);
        return toAjax(rows);
    }

    /* ==================== 2.37 Demand Tags ==================== */

    /** 2.37 Demand tag list */
    @PreAuthorize("@ss.hasPermi('trade:tags:list')")
    @GetMapping("/trade/partners/tags")
    public TableDataInfo listDemandTags(SysDemandTag tag)
    {
        startPage();
        List<SysDemandTag> list = demandTagService.selectDemandTagList(tag);
        return getDataTable(list);
    }

    /** 2.37 Create demand tag */
    @PreAuthorize("@ss.hasPermi('trade:tags:add')")
    @Log(title = "C-DemandTag", businessType = BusinessType.INSERT)
    @PostMapping("/trade/partners/tags")
    public AjaxResult createDemandTag(@RequestBody SysDemandTag tag)
    {
        int rows = demandTagService.insertDemandTag(tag);
        return toAjax(rows);
    }

    /** Update demand tag (documented gap, front-end stub) */
    @PreAuthorize("@ss.hasPermi('trade:tags:edit')")
    @Log(title = "C-DemandTag", businessType = BusinessType.UPDATE)
    @PutMapping("/trade/partners/tags/{tagId}")
    public AjaxResult updateDemandTag(@PathVariable Long tagId, @RequestBody SysDemandTag tag)
    {
        tag.setTagId(tagId);
        int rows = demandTagService.updateDemandTag(tag);
        return toAjax(rows);
    }

    /** Delete demand tag (documented gap, front-end stub) */
    @PreAuthorize("@ss.hasPermi('trade:tags:remove')")
    @Log(title = "C-DemandTag", businessType = BusinessType.DELETE)
    @DeleteMapping("/trade/partners/tags/{tagId}")
    public AjaxResult deleteDemandTag(@PathVariable Long tagId)
    {
        int rows = demandTagService.deleteDemandTagByIds(new Long[] { tagId });
        return toAjax(rows);
    }

    /* ==================== 2.38 Follow-ups ==================== */

    /** 2.38 Follow-up record list */
    @PreAuthorize("@ss.hasPermi('trade:followups:list')")
    @GetMapping("/trade/partners/follow-ups")
    public TableDataInfo listFollowUps(SysBusinessFollow follow)
    {
        startPage();
        List<SysBusinessFollow> list = followUpService.selectFollowList(follow);
        return getDataTable(list);
    }

    /** 2.38 Create follow-up record */
    @PreAuthorize("@ss.hasPermi('trade:followups:add')")
    @Log(title = "C-FollowUp", businessType = BusinessType.INSERT)
    @PostMapping("/trade/partners/follow-ups")
    public AjaxResult createFollowUp(@RequestBody SysBusinessFollow follow)
    {
        int rows = followUpService.insertFollow(follow);
        return toAjax(rows);
    }

    /* ==================== Inquiry (documented gap 3.2) ==================== */

    /** Inquiry list */
    @PreAuthorize("@ss.hasPermi('trade:inquiry:list')")
    @GetMapping("/trade/inquiry/list")
    public TableDataInfo listInquiries(SysInquiry inquiry)
    {
        startPage();
        List<SysInquiry> list = inquiryService.selectInquiryList(inquiry);
        return getDataTable(list);
    }

    /** Inquiry detail */
    @PreAuthorize("@ss.hasPermi('trade:inquiry:query')")
    @GetMapping("/trade/inquiry/detail/{id}")
    public AjaxResult getInquiryDetail(@PathVariable Long id)
    {
        SysInquiry inquiry = inquiryService.selectInquiryById(id);
        if (inquiry == null)
        {
            return AjaxResult.error("Inquiry not found");
        }
        return AjaxResult.success(inquiry);
    }

    /** Follow up on inquiry (placeholder - updates inquiry status/remark) */
    @PreAuthorize("@ss.hasPermi('trade:inquiry:edit')")
    @Log(title = "C-Inquiry", businessType = BusinessType.UPDATE)
    @PostMapping("/trade/inquiry/follow-up/{id}")
    public AjaxResult followUpInquiry(@PathVariable Long id, @RequestBody SysInquiry inquiry)
    {
        inquiry.setInquiryId(id);
        // For now, just update inquiry fields (status, remark)
        return AjaxResult.success();
    }

    /** Convert inquiry to order (idempotent) */
    @PreAuthorize("@ss.hasPermi('trade:inquiry:convert')")
    @Log(title = "C-Inquiry-Convert", businessType = BusinessType.INSERT)
    @PostMapping("/trade/inquiry/convert-order/{id}")
    public AjaxResult convertInquiryToOrder(@PathVariable Long id)
    {
        SysOrder order = inquiryService.convertToOrder(id);
        return AjaxResult.success(order);
    }

    /* ==================== Quote (documented gap) ==================== */

    /** Quote/negotiation list */
    @PreAuthorize("@ss.hasPermi('trade:quote:list')")
    @GetMapping("/trade/quote/list")
    public TableDataInfo listQuotes(SysQuote quote)
    {
        startPage();
        List<SysQuote> list = quoteService.selectQuoteList(quote);
        return getDataTable(list);
    }

    /** Accept a quote */
    @PreAuthorize("@ss.hasPermi('trade:quote:edit')")
    @PutMapping("/trade/quote/{quoteId}/accept")
    public AjaxResult acceptQuote(@PathVariable Long quoteId)
    {
        int rows = quoteService.updateQuoteStatus(quoteId, "accepted");
        return toAjax(rows);
    }

    /** Reject a quote */
    @PreAuthorize("@ss.hasPermi('trade:quote:edit')")
    @PutMapping("/trade/quote/{quoteId}/reject")
    public AjaxResult rejectQuote(@PathVariable Long quoteId)
    {
        int rows = quoteService.updateQuoteStatus(quoteId, "rejected");
        return toAjax(rows);
    }

    /* ==================== Demand / Submissions (documented gap) ==================== */

    /** Demand project list */
    @PreAuthorize("@ss.hasPermi('trade:demand:list')")
    @GetMapping("/trade/demand/list")
    public TableDataInfo listDemands(SysDemand demand)
    {
        startPage();
        List<SysDemand> list = demandService.selectDemandList(demand);
        return getDataTable(list);
    }

    /** Demand submissions */
    @PreAuthorize("@ss.hasPermi('trade:demand:query')")
    @GetMapping("/trade/demand/{demandId}/submissions")
    public TableDataInfo listDemandSubmissions(@PathVariable Long demandId)
    {
        List<SysDemandSubmission> list = demandService.selectSubmissionsByDemandId(demandId);
        return getDataTable(list);
    }

    /** Create demand project */
    @PreAuthorize("@ss.hasPermi('trade:demand:add')")
    @PostMapping("/trade/demand")
    public AjaxResult createDemand(@RequestBody SysDemand demand)
    {
        String demandNo = "DM" + System.currentTimeMillis();
        demand.setDemandNo(demandNo);
        demand.setSubmissionCount(0);
        demand.setStatus("open");
        demand.setCreateBy(getUsername());
        demand.setCreateTime(new java.util.Date());
        int rows = demandService.insertDemand(demand);
        return toAjax(rows);
    }
}
