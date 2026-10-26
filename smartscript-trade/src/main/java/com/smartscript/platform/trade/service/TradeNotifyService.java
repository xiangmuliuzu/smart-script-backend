package com.smartscript.platform.trade.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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

    /** 发起询盘 → 通知卖方及时处理。 */
    public void notifyInquiryCreated(Long inquiryId, String inquiryNo, Long sellerId)
    {
        push("TRD-INQ-CRT-" + inquiryId, "新的合作询盘",
                "您收到一条新的合作询盘" + noSuffix(inquiryNo) + "，请及时前往「收到的询盘」处理。",
                "INQUIRY", str(inquiryId), one(sellerId));
    }

    /** 卖方接受询盘 → 通知买方进入会话洽谈。 */
    public void notifyInquiryAccepted(Long inquiryId, String inquiryNo, Long buyerId)
    {
        push("TRD-INQ-ACC-" + inquiryId, "询盘已被接受",
                "卖方已接受您的询盘" + noSuffix(inquiryNo) + "，请进入交易会话开始洽谈报价。",
                "INQUIRY", str(inquiryId), one(buyerId));
    }

    /** 一方接受报价 → 通知另一方订单已生成，进入订单流程。 */
    public void notifyQuoteAccepted(Long orderId, String orderNo, Long counterpartUserId)
    {
        push("TRD-QTE-ACC-" + orderId, "报价已接受，订单已生成",
                "对方已接受报价，授权订单" + noSuffix(orderNo) + "已生成，请前往「我的订单」查看并推进后续流程。",
                "ORDER", str(orderId), one(counterpartUserId));
    }

    /** 一方签署合同 → 通知另一方及时完成签署。 */
    public void notifyContractSignedByParty(Long contractId, String orderNo, Long counterpartUserId)
    {
        push("TRD-CTC-SGN-" + contractId, "对方已签署合同",
                "订单" + noSuffix(orderNo) + "的合同对方已完成签署，请您及时完成签署。",
                "CONTRACT", str(contractId), one(counterpartUserId));
    }

    /** 双方签署完成 → 通知买方付款至托管。 */
    public void notifyContractCompleted(Long orderId, String orderNo, Long buyerId)
    {
        push("TRD-CTC-CMP-" + orderId, "合同签署完成，待付款",
                "订单" + noSuffix(orderNo) + "的合同已双方签署完成，请及时付款至托管账户以推进交割。",
                "ORDER", str(orderId), one(buyerId));
    }

    /** 买方付款到托管 → 通知卖方开始交割。 */
    public void notifyOrderPaid(Long orderId, String orderNo, Long sellerId)
    {
        push("TRD-ORD-PAY-" + orderId, "买方已付款，请交割",
                "订单" + noSuffix(orderNo) + "买方已付款至托管账户，请尽快开始交割、交付作品。",
                "ORDER", str(orderId), one(sellerId));
    }

    /** 买方确认收货、订单完成 → 通知买卖双方订单已完成、资金已释放。 */
    public void notifyOrderCompleted(Long orderId, String orderNo, Long buyerId, Long sellerId)
    {
        push("TRD-ORD-CMP-" + orderId, "订单已完成",
                "订单" + noSuffix(orderNo) + "已完成，托管资金已释放给卖方，感谢本次交易。",
                "ORDER", str(orderId), two(buyerId, sellerId));
    }

    // ===== 内部工具 =====

    /** 统一投递：过滤空收件人、REQUIRES_NEW 独立事务、吞异常。 */
    private void push(String requestId, String title, String content,
            String businessType, String businessId, List<Long> userIds)
    {
        List<Long> receivers = normalize(userIds);
        if (receivers.isEmpty())
        {
            return;
        }
        try
        {
            newTx.executeWithoutResult(status -> notificationService.create(
                    requestId, AppAdminConstants.NOTIFICATION_TRANSACTION, title, content,
                    businessType, businessId, receivers, "system"));
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

    private List<Long> two(Long a, Long b)
    {
        List<Long> list = new ArrayList<>(2);
        list.add(a);
        list.add(b);
        return list;
    }

    private String noSuffix(String no)
    {
        return (no == null || no.isBlank()) ? "" : "（编号 " + no + "）";
    }

    private String str(Object v)
    {
        return v == null ? null : String.valueOf(v);
    }
}
