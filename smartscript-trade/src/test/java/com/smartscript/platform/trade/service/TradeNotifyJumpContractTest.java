package com.smartscript.platform.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.domain.SysDemandStage;
import com.smartscript.platform.trade.mapper.AdminNoticeMapper;
import com.smartscript.platform.user.service.UserNotificationAdminService;

/**
 * 交易通知的「点击落到哪一页」契约测试。
 *
 * <p>APP 端与 PC 端都按 businessType + businessId 直接拼跳转，不再从正文里猜单号，
 * 所以这两个字段的语义是接口契约而不是实现细节：
 * <b>businessId 必须是落地页的主键</b>。合同签署页按订单定位（{@code GET /trade/contracts/{orderId}}），
 * 因此 CONTRACT 类通知带的是 orderId 而不是 contractId；交割类通知带 orderId 而不是 deliveryId。
 * 这一组断言就是把这条口径钉住，防止以后又退回「主键同名但不同表」的错位。
 */
class TradeNotifyJumpContractTest
{
    private static final long ORDER_ID = 2002L;
    private static final long CONTRACT_ID = 1001L;
    private static final long DELIVERY_ID = 3030L;
    private static final long INQUIRY_ID = 4004L;
    private static final long SUBMISSION_ID = 5005L;
    private static final long DEMAND_ID = 56L;
    private static final long BUYER_ID = 9091L;
    private static final long CREATOR_ID = 9092L;
    private static final BigDecimal AMOUNT = new BigDecimal("1800.00");

    private UserNotificationAdminService notifications;
    private TradeNotifyService service;

    @BeforeEach
    void setUp()
    {
        notifications = mock(UserNotificationAdminService.class);
        service = new TradeNotifyService(notifications, mock(AdminNoticeMapper.class),
                mock(PlatformTransactionManager.class));
    }

    /** 只断言跳转两字段与收件人，正文另有内容测试。 */
    private void expectJump(String requestId, String businessType, String businessId,
            List<Long> receivers, String audience)
    {
        verify(notifications).create(eq(requestId), anyString(), anyString(), anyString(),
                eq(businessType), eq(businessId), eq(receivers), eq("system"), eq(audience));
    }

    @Test
    @DisplayName("对方已签署合同：CONTRACT 类通知带 orderId，APP 才能直落签署页")
    void contractSigned_carriesOrderIdNotContractId()
    {
        service.notifyContractSignedByParty(CONTRACT_ID, ORDER_ID, "ORD-2026-0001", "旧城剧本",
                AMOUNT, "exclusive", "张三", CREATOR_ID);

        expectJump("TRD-CTC-SGN-" + CONTRACT_ID, "CONTRACT", String.valueOf(ORDER_ID),
                List.of(CREATOR_ID), "BOTH");
    }

    @Test
    @DisplayName("卖方上传交割材料：ORDER 类通知带 orderId，直落订单详情的交割段")
    void deliveryUploaded_carriesOrderIdNotDeliveryId()
    {
        service.notifyDeliveryUploaded(ORDER_ID, DELIVERY_ID, "DLV-0007", "ORD-2026-0001",
                "旧城剧本", AMOUNT, "张三", BUYER_ID);

        expectJump("TRD-DLV-UP-" + DELIVERY_ID, "ORDER", String.valueOf(ORDER_ID),
                List.of(BUYER_ID), "BUYER");
    }

    @Test
    @DisplayName("投稿中标：中标即建询盘，通知落 INQUIRY 而不是 DEMAND")
    void submissionAccepted_jumpsToInquiry()
    {
        service.notifySubmissionAccepted(SUBMISSION_ID, DEMAND_ID, "征集：短剧脚本", "入围作品",
                INQUIRY_ID, "INQ-0009", CREATOR_ID);

        expectJump("TRD-DM-ACC-" + SUBMISSION_ID, "INQUIRY", String.valueOf(INQUIRY_ID),
                List.of(CREATOR_ID), "SELLER");
    }

    @Test
    @DisplayName("提现审核结果：WITHDRAW 落到提现列表，不带询盘/订单语义")
    void withdrawResult_jumpsToWithdrawList()
    {
        service.notifyWithdrawResult(77L, "WDR-0001", AMOUNT, true, null, CREATOR_ID);

        expectJump("TRD-WDR-77-OK", "WITHDRAW", "77", List.of(CREATOR_ID), "SELLER");
    }

    @Test
    @DisplayName("临近截止提醒：DEMAND + demandId，幂等键含档位与截止时刻")
    void dueSoonReminder_jumpAndIdempotencyKey()
    {
        SysDemand demand = new SysDemand();
        demand.setDemandId(DEMAND_ID);
        demand.setDemandNo("DM-2026-0056");
        demand.setTitle("征集：短剧脚本");
        demand.setAcceptedCreatorId(CREATOR_ID);

        Date dueAt = new Date(System.currentTimeMillis() + 2 * 3_600_000L);
        SysDemandStage stage = new SysDemandStage();
        stage.setStageId(80L);
        stage.setDemandId(DEMAND_ID);
        stage.setStageOrder(2);
        stage.setAmount(AMOUNT);
        stage.setDueAt(dueAt);

        service.notifyStageDueSoon(demand, stage, 2, 110);

        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(notifications).create(requestId.capture(), anyString(), anyString(),
                content.capture(), eq("DEMAND"), eq(String.valueOf(DEMAND_ID)),
                eq(List.of(CREATOR_ID)), eq("system"), eq("SELLER"));
        assertTrue(requestId.getValue().startsWith("TRD-DM-DUE-" + DEMAND_ID + "-2-2H-"),
                "档位要进幂等键：" + requestId.getValue());
        assertTrue(requestId.getValue().length() <= 64, "request_id 列上限 64");

        String body = content.getValue();
        // 「内容做完整」：任务、需求编号、轮次、金额、截止时刻、剩余时长缺一不可
        assertTrue(body.contains("征集：短剧脚本"), body);
        assertTrue(body.contains("DM-2026-0056"), body);
        assertTrue(body.contains("第 2 轮") || body.contains("第2轮"), body);
        assertTrue(body.contains("1800"), body);
        assertTrue(body.contains("剩余"), body);
        assertTrue(body.contains("任务详情"), body);
    }

    @Test
    @DisplayName("剩余不足 1 小时的提醒按分钟展示，不写成「0 小时」")
    void dueSoonReminder_showsMinutesUnderOneHour()
    {
        SysDemand demand = new SysDemand();
        demand.setDemandId(DEMAND_ID);
        demand.setAcceptedCreatorId(CREATOR_ID);
        SysDemandStage stage = new SysDemandStage();
        stage.setStageOrder(1);
        stage.setDueAt(new Date());

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        service.notifyStageDueSoon(demand, stage, 2, 45);

        verify(notifications).create(anyString(), anyString(), anyString(), content.capture(),
                anyString(), anyString(), any(), anyString(), anyString());
        assertEquals(true, content.getValue().contains("45分钟"), content.getValue());
    }
}
