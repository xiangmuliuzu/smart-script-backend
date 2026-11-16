package com.smartscript.platform.trade.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.smartscript.platform.trade.domain.SysOrder;

class AlipaySandboxPaymentServiceTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    TradeOrderService orders = mock(TradeOrderService.class);
    AlipayClient client = mock(AlipayClient.class);
    AlipaySandboxPaymentService service;
    SysOrder order;
    @BeforeEach void setup() {
        service = spy(new AlipaySandboxPaymentService(jdbc, orders, true, "test-app", "test-private", "test-public"));
        doReturn(client).when(service).client();
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("smartscript_sandbox");
        order = new SysOrder(); order.setOrderId(1L); order.setBuyerId(2L); order.setTotalAmount(new BigDecimal("12.00")); order.setStatus("paying");
        when(orders.selectOrderById(1L)).thenReturn(order);
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(Map.of("out_trade_no", "SSSB123", "buyer_id", 2L, "amount", new BigDecimal("12.00"), "status", "PENDING")));
    }
    @Test void createOnlyReturnsVerifiedSandboxQrWithoutAdvancing() throws Exception {
        when(jdbc.queryForMap(anyString(), eq(1L))).thenReturn(Map.of("out_trade_no", "SSSB123", "buyer_id", 2L, "amount", new BigDecimal("12.00"), "status", "PENDING"));
        AlipayTradePrecreateResponse response = mock(AlipayTradePrecreateResponse.class);
        when(client.execute(any(AlipayTradePrecreateRequest.class))).thenReturn(response);
        when(response.isSuccess()).thenReturn(true);
        when(response.getOutTradeNo()).thenReturn("SSSB123");
        when(response.getQrCode()).thenReturn("https://example.test/qr");
        assertEquals("https://example.test/qr", service.create(1L, 2L).get("qrCode"));
        verify(orders, never()).payOrder(anyLong(), anyLong());
        when(response.getOutTradeNo()).thenReturn("wrong-order");
        assertThrows(IllegalStateException.class, () -> service.create(1L, 2L));
    }

    @Test void unsignedOrWrongApplicationNotificationIsRejected() {
        assertFalse(service.processNotification(Map.of("app_id", "test-app", "sign_type", "RSA2", "trade_status", "TRADE_SUCCESS", "out_trade_no", "fake")));
        assertFalse(service.processNotification(Map.of("app_id", "wrong", "sign_type", "RSA2", "trade_status", "TRADE_SUCCESS", "out_trade_no", "fake")));
        verify(orders, never()).payOrder(anyLong(), anyLong());
    }

    @Test void signedNotificationRequeriesAndRejectsTampering() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        String pub = java.util.Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        String priv = java.util.Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        var notified = new AlipaySandboxPaymentService(jdbc, orders, true, "test-app", "unused", pub);
        @SuppressWarnings("unchecked") var provider = (org.springframework.beans.factory.ObjectProvider<AlipaySandboxPaymentService>) mock(org.springframework.beans.factory.ObjectProvider.class);
        var proxy = mock(AlipaySandboxPaymentService.class);
        when(provider.getObject()).thenReturn(proxy);
        org.springframework.test.util.ReflectionTestUtils.setField(notified, "self", provider);
        when(jdbc.queryForList(contains("WHERE out_trade_no"), eq("SSSB123"))).thenReturn(List.of(Map.of("order_id", 1L, "buyer_id", 2L)));
        when(proxy.confirm(1L, 2L)).thenReturn(Map.of("paid", true));
        var params = new java.util.HashMap<String,String>();
        params.put("app_id", "test-app"); params.put("trade_status", "TRADE_SUCCESS"); params.put("out_trade_no", "SSSB123"); params.put("sign_type", "RSA2");
        String content = com.alipay.api.internal.util.AlipaySignature.getSignCheckContentV1(params);
        params.put("sign_type", "RSA2");
        params.put("sign", com.alipay.api.internal.util.AlipaySignature.rsaSign(content, priv, "UTF-8", "RSA2"));
        assertTrue(com.alipay.api.internal.util.AlipaySignature.rsaCheckV1(new java.util.HashMap<>(params), pub, "UTF-8", "RSA2"));
        assertTrue(notified.processNotification(params));
        verify(proxy).confirm(1L, 2L);
        params.put("out_trade_no", "tampered");
        assertFalse(notified.processNotification(params));
        verifyNoMoreInteractions(proxy);
    }

    @Test void productionDatabaseCannotUseSandbox() {
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("script_platform_dev");
        assertThrows(IllegalStateException.class, () -> service.create(1L, 2L));
        verifyNoInteractions(client);
        verify(orders, never()).payOrder(anyLong(), anyLong());
    }
    @Test void unconfirmedOrWrongAmountCannotAdvanceOrder() throws Exception {
        AlipayTradeQueryResponse response = mock(AlipayTradeQueryResponse.class);
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);
        when(response.isSuccess()).thenReturn(true);
        when(response.getTradeStatus()).thenReturn("WAIT_BUYER_PAY");
        assertEquals(false, service.confirm(1L, 2L).get("paid"));
        when(response.getTradeStatus()).thenReturn("TRADE_SUCCESS");
        when(response.getOutTradeNo()).thenReturn("SSSB123");
        when(response.getTradeNo()).thenReturn("alipay-trade");
        when(response.getTotalAmount()).thenReturn("12.01");
        assertThrows(IllegalStateException.class, () -> service.confirm(1L, 2L));
        verify(orders, never()).payOrder(anyLong(), anyLong());
    }
    @Test void confirmedPaymentAdvancesOnceAndRejectsOtherBuyer() throws Exception {
        AlipayTradeQueryResponse response = mock(AlipayTradeQueryResponse.class);
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);
        when(response.isSuccess()).thenReturn(true);
        when(response.getTradeStatus()).thenReturn("TRADE_SUCCESS");
        when(response.getOutTradeNo()).thenReturn("SSSB123");
        when(response.getTradeNo()).thenReturn("alipay-trade");
        when(response.getTotalAmount()).thenReturn("12.00");
        assertThrows(IllegalArgumentException.class, () -> service.confirm(1L, 3L));
        assertEquals(true, service.confirm(1L, 2L).get("paid"));
        verify(orders).payOrder(1L, 2L);
        when(jdbc.queryForList(anyString(), eq(1L))).thenReturn(List.of(Map.of("out_trade_no", "SSSB123", "buyer_id", 2L, "amount", new BigDecimal("12.00"), "status", "PAID")));
        assertEquals(true, service.confirm(1L, 2L).get("paid"));
        verify(orders, times(1)).payOrder(1L, 2L);
    }
}
