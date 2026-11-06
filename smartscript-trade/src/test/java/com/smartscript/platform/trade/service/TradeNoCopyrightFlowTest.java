package com.smartscript.platform.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartscript.platform.trade.domain.SysInquiry;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.domain.SysOrderStatusLog;
import com.smartscript.platform.trade.domain.SysWork;
import com.smartscript.platform.trade.mapper.SysInquiryMapper;
import com.smartscript.platform.trade.mapper.SysOrderMapper;
import com.smartscript.platform.trade.mapper.SysOrderStatusLogMapper;
import com.smartscript.platform.trade.mapper.SysWorkCopyrightMapper;
import com.smartscript.platform.trade.mapper.SysWorkMapper;

/**
 * 无版权作品简化交易流程分流单元测试。
 *
 * 判定口径：sys_copyright_application 存在 certificated 记录 = 有版权。
 * 覆盖两条分流：
 *   发起询盘：有版权授权类型必填（现状）；无版权归一置 null 放行
 *   生成订单：有版权 confirmed + 自动生成合同；无版权成单即 paying 且不建合同
 *
 * 不验证 Mapper SQL 本身（hasCertificatedCopyright 属集成测试），只验证服务层分流。
 */
class TradeNoCopyrightFlowTest
{
    private static final long WORK_ID = 42L;
    private static final long BUYER_ID = 106L;
    private static final long SELLER_ID = 1L;
    private static final long INQUIRY_ID = 77L;

    private SysWorkCopyrightMapper copyrightMapper;
    private SysInquiryMapper inquiryMapper;
    private SysWorkMapper workMapper;
    private TradeNotifyService notifyService;
    private TradeInquiryService inquiryService;

    private SysOrderMapper orderMapper;
    private SysOrderStatusLogMapper logMapper;
    private TradeContractService contractService;
    private TradeOrderService orderService;

    @BeforeEach
    void setUp()
    {
        copyrightMapper = mock(SysWorkCopyrightMapper.class);
        inquiryMapper = mock(SysInquiryMapper.class);
        workMapper = mock(SysWorkMapper.class);
        notifyService = mock(TradeNotifyService.class);

        inquiryService = new TradeInquiryService();
        ReflectionTestUtils.setField(inquiryService, "copyrightMapper", copyrightMapper);
        ReflectionTestUtils.setField(inquiryService, "inquiryMapper", inquiryMapper);
        ReflectionTestUtils.setField(inquiryService, "workMapper", workMapper);
        ReflectionTestUtils.setField(inquiryService, "notifyService", notifyService);

        orderMapper = mock(SysOrderMapper.class);
        logMapper = mock(SysOrderStatusLogMapper.class);
        contractService = mock(TradeContractService.class);

        orderService = new TradeOrderService();
        ReflectionTestUtils.setField(orderService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(orderService, "logMapper", logMapper);
        ReflectionTestUtils.setField(orderService, "copyrightMapper", copyrightMapper);
        ReflectionTestUtils.setField(orderService, "contractService", contractService);
    }

    private SysWork work()
    {
        SysWork w = new SysWork();
        w.setWorkId(WORK_ID);
        w.setAuthorId(SELLER_ID);
        return w;
    }

    private SysInquiry inquiry(String licenseType)
    {
        SysInquiry inq = new SysInquiry();
        inq.setWorkId(WORK_ID);
        inq.setBuyerId(BUYER_ID);
        // 卖方由作品归属回填，此处仅满足入参非空校验
        inq.setSellerId(SELLER_ID);
        inq.setLicenseType(licenseType);
        inq.setIntendedUse("影视化改编");
        inq.setBudget(new BigDecimal("50000.00"));
        inq.setCreateBy(String.valueOf(BUYER_ID));
        return inq;
    }

    // ===== 发起询盘分流 =====

    @Test
    @DisplayName("有版权 + 授权类型为空：仍拒绝（现状校验保持）")
    void createInquiryCopyrightedBlankLicenseRejected()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(work());
        when(copyrightMapper.hasCertificatedCopyright(WORK_ID)).thenReturn(true);

        SysInquiry inq = inquiry(null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> inquiryService.createInquiry(inq));
        assertEquals("授权类型不能为空", ex.getMessage());
        verify(inquiryMapper, never()).insertInquiry(any());
    }

    @Test
    @DisplayName("有版权 + 已选授权类型：正常落库且授权类型保留")
    void createInquiryCopyrightedKeepsLicenseType()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(work());
        when(copyrightMapper.hasCertificatedCopyright(WORK_ID)).thenReturn(true);
        when(inquiryMapper.insertInquiry(any(SysInquiry.class))).thenAnswer(inv -> {
            ((SysInquiry) inv.getArgument(0)).setInquiryId(INQUIRY_ID);
            return 1;
        });

