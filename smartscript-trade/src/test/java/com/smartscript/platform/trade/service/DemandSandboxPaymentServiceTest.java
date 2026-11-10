package com.smartscript.platform.trade.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.smartscript.platform.trade.domain.DemandSandboxPayment;
import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.mapper.DemandSandboxPaymentMapper;

/** 需求任务沙箱收款码换号逻辑：手动刷新、超时自动换号、共用押金码不越权换号、旧码已付就地结算。 */
class DemandSandboxPaymentServiceTest
{
    static final long DEMAND_ID = 7L;
    static final long PUBLISHER = 11L;
    static final long CREATOR_A = 21L;
    static final long CREATOR_B = 22L;

    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    DemandSandboxPaymentMapper paymentMapper = mock(DemandSandboxPaymentMapper.class);
    TradeDemandService demandService = mock(TradeDemandService.class);
    AlipayClient client = mock(AlipayClient.class);
    DemandSandboxPaymentService service;
    SysDemand demand;

    @BeforeEach
    void setup() throws Exception
    {
        service = spy(new DemandSandboxPaymentService(jdbc, paymentMapper, demandService,
                true, "test-app", "test-private", "test-public"));
        doReturn(client).when(service).client();
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("smartscript_sandbox");

        demand = new SysDemand();
        demand.setDemandId(DEMAND_ID);
        demand.setClientId(PUBLISHER);
        demand.setStatus("open");
        demand.setEscrowStatus("pending");
        demand.setBudget(new BigDecimal("100.00"));
        when(demandService.selectDemandById(DEMAND_ID)).thenReturn(demand);

        // 预下单响应回填请求里的商户单号，便于断言最终用的是旧号还是新号
        when(client.execute(any(AlipayTradePrecreateRequest.class))).thenAnswer(invocation -> {
            AlipayTradePrecreateModel model = (AlipayTradePrecreateModel)
                    ((AlipayTradePrecreateRequest) invocation.getArgument(0)).getBizModel();
            AlipayTradePrecreateResponse response = mock(AlipayTradePrecreateResponse.class);
            when(response.isSuccess()).thenReturn(true);
            when(response.getOutTradeNo()).thenReturn(model.getOutTradeNo());
            when(response.getQrCode()).thenReturn("https://qr.test/" + model.getOutTradeNo());
            return response;
        });
        when(client.execute(any(AlipayTradeCloseRequest.class))).thenReturn(mock(AlipayTradeCloseResponse.class));
        waitBuyerPay();
    }

