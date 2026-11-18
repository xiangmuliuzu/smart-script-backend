package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.ReviewLog;
import com.smartscript.platform.review.domain.ReviewRecord;
import com.smartscript.platform.review.mapper.ReviewLogMapper;
import com.smartscript.platform.review.mapper.ReviewRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 审核状态机单元测试
 * 覆盖：合法流转/非法跳跃拒绝/终态拒绝/发回后重新提交
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRecordMapper reviewRecordMapper;

    @Mock
    private ReviewLogMapper reviewLogMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
    void operate_work_approved_只发审核通过通知_不自动上架() {
        ReviewRecord pending = record("pending");
        pending.setTargetType("work");
        pending.setTargetId(301L);
        pending.setUserId(88L);
        pending.setWorkTitle("悬疑故事");
        pending.setAuthorName("作者甲");
        when(reviewRecordMapper.selectReviewRecordById(1L)).thenReturn(pending);
        when(reviewRecordMapper.updateReviewRecord(any())).thenReturn(1);

        reviewService.operateReview(1L, "approved", "内容合格", 1L, "管理员");

        // 审核通过只改审核结果：不自动上架，也不广播「作品发布」。
        // 上架由作者在作品详情手动点击触发，发布事件在上下架接口里发。
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertTrue(captor.getValue() instanceof ReviewApprovedEvent);
        assertEquals(301L, ((ReviewApprovedEvent) captor.getValue()).getWorkId());
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
}
