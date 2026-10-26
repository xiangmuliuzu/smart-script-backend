package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.ReviewLog;
import com.smartscript.platform.review.domain.ReviewRecord;
import com.smartscript.platform.review.domain.vo.WorkChapterVO;
import com.smartscript.platform.review.domain.vo.WorkContentVO;
import com.smartscript.platform.review.mapper.ReviewLogMapper;
import com.smartscript.platform.review.mapper.ReviewRecordMapper;
import com.smartscript.platform.review.mapper.ReviewWorkContentMapper;
import com.smartscript.platform.review.mapper.OperationLogMapper;
import com.smartscript.platform.review.util.ReviewContentText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Autowired
    private ReviewWorkContentMapper reviewWorkContentMapper;

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
     * 查询审核作品完整内容（人工审核用）：简介 + 各章节正文（纯文本）。
     * 同时返回 AI 审核结果（aiInfo）与章节级问题定位（issues：命中词 + 上下文片段），
     * 供审核工作台「详情」只展示有问题的地方（问题片段红字 + 章节后驳回原因）。
     * @return 内容数据；审核记录不存在或非作品类型时返回 null
     */
    public java.util.Map<String, Object> selectReviewContent(Long reviewId) {
        ReviewRecord record = reviewRecordMapper.selectReviewRecordById(reviewId);
        if (record == null || record.getTargetId() == null) {
            return null;
        }
        Long workId = record.getTargetId();
        WorkContentVO work = reviewWorkContentMapper.selectWorkContent(workId);
        List<WorkChapterVO> chapters = reviewWorkContentMapper.selectWorkChapters(workId);
        for (WorkChapterVO ch : chapters) {
            ch.setContent(ReviewContentText.plain(ch.getContent()));
        }
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("reviewId", reviewId);
        data.put("workId", workId);
        data.put("work", work);
        data.put("chapters", chapters);

        // AI 审核结果（优先从 ai_result JSON 解析；ai_score 列可能未被写入）
        java.util.Map<String, Object> aiInfo = new java.util.HashMap<>();
        aiInfo.put("score", record.getAiScore());
        aiInfo.put("level", record.getAiRiskLevel());
        java.util.List<String> keywords = new java.util.ArrayList<>();
        if (record.getAiResult() != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(record.getAiResult());
                if (node.path("score").isNumber()) {
                    aiInfo.put("score", node.path("score").asInt());
                }
                if (node.path("level").isTextual() && !node.path("level").asText().isBlank()) {
                    aiInfo.put("level", node.path("level").asText());
                }
                if (node.path("keywords").isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode k : node.path("keywords")) {
                        String s = k.asText("").trim();
                        if (!s.isEmpty()) {
                            keywords.add(s);
                        }
                    }
                }
            } catch (Exception ignored) {
                // ai_result 非 JSON（历史脏数据）时忽略，仅用 ai_sensitive_words 定位
            }
        }
        // 补充 AI 命中的敏感词列（逗号/顿号/分号/竖线分隔）
        if (record.getAiSensitiveWords() != null) {
            for (String s : record.getAiSensitiveWords().split("[,，;；|]+")) {
                String w = s.trim();
                if (!w.isEmpty() && !keywords.contains(w)) {
                    keywords.add(w);
                }
            }
        }
        aiInfo.put("keywords", new java.util.ArrayList<>(keywords));

        // 逐章节定位命中词 → 问题片段（只输出有问题章节）
        java.util.List<java.util.Map<String, Object>> issues = new java.util.ArrayList<>();
        for (WorkChapterVO ch : chapters) {
            String text = ch.getContent() == null ? "" : ch.getContent();
            java.util.List<java.util.Map<String, Object>> hits = hitsOf(text, keywords);
            if (!hits.isEmpty()) {
                java.util.Map<String, Object> issue = new java.util.HashMap<>();
                issue.put("chapterId", ch.getChapterId());
                issue.put("chapterNo", ch.getChapterNo());
                issue.put("chapterTitle", ch.getChapterTitle());
                issue.put("hits", hits);
                issues.add(issue);
            }
        }
        // 简介中的命中词也作为「简介」问题项输出
        String summary = work == null ? null : work.getSummary();
        if (summary != null && !summary.isEmpty()) {
            java.util.List<java.util.Map<String, Object>> hits = hitsOf(summary, keywords);
            if (!hits.isEmpty()) {
                java.util.Map<String, Object> issue = new java.util.HashMap<>();
                issue.put("chapterId", null);
                issue.put("chapterNo", 0);
                issue.put("chapterTitle", "简介");
                issue.put("hits", hits);
                issues.add(0, issue);
            }
        }
        data.put("aiInfo", aiInfo);
        data.put("issues", issues);
        return data;
    }

    /** 在文本中查找所有命中词及其上下文片段。整词未命中时按标点/符号拆词再查（如
     *  AI 给出的语义化关键词"疑似《斗破苍穹》原文，版权风险"可拆出"斗破苍穹"等原文词），
     *  拆词命中项 word 记录实际命中的词，便于前端红字定位。 */
    private java.util.List<java.util.Map<String, Object>> hitsOf(String text, java.util.List<String> keywords) {
        java.util.List<java.util.Map<String, Object>> hits = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            return hits;
        }
        for (String kw : keywords) {
            int idx = text.indexOf(kw);
            if (idx >= 0) {
                java.util.Map<String, Object> hit = new java.util.HashMap<>();
                hit.put("word", kw);
                hit.put("snippet", snippetAround(text, idx, kw.length(), 30));
                hits.add(hit);
                continue;
            }
            // 整词未命中：拆词兜底（仅取长度>=2 的子词，避免单字噪声）
            for (String part : kw.split("[，。、；：！？!?\"“”'（）()《》<>\\s\\[\\]【】]+")) {
                String w = part.trim();
                if (w.length() < 2) {
                    continue;
                }
                int p = text.indexOf(w);
                if (p >= 0 && !containsWord(hits, w)) {
                    java.util.Map<String, Object> hit = new java.util.HashMap<>();
                    hit.put("word", w);
                    hit.put("snippet", snippetAround(text, p, w.length(), 30));
                    hits.add(hit);
                }
            }
        }
        return hits;
    }

    private boolean containsWord(java.util.List<java.util.Map<String, Object>> hits, String word) {
        for (java.util.Map<String, Object> h : hits) {
            if (word.equals(h.get("word"))) {
                return true;
            }
        }
        return false;
    }

    /** 截取命中词前后各 pad 个字符作为上下文片段 */
    private String snippetAround(String text, int start, int len, int pad) {
        int from = Math.max(0, start - pad);
        int to = Math.min(text.length(), start + len + pad);
        return text.substring(from, to);
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
     * pending -> approved(通过) / rejected(驳回) / revision(发回修改)
     * revision(发回修改) -> ai_reviewing(重新初筛) / pending
     * rejected(驳回) -> ai_reviewing(重新提交重审)
     * approved(通过) -> 终态，不可再流转
     */
    private static final java.util.Map<String, java.util.List<String>> STATUS_FLOW = new java.util.HashMap<>();
    static {
        STATUS_FLOW.put("ai_reviewing", java.util.Arrays.asList("pending", "revision", "rejected"));
        STATUS_FLOW.put("pending", java.util.Arrays.asList("approved", "rejected", "revision"));
        STATUS_FLOW.put("revision", java.util.Arrays.asList("ai_reviewing", "pending"));
        STATUS_FLOW.put("rejected", java.util.Arrays.asList("ai_reviewing"));
        STATUS_FLOW.put("approved", java.util.Collections.emptyList());
    }

    /**
     * 审核操作（通过/驳回/发回修改），带状态机校验，非法流转直接拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
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
        // 审核通过 → 内容侧作品自动上架（两个业务模块无编译期依赖，故在共享库的 Mapper 层落地）
        if (cnt > 0 && "approved".equals(status) && "work".equals(before.getTargetType()) && before.getTargetId() != null)
        {
            reviewRecordMapper.markWorkOnShelf(before.getTargetId(),
                    reviewerId == null ? null : String.valueOf(reviewerId));
        }
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
            default: return "operate";
        }
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
    public java.util.Map<String, Object> selectReviewStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        int pending = 0, aiReviewing = 0, approved = 0, rejected = 0, revision = 0;
        for (java.util.Map<String, Object> row : reviewRecordMapper.selectStatusGroup()) {
            String status = String.valueOf(row.get("status"));
            int cnt = ((Number) row.get("cnt")).intValue();
            if ("pending".equals(status)) pending += cnt;
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
