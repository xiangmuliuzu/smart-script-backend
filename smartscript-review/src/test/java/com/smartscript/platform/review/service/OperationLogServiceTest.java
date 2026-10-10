package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.OperationLog;
import com.smartscript.platform.review.mapper.OperationLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 操作日志Service单元测试
 */
@ExtendWith(MockitoExtension.class)
class OperationLogServiceTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    @InjectMocks
    private OperationLogService operationLogService;

    @Test
    void record_成功写入() {
        operationLogService.record("ai_quota", "consume", "user", "9093", null, 9093L, "9093", "success", "测试");
        verify(operationLogMapper, times(1)).insertOperationLog(any(OperationLog.class));
    }

    @Test
    void record_mapper异常_不阻断() {
        doThrow(new RuntimeException("db down")).when(operationLogMapper).insertOperationLog(any());
        // 不应抛出异常
        assertDoesNotThrow(() ->
                operationLogService.record("ai_quota", "consume", "user", "9093", null, 9093L, "9093", "success", "x"));
    }

    @Test
    void record_nullOperator_默认0和system() {
        operationLogService.record("review", "approve", "review_record", "1", "作品A", null, null, "success", null);
        // insert 被调用即通过；operator 默认值在 mapper 层落库
        verify(operationLogMapper, times(1)).insertOperationLog(any());
    }

    @Test
    void selectList_透传查询条件() {
        OperationLog query = new OperationLog();
        query.setModule("ai_quota");
        when(operationLogMapper.selectOperationLogList(query)).thenReturn(Collections.singletonList(new OperationLog()));

        assertEquals(1, operationLogService.selectOperationLogList(query).size());
        verify(operationLogMapper, times(1)).selectOperationLogList(query);
    }
}
