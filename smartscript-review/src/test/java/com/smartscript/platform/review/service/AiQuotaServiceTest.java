package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.AiQuotaAccount;
import com.smartscript.platform.review.domain.AiQuotaRecord;
import com.smartscript.platform.review.mapper.AiQuotaAccountMapper;
import com.smartscript.platform.review.mapper.AiQuotaRecordMapper;
import com.smartscript.platform.review.mapper.AiRequestMapper;
import com.smartscript.platform.review.domain.PointsAccount;
import com.smartscript.platform.review.mapper.PointsAccountMapper;
import com.smartscript.platform.review.mapper.PointsRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * AI次数消耗/补偿业务单元测试
 * 覆盖：正常扣减/余额不足拒绝/无账户自动建/幂等防重复扣减/补偿/幂等防重复补偿/余额查询
 */
@ExtendWith(MockitoExtension.class)
class AiQuotaServiceTest {

    @Mock
    private AiQuotaAccountMapper aiQuotaAccountMapper;

    @Mock
    private AiQuotaRecordMapper aiQuotaRecordMapper;

    @Mock
    private AiRequestMapper aiRequestMapper;

    @Mock
    private PointsAccountMapper pointsAccountMapper;

    @Mock
    private PointsRecordMapper pointsRecordMapper;

    @InjectMocks
    private AiQuotaService aiQuotaService;

    private AiQuotaAccount account;

    @BeforeEach
    void setUp() {
        account = new AiQuotaAccount();
        account.setAccountId(1L);
        account.setUserId(106L);
        account.setAvailableQuota(BigDecimal.valueOf(50));
        account.setReservedQuota(BigDecimal.ZERO);
        account.setTotalEarned(BigDecimal.valueOf(50));
        account.setTotalConsumed(BigDecimal.ZERO);
        account.setTotalRefunded(BigDecimal.ZERO);
        account.setVersion(1L);
    }

    @Test
    void consume_正常扣减() {
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);
        when(aiQuotaAccountMapper.updateConsume(106L, BigDecimal.valueOf(1), 1L)).thenReturn(1);

        Map<String, Object> r = aiQuotaService.consume(106L, "writing", 1, 32L, "idem_consume_1");

