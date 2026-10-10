package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.ReviewLog;
import com.smartscript.platform.review.domain.ReviewRecord;
import com.smartscript.platform.review.mapper.ReviewLogMapper;
import com.smartscript.platform.review.mapper.ReviewRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 审核状态机单元测试
 * 覆盖：合法流转/非法跳跃拒绝/终态拒绝/发回后重新提交/批量分配校验
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRecordMapper reviewRecordMapper;

    @Mock
    private ReviewLogMapper reviewLogMapper;

    @InjectMocks
    private ReviewService reviewService;

    private ReviewRecord record(String status) {
        ReviewRecord r = new ReviewRecord();
        r.setReviewId(1L);
        r.setStatus(status);
        return r;
    }

    @Test
    void operate_pending_approved_合法() {
        when(reviewRecordMapper.selectReviewRecordById(1L)).thenReturn(record("pending"));
        when(reviewRecordMapper.updateReviewRecord(any())).thenReturn(1);

        Map<String, Object> r = reviewService.operateReview(1L, "approved", "内容合格", 1L, "管理员");

        assertEquals(200, r.get("code"));
        assertEquals("pending", r.get("from"));
        assertEquals("approved", r.get("to"));
        verify(reviewLogMapper, times(1)).insertReviewLog(any(ReviewLog.class));
    }

    @Test
    void operate_pending_aiReviewing_非法跳跃() {
        when(reviewRecordMapper.selectReviewRecordById(1L)).thenReturn(record("pending"));

        Map<String, Object> r = reviewService.operateReview(1L, "ai_reviewing", null, 1L, "管理员");

        assertEquals(400, r.get("code"));
        assertTrue(r.get("msg").toString().contains("不允许跳跃"));
        verify(reviewRecordMapper, never()).updateReviewRecord(any());
        verify(reviewLogMapper, never()).insertReviewLog(any());
    }

    @Test
    void operate_approved_再驳回_终态拒绝() {
        when(reviewRecordMapper.selectReviewRecordById(1L)).thenReturn(record("approved"));

        Map<String, Object> r = reviewService.operateReview(1L, "rejected", null, 1L, "管理员");

        assertEquals(400, r.get("code"));
        verify(reviewRecordMapper, never()).updateReviewRecord(any());
    }

    @Test
    void operate_revision_重新提交_合法() {
        when(reviewRecordMapper.selectReviewRecordById(1L)).thenReturn(record("revision"));
        when(reviewRecordMapper.updateReviewRecord(any())).thenReturn(1);

        Map<String, Object> r = reviewService.operateReview(1L, "ai_reviewing", "已修改，重新初筛", 1L, "管理员");

        assertEquals(200, r.get("code"));
        assertEquals("revision", r.get("from"));
        assertEquals("ai_reviewing", r.get("to"));
    }

    @Test
    void operate_记录不存在_404() {
        when(reviewRecordMapper.selectReviewRecordById(1L)).thenReturn(null);

        Map<String, Object> r = reviewService.operateReview(1L, "approved", null, 1L, "管理员");

        assertEquals(404, r.get("code"));
    }

    @Test
    void batchAssign_pending_可分配() {
        when(reviewRecordMapper.selectReviewRecordById(1L)).thenReturn(record("pending"));
        when(reviewRecordMapper.updateReviewRecord(any())).thenReturn(1);

        int cnt = reviewService.batchAssign(Collections.singletonList(1L), 5L, "管理员");

        assertEquals(1, cnt);
        verify(reviewLogMapper, times(1)).insertReviewLog(any());
    }

    @Test
    void batchAssign_approved_不可分配() {
        when(reviewRecordMapper.selectReviewRecordById(1L)).thenReturn(record("approved"));

        int cnt = reviewService.batchAssign(Collections.singletonList(1L), 5L, "管理员");

        assertEquals(0, cnt);
        verify(reviewRecordMapper, never()).updateReviewRecord(any());
    }

    @Test
    void batchAssign_多记录_混合() {
        when(reviewRecordMapper.selectReviewRecordById(1L)).thenReturn(record("pending"));
        when(reviewRecordMapper.selectReviewRecordById(2L)).thenReturn(record("approved"));
        when(reviewRecordMapper.selectReviewRecordById(3L)).thenReturn(record("ai_reviewing"));
        when(reviewRecordMapper.updateReviewRecord(any())).thenReturn(1);

        int cnt = reviewService.batchAssign(Arrays.asList(1L, 2L, 3L), 5L, "管理员");

        assertEquals(2, cnt);
    }
}
