package com.smartscript.platform.trade.service;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.SecurityUtils;
import com.smartscript.platform.trade.domain.SysInquiry;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.domain.SysOrderStatusLog;
import com.smartscript.platform.trade.mapper.SysInquiryMapper;
import com.smartscript.platform.trade.mapper.SysOrderMapper;
import com.smartscript.platform.trade.mapper.SysOrderStatusLogMapper;

/**
 * C module: inquiry management service.
 * Handles inquiry CRUD and inquiry-to-order conversion.
 */
@Service
public class TradeInquiryService
{
    @Autowired
    private SysInquiryMapper inquiryMapper;

    @Autowired
    private SysOrderMapper orderMapper;

    @Autowired
    private SysOrderStatusLogMapper logMapper;

    public List<SysInquiry> selectInquiryList(SysInquiry inquiry)
    {
        return inquiryMapper.selectInquiryList(inquiry);
    }

    public SysInquiry selectInquiryById(Long inquiryId)
    {
        return inquiryMapper.selectInquiryById(inquiryId);
    }

    /**
     * Convert inquiry to order (idempotent: if order already exists for this inquiry, return it).
     */
    @Transactional
    public SysOrder convertToOrder(Long inquiryId)
    {
        // Idempotency check: if order already exists for this inquiry, return existing
        SysOrder existing = orderMapper.selectOrderByInquiryId(inquiryId);
        if (existing != null)
        {
            return existing;
        }

        SysInquiry inquiry = inquiryMapper.selectInquiryById(inquiryId);
        if (inquiry == null)
        {
            throw new RuntimeException("Inquiry not found: " + inquiryId);
        }

        // Create order from inquiry
        SysOrder order = new SysOrder();
        order.setOrderNo("ORD-" + System.currentTimeMillis());
        order.setInquiryId(inquiryId);
        order.setWorkId(inquiry.getWorkId());
        order.setBuyerId(inquiry.getBuyerId());
        order.setSellerId(inquiry.getSellerId());
        order.setOrderType("inquiry");
        order.setLicenseType(inquiry.getLicenseType());
        // Amounts default to 0 until quote is finalized; D module handles financial details
        order.setTotalAmount(inquiry.getBudget() != null ? inquiry.getBudget() : java.math.BigDecimal.ZERO);
        order.setPlatformFee(java.math.BigDecimal.ZERO);
        order.setSplitRate(java.math.BigDecimal.ZERO);
        order.setSellerAmount(order.getTotalAmount());
        order.setStatus("inquiry");
        order.setCreateBy(SecurityUtils.getUsername());
        order.setCreateTime(new Date());
        orderMapper.insertOrder(order);

        // Log status transition
        SysOrderStatusLog log = new SysOrderStatusLog();
        log.setOrderId(order.getOrderId());
        log.setFromStatus(null);
        log.setToStatus("inquiry");
        log.setOperatorId(SecurityUtils.getUserId());
        log.setOperatorRole("admin");
        log.setRemark("Inquiry converted to order");
        log.setCreateBy(SecurityUtils.getUsername());
        log.setCreateTime(new Date());
        logMapper.insertLog(log);

        // Update inquiry status
        inquiry.setStatus("converted");
        inquiry.setUpdateBy(SecurityUtils.getUsername());
        inquiry.setUpdateTime(new Date());
        inquiryMapper.updateInquiry(inquiry);

        return order;
    }
}
