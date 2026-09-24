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
}
