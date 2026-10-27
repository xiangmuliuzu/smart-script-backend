package com.smartscript.platform.trade.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.uuid.Seq;
import com.smartscript.platform.trade.domain.SysInquiry;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.domain.SysOrderStatusLog;
import com.smartscript.platform.trade.domain.SysEscrow;
import com.smartscript.platform.trade.mapper.SysOrderMapper;
import com.smartscript.platform.trade.mapper.SysOrderStatusLogMapper;
import com.smartscript.platform.trade.mapper.SysEscrowMapper;

/**
 * C module: order query + generation service.
 * C owns order creation + status flow; contract/escrow/settlement belongs to D.
 *
 * 金额规则（用户决策 2026-09-28）：暂不抽成，platformFee=0、splitRate=0、sellerAmount=totalAmount，
 * 满足清单第 6 节金额一致性 total_amount = platform_fee + seller_amount。
 */
@Service
public class TradeOrderService
{
    /** 订单生成后的前置状态（PRD 9.3）：deal 达成即进入 confirmed，本态用于双方签署合同 */
    public static final String ORDER_STATUS_CONFIRMED = "confirmed";
    /** 双方合同签署完成后自动进入：待买方付款 */
    public static final String ORDER_STATUS_PAYING = "paying";
    public static final String ORDER_STATUS_DELIVERING = "delivering";
    public static final String ORDER_STATUS_COMPLETED = "completed";
    /** 遗留状态：仅历史数据只读展示，不再产生 */
    public static final String ORDER_STATUS_CANCELLED = "cancelled";
    public static final String ORDER_STATUS_ESCROW_PENDING = "escrow_pending";

    @Autowired
    private SysOrderMapper orderMapper;

    @Autowired
    private SysOrderStatusLogMapper logMapper;

    @Autowired
    private SysEscrowMapper escrowMapper;

    /** 状态流转用 CAS 定点更新，避免全字段 updateOrder 覆盖并发写入 */
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TradeFinanceService financeService;

    @Autowired
    private TradeContractService contractService;

    /** 交易全生命周期站内通知统一出口。 */
    @Autowired
    private TradeNotifyService notifyService;

    public List<SysOrder> selectOrderList(SysOrder order)
    {
        return orderMapper.selectOrderList(order);
    }

    public SysOrder selectOrderById(Long orderId)
    {
        return orderMapper.selectOrderById(orderId);
    }

    public List<SysOrderStatusLog> selectStatusLogByOrderId(Long orderId)
    {
        return logMapper.selectLogsByOrderId(orderId);
    }

    /** 按作品 ID 查询授权历史（订单列表），供版权资产页调用 */
    public List<SysOrder> selectAuthorizationHistoryByWorkId(Long workId)
    {
        return orderMapper.selectAuthorizationHistoryByWorkId(workId);
    }

    /**
     * 依据询盘生成授权订单（幂等）。转订单与接受报价两条路径共用。
     * 幂等：同一 inquiry_id 已存在订单则直接返回；并由 sys_order.inquiry_id 唯一索引兜底并发。
     *
     * @param inquiry     询盘
     * @param quoteId     报价 ID（转订单路径传 null）
     * @param totalAmount 订单金额（接受报价传报价 price；转订单传询盘 budget）
     * @param orderType   订单类型：inquiry / quote
     * @param logRemark   状态日志备注
     */
    @Transactional
    public SysOrder generateOrderFromInquiry(SysInquiry inquiry, Long quoteId, BigDecimal totalAmount,
                                             String orderType, String logRemark)
    {
        SysOrder existing = orderMapper.selectOrderByInquiryId(inquiry.getInquiryId());
        if (existing != null)
        {
            return existing;
        }

        BigDecimal total = totalAmount != null ? totalAmount
                : (inquiry.getBudget() != null ? inquiry.getBudget() : BigDecimal.ZERO);

        SysOrder order = new SysOrder();
        order.setOrderNo("ORD" + Seq.getId());
        order.setInquiryId(inquiry.getInquiryId());
        order.setQuoteId(quoteId);
        order.setWorkId(inquiry.getWorkId());
        order.setBuyerId(inquiry.getBuyerId());
        order.setSellerId(inquiry.getSellerId());
        order.setOrderType(orderType);
        order.setLicenseType(inquiry.getLicenseType());
        // 暂不抽成：金额一致性 total = platformFee + sellerAmount
        order.setTotalAmount(total);
        order.setPlatformFee(BigDecimal.ZERO);
        order.setSplitRate(BigDecimal.ZERO);
        order.setSellerAmount(total);
        order.setStatus(ORDER_STATUS_CONFIRMED);
        order.setCreateBy(safeGetOperatorName());
        order.setCreateTime(new Date());
        orderMapper.insertOrder(order);

        SysOrderStatusLog log = new SysOrderStatusLog();
        log.setOrderId(order.getOrderId());
        log.setFromStatus(null);
        log.setToStatus(ORDER_STATUS_CONFIRMED);
        log.setOperatorId(safeGetOperatorId());
        log.setOperatorRole("admin");
        log.setRemark(logRemark);
        log.setCreateBy(safeGetOperatorName());
        log.setCreateTime(new Date());
        logMapper.insertLog(log);

        // 自动生成合同：与订单创建同事务，失败即整体回滚。
        // 不再吞异常——若此处放过失败，会建出「无合同、签不了也付不了」的 confirmed 死单（问题3）。
        String buyerName = inquiry.getBuyerName() != null ? inquiry.getBuyerName() : "买方" + inquiry.getBuyerId();
        String sellerName = inquiry.getSellerName() != null ? inquiry.getSellerName() : "卖方" + inquiry.getSellerId();
        String workTitle = inquiry.getWorkTitle() != null ? inquiry.getWorkTitle() : "作品#" + inquiry.getWorkId();
        contractService.autoGenerateContract(
                order.getOrderId(), order.getOrderNo(),
                inquiry.getBuyerId(), inquiry.getSellerId(),
                buyerName, sellerName, workTitle,
                total, inquiry.getLicenseType());

        return order;
    }