    private void waitBuyerPay() throws Exception
    {
        AlipayTradeQueryResponse response = mock(AlipayTradeQueryResponse.class);
        when(response.isSuccess()).thenReturn(true);
        when(response.getTradeStatus()).thenReturn("WAIT_BUYER_PAY");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);
    }

    private DemandSandboxPayment record(String purpose, String outTradeNo, Long payerId,
            String amount, Date updatedAt, String status)
    {
        DemandSandboxPayment row = new DemandSandboxPayment();
        row.setDemandId(DEMAND_ID);
        row.setPurpose(purpose);
        row.setOutTradeNo(outTradeNo);
        row.setPayerId(payerId);
        row.setAmount(new BigDecimal(amount));
        row.setUpdatedAt(updatedAt);
        row.setStatus(status);
        return row;
    }

    private Date minutesAgo(int minutes)
    {
        return new Date(System.currentTimeMillis() - minutes * 60_000L);
    }

    @Test
    void freshPendingCodeIsReusedWithoutRotation() throws Exception
    {
        when(paymentMapper.selectForUpdate(DEMAND_ID, "escrow"))
                .thenReturn(record("escrow", "SDESCOLD", PUBLISHER, "100.00", new Date(), "PENDING"));

        Map<String, Object> result = service.createEscrow(DEMAND_ID, PUBLISHER, false);

        assertEquals("SDESCOLD", result.get("outTradeNo"));
        verify(paymentMapper, never()).rotatePending(any(), any(), any(), any(), any());
        verify(client, never()).execute(any(AlipayTradeQueryRequest.class));
        verify(client, never()).execute(any(AlipayTradeCloseRequest.class));
    }

    @Test
    void stalePendingCodeIsReplacedByNewOutTradeNo() throws Exception
    {
        when(paymentMapper.selectForUpdate(DEMAND_ID, "escrow"))
                .thenReturn(record("escrow", "SDESCOLD", PUBLISHER, "100.00", minutesAgo(11), "PENDING"));

        Map<String, Object> result = service.createEscrow(DEMAND_ID, PUBLISHER, false);

        ArgumentCaptor<String> rotated = ArgumentCaptor.forClass(String.class);
        verify(paymentMapper).rotatePending(eq(DEMAND_ID), eq("escrow"), rotated.capture(), eq(PUBLISHER),
                eq(new BigDecimal("100.00")));
        assertEquals(rotated.getValue(), result.get("outTradeNo"));
        assertNotEquals("SDESCOLD", result.get("outTradeNo"));
        assertTrue(((String) result.get("outTradeNo")).startsWith("SDESC"));
        // 换号前先查单，再关掉仍活着的旧单，旧码才真正失效
        verify(client).execute(any(AlipayTradeQueryRequest.class));
        verify(client).execute(any(AlipayTradeCloseRequest.class));
    }

    @Test
    void refreshFlagRotatesEvenAFreshCode() throws Exception
    {
        when(paymentMapper.selectForUpdate(DEMAND_ID, "escrow"))
                .thenReturn(record("escrow", "SDESCOLD", PUBLISHER, "100.00", new Date(), "PENDING"));

        Map<String, Object> result = service.createEscrow(DEMAND_ID, PUBLISHER, true);

        assertNotEquals("SDESCOLD", result.get("outTradeNo"));
        verify(paymentMapper).rotatePending(eq(DEMAND_ID), eq("escrow"), anyString(), eq(PUBLISHER), any());
    }

    @Test
    void closedRecordRotatesWithoutExtraQueryOrClose() throws Exception
    {
        when(paymentMapper.selectForUpdate(DEMAND_ID, "escrow"))
                .thenReturn(record("escrow", "SDESCOLD", PUBLISHER, "100.00", new Date(), "CLOSED"));

        Map<String, Object> result = service.createEscrow(DEMAND_ID, PUBLISHER, false);

        assertNotEquals("SDESCOLD", result.get("outTradeNo"));
        verify(client, never()).execute(any(AlipayTradeQueryRequest.class));
        verify(client, never()).execute(any(AlipayTradeCloseRequest.class));
    }

    @Test
    void anotherCreatorCannotRotateTheSharedDepositCode() throws Exception
    {
        demand.setEscrowStatus("escrowed");
        when(paymentMapper.selectForUpdate(DEMAND_ID, "deposit"))
                .thenReturn(record("deposit", "SDDEPOLD", CREATOR_A, "10.00", minutesAgo(30), "PENDING"));

        Map<String, Object> result = service.createDeposit(DEMAND_ID, CREATOR_B, true);

        // 押金码按任务共用，非付款人只能沿用同一张码，保住"先付先确认"的抢单口径
        assertEquals("SDDEPOLD", result.get("outTradeNo"));
        verify(paymentMapper, never()).rotatePending(any(), any(), any(), any(), any());
    }

    @Test
    void rotationSettlesAnAlreadyPaidOldCode() throws Exception
    {
        DemandSandboxPayment row = record("escrow", "SDESCOLD", PUBLISHER, "100.00", minutesAgo(20), "PENDING");
        when(paymentMapper.selectForUpdate(DEMAND_ID, "escrow")).thenReturn(row);
        AlipayTradeQueryResponse paid = mock(AlipayTradeQueryResponse.class);
        when(paid.isSuccess()).thenReturn(true);
        when(paid.getTradeStatus()).thenReturn("TRADE_SUCCESS");
        when(paid.getOutTradeNo()).thenReturn("SDESCOLD");
        when(paid.getTradeNo()).thenReturn("alipay-trade-no");
        when(paid.getTotalAmount()).thenReturn("100.00");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(paid);

        Map<String, Object> result = service.createEscrow(DEMAND_ID, PUBLISHER, true);

        assertEquals(true, result.get("paid"));
        verify(paymentMapper).markPaid("SDESCOLD", "alipay-trade-no");
        verify(demandService).markEscrowed(DEMAND_ID);
        // 旧码已收款成功，绝不能换号让用户再付一次
        verify(paymentMapper, never()).rotatePending(any(), any(), any(), any(), any());
        verify(client, never()).execute(any(AlipayTradePrecreateRequest.class));
    }
}
