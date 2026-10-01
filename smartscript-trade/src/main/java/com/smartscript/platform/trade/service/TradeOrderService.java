package com.smartscript.platform.trade.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.uuid.Seq;
import com.smartscript.platform.trade.domain.SysInquiry;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.domain.SysOrderStatusLog;
import com.smartscript.platform.trade.mapper.SysOrderMapper;
import com.smartscript.platform.trade.mapper.SysOrderStatusLogMapper;

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

    @Autowired
    private SysOrderMapper orderMapper;

    @Autowired
    private SysOrderStatusLogMapper logMapper;

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
        order.setCreateBy(SecurityUtils.getUsername());
        order.setCreateTime(new Date());
        orderMapper.insertOrder(order);

        SysOrderStatusLog log = new SysOrderStatusLog();
        log.setOrderId(order.getOrderId());
        log.setFromStatus(null);
        log.setToStatus(ORDER_STATUS_CONFIRMED);
        log.setOperatorId(SecurityUtils.getUserId());
        log.setOperatorRole("admin");
        log.setRemark(logRemark);
        log.setCreateBy(SecurityUtils.getUsername());
        log.setCreateTime(new Date());
        logMapper.insertLog(log);

        return order;
    }
}
