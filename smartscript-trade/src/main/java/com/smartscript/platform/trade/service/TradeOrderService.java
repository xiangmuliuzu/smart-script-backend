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
    /** 订单生成后的前置状态（PRD 9.3）：deal 达成即进入 confirmed，其后由 D 接手流转 */
    public static final String ORDER_STATUS_CONFIRMED = "confirmed";
    public static final String ORDER_STATUS_CANCELLED = "cancelled";
    public static final String ORDER_STATUS_ESCROW_PENDING = "escrow_pending";
    public static final String ORDER_STATUS_DELIVERING = "delivering";
    public static final String ORDER_STATUS_COMPLETED = "completed";

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

        // 自动生成合同
        try
        {
            String buyerName = inquiry.getBuyerName() != null ? inquiry.getBuyerName() : "买方" + inquiry.getBuyerId();
            String sellerName = inquiry.getSellerName() != null ? inquiry.getSellerName() : "卖方" + inquiry.getSellerId();
            String workTitle = inquiry.getWorkTitle() != null ? inquiry.getWorkTitle() : "作品#" + inquiry.getWorkId();
            contractService.autoGenerateContract(
                    order.getOrderId(), order.getOrderNo(),
                    inquiry.getBuyerId(), inquiry.getSellerId(),
                    buyerName, sellerName, workTitle,
                    total, inquiry.getLicenseType());
        }
        catch (Exception e)
        {
            // 合同生成失败不阻塞订单创建，记录日志即可
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("订单 {} 自动生成合同失败: {}", order.getOrderId(), e.getMessage());
        }

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
     * 取消订单。仅 confirmed 状态可取消。
     */
    @Transactional
    public void cancelOrder(Long orderId, String reason, Long operatorId, String operatorName)
    {
        SysOrder order = orderMapper.selectOrderById(orderId);
        if (order == null)
        {
            throw new RuntimeException("订单不存在");
        }
        if (!ORDER_STATUS_CONFIRMED.equals(order.getStatus()))
        {
            throw new RuntimeException("当前订单状态不可取消");
        }

        // CAS 落库：仅当订单仍处于 confirmed 时生效，防止与付款/完成流转并发竞态
        int changed = jdbc.update("UPDATE sys_order SET status = ?, remark = ?, update_by = ?, update_time = NOW() "
                + "WHERE order_id = ? AND status = ?",
                ORDER_STATUS_CANCELLED, reason, operatorName, orderId, ORDER_STATUS_CONFIRMED);
        if (changed != 1)
        {
            throw new RuntimeException("订单状态已变化，请刷新后重试");
        }

        SysOrderStatusLog log = new SysOrderStatusLog();
        log.setOrderId(orderId);
        log.setFromStatus(ORDER_STATUS_CONFIRMED);
        log.setToStatus(ORDER_STATUS_CANCELLED);
        log.setOperatorId(operatorId);
        log.setOperatorRole("buyer");
        log.setRemark(reason != null ? reason : "买方取消订单");
        log.setCreateBy(operatorName);
        log.setCreateTime(new Date());
        logMapper.insertLog(log);
    }

    /**
     * 买方付款到托管账户。订单 confirmed → escrow_pending，创建托管记录。
     * <p>
     * 前置条件：关联合同必须双方均已签署（status=completed）。
     */
    @Transactional
    public SysEscrow payToEscrow(Long orderId, String payChannel)
    {
        SysOrder order = orderMapper.selectOrderById(orderId);
        if (order == null)
        {
            throw new RuntimeException("订单不存在");
        }
        if (!ORDER_STATUS_CONFIRMED.equals(order.getStatus()))
        {
            throw new RuntimeException("当前订单状态不可付款，需为 confirmed");
        }

        // 校验合同签署状态（脏数据场景转业务提示，避免落 500 兜底）
        if (order.getContractId() == null)
        {
            throw new RuntimeException("该订单尚未生成合同，无法付款");
        }
        java.util.Map<String, Object> contract;
        try
        {
            contract = contractService.getById(order.getContractId());
        }
        catch (IllegalArgumentException e)
        {
            throw new RuntimeException("关联合同数据异常，无法付款，请联系管理员");
        }
        String contractStatus = String.valueOf(contract.get("status"));
        if (!"completed".equals(contractStatus))
        {
            throw new RuntimeException("合同尚未完成签署（当前状态：" + contractStatus + "），需双方签署后才能付款");
        }

        // 创建托管记录
        SysEscrow escrow = new SysEscrow();
        escrow.setEscrowNo("ESC" + Seq.getId());
        escrow.setOrderId(orderId);
        escrow.setAmount(order.getTotalAmount());
        escrow.setPaidAt(new Date());
        escrow.setPayChannel(payChannel != null ? payChannel : "balance");
        escrow.setStatus("escrowed");
        escrow.setCreateBy(safeGetOperatorName());
        escrow.setCreateTime(new Date());
        escrowMapper.insertEscrow(escrow);

        // CAS 落库：防止并发重复托管（重复 escrow 记录）；失败时事务回滚，已插入的托管记录一并撤销
        int changed = jdbc.update("UPDATE sys_order SET status = ?, pay_time = ?, escrow_id = ?, update_by = ?, update_time = NOW() "
                        + "WHERE order_id = ? AND status = ?",
                ORDER_STATUS_ESCROW_PENDING, new Date(), escrow.getEscrowId(), safeGetOperatorName(), orderId, ORDER_STATUS_CONFIRMED);
        if (changed != 1)
        {
            throw new RuntimeException("订单状态已变化，请刷新后重试");
        }

        // 状态日志
        SysOrderStatusLog log = new SysOrderStatusLog();
        log.setOrderId(orderId);
        log.setFromStatus(ORDER_STATUS_CONFIRMED);
        log.setToStatus(ORDER_STATUS_ESCROW_PENDING);
        log.setOperatorId(safeGetOperatorId());
        log.setOperatorRole("buyer");
        log.setRemark("买方付款到托管账户，托管单号: " + escrow.getEscrowNo());
        log.setCreateBy(safeGetOperatorName());
        log.setCreateTime(new Date());
        logMapper.insertLog(log);

        // 买方付款到托管 → 通知卖方开始交割
        notifyService.notifyOrderPaid(orderId, order.getOrderNo(), order.getSellerId());

        return escrow;
    }

    /**
     * 卖方确认交割。订单 escrow_pending → delivering。
     */
    @Transactional
    public void deliverOrder(Long orderId, Long operatorId)
    {
        SysOrder order = orderMapper.selectOrderById(orderId);
        if (order == null)
        {
            throw new RuntimeException("订单不存在");
        }
        if (!ORDER_STATUS_ESCROW_PENDING.equals(order.getStatus()))
        {
            throw new RuntimeException("当前订单状态不可交割，需为 escrow_pending");
        }
        // 校验操作人是卖方
        if (!order.getSellerId().equals(operatorId))
        {
            throw new RuntimeException("只有卖方可以确认交割");
        }

        // CAS 落库：仅 escrow_pending 可交割
        int changed = jdbc.update("UPDATE sys_order SET status = ?, update_by = ?, update_time = NOW() "
                + "WHERE order_id = ? AND status = ?",
                ORDER_STATUS_DELIVERING, safeGetOperatorName(), orderId, ORDER_STATUS_ESCROW_PENDING);
        if (changed != 1)
        {
            throw new RuntimeException("订单状态已变化，请刷新后重试");
        }

        SysOrderStatusLog log = new SysOrderStatusLog();
        log.setOrderId(orderId);
        log.setFromStatus(ORDER_STATUS_ESCROW_PENDING);
        log.setToStatus(ORDER_STATUS_DELIVERING);
        log.setOperatorId(operatorId);
        log.setOperatorRole("seller");
        log.setRemark("卖方确认交割，开始交付作品");
        log.setCreateBy(safeGetOperatorName());
        log.setCreateTime(new Date());
        logMapper.insertLog(log);
    }

    /**
     * 买方确认收货。订单 delivering → completed，释放托管资金，写入作者收益。
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
        // 确保后续资金释放与收益入账仅执行一次
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

        // 买方确认收货、订单完成 → 通知买卖双方订单已完成、资金已释放
        notifyService.notifyOrderCompleted(orderId, order.getOrderNo(), order.getBuyerId(), order.getSellerId());
    }
}