        assertEquals(200, r.get("code"));
        assertEquals(49, r.get("balance"));
        assertEquals(1, r.get("consumed"));
        verify(aiQuotaRecordMapper, times(1)).insertAiQuotaRecord(any(AiQuotaRecord.class));
        verify(aiRequestMapper, times(1)).insertAiRequest(any());
    }

    @Test
    void consume_余额不足_拒绝() {
        account.setAvailableQuota(BigDecimal.valueOf(0));
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);

        Map<String, Object> r = aiQuotaService.consume(106L, "writing", 1, null, "idem_consume_2");

        assertEquals(400, r.get("code"));
        assertTrue(r.get("msg").toString().contains("不足"));
        verify(aiQuotaRecordMapper, never()).insertAiQuotaRecord(any());
    }

    @Test
    void consume_无账户_自动创建后拒绝() {
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(null);
        when(aiQuotaAccountMapper.insertAiQuotaAccount(any())).thenReturn(1);

        Map<String, Object> r = aiQuotaService.consume(106L, "outline", 1, null, "idem_consume_3");

        assertEquals(400, r.get("code"));
        verify(aiQuotaAccountMapper, times(1)).insertAiQuotaAccount(any());
    }

    @Test
    void consume_幂等键重复_不重复扣减() {
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);
        doThrow(new DuplicateKeyException("uk_idempotency_key")).when(aiQuotaRecordMapper).insertAiQuotaRecord(any());

        Map<String, Object> r = aiQuotaService.consume(106L, "writing", 1, 32L, "idem_same_key");

        assertEquals(200, r.get("code"));
        assertTrue(r.get("msg").toString().contains("已处理"));
        verify(aiQuotaAccountMapper, never()).updateConsume(anyLong(), any(), anyLong());
    }

    @Test
    void refund_正常回补() {
        account.setAvailableQuota(BigDecimal.valueOf(49));
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);
        when(aiQuotaAccountMapper.updateRefund(106L, BigDecimal.valueOf(1), 1L)).thenReturn(1);

        Map<String, Object> r = aiQuotaService.refund(106L, 1, 32L, "idem_refund_1");

        assertEquals(200, r.get("code"));
        assertEquals(50, r.get("balance"));
        assertEquals(1, r.get("refunded"));
    }

    @Test
    void refund_幂等键重复_不重复回补() {
        account.setAvailableQuota(BigDecimal.valueOf(49));
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);
        doThrow(new DuplicateKeyException("uk_idempotency_key")).when(aiQuotaRecordMapper).insertAiQuotaRecord(any());

        Map<String, Object> r = aiQuotaService.refund(106L, 1, 32L, "idem_same_refund");

        assertEquals(200, r.get("code"));
        assertTrue(r.get("msg").toString().contains("已补偿"));
        verify(aiQuotaAccountMapper, never()).updateRefund(anyLong(), any(), anyLong());
    }

    @Test
    void balance_返回余额与统计() {
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);
        when(aiQuotaRecordMapper.selectAiQuotaRecordList(any())).thenReturn(java.util.Collections.emptyList());

        Map<String, Object> r = aiQuotaService.balance(106L);

        assertEquals(200, r.get("code"));
        assertEquals(50, r.get("availableQuota"));
        assertEquals(50, r.get("totalEarned"));
    }

    @Test
    void consume_参数缺失_400() {
        assertEquals(400, aiQuotaService.consume(null, "writing", 1, null, "key").get("code"));
        assertEquals(400, aiQuotaService.consume(106L, "writing", 0, null, "key").get("code"));
        assertEquals(400, aiQuotaService.consume(106L, "writing", 1, null, "").get("code"));
    }

    /* ============ 阶段2：积分兑换AI次数 ============ */

    private PointsAccount pointsAccount() {
        PointsAccount pa = new PointsAccount();
        pa.setAccountId(1L);
        pa.setUserId(106L);
        pa.setBalance(100);
        pa.setTotalEarned(100);
        pa.setTotalSpent(0);
        return pa;
    }

    @Test
    void exchange_正常兑换() {
        when(pointsAccountMapper.selectByUserId(106L)).thenReturn(pointsAccount());
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);
        when(pointsAccountMapper.decreaseBalance(106L, 20)).thenReturn(1);
        when(aiQuotaAccountMapper.updateEarn(106L, BigDecimal.valueOf(2), 1L)).thenReturn(1);

        Map<String, Object> r = aiQuotaService.exchange(106L, 20, "idem_ex_1");

        assertEquals(200, r.get("code"));
        assertEquals(2, r.get("quotaEarned"));
        assertEquals(20, r.get("pointsSpent"));
        assertEquals(80, r.get("pointsBalance"));
        assertEquals(52, r.get("quotaBalance"));
        assertEquals(10, r.get("rate"));
        verify(pointsRecordMapper, times(1)).insertPointsRecord(any());
    }

    @Test
    void exchange_积分不足_拒绝() {
        PointsAccount pa = pointsAccount();
        pa.setBalance(5);
        when(pointsAccountMapper.selectByUserId(106L)).thenReturn(pa);

        Map<String, Object> r = aiQuotaService.exchange(106L, 20, "idem_ex_2");

        assertEquals(400, r.get("code"));
        assertTrue(r.get("msg").toString().contains("积分不足"));
        verify(pointsAccountMapper, never()).decreaseBalance(anyLong(), anyInt());
    }

    @Test
    void exchange_积分不够最低兑换比例_拒绝() {
        Map<String, Object> r = aiQuotaService.exchange(106L, 9, "idem_ex_3");

        assertEquals(400, r.get("code"));
        verify(pointsAccountMapper, never()).decreaseBalance(anyLong(), anyInt());
    }

    @Test
    void exchange_幂等键重复_不重复兑换() {
        when(pointsAccountMapper.selectByUserId(106L)).thenReturn(pointsAccount());
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);
        doThrow(new DuplicateKeyException("uk_idempotency_key")).when(aiQuotaRecordMapper).insertAiQuotaRecord(any());

        Map<String, Object> r = aiQuotaService.exchange(106L, 20, "idem_same_ex");

        assertEquals(200, r.get("code"));
        assertTrue(r.get("msg").toString().contains("已兑换"));
        verify(pointsAccountMapper, never()).decreaseBalance(anyLong(), anyInt());
    }

    @Test
    void exchange_无AI账户_自动创建后兑换() {
        when(pointsAccountMapper.selectByUserId(106L)).thenReturn(pointsAccount());
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(null);
        when(aiQuotaAccountMapper.insertAiQuotaAccount(any())).thenReturn(1);
        when(pointsAccountMapper.decreaseBalance(106L, 20)).thenReturn(1);
        when(aiQuotaAccountMapper.updateEarn(106L, BigDecimal.valueOf(2), 0L)).thenReturn(1);

        Map<String, Object> r = aiQuotaService.exchange(106L, 20, "idem_ex_4");

        assertEquals(200, r.get("code"));
        assertEquals(2, r.get("quotaEarned"));
        verify(aiQuotaAccountMapper, times(1)).insertAiQuotaAccount(any());
    }

    /* ============ 预留锁定业务：reserve / confirm / release ============ */

    @Test
    void reserve_正常锁定() {
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);
        when(aiQuotaAccountMapper.updateReserve(106L, BigDecimal.valueOf(1), 1L)).thenReturn(1);

        Map<String, Object> r = aiQuotaService.reserve(106L, "writing", 1, 32L, "idem_rsv_1");

        assertEquals(200, r.get("code"));
        assertEquals(49, r.get("availableQuota"));
        assertEquals(1, r.get("reserved"));
        verify(aiQuotaRecordMapper, times(1)).insertAiQuotaRecord(any(AiQuotaRecord.class));
        verify(aiQuotaAccountMapper, times(1)).updateReserve(anyLong(), any(), anyLong());
    }

    @Test
    void reserve_余额不足_拒绝() {
        account.setAvailableQuota(BigDecimal.valueOf(0));
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);

        Map<String, Object> r = aiQuotaService.reserve(106L, "writing", 1, null, "idem_rsv_2");

        assertEquals(400, r.get("code"));
        assertTrue(r.get("msg").toString().contains("不足"));
        verify(aiQuotaAccountMapper, never()).updateReserve(anyLong(), any(), anyLong());
    }

    @Test
    void reserve_无账户_404() {
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(null);

        Map<String, Object> r = aiQuotaService.reserve(106L, "writing", 1, null, "idem_rsv_3");

        assertEquals(404, r.get("code"));
        verify(aiQuotaAccountMapper, never()).updateReserve(anyLong(), any(), anyLong());
    }

    @Test
    void reserve_幂等键重复_不重复锁定() {
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);
        doThrow(new DuplicateKeyException("uk_idempotency_key")).when(aiQuotaRecordMapper).insertAiQuotaRecord(any());

        Map<String, Object> r = aiQuotaService.reserve(106L, "writing", 1, 32L, "idem_same_rsv");

        assertEquals(200, r.get("code"));
        assertTrue(r.get("msg").toString().contains("已锁定"));
        verify(aiQuotaAccountMapper, never()).updateReserve(anyLong(), any(), anyLong());
    }

    @Test
    void confirm_正常确认消耗() {
        account.setReservedQuota(BigDecimal.valueOf(1));
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);
        when(aiQuotaAccountMapper.updateConfirm(106L, BigDecimal.valueOf(1), 1L)).thenReturn(1);

        Map<String, Object> r = aiQuotaService.confirm(106L, 1, 32L, "idem_cfm_1");

        assertEquals(200, r.get("code"));
        assertEquals(0, r.get("reservedQuota"));
        assertEquals(1, r.get("totalConsumed"));
        verify(aiQuotaAccountMapper, times(1)).updateConfirm(anyLong(), any(), anyLong());
    }

    @Test
    void confirm_预留不足_拒绝() {
        account.setReservedQuota(BigDecimal.ZERO);
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);

        Map<String, Object> r = aiQuotaService.confirm(106L, 1, 32L, "idem_cfm_2");

        assertEquals(400, r.get("code"));
        assertTrue(r.get("msg").toString().contains("预留"));
        verify(aiQuotaAccountMapper, never()).updateConfirm(anyLong(), any(), anyLong());
    }

    @Test
    void confirm_幂等键重复_不重复确认() {
        account.setReservedQuota(BigDecimal.valueOf(1));
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);
        doThrow(new DuplicateKeyException("uk_idempotency_key")).when(aiQuotaRecordMapper).insertAiQuotaRecord(any());

        Map<String, Object> r = aiQuotaService.confirm(106L, 1, 32L, "idem_same_cfm");

        assertEquals(200, r.get("code"));
        assertTrue(r.get("msg").toString().contains("已确认"));
        verify(aiQuotaAccountMapper, never()).updateConfirm(anyLong(), any(), anyLong());
    }

    @Test
    void release_正常释放回补() {
        account.setReservedQuota(BigDecimal.valueOf(1));
        account.setAvailableQuota(BigDecimal.valueOf(49));
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);
        when(aiQuotaAccountMapper.updateRelease(106L, BigDecimal.valueOf(1), 1L)).thenReturn(1);

        Map<String, Object> r = aiQuotaService.release(106L, 1, 32L, "idem_rls_1");

        assertEquals(200, r.get("code"));
        assertEquals(50, r.get("availableQuota"));
        assertEquals(0, r.get("reservedQuota"));
        assertEquals(1, r.get("refunded"));
        verify(aiQuotaAccountMapper, times(1)).updateRelease(anyLong(), any(), anyLong());
    }

    @Test
    void release_预留不足_拒绝() {
        account.setReservedQuota(BigDecimal.ZERO);
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);

        Map<String, Object> r = aiQuotaService.release(106L, 1, 32L, "idem_rls_2");

        assertEquals(400, r.get("code"));
        assertTrue(r.get("msg").toString().contains("预留"));
        verify(aiQuotaAccountMapper, never()).updateRelease(anyLong(), any(), anyLong());
    }

    @Test
    void release_幂等键重复_不重复释放() {
        account.setReservedQuota(BigDecimal.valueOf(1));
        when(aiQuotaAccountMapper.selectAiQuotaAccountByUserId(106L)).thenReturn(account);
        doThrow(new DuplicateKeyException("uk_idempotency_key")).when(aiQuotaRecordMapper).insertAiQuotaRecord(any());

        Map<String, Object> r = aiQuotaService.release(106L, 1, 32L, "idem_same_rls");

        assertEquals(200, r.get("code"));
        assertTrue(r.get("msg").toString().contains("已释放"));
        verify(aiQuotaAccountMapper, never()).updateRelease(anyLong(), any(), anyLong());
    }

    @Test
    void reserveConfirmRelease_参数缺失_400() {
        assertEquals(400, aiQuotaService.reserve(null, "writing", 1, null, "k").get("code"));
        assertEquals(400, aiQuotaService.reserve(106L, "writing", 0, null, "k").get("code"));
        assertEquals(400, aiQuotaService.confirm(106L, 0, null, "k").get("code"));
        assertEquals(400, aiQuotaService.confirm(106L, 1, null, "").get("code"));
        assertEquals(400, aiQuotaService.release(null, 1, null, "k").get("code"));
        assertEquals(400, aiQuotaService.release(106L, 0, null, "k").get("code"));
    }
}