        SysInquiry created = inquiryService.createInquiry(inquiry("exclusive"));
        assertEquals("exclusive", created.getLicenseType());
        verify(inquiryMapper).insertInquiry(created);
    }

    @Test
    @DisplayName("无版权 + 未传授权类型：放行且归一置 null 落库")
    void createInquiryNoCopyrightAllowsBlankLicense()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(work());
        when(copyrightMapper.hasCertificatedCopyright(WORK_ID)).thenReturn(false);
        when(inquiryMapper.insertInquiry(any(SysInquiry.class))).thenAnswer(inv -> {
            ((SysInquiry) inv.getArgument(0)).setInquiryId(INQUIRY_ID);
            return 1;
        });

        inquiryService.createInquiry(inquiry(null));
        ArgumentCaptor<SysInquiry> captor = ArgumentCaptor.forClass(SysInquiry.class);
        verify(inquiryMapper).insertInquiry(captor.capture());
        assertNull(captor.getValue().getLicenseType());
    }

    @Test
    @DisplayName("无版权 + 前端误传授权类型：服务端归一置 null，防脏数据")
    void createInquiryNoCopyrightNormalizesStrayLicense()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(work());
        when(copyrightMapper.hasCertificatedCopyright(WORK_ID)).thenReturn(false);
        when(inquiryMapper.insertInquiry(any(SysInquiry.class))).thenAnswer(inv -> {
            ((SysInquiry) inv.getArgument(0)).setInquiryId(INQUIRY_ID);
            return 1;
        });

        inquiryService.createInquiry(inquiry("non_exclusive"));
        ArgumentCaptor<SysInquiry> captor = ArgumentCaptor.forClass(SysInquiry.class);
        verify(inquiryMapper).insertInquiry(captor.capture());
        assertNull(captor.getValue().getLicenseType());
    }

    // ===== 生成订单分流 =====

    private SysInquiry orderInquiry()
    {
        SysInquiry inq = inquiry(null);
        inq.setInquiryId(INQUIRY_ID);
        inq.setInquiryNo("INQ001");
        return inq;
    }

    @Test
    @DisplayName("有版权成单：confirmed 且自动生成合同（现状不变）")
    void generateOrderCopyrightedConfirmedWithContract()
    {
        when(orderMapper.selectOrderByInquiryId(INQUIRY_ID)).thenReturn(null);
        when(copyrightMapper.hasCertificatedCopyright(WORK_ID)).thenReturn(true);

        SysOrder order = orderService.generateOrderFromInquiry(
                orderInquiry(), 5L, new BigDecimal("30000.00"), "quote", "Quote accepted");
        assertEquals(TradeOrderService.ORDER_STATUS_CONFIRMED, order.getStatus());
        verify(contractService).autoGenerateContract(any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("无版权成单：直达 paying、不建合同、状态日志记跳过原因")
    void generateOrderNoCopyrightSkipsContractToPaying()
    {
        when(orderMapper.selectOrderByInquiryId(INQUIRY_ID)).thenReturn(null);
        when(copyrightMapper.hasCertificatedCopyright(WORK_ID)).thenReturn(false);

        SysOrder order = orderService.generateOrderFromInquiry(
                orderInquiry(), 5L, new BigDecimal("30000.00"), "quote", "Quote accepted");
        assertEquals(TradeOrderService.ORDER_STATUS_PAYING, order.getStatus());
        verify(contractService, never()).autoGenerateContract(any(), any(), any(), any(),
                any(), any(), any(), any(), any());

        ArgumentCaptor<SysOrderStatusLog> logCaptor = ArgumentCaptor.forClass(SysOrderStatusLog.class);
        verify(logMapper).insertLog(logCaptor.capture());
        SysOrderStatusLog log = logCaptor.getValue();
        assertEquals(TradeOrderService.ORDER_STATUS_PAYING, log.getToStatus());
        org.junit.jupiter.api.Assertions.assertTrue(log.getRemark().contains("无版权作品：跳过合同"));
    }

    @Test
    @DisplayName("幂等：同询盘已有订单直接复用，不再分流判定/不重复插单")
    void generateOrderIdempotentReturnsExisting()
    {
        SysOrder existing = new SysOrder();
        existing.setOrderId(9L);
        existing.setStatus(TradeOrderService.ORDER_STATUS_CONFIRMED);
        when(orderMapper.selectOrderByInquiryId(INQUIRY_ID)).thenReturn(existing);

        SysOrder order = orderService.generateOrderFromInquiry(
                orderInquiry(), 5L, new BigDecimal("30000.00"), "quote", "Quote accepted");
        assertEquals(existing, order);
        verify(orderMapper, never()).insertOrder(any());
        verify(copyrightMapper, never()).hasCertificatedCopyright(anyLong());
    }
}
