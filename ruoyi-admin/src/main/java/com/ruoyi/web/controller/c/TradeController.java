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
import com.smartscript.platform.trade.domain.SysOfflineCooperation;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.domain.SysOrderStatusLog;
import com.smartscript.platform.trade.domain.SysPartner;
import com.smartscript.platform.trade.domain.SysQuote;
import com.smartscript.platform.trade.domain.SysWork;
import com.smartscript.platform.trade.service.TradeCooperationService;
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
    @Autowired private TradeCooperationService cooperationService;

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

    /** Update partner (documented gap; front-end edit reuses this instead of create) */
    @PreAuthorize("@ss.hasPermi('trade:partners:edit')")
    @Log(title = "C-Partner", businessType = BusinessType.UPDATE)
    @PutMapping("/trade/partners/{partnerId}")
    public AjaxResult updatePartner(@PathVariable Long partnerId, @RequestBody SysPartner partner)
    {
        partner.setPartnerId(partnerId);
        int rows = partnerService.updatePartner(partner);
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

    /* ==================== Cooperation records (分工 15 线上合作意向 / 16 线下谈判) ==================== */
    // 数据源 sys_offline_cooperation（C 主导表），source 区分 online 线上合作意向 / offline 线下谈判。
    // PC 商务跟进页以只读列表/详情消费；create/update 供 APP 侧（APP-TRADE-04）复用同一 Service。
    // 权限沿用商务跟进页的 trade:followups:*（同一页面内展示，无独立菜单）。

    /** 合作记录列表（source=online 线上合作记录 / source=offline 线下谈判记录） */
    @PreAuthorize("@ss.hasPermi('trade:followups:list')")
    @GetMapping("/trade/cooperation/list")
    public TableDataInfo listCooperations(SysOfflineCooperation cooperation)
    {
        startPage();
        List<SysOfflineCooperation> list = cooperationService.selectCooperationList(cooperation);
        return getDataTable(list);
    }

    /** 合作记录详情 */
    @PreAuthorize("@ss.hasPermi('trade:followups:list')")
    @GetMapping("/trade/cooperation/{id}")
    public AjaxResult getCooperationDetail(@PathVariable Long id)
    {
        SysOfflineCooperation cooperation = cooperationService.selectCooperationById(id);
        if (cooperation == null)
        {
            return AjaxResult.error("Cooperation record not found");
        }
        return AjaxResult.success(cooperation);
    }

    /** 新增合作记录（分工 16 线下谈判录入；APP 复用，PC 商务跟进页只读不发起） */
    @PreAuthorize("@ss.hasPermi('trade:followups:add')")
    @Log(title = "C-Cooperation", businessType = BusinessType.INSERT)
    @PostMapping("/trade/cooperation")
    public AjaxResult createCooperation(@RequestBody SysOfflineCooperation cooperation)
    {
        return AjaxResult.success(cooperationService.insertCooperation(cooperation));
    }

    /** 更新合作记录 */
    @PreAuthorize("@ss.hasPermi('trade:followups:add')")
    @Log(title = "C-Cooperation", businessType = BusinessType.UPDATE)
    @PutMapping("/trade/cooperation/{id}")
    public AjaxResult updateCooperation(@PathVariable Long id, @RequestBody SysOfflineCooperation cooperation)
    {
        cooperation.setCooperationId(id);
        return toAjax(cooperationService.updateCooperation(cooperation));
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

    /** Follow up on inquiry: records a follow entry and appends to inquiry remark */
    @PreAuthorize("@ss.hasPermi('trade:inquiry:edit')")
    @Log(title = "C-Inquiry", businessType = BusinessType.UPDATE)
    @PostMapping("/trade/inquiry/follow-up/{id}")
    public AjaxResult followUpInquiry(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body)
    {
        Object content = body.get("content");
        if (content == null)
        {
            content = body.get("remark");
        }
        int rows = inquiryService.followUp(id, content != null ? content.toString() : "");
        return toAjax(rows);
    }

    /**
     * Convert inquiry to order (idempotent).
     *
     * @deprecated 2026-09-29 用户决策：取消「转为订单」功能，订单一律在接受报价时生成。
     * 本端点仅为兼容接口文档保留（Service 已收紧为仅 deal 可调），PC 页面入口已下线。
     */
    @Deprecated
    @PreAuthorize("@ss.hasPermi('trade:inquiry:convert')")
    @Log(title = "C-Inquiry-Convert", businessType = BusinessType.INSERT)
    @PostMapping("/trade/inquiry/convert-order/{id}")
    public AjaxResult convertInquiryToOrder(@PathVariable Long id)
    {
        SysOrder order = inquiryService.convertToOrder(id);
        return AjaxResult.success(order);
    }

    /** Create inquiry (documented gap; 分工条目 9, PRD APP-TRADE-02) */
    @PreAuthorize("@ss.hasPermi('trade:inquiry:add')")
    @Log(title = "C-Inquiry", businessType = BusinessType.INSERT)
    @PostMapping("/trade/inquiry")
    public AjaxResult createInquiry(@RequestBody SysInquiry inquiry)
    {
        SysInquiry created = inquiryService.createInquiry(inquiry);
        return AjaxResult.success(created);
    }

    /** Accept inquiry (分工条目 12): pending -> accepted */
    @PreAuthorize("@ss.hasPermi('trade:inquiry:edit')")
    @Log(title = "C-Inquiry", businessType = BusinessType.UPDATE)
    @PutMapping("/trade/inquiry/{id}/accept")
    public AjaxResult acceptInquiry(@PathVariable Long id)
    {
        return toAjax(inquiryService.acceptInquiry(id));
    }

    /** Reject inquiry (分工条目 12): pending -> rejected */
    @PreAuthorize("@ss.hasPermi('trade:inquiry:edit')")
    @Log(title = "C-Inquiry", businessType = BusinessType.UPDATE)
    @PutMapping("/trade/inquiry/{id}/reject")
    public AjaxResult rejectInquiry(@PathVariable Long id)
    {
        return toAjax(inquiryService.rejectInquiry(id));
    }

    /** Close inquiry (分工条目 12): -> closed */
    @PreAuthorize("@ss.hasPermi('trade:inquiry:edit')")
    @Log(title = "C-Inquiry", businessType = BusinessType.UPDATE)
    @PutMapping("/trade/inquiry/{id}/close")
    public AjaxResult closeInquiry(@PathVariable Long id)
    {
        return toAjax(inquiryService.closeInquiry(id));
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

    /** Create quote (分工条目 17, PRD APP-TRADE-03): seller-role quote */
    @PreAuthorize("@ss.hasPermi('trade:quote:add')")
    @Log(title = "C-Quote", businessType = BusinessType.INSERT)
    @PostMapping("/trade/quote")
    public AjaxResult createQuote(@RequestBody SysQuote quote)
    {
        return AjaxResult.success(quoteService.createQuote(quote));
    }

    /** Buyer counter-offer (分工条目 19): inserts a buyer-role quote, preserves history */
    @PreAuthorize("@ss.hasPermi('trade:quote:add')")
    @Log(title = "C-Quote-Counter", businessType = BusinessType.INSERT)
    @PostMapping("/trade/quote/counter-offer")
    public AjaxResult counterOffer(@RequestBody SysQuote quote)
    {
        return AjaxResult.success(quoteService.counterOffer(quote));
    }

    /** Modify quote (分工条目 18): only pending quotes */
    @PreAuthorize("@ss.hasPermi('trade:quote:edit')")
    @Log(title = "C-Quote", businessType = BusinessType.UPDATE)
    @PutMapping("/trade/quote/{quoteId}")
    public AjaxResult modifyQuote(@PathVariable Long quoteId, @RequestBody SysQuote quote)
    {
        // 本端为甲方 PC（买方视角）：仅可修改买方议价，卖方报价由卖方在客户端修改
        return toAjax(quoteService.modifyQuote(quoteId, quote, TradeQuoteService.OPERATOR_ROLE_BUYER));
    }

    /** Accept a quote: validates state/expiry, generates order (idempotent), sets quote accepted */
    @PreAuthorize("@ss.hasPermi('trade:quote:edit')")
    @Log(title = "C-Quote", businessType = BusinessType.UPDATE)
    @PutMapping("/trade/quote/{quoteId}/accept")
    public AjaxResult acceptQuote(@PathVariable Long quoteId)
    {
        SysOrder order = quoteService.acceptQuote(quoteId);
        return AjaxResult.success(order);
    }

    /** Reject a quote: validates state, sets quote rejected */
    @PreAuthorize("@ss.hasPermi('trade:quote:edit')")
    @Log(title = "C-Quote", businessType = BusinessType.UPDATE)
    @PutMapping("/trade/quote/{quoteId}/reject")
    public AjaxResult rejectQuote(@PathVariable Long quoteId)
    {
        int rows = quoteService.rejectQuote(quoteId);
        return toAjax(rows);
    }

    /**
     * 卖方接受买方议价（2026-09-29 用户决策：任意一方接受即直接生成订单）。
     * 供客户端（卖方端）调用：仅可接受 buyer + pending_seller 的议价，接受后生成订单、询盘置 deal。
     * 甲方 PC 页面暂不露出本端点。
     */
    @PreAuthorize("@ss.hasPermi('trade:quote:edit')")
    @Log(title = "C-Quote-SellerConfirm", businessType = BusinessType.UPDATE)
    @PutMapping("/trade/quote/{quoteId}/seller-accept")
    public AjaxResult sellerAcceptQuote(@PathVariable Long quoteId)
    {
        SysOrder order = quoteService.acceptQuote(quoteId, TradeQuoteService.OPERATOR_ROLE_SELLER);
        return AjaxResult.success(order);
    }

    /** 卖方拒绝买方议价（供客户端调用）：仅置 rejected，不生成订单，询盘可继续议价 */
    @PreAuthorize("@ss.hasPermi('trade:quote:edit')")
    @Log(title = "C-Quote-SellerConfirm", businessType = BusinessType.UPDATE)
    @PutMapping("/trade/quote/{quoteId}/seller-reject")
    public AjaxResult sellerRejectQuote(@PathVariable Long quoteId)
    {
        int rows = quoteService.rejectQuote(quoteId, TradeQuoteService.OPERATOR_ROLE_SELLER);
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
