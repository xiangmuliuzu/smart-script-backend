package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.ReviewLog;
import com.smartscript.platform.review.domain.ReviewRecord;
import com.smartscript.platform.review.mapper.ReviewLogMapper;
import com.smartscript.platform.review.mapper.ReviewRecordMapper;
import com.smartscript.platform.review.mapper.OperationLogMapper;
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

    @Autowired
    private OperationLogMapper operationLogMapper;

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
     * 审核状态机：合法流转表（状态不能跳跃）。
     * ai_reviewing(AI初筛中) -> pending(待人工) / revision(发回修改) / rejected(驳回)
     * pending / pending_review -> approved(通过) / rejected(驳回) / revision(发回修改)
     * revision(发回修改) -> ai_reviewing(重新初筛) / pending
     * rejected(驳回) -> ai_reviewing(重新提交重审)
     * approved(通过) -> 终态，不可再流转
     */
    private static final java.util.Map<String, java.util.List<String>> STATUS_FLOW = new java.util.HashMap<>();
    static {
        STATUS_FLOW.put("ai_reviewing", java.util.Arrays.asList("pending", "revision", "rejected"));
        STATUS_FLOW.put("pending", java.util.Arrays.asList("approved", "rejected", "revision"));
        STATUS_FLOW.put("pending_review", java.util.Arrays.asList("approved", "rejected", "revision"));
        STATUS_FLOW.put("revision", java.util.Arrays.asList("ai_reviewing", "pending"));
        STATUS_FLOW.put("rejected", java.util.Arrays.asList("ai_reviewing"));
        STATUS_FLOW.put("approved", java.util.Collections.emptyList());
    }

    /**
     * 审核操作（通过/驳回/发回修改），带状态机校验，非法流转直接拒绝。
     */
    public java.util.Map<String, Object> operateReview(Long reviewId, String status, String reviewOpinion, Long reviewerId, String operatorName) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        ReviewRecord before = reviewRecordMapper.selectReviewRecordById(reviewId);
        if (before == null) {
            result.put("code", 404);
            result.put("msg", "审核记录不存在: " + reviewId);
            return result;
        }
        String from = before.getStatus();
        java.util.List<String> allowed = STATUS_FLOW.get(from);
        if (allowed == null || !allowed.contains(status)) {
            result.put("code", 400);
            result.put("msg", "非法状态流转: " + from + " -> " + status + "，审核状态不允许跳跃");
            return result;
        }
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
        log.setAction(actionOf(status));
        log.setOperatorId(reviewerId);
        log.setOperatorName(operatorName);
        log.setBeforeStatus(from);
        log.setAfterStatus(status);
        log.setReviewOpinion(reviewOpinion);
        reviewLogMapper.insertReviewLog(log);
        logOp(actionOf(status), reviewId, reviewerId, operatorName, from, status, reviewOpinion);
        result.put("code", 200);
        result.put("msg", "审核操作成功");
        result.put("from", from);
        result.put("to", status);
        result.put("cnt", cnt);
        return result;
    }

    private String actionOf(String status) {
        switch (status == null ? "" : status) {
            case "approved": return "approve";
            case "rejected": return "reject";
            case "revision": return "revision";
            case "ai_reviewing": return "ai_review";
            case "pending": return "pending";
            case "pending_review": return "assign";
            default: return "operate";
        }
    }

    /**
     * 批量分配审核任务（仅待分配/初审中可分配，终态不可）
     */
    public int batchAssign(List<Long> reviewIds, Long reviewerId, String operatorName) {
        int count = 0;
        for (Long reviewId : reviewIds) {
            ReviewRecord before = reviewRecordMapper.selectReviewRecordById(reviewId);
            if (before == null) {
                continue;
            }
            String from = before.getStatus();
            if (!"pending".equals(from) && !"ai_reviewing".equals(from)) {
                continue; // 终态或已分配/进行中的不重复分配
            }
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
            log.setOperatorName(operatorName);
            log.setBeforeStatus(from);
            log.setAfterStatus("pending_review");
            reviewLogMapper.insertReviewLog(log);
            logOp("assign", reviewId, reviewerId, operatorName, from, "pending_review", null);
        }
        return count;
    }

    private void logOp(String operation, Long reviewId, Long operatorId, String operatorName,
                       String from, String to, String opinion) {
        try {
            com.smartscript.platform.review.domain.OperationLog log = new com.smartscript.platform.review.domain.OperationLog();
            log.setModule("review");
            log.setOperation(operation);
            log.setTargetType("review_record");
            log.setTargetId(String.valueOf(reviewId));
            log.setOperatorId(operatorId == null ? 0L : operatorId);
            log.setOperatorName(operatorName == null ? "system" : operatorName);
            log.setStatus("success");
            log.setRemark("审核流转: " + from + " -> " + to + (opinion == null ? "" : ("，意见: " + opinion)));
            operationLogMapper.insertOperationLog(log);
        } catch (Exception e) {
            // 埋点失败不阻断审核
        }
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
    public java.util.Map<String, Object> selectReviewStatistics(ReviewRecord reviewRecord) {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        int pending = 0, aiReviewing = 0, approved = 0, rejected = 0, revision = 0;
        for (java.util.Map<String, Object> row : reviewRecordMapper.selectStatusGroup(reviewRecord)) {
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

    /**
     * 可分配审核员列表
     */
    public java.util.List<java.util.Map<String, Object>> selectReviewers() {
        return reviewRecordMapper.selectReviewers();
    }
}
