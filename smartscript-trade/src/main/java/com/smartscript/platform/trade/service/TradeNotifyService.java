package com.smartscript.platform.trade.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.domain.SysDemandStage;
import com.smartscript.platform.trade.domain.SysInquiry;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.user.constant.AppAdminConstants;
import com.smartscript.platform.user.service.UserNotificationAdminService;

/**
 * C 交易域：全生命周期站内通知统一出口。
 * <p>
 * 覆盖询盘、报价、合同、订单付款/交割/完成等事件，向对应收件人投递
 * {@link AppAdminConstants#NOTIFICATION_TRANSACTION} 类型站内消息（进 APP「消息」中心）。
 * <p>
 * 设计约束：
 * <ul>
 *   <li><b>通知失败绝不影响业务</b>：每个事件用 REQUIRES_NEW 独立事务投递并吞掉所有异常，
 *       既不会因通知抛错回滚外层业务事务，也不会把业务事务标记为 rollback-only。</li>
 *   <li><b>幂等</b>：每个事件用确定性 requestId（{@code TRD-<事件>-<业务主键>}），
 *       同一事件重复触发只落一条通知，避免并发/重试刷屏。</li>
 *   <li><b>集中收口</b>：所有交易通知文案在此拼装，调用方只传业务主键与收件人，
 *       规避「多写端点各自调通知、漏接导致同类操作时有时缺」的老问题。</li>
 *   <li><b>受众标注</b>：每条通知携带 audience（BUYER/SELLER/BOTH），PC 企业端(买方门户)
 *       与创作者端(卖方门户)按受众过滤收件箱，APP 端不过滤；同一账号身兼买卖两侧时
 *       各门户只看到自己角色视角的通知。</li>
 *   <li><b>多行详情</b>：正文按「事件叙述 → 逐行关键信息（作品/单号/金额/对方/原因）
 *       → 行动指引」拼装；列表 summary 由服务层压平截断，详情页/PC/APP 展示原始换行。</li>
 * </ul>
 */
@Service
public class TradeNotifyService
{
    private static final Logger log = LoggerFactory.getLogger(TradeNotifyService.class);

    private final UserNotificationAdminService notificationService;
    private final TransactionTemplate newTx;