    /**
     * 双端兼容取操作人 ID：APP 链路 principal 是 AppIdentityContext，
     * PC 链路才是若依 LoginUser；直接调 SecurityUtils 会抛「获取用户账户异常」。
     */
    private Long safeGetOperatorId()
    {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof com.smartscript.platform.user.security.AppIdentityContext identity)
        {
            return identity.getUserId();
        }
        try
        {
            return SecurityUtils.getUserId();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 双端兼容取操作人名称：同 {@link #safeGetOperatorId()}。
     */
    private String safeGetOperatorName()
    {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof com.smartscript.platform.user.security.AppIdentityContext identity)
        {
            return String.valueOf(identity.getUserId());
        }
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }

    /**
     * 买方付款。订单 paying → delivering（付款不再创建托管记录，仅推进状态）。
     * <p>
     * 前置：订单已由合同双签自动流转至 paying。
     */
    @Transactional
    public void payOrder(Long orderId, Long operatorId)
    {
        SysOrder order = orderMapper.selectOrderById(orderId);
        if (order == null)
        {
            throw new RuntimeException("订单不存在");
        }
        // 纵深防御：service 层同样校验操作人必须是买方，与 completeOrder 对称，
        // 不再单靠 Controller 兜底（问题6）。
        if (operatorId == null || !operatorId.equals(order.getBuyerId()))
        {
            throw new RuntimeException("只有买方可以付款");
        }
        if (!ORDER_STATUS_PAYING.equals(order.getStatus()))
        {
            throw new RuntimeException("当前订单状态不可付款，需为 paying");
        }

        // CAS 落库：仅 paying 可付款，防并发重复
        int changed = jdbc.update("UPDATE sys_order SET status = ?, pay_time = NOW(), update_by = ?, update_time = NOW() "
                + "WHERE order_id = ? AND status = ?",
                ORDER_STATUS_DELIVERING, safeGetOperatorName(), orderId, ORDER_STATUS_PAYING);
        if (changed != 1)
        {
            throw new RuntimeException("订单状态已变化，请刷新后重试");
        }

        SysOrderStatusLog log = new SysOrderStatusLog();
        log.setOrderId(orderId);
        log.setFromStatus(ORDER_STATUS_PAYING);
        log.setToStatus(ORDER_STATUS_DELIVERING);
        log.setOperatorId(safeGetOperatorId());
        log.setOperatorRole("buyer");
        log.setRemark("买方付款完成，订单进入交割");
        log.setCreateBy(safeGetOperatorName());
        log.setCreateTime(new Date());
        logMapper.insertLog(log);

        // 买方付款 → 通知卖方订单进入交割
        notifyService.notifyOrderPaid(orderId, order.getOrderNo(), order.getSellerId());
    }

    /**
     * 买方确认收货。订单 delivering → completed，写入作者收益。
     */
    @Transactional
    public void completeOrder(Long orderId, Long operatorId)
    {
        SysOrder order = orderMapper.selectOrderById(orderId);
        if (order == null)
        {
            throw new RuntimeException("订单不存在");
        }
        if (!ORDER_STATUS_DELIVERING.equals(order.getStatus()))
        {
            throw new RuntimeException("当前订单状态不可确认完成，需为 delivering");
        }
        // 校验操作人是买方
        if (!order.getBuyerId().equals(operatorId))
        {
            throw new RuntimeException("只有买方可以确认收货");
        }

        // CAS 落库：仅 delivering 可完成，并发重复调用（双击/重试）在此拦截，
        // 确保后续收益入账仅执行一次
        int changed = jdbc.update("UPDATE sys_order SET status = ?, complete_time = ?, update_by = ?, update_time = NOW() "
                + "WHERE order_id = ? AND status = ?",
                ORDER_STATUS_COMPLETED, new Date(), safeGetOperatorName(), orderId, ORDER_STATUS_DELIVERING);
        if (changed != 1)
        {
            throw new RuntimeException("订单状态已变化，请刷新后重试");
        }

        // 释放托管
        SysEscrow escrow = escrowMapper.selectEscrowByOrderId(orderId);
        if (escrow != null && "escrowed".equals(escrow.getStatus()))
        {
            escrow.setStatus("released");
            escrow.setReleaseTime(new Date());
            escrow.setReleaseReason("买方确认收货，资金释放给卖方");
            escrow.setUpdateBy(safeGetOperatorName());
            escrow.setUpdateTime(new Date());
            escrowMapper.updateEscrow(escrow);
        }

        // 到账前先生成待结算单，作者确认后再入账（不直接 creditSellerIncome）
        // 重新查询以带上作品标题等展示字段
        SysOrder settledOrder = orderMapper.selectOrderById(orderId);
        financeService.createPendingSettle(settledOrder != null ? settledOrder : order);

        SysOrderStatusLog log = new SysOrderStatusLog();
        log.setOrderId(orderId);
        log.setFromStatus(ORDER_STATUS_DELIVERING);
        log.setToStatus(ORDER_STATUS_COMPLETED);
        log.setOperatorId(operatorId);
        log.setOperatorRole("buyer");
        log.setRemark("买方确认收货，订单完成，资金已释放，待卖家确认收益结算");
        log.setCreateBy(safeGetOperatorName());
        log.setCreateTime(new Date());
        logMapper.insertLog(log);

        // 买方确认收货、订单完成 → 通知买卖双方
        notifyService.notifyOrderCompleted(orderId, order.getOrderNo(), order.getBuyerId(), order.getSellerId());
    }

    /**
     * 取消订单（问题4）：仅「双方签署完成前」（即订单仍为 confirmed，合同为 pending/partial_signed）允许。
     * 一旦合同双签完成、订单自动进入 paying，即锁定不可取消。
     * <p>
     * 买卖双方任一方可发起；CAS 落库（仅 confirmed 可取消，防并发）；
     * 连带作废该订单下尚未签满的合同，避免残留合同被签署后试图把已取消订单推回 paying。
     *
     * @param operatorId 操作人（须为买方或卖方）
     * @param reason     取消原因，落 sys_order.remark
     */
    @Transactional
    public void cancelOrder(Long orderId, Long operatorId, String reason)
    {
        SysOrder order = orderMapper.selectOrderById(orderId);
        if (order == null)
        {
            throw new RuntimeException("订单不存在");
        }
        if (operatorId == null
                || !(operatorId.equals(order.getBuyerId()) || operatorId.equals(order.getSellerId())))
        {
            throw new RuntimeException("只有交易双方可以取消订单");
        }
        if (!ORDER_STATUS_CONFIRMED.equals(order.getStatus()))
        {
            throw new RuntimeException("合同双方签署完成后不可取消订单，当前状态: " + order.getStatus());
        }

        // CAS 落库：仅 confirmed 可取消，防并发重复；取消原因落 remark（toAppMap / 列表按 cancelReason 展示）
        String actor = safeGetOperatorName();
        int changed = jdbc.update("UPDATE sys_order SET status = ?, remark = ?, update_by = ?, update_time = NOW() "
                + "WHERE order_id = ? AND status = ?",
                ORDER_STATUS_CANCELLED, reason, actor, orderId, ORDER_STATUS_CONFIRMED);
        if (changed != 1)
        {
            throw new RuntimeException("订单状态已变化，请刷新后重试");
        }

        // 连带作废尚未签满的合同（pending/partial_signed），彻底切断后续签署驱动订单的回退路径
        jdbc.update("UPDATE sys_contract SET status = 'cancelled', update_time = NOW() "
                + "WHERE order_id = ? AND status IN ('pending', 'partial_signed')", orderId);

        SysOrderStatusLog log = new SysOrderStatusLog();
        log.setOrderId(orderId);
        log.setFromStatus(ORDER_STATUS_CONFIRMED);
        log.setToStatus(ORDER_STATUS_CANCELLED);
        log.setOperatorId(operatorId);
        log.setOperatorRole(operatorId.equals(order.getBuyerId()) ? "buyer" : "seller");
        log.setRemark("合同签署前取消订单" + (reason != null && !reason.isBlank() ? "：" + reason : ""));
        log.setCreateBy(actor);
        log.setCreateTime(new Date());
        logMapper.insertLog(log);

        // 取消 → 通知对方（未发起取消的那一方）
        Long counterpartId = operatorId.equals(order.getBuyerId()) ? order.getSellerId() : order.getBuyerId();
        notifyService.notifyOrderCancelled(orderId, order.getOrderNo(), counterpartId);
    }
}
