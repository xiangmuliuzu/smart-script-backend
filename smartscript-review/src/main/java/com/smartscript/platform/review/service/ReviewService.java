package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.ReviewLog;
import com.smartscript.platform.review.domain.ReviewRecord;
import com.smartscript.platform.review.mapper.ReviewLogMapper;
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

    @Autowired
    private ReviewLogMapper reviewLogMapper;

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
        ReviewRecord before = reviewRecordMapper.selectReviewRecordById(reviewId);
        ReviewRecord reviewRecord = new ReviewRecord();
        reviewRecord.setReviewId(reviewId);
        reviewRecord.setStatus(status);
        reviewRecord.setReviewResult(status);
        reviewRecord.setReviewOpinion(reviewOpinion);
        reviewRecord.setReviewerId(reviewerId);
        int cnt = reviewRecordMapper.updateReviewRecord(reviewRecord);
        // 写审核操作日志（真实落库）
        ReviewLog log = new ReviewLog();
        log.setReviewId(reviewId);
        log.setAction("operate");
        log.setOperatorId(reviewerId);
        log.setBeforeStatus(before != null ? before.getStatus() : null);
        log.setAfterStatus(status);
        log.setReviewOpinion(reviewOpinion);
        reviewLogMapper.insertReviewLog(log);
        return cnt;
    }

    /**
     * 批量分配审核任务
     */
    public int batchAssign(List<Long> reviewIds, Long reviewerId) {
        int count = 0;
        for (Long reviewId : reviewIds) {
            ReviewRecord before = reviewRecordMapper.selectReviewRecordById(reviewId);
            ReviewRecord reviewRecord = new ReviewRecord();
            reviewRecord.setReviewId(reviewId);
            reviewRecord.setReviewerId(reviewerId);
            reviewRecord.setStatus("pending_review");
            count += reviewRecordMapper.updateReviewRecord(reviewRecord);
            // 写分配日志
            ReviewLog log = new ReviewLog();
            log.setReviewId(reviewId);
            log.setAction("assign");
            log.setOperatorId(reviewerId);
            log.setBeforeStatus(before != null ? before.getStatus() : null);
            log.setAfterStatus("pending_review");
            reviewLogMapper.insertReviewLog(log);
        }
        return count;
    }

    /**
     * 查询审核日志（真实读库）
     */
    public List<ReviewLog> selectReviewLogs(Long reviewId) {
        ReviewLog query = new ReviewLog();
        query.setReviewId(reviewId);
        return reviewLogMapper.selectReviewLogList(query);
    }

    /**
     * 审核统计（真实按状态分组）
     */
    public java.util.Map<String, Object> selectReviewStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        int pending = 0, aiReviewing = 0, approved = 0, rejected = 0, revision = 0;
        for (java.util.Map<String, Object> row : reviewRecordMapper.selectStatusGroup()) {
            String status = String.valueOf(row.get("status"));
            int cnt = ((Number) row.get("cnt")).intValue();
            if ("pending".equals(status) || "pending_review".equals(status)) pending += cnt;
            else if ("ai_reviewing".equals(status)) aiReviewing += cnt;
            else if ("approved".equals(status)) approved += cnt;
            else if ("rejected".equals(status)) rejected += cnt;
            else if ("revision".equals(status)) revision += cnt;
        }
        stats.put("pending", pending);
        stats.put("aiReviewing", aiReviewing);
        stats.put("approved", approved);
        stats.put("rejected", rejected);
        stats.put("revision", revision);
        return stats;
    }
}
