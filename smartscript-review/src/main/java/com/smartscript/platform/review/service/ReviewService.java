package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.ReviewRecord;
import com.smartscript.platform.review.mapper.ReviewRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 审核记录Service业务层处理
 *
 * @author smartscript
 */
@Service
public class ReviewService {

    @Autowired
    private ReviewRecordMapper reviewRecordMapper;

    /**
     * 查询审核记录列表
     */
    public List<ReviewRecord> selectReviewRecordList(ReviewRecord reviewRecord) {
        return reviewRecordMapper.selectReviewRecordList(reviewRecord);
    }

    /**
     * 查询审核记录详情
     */
    public ReviewRecord selectReviewRecordById(Long reviewId) {
        return reviewRecordMapper.selectReviewRecordById(reviewId);
    }

    /**
     * 新增审核记录
     */
    public int insertReviewRecord(ReviewRecord reviewRecord) {
        return reviewRecordMapper.insertReviewRecord(reviewRecord);
    }

    /**
     * 修改审核记录
     */
    public int updateReviewRecord(ReviewRecord reviewRecord) {
        return reviewRecordMapper.updateReviewRecord(reviewRecord);
    }

    /**
     * 删除审核记录
     */
    public int deleteReviewRecordById(Long reviewId) {
        return reviewRecordMapper.deleteReviewRecordById(reviewId);
    }

    /**
     * 审核操作（通过/驳回/发回修改）
     */
    public int operateReview(Long reviewId, String status, String reviewOpinion, Long reviewerId) {
        ReviewRecord reviewRecord = new ReviewRecord();
        reviewRecord.setReviewId(reviewId);
        reviewRecord.setStatus(status);
        reviewRecord.setReviewOpinion(reviewOpinion);
        reviewRecord.setReviewerId(reviewerId);
        return reviewRecordMapper.updateReviewRecord(reviewRecord);
    }

    /**
     * 批量分配审核任务
     */
    public int batchAssign(List<Long> reviewIds, Long reviewerId) {
        int count = 0;
        for (Long reviewId : reviewIds) {
            ReviewRecord reviewRecord = new ReviewRecord();
            reviewRecord.setReviewId(reviewId);
            reviewRecord.setReviewerId(reviewerId);
            reviewRecord.setStatus("pending_review");
            count += reviewRecordMapper.updateReviewRecord(reviewRecord);
        }
        return count;
    }

    /**
     * 查询审核日志
     */
    public List<java.util.Map<String, Object>> selectReviewLogs(Long reviewId) {
        // 临时返回模拟数据，后续建表后改为数据库查询
        java.util.List<java.util.Map<String, Object>> logs = new java.util.ArrayList<>();
        java.util.Map<String, Object> log1 = new java.util.HashMap<>();
        log1.put("logId", 1);
        log1.put("reviewId", reviewId);
        log1.put("operator", "管理员");
        log1.put("action", "提交审核");
        log1.put("beforeStatus", "draft");
        log1.put("afterStatus", "pending_review");
        log1.put("createTime", "2026-09-29 10:00:00");
        logs.add(log1);
        return logs;
    }
}