    public TradeNotifyService(UserNotificationAdminService notificationService,
            PlatformTransactionManager transactionManager)
    {
        this.notificationService = notificationService;
        // REQUIRES_NEW：独立于调用方业务事务，通知提交与业务提交解耦
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    /** 发起询盘 → 通知卖方及时处理（带作品、买家、预算、意向用途）。 */
    public void notifyInquiryCreated(SysInquiry inquiry)
    {
        Long inquiryId = inquiry.getInquiryId();
        StringBuilder content = new StringBuilder("您收到一条新的合作询盘，请及时处理。\n");
        line(content, "作品", titleOf(inquiry.getWorkTitle()));
        line(content, "询盘编号", inquiry.getInquiryNo());
        line(content, "买家", nameOf(inquiry.getBuyerName()));
        line(content, "授权类型", licenseLabel(inquiry.getLicenseType()));
        line(content, "预算金额", amountText(inquiry.getBudget()));
        line(content, "意向用途", inquiry.getIntendedUse());
        content.append("可前往「询盘」处理：接受后即可进入会话洽谈。");
        push("TRD-INQ-CRT-" + inquiryId, "新的合作询盘", content.toString(),
                "INQUIRY", str(inquiryId), one(inquiry.getSellerId()), "SELLER");
    }

    /** 卖方接受询盘 → 通知买方进入会话洽谈。 */
    public void notifyInquiryAccepted(Long inquiryId, String inquiryNo, String workTitle,
            String sellerName, Long buyerId)
    {
        StringBuilder content = new StringBuilder("好消息：卖方已接受您的询盘，现在可以进入交易会话洽谈报价。\n");
        line(content, "作品", titleOf(workTitle));
        line(content, "询盘编号", inquiryNo);
        line(content, "卖方", nameOf(sellerName));
        content.append("请在会话中与对方沟通需求并接受报价，成交后自动生成订单。");
        push("TRD-INQ-ACC-" + inquiryId, "询盘已被接受", content.toString(),
                "INQUIRY", str(inquiryId), one(buyerId), "BUYER");
    }

    /** 一方接受报价 → 通知另一方订单已生成：有版权进合同签署，无版权直达付款。 */
    public void notifyQuoteAccepted(Long orderId, String orderNo, String workTitle, BigDecimal amount,
            String acceptorName, Long counterpartUserId, boolean copyrighted)
    {
        StringBuilder content = new StringBuilder("对方已接受报价，交易达成一致，授权订单已生成。\n");
        line(content, "作品", titleOf(workTitle));
        line(content, "订单编号", orderNo);
        line(content, "成交金额", amountText(amount));
        line(content, "接受方", nameOf(acceptorName));
        content.append(copyrighted
                ? "下一步请前往「合同」完成双方签署，签署完成后进入付款流程。"
                : "该作品未确权，本次交易免签合同，请前往「订单」直接完成付款。");
        push("TRD-QTE-ACC-" + orderId, "报价已接受，订单已生成", content.toString(),
                "ORDER", str(orderId), one(counterpartUserId), "BOTH");
    }

    /** 一方签署合同 → 通知另一方及时完成签署。 */
    public void notifyContractSignedByParty(Long contractId, String orderNo, String workTitle,
            BigDecimal amount, String licenseType, String signerName, Long counterpartUserId)
    {
        StringBuilder content = new StringBuilder("对方已完成合同签署，等待您的签署。\n");
        line(content, "作品", titleOf(workTitle));
        line(content, "订单编号", orderNo);
        line(content, "授权类型", licenseLabel(licenseType));
        line(content, "合同金额", amountText(amount));
        line(content, "签署方", nameOf(signerName));
        content.append("请尽快前往「合同」完成签署，双方签署完成后订单将进入付款环节。");
        push("TRD-CTC-SGN-" + contractId, "对方已签署合同", content.toString(),
                "CONTRACT", str(contractId), one(counterpartUserId), "BOTH");
    }

    /** 双方签署完成 → 通知买方付款至托管。 */
    public void notifyContractCompleted(Long orderId, String orderNo, String workTitle,
            BigDecimal amount, String sellerName, Long buyerId)
    {
        StringBuilder content = new StringBuilder("合同已双方签署完成，订单进入付款环节。\n");
        line(content, "作品", titleOf(workTitle));
        line(content, "订单编号", orderNo);
        line(content, "应付金额", amountText(amount));
        line(content, "交易对方", nameOf(sellerName));
        content.append("款项将托管至平台，卖方交割后确认收货即可释放；请尽快前往「订单」完成付款。");
        push("TRD-CTC-CMP-" + orderId, "合同签署完成，待付款", content.toString(),
                "ORDER", str(orderId), one(buyerId), "BUYER");
    }

    /** 买方付款 → 通知卖方订单进入交割。 */
    public void notifyOrderPaid(Long orderId, String orderNo, String workTitle, BigDecimal amount,
            String buyerName, Long sellerId)
    {
        StringBuilder content = new StringBuilder("买方已付款，货款已托管至平台，订单进入交割环节。\n");
        line(content, "作品", titleOf(workTitle));
        line(content, "订单编号", orderNo);
        line(content, "托管金额", amountText(amount));
        line(content, "买方", nameOf(buyerName));
        content.append("请尽快前往「订单」交付作品，买方确认收货后货款将释放至您的收益账户。");
        push("TRD-ORD-PAY-" + orderId, "买方已付款，请交割", content.toString(),
                "ORDER", str(orderId), one(sellerId), "SELLER");
    }

    /** 买方确认收货、订单完成 → 通知买卖双方订单已完成（卖方附加收益结算指引）。 */
    public void notifyOrderCompleted(Long orderId, String orderNo, String workTitle, BigDecimal amount,
            Long buyerId, Long sellerId)
    {
        String buyerContent = "买方已确认收货，交易完成，感谢本次交易。\n"
                + detailLine("作品", titleOf(workTitle))
                + detailLine("订单编号", orderNo)
                + detailLine("成交金额", amountText(amount))
                + "订单详情可在「订单」查看。";
        push("TRD-ORD-CMP-B-" + orderId, "订单已完成", buyerContent,
                "ORDER", str(orderId), one(buyerId), "BUYER");

        String sellerContent = "买方已确认收货，订单交易完成。\n"
                + detailLine("作品", titleOf(workTitle))
                + detailLine("订单编号", orderNo)
                + detailLine("成交金额", amountText(amount))
                + "货款已释放，可前往「收益」页面确认收益并申请结算。";
        push("TRD-ORD-CMP-S-" + orderId, "订单已完成，收益待结算", sellerContent,
                "ORDER", str(orderId), one(sellerId), "SELLER");
    }

    /** 合同签署前任一方取消订单 → 通知对方（带取消原因）。 */
    public void notifyOrderCancelled(Long orderId, String orderNo, String workTitle,
            String cancelActorName, String reason, Long counterpartUserId)
    {
        StringBuilder content = new StringBuilder("对方已在合同签署完成前取消订单，本次交易终止。\n");
        line(content, "作品", titleOf(workTitle));
        line(content, "订单编号", orderNo);
        line(content, "取消方", nameOf(cancelActorName));
        line(content, "取消原因", reason);
        content.append("如有疑问，可与对方沟通了解详情。");
        push("TRD-ORD-CXL-" + orderId, "订单已取消", content.toString(),
                "ORDER", str(orderId), one(counterpartUserId), "BOTH");
    }

    // ===== 内部工具 =====

    /**
     * 统一投递：过滤空收件人、REQUIRES_NEW 独立事务、吞异常。audience 标注受众门户。
     * 同一事件两个收件人时按受众拆分为两条（requestId 加收件人后缀），
     * 使 audience 与收件人角色保持一致。
     */
    private void push(String requestId, String title, String content,
            String businessType, String businessId, List<Long> userIds, String audience)
    {
        List<Long> receivers = normalize(userIds);
        if (receivers.isEmpty())
        {
            return;
        }
        // 多收件人且带角色受众：逐个投递，受众取该收件人在本事件中的角色（当前仅订单完成：买方/卖方）
        if (receivers.size() > 1 && !"BOTH".equals(audience))
        {
            throw new IllegalArgumentException("多收件人通知须为 BOTH 或改用分角色投递");
        }
        try
        {
            newTx.executeWithoutResult(status -> notificationService.create(
                    requestId, AppAdminConstants.NOTIFICATION_TRANSACTION, title, content,
                    businessType, businessId, receivers, "system", audience));
        }
        catch (Exception e)
        {
            // 通知失败仅告警，绝不冒泡影响业务事务
            log.warn("交易通知投递失败 requestId={}: {}", requestId, e.getMessage());
        }
    }

    /** 去 null、去重、保序。 */
    private List<Long> normalize(List<Long> userIds)
    {
        List<Long> result = new ArrayList<>();
        if (userIds == null)
        {
            return result;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : userIds)
        {
            if (id != null)
            {
                distinct.add(id);
            }
        }
        result.addAll(distinct);
        return result;
    }

    private List<Long> one(Long userId)
    {
        List<Long> list = new ArrayList<>(1);
        list.add(userId);
        return list;
    }

    /** 金额展示：保留原始精度，去多余尾零（null 返回空，调用侧自动省略该行）。 */
    private String amountText(BigDecimal amount)
    {
        return amount == null ? null : amount.stripTrailingZeros().toPlainString() + " 元";
    }

    /** 授权类型中文标签，与前端展示口径一致。 */
    private String licenseLabel(String licenseType)
    {
        if (licenseType == null || licenseType.isBlank())
        {
            return null;
        }
        switch (licenseType)
        {
            case "exclusive":
                return "独家授权";
            case "non_exclusive":
                return "非独家授权";
            case "adaptation":
                return "改编授权";
            default:
                return licenseType;
        }
    }

    private String titleOf(String title)
    {
        return (title == null || title.isBlank()) ? null : "《" + title + "》";
    }

    private String nameOf(String name)
    {
        return (name == null || name.isBlank()) ? null : "「" + name + "」";
    }

    /** 追加一行关键信息（值为空则整行省略，避免通知里出现空条目）。 */
    private void line(StringBuilder sb, String label, String value)
    {
        if (value == null || value.isBlank())
        {
            return;
        }
        sb.append(detailLine(label, value));
    }

    private String detailLine(String label, String value)
    {
        return "· " + label + "：" + value + "\n";
    }

    private String str(Object v)
    {
        return v == null ? null : String.valueOf(v);
    }

    /** 轮次展示文案“第X轮”。 */
    private String roundText(Integer stageOrder)
    {
        return stageOrder == null ? null : "第" + stageOrder + "轮";
    }

    /** 时长展示“X天Y小时”，为 0 的分量省略，均为 0 返回 null（整行省略）。 */
    private String durationText(Integer days, Integer hours)
    {
        int d = days == null ? 0 : days;
        int h = hours == null ? 0 : hours;
        if (d > 0 && h > 0)
        {
            return d + "天" + h + "小时";
        }
        if (d > 0)
        {
            return d + "天";
        }
        return h > 0 ? h + "小时" : null;
    }

    /** 截止时间展示“MM月dd日 HH:mm”，与 APP 记录行同口径。 */
    private String dueText(Date date)
    {
        return date == null ? null : new SimpleDateFormat("MM月dd日 HH:mm").format(date);
    }

    /** 任务广场：有新投稿时通知发布者。 */
    public void notifyDemandSubmitted(Long demandId, String demandTitle, Long clientId)
    {
        StringBuilder content = new StringBuilder("您发布的征集任务收到新的投稿，请查看。\n");
        line(content, "任务", titleOf(demandTitle));
        content.append("可前往「任务广场」查看投稿并进行评审。");
        push("TRD-DM-SUB-" + demandId + "-" + System.currentTimeMillis(),
                "征集任务新投稿", content.toString(),
                "DEMAND", str(demandId), one(clientId), "BOTH");
    }

    /** 任务广场：中标通知。 */
    public void notifySubmissionAccepted(Long submissionId, Long submitterId, String demandTitle)
    {
        StringBuilder content = new StringBuilder("恭喜！您的投稿已被选中。\n");
        line(content, "任务", titleOf(demandTitle));
        content.append("系统已自动创建询盘，请前往「我的合作-询盘」查看并进入报价流程。");
        push("TRD-DM-ACC-" + submissionId,
                "投稿中标", content.toString(),
                "DEMAND", str(submissionId), one(submitterId), "SELLER");
    }

    /** 任务广场：创作者抢单成功，通知发布方。 */
    public void notifyDemandClaimed(Long demandId, String demandTitle, Long clientId, String creatorName)
    {
        StringBuilder content = new StringBuilder("您发布的任务已被接单，创作者将按时交付。\n");
        line(content, "任务", titleOf(demandTitle));
        if (creatorName != null && !creatorName.isEmpty())
        {
            line(content, "接单人", creatorName);
        }
        content.append("请前往「任务广场」关注交付进度并逐轮验收。");
        push("TRD-DM-CLAIM-" + demandId + "-" + System.currentTimeMillis(),
                "任务已被接单", content.toString(),
                "DEMAND", str(demandId), one(clientId), "BOTH");
    }

    /** 创作者申请本轮延时 → 通知发布者处理。 */
    public void notifyExtendRequested(Long applyId, Long demandId, String demandTitle, Integer stageOrder,
            Integer days, Integer hours, String reason, Long buyerId)
    {
        StringBuilder content = new StringBuilder("创作者申请延长本轮交付时限，请及时处理。\n");
        line(content, "任务", titleOf(demandTitle));
        line(content, "轮次", roundText(stageOrder));
        line(content, "申请延时", durationText(days, hours));
        line(content, "申请原因", reason);
        content.append("请前往「任务详情-验收进度」处理：同意则本轮截止按申请时长顺延，拒绝则截止时间不变。");
        push("TRD-DM-EXT-REQ-" + applyId, "创作者申请延时", content.toString(),
                "DEMAND", str(demandId), one(buyerId), "BUYER");
    }

    /** 发布者处理延时申请 → 通知接单创作者处理结果。 */
    public void notifyExtendDecided(Long applyId, Long demandId, String demandTitle, Integer stageOrder,
            boolean approved, Integer days, Integer hours, Date newDue, Long creatorId)
    {
        StringBuilder content = new StringBuilder(approved
                ? "您申请的本轮延时已被发布者同意。\n"
                : "您申请的本轮延时已被发布者拒绝。\n");
        line(content, "任务", titleOf(demandTitle));
        line(content, "轮次", roundText(stageOrder));
        line(content, "申请延时", durationText(days, hours));
        line(content, "新的截止", approved ? dueText(newDue) : null);
        content.append(approved
                ? "本轮截止时间已顺延，请按新时限完成交付。"
                : "本轮截止时间不变，请在截止前完成交付，否则任务将超时终止。");
        push("TRD-DM-EXT-DEC-" + applyId, approved ? "延时申请已同意" : "延时申请已拒绝",
                content.toString(), "DEMAND", str(demandId), one(creatorId), "SELLER");
    }

    /** 发布者超时未处理延时申请 → 系统自动驳回，通知接单创作者。 */
    public void notifyExtendAutoRejected(Long applyId, Long demandId, String demandTitle, Integer stageOrder,
            Integer days, Integer hours, Long creatorId)
    {
        StringBuilder content = new StringBuilder("您申请的本轮延时因发布者长时间未处理，已由系统自动驳回。\n");
        line(content, "任务", titleOf(demandTitle));
        line(content, "轮次", roundText(stageOrder));
        line(content, "申请延时", durationText(days, hours));
        content.append("本轮截止时间保持不变，请在截止前完成交付，否则任务将超时终止。");
        push("TRD-DM-EXT-AUTO-" + applyId, "延时申请已自动驳回",
                content.toString(), "DEMAND", str(demandId), one(creatorId), "SELLER");
    }

    /** 发布者自主延时（不经申请） → 通知接单创作者新时限。 */
    public void notifyExtendSelfDone(Long applyId, Long demandId, String demandTitle, Integer stageOrder,
            Integer days, Integer hours, Date newDue, Long creatorId)
    {
        StringBuilder content = new StringBuilder("发布者已直接延长本轮交付时限。\n");
        line(content, "任务", titleOf(demandTitle));
        line(content, "轮次", roundText(stageOrder));
        line(content, "延长时长", durationText(days, hours));
        line(content, "新的截止", dueText(newDue));
        content.append("请在新时限内完成本轮交付。");
        push("TRD-DM-EXT-SELF-" + applyId, "发布者已延长本轮时限", content.toString(),
                "DEMAND", str(demandId), one(creatorId), "SELLER");
    }

    /**
     * 本轮验收通过、本轮款项已放出 → 通知接单创作者去「收益-待结算」确认入账。
     * requestId 以轮次为键，保证同一轮重复触发只会有一条。
     */
    public void notifyStageSettleCreated(Long demandId, String demandTitle, Integer stageOrder,
            BigDecimal amount, BigDecimal sellerAmount, Long creatorId)
    {
        StringBuilder content = new StringBuilder("您本轮的交付已通过验收，本轮款项已放出。\n");
        line(content, "任务", titleOf(demandTitle));
        line(content, "轮次", roundText(stageOrder));
        line(content, "本轮金额", amountText(amount));
        line(content, "作者实得", amountText(sellerAmount));
        content.append("可前往「收益 - 待结算」确认入账，确认后将计入可提现余额。");
        push("TRD-DM-FUND-" + demandId + "-" + stageOrder, "本轮验收通过，款项已放出",
                content.toString(), "DEMAND", str(demandId), one(creatorId), "SELLER");
    }

    /** 本轮超时未通过 → 任务失败终止，按角色分别通知发布者与创作者（含托管退回与押金没收入账金额）。 */
    public void notifyDemandRoundFailed(Long demandId, String demandTitle, Integer stageOrder,
            String reason, Long buyerId, Long creatorId, BigDecimal escrowAmount, BigDecimal depositAmount)
    {
        String buyerContent = "任务因本轮交付超时未通过而失败终止。\n"
                + detailLine("任务", titleOf(demandTitle))
                + detailLine("超时轮次", roundText(stageOrder))
                + detailLine("终止原因", reason)
                + detailLine("托管退回", amountText(escrowAmount))
                + detailLine("押金入账", amountText(depositAmount))
                + "托管款项已退回给您；创作者本次任务押金已按规则没收并入账到您的账户。";
        push("TRD-DM-FAIL-" + demandId + "-" + stageOrder + "-B", "任务超时终止",
                buyerContent, "DEMAND", str(demandId), one(buyerId), "BUYER");

        String creatorContent = "任务因本轮交付超时未通过而失败终止。\n"
                + detailLine("任务", titleOf(demandTitle))
                + detailLine("超时轮次", roundText(stageOrder))
                + detailLine("终止原因", reason)
                + detailLine("押金没收", amountText(depositAmount))
                + "本次任务押金已按规则没收并入账至发布方，如有疑问可与发布方沟通。";
        push("TRD-DM-FAIL-" + demandId + "-" + stageOrder + "-S", "任务超时终止",
                creatorContent, "DEMAND", str(demandId), one(creatorId), "SELLER");
    }

    /**
     * AI 审核通过转人工验收 → 通知发布者处理本轮交付。
     * 列表侧展示事件叙述，详情侧提供需求编号/交付作品/本轮金额/本轮截止/接单人/交付说明 六个关键行。
     * requestId 以 (轮次, 交付提交时刻) 为键：同一轮多次重交付每次都会通知；
     * 同一轮 AI 恢复扫描重复触发因 submittedAt 不变而幂等收敛为一条。
     */
    public void notifyStageReadyForReview(SysDemand demand, SysDemandStage stage)
    {
        StringBuilder content = new StringBuilder("创作者已提交本轮交付并通过 AI 前置审核，请及时人工验收。\n");
        line(content, "任务", titleOf(demand.getTitle()));
        line(content, "需求编号", demand.getDemandNo());
        line(content, "轮次", roundText(stage.getStageOrder()));
        line(content, "交付作品", titleOf(stage.getWorkTitle()));
        line(content, "本轮金额", amountText(stage.getAmount()));
        line(content, "本轮截止", dueText(stage.getDueAt()));
        line(content, "接单人", nameOf(demand.getAcceptedCreatorName()));
        line(content, "交付说明", stage.getDeliverableDesc());
        content.append("可前往「任务详情-验收进度」处理：通过则放出本轮款项，驳回则本轮恢复计时等待重交。");
        long submittedAt = stage.getSubmittedAt() != null
                ? stage.getSubmittedAt().getTime() : System.currentTimeMillis();
        push("TRD-DM-REVIEW-" + demand.getDemandId() + "-" + stage.getStageOrder() + "-" + submittedAt,
                "本轮交付待验收", content.toString(),
                "DEMAND", str(demand.getDemandId()), one(demand.getClientId()), "BUYER");
    }

    /**
     * AI 审核未通过自动打回 → 通知创作者修改后重交。
     * 详情展示需求编号/交付作品/本轮金额/AI 评分/打回原因/本轮截止/交付说明。
     * AI 评分与打回原因作为写入参数显式传递，避免依赖调用侧重新读取时是否已刷新。
     * requestId 键位同 {@link #notifyStageReadyForReview}。
     */
    public void notifyStageRejectedByAi(SysDemand demand, SysDemandStage stage, Integer score, String rejectReason)
    {
        StringBuilder content = new StringBuilder("本轮交付未通过 AI 前置审核，请修改后重新提交。\n");
        line(content, "任务", titleOf(demand.getTitle()));
        line(content, "需求编号", demand.getDemandNo());
        line(content, "轮次", roundText(stage.getStageOrder()));
        line(content, "交付作品", titleOf(stage.getWorkTitle()));
        line(content, "本轮金额", amountText(stage.getAmount()));
        if (score != null)
        {
            line(content, "AI 评分", String.valueOf(score));
        }
        line(content, "打回原因", rejectReason);
        line(content, "本轮截止", dueText(stage.getDueAt()));
        line(content, "交付说明", stage.getDeliverableDesc());
        content.append("请在截止时间前完成重新交付，否则本轮任务将超时终止。");
        long submittedAt = stage.getSubmittedAt() != null
                ? stage.getSubmittedAt().getTime() : System.currentTimeMillis();
        push("TRD-DM-REJ-AI-" + demand.getDemandId() + "-" + stage.getStageOrder() + "-" + submittedAt,
                "本轮 AI 审核未通过", content.toString(),
                "DEMAND", str(demand.getDemandId()), one(demand.getAcceptedCreatorId()), "SELLER");
    }

    /**
     * 发布者人工驳回本轮 → 通知创作者重交。
     * 详情展示需求编号/交付作品/本轮金额/驳回原因/新的截止/发布者/交付说明。
     * reviewedAtMillis 仅作为幂等时间戳，上游 updateReview CAS 已保证同次审核仅一次进入。
     */
    public void notifyStageRejectedByBuyer(SysDemand demand, SysDemandStage stage, String rejectReason,
            Date newDue, long reviewedAtMillis)
    {
        StringBuilder content = new StringBuilder("本轮交付已被发布者驳回，请修改后重新提交。\n");
        line(content, "任务", titleOf(demand.getTitle()));
        line(content, "需求编号", demand.getDemandNo());
        line(content, "轮次", roundText(stage.getStageOrder()));
        line(content, "交付作品", titleOf(stage.getWorkTitle()));
        line(content, "本轮金额", amountText(stage.getAmount()));
        line(content, "驳回原因", rejectReason);
        line(content, "新的截止", dueText(newDue));
        line(content, "发布者", nameOf(demand.getClientName()));
        line(content, "交付说明", stage.getDeliverableDesc());
        content.append("请在新截止前完成重新交付，否则本轮任务将超时终止。");
        push("TRD-DM-REJ-BYR-" + demand.getDemandId() + "-" + stage.getStageOrder() + "-" + reviewedAtMillis,
                "本轮验收未通过", content.toString(),
                "DEMAND", str(demand.getDemandId()), one(demand.getAcceptedCreatorId()), "SELLER");
    }

    /**
     * 全部轮次通过 → 任务完成，按角色分别通知发布者与创作者。
     * 发布者看到预算与“无待处理项”，创作者额外看到押金金额与收益入口。
     * requestId 以 demandId 单键幂等：上游状态机 `!COMPLETED` 保证整单完成仅触发一次。
     */
    public void notifyDemandCompleted(SysDemand demand)
    {
        String buyerContent = "任务已全部轮次验收通过，交易完成。\n"
                + detailLine("任务", titleOf(demand.getTitle()))
                + detailLine("需求编号", demand.getDemandNo())
                + detailLine("预算金额", amountText(demand.getBudget()))
                + detailLine("接单人", nameOf(demand.getAcceptedCreatorName()))
                + "托管款项已按轮次放出，本单无待处理项。";
        push("TRD-DM-DONE-" + demand.getDemandId() + "-B", "任务已完成",
                buyerContent, "DEMAND", str(demand.getDemandId()), one(demand.getClientId()), "BUYER");

        String creatorContent = "任务已全部轮次验收通过，交易完成。\n"
                + detailLine("任务", titleOf(demand.getTitle()))
                + detailLine("需求编号", demand.getDemandNo())
                + detailLine("预算总额", amountText(demand.getBudget()))
                + detailLine("押金退还", amountText(demand.getDepositAmount()))
                + detailLine("发布者", nameOf(demand.getClientName()))
                + "押金已随任务完成退还，收益请前往「收益-待结算」逐轮确认入账。";
        push("TRD-DM-DONE-" + demand.getDemandId() + "-S", "任务已完成",
                creatorContent, "DEMAND", str(demand.getDemandId()), one(demand.getAcceptedCreatorId()), "SELLER");
    }

    /**
     * 本轮验收通过且存在下一轮 → 通知创作者新一轮已激活，展示截止时间。
     * requestId 以 (轮次, 激活时刻) 为键，同一轮仅触发一次。
     */
    public void notifyNextRoundActivated(SysDemand demand, SysDemandStage nextStage, Date nextDue)
    {
        StringBuilder content = new StringBuilder("上一轮已通过验收，新一轮交付任务已开启，请开始创作。\n");
        line(content, "任务", titleOf(demand.getTitle()));
        line(content, "需求编号", demand.getDemandNo());
        line(content, "新轮次", roundText(nextStage.getStageOrder()));
        line(content, "本轮金额", amountText(nextStage.getAmount()));
        line(content, "本轮截止", dueText(nextDue));
        content.append("请前往「任务详情-交付」查看本轮要求并开始创作，截止前完成交付即可通过 AI 审核进入验收。");
        push("TRD-DM-NEXT-" + demand.getDemandId() + "-" + nextStage.getStageOrder(),
                "新一轮任务已开启", content.toString(),
                "DEMAND", str(demand.getDemandId()), one(demand.getAcceptedCreatorId()), "SELLER");
    }
}

