package com.smartscript.platform.trade.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.review.service.AiReviewService;
import com.smartscript.platform.review.util.ReviewContentText;
import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.domain.SysDemandStage;
import com.smartscript.platform.trade.mapper.SysDemandMapper;
import com.smartscript.platform.trade.mapper.SysDemandStageMapper;

/**
 * C module: 交付物 AI 审核（提交后、平台验收前的自动前置门）。
 *
 * 流程：创作者提交 → 轮次置 ai_reviewing（与人工验收一样暂停计时）→ 本服务后台调用
 * AiReviewService 打分 → 分数 >= {@link #AI_PASS_SCORE} 自动进入人工验收(submitted)，
 * < 该阈值自动打回(rejected)并写明原因；两种情况都把 AI 审核耗时补回截止时间，
 * 即 AI 审核不消耗创作者本轮时限。AI 审核本身不需要人工介入。
 *
 * 容错口径（放宽）：无正文可审、AI 调用异常时一律视为通过转人工验收，不卡创作者。
 */
@Service
public class DemandStageAiReviewService
{
    private static final Logger log = LoggerFactory.getLogger(DemandStageAiReviewService.class);

    /** AI 审核通过阈值：>=60 通过（进入平台验收），<60 自动打回 */
    public static final int AI_PASS_SCORE = 60;

    /** 送审文本上限（章节正文可能很长，截断防止 AI 调用超时） */
    private static final int MAX_CONTENT_CHARS = 6000;

    /** 落库字段 ai_result 上限，与列宽一致 */
    private static final int MAX_RESULT_CHARS = 2000;

    @Autowired
    private SysDemandStageMapper stageMapper;

    @Autowired
    private SysDemandMapper demandMapper;

    @Autowired
    private AiReviewService aiReviewService;

    /** AI 审核完成后的站内通知出口（失败不冒泡，不影响状态流转）。 */
    @Autowired
    private TradeNotifyService notifyService;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor executor;

    /** 正在跑的轮次，避免启动恢复等场景重复提交同一轮 */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 提交交付物后触发 AI 审核（必须在交付事务提交之后调用，否则后台读不到 ai_reviewing）。
     * 立即返回，不阻塞请求线程。
     */
    public void trigger(Long stageId)
    {
        if (stageId == null) return;
        if (!inFlight.add(stageId))
        {
            log.debug("AI审核已在队列中，跳过重复触发 stageId={}", stageId);
            return;
        }
        try
        {
            executor.execute(() -> run(stageId));
        }
        catch (Exception e)
        {
            inFlight.remove(stageId);
            log.error("AI审核任务提交失败，转人工验收兜底 stageId={}", stageId, e);
            fallbackToManual(stageId);
        }
    }

    /** 启动恢复：进程重启导致卡在 ai_reviewing 的轮次重新跑一遍 */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverStuckStages()
    {
        try
        {
            List<SysDemandStage> stuck = stageMapper.selectAiReviewingStages();
            if (stuck == null || stuck.isEmpty()) return;
            log.info("启动恢复：{} 个轮次处于AI审核中，重新触发", stuck.size());
            for (SysDemandStage stage : stuck)
            {
                trigger(stage.getStageId());
            }
        }
        catch (Exception e)
        {
            log.warn("AI审核启动恢复失败: {}", e.getMessage());
        }
    }

    /** 后台执行一轮 AI 审核并流转状态。 */
    private void run(Long stageId)
    {
        try
        {
            SysDemandStage stage = stageMapper.selectByStageId(stageId);
            if (stage == null || !SysDemandStage.STAGE_STATUS_AI_REVIEWING.equals(stage.getStatus()))
            {
                return;
            }

            Integer score = null;
            boolean pass;
            String aiResult;
            String skipReason = null;
            String content = buildContent(stage);
            if (content.isBlank())
            {
                // 无正文可审（作品还没有章节内容）：放宽处理，直接转人工验收
                pass = true;
                aiResult = json(noteOnly("无正文内容，跳过AI审核，转人工验收", null));
                skipReason = "无正文内容";
            }
            else
            {
                try
                {
                    Map<String, Object> judged = aiReviewService.scoreContent(content);
                    score = ((Number) judged.get("score")).intValue();
                    pass = score >= AI_PASS_SCORE;
                    aiResult = buildResultJson(judged);
                }
                catch (Exception e)
                {
                    // AI 不可用不拦人：视为通过，交由平台验收
                    log.warn("交付物AI审核失败，放宽转人工验收 stageId={}, err={}", stageId, e.getMessage());
                    pass = true;
                    aiResult = json(noteOnly("AI审核异常，转人工验收", e.getMessage()));
                    skipReason = "AI审核异常";
                }
            }

            String nextStatus = pass ? SysDemandStage.STAGE_STATUS_SUBMITTED : SysDemandStage.STAGE_STATUS_REJECTED;
            String rejectReason = pass ? null : buildRejectReason(score, aiResult);
            int changed = stageMapper.updateAiReview(stageId, nextStatus, score, trim(aiResult, MAX_RESULT_CHARS),
                    rejectReason, "ai");
            if (changed == 0)
            {
                log.info("AI审核完成但该轮已被处理，不流转 stageId={}", stageId);
                return;
            }
            log.info("交付物AI审核完成: stageId={}, score={}, -> {}, {}",
                    stageId, score, nextStatus, skipReason == null ? "" : skipReason);

            // AI 审核耗时不计入本轮时限：把（提交→AI完成）的时长补回截止时间
            restorePausedDuration(stageId);

            // 通知投递：pass 告知平台验收，fail 告知创作者重交。
            // 重新读取本轮，拿包含 AI 耗时补回后的最新 dueAt；避免通知仍展示旧截止。
            // 任何异常已由 TradeNotifyService.push 内部吞下，不需此处再包。
            SysDemandStage fresh = stageMapper.selectByStageId(stageId);
            notifyAiOutcome(fresh != null ? fresh : stage, pass, score, rejectReason);
        }
        catch (Exception e)
        {
            log.error("AI审核异常终止，兜底转人工验收 stageId={}", stageId, e);
            fallbackToManual(stageId);
        }
        finally
        {
            inFlight.remove(stageId);
        }
    }

    /** 兜底：AI 审核跑不动时直接放行进人工验收，避免轮次永久卡在 ai_reviewing。 */
    private void fallbackToManual(Long stageId)
    {
        try
        {
            int changed = stageMapper.updateAiReview(stageId, SysDemandStage.STAGE_STATUS_SUBMITTED, null,
                    json(java.util.Map.of("note", "AI审核未完成，转人工验收")), null, "ai");
            if (changed > 0)
            {
                restorePausedDuration(stageId);
                // 兜底转人工与 AI 通过同口径：通知发布者进入验收面板处理
                SysDemandStage after = stageMapper.selectByStageId(stageId);
                if (after != null)
                {
                    notifyAiOutcome(after, true, null, null);
                }
            }
        }
        catch (Exception e)
        {
            log.error("AI审核兜底转人工失败 stageId={}", stageId, e);
        }
    }

    /**
     * 把本轮「提交→AI审核完成」的暂停时长补回截止时间（与人工验收恢复计时同一口径），
     * 并镜像到 sys_demand 供任务列表展示。
     */
    private void restorePausedDuration(Long stageId)
    {
        SysDemandStage after = stageMapper.selectByStageId(stageId);
        if (after == null || after.getDueAt() == null || after.getSubmittedAt() == null
                || after.getAiReviewedAt() == null)
        {
            return;
        }
        long pausedMillis = Math.max(0, after.getAiReviewedAt().getTime() - after.getSubmittedAt().getTime());
        if (pausedMillis <= 0) return;
        Date newDue = new Date(after.getDueAt().getTime() + pausedMillis);
        stageMapper.pushDue(stageId, newDue, "ai");
        demandMapper.mirrorRoundState(after.getDemandId(), newDue, 0);
    }

    /**
     * AI 审核完成后的通知分发：
     * <ul>
     *   <li>pass：通知发布者本轮已交付等待人工验收（audience=BUYER）；</li>
     *   <li>fail：通知创作者本轮已被 AI 自动打回（audience=SELLER）。</li>
     * </ul>
     * 通知失败不冒泡也不影响已有状态流转；TradeNotifyService.push 内部已吞下异常，
     * 本层仅对 demand 反查等潜在 NPE 再包一层。
     */
    private void notifyAiOutcome(SysDemandStage stage, boolean pass, Integer score, String rejectReason)
    {
        try
        {
            SysDemand demand = demandMapper.selectDemandById(stage.getDemandId());
            if (demand == null)
            {
                log.warn("AI 审核通知跳过：任务不存在 demandId={}", stage.getDemandId());
                return;
            }
            if (pass)
            {
                notifyService.notifyStageReadyForReview(demand, stage);
            }
            else
            {
                notifyService.notifyStageRejectedByAi(demand, stage, score, rejectReason);
            }
        }
        catch (Exception e)
        {
            log.warn("AI 审核完成通知投递异常 stageId={}: {}", stage.getStageId(), e.getMessage());
        }
    }

    /** 送审文本：作品标题 + 交付说明 + 各章正文（纯文本，截断）。 */
    private String buildContent(SysDemandStage stage)
    {
        StringBuilder sb = new StringBuilder();
        if (stage.getWorkTitle() != null && !stage.getWorkTitle().isBlank())
        {
            sb.append("作品标题：").append(stage.getWorkTitle()).append('\n');
        }
        if (stage.getDeliverableDesc() != null && !stage.getDeliverableDesc().isBlank())
        {
            sb.append("交付说明：").append(stage.getDeliverableDesc()).append('\n');
        }
        if (stage.getWorkId() != null)
        {
            // 只送本轮验收集数内的正文：后续轮次尚未交付，不纳入本轮 AI 审核
            List<String> chapters = stageMapper.selectChapterContents(stage.getWorkId(), stage.getEpisodeTo());
            if (chapters != null)
            {
                for (String raw : chapters)
                {
                    String plain = ReviewContentText.plain(raw);
                    if (plain != null && !plain.isBlank())
                    {
                        sb.append(plain.trim()).append('\n');
                    }
                    if (sb.length() >= MAX_CONTENT_CHARS) break;
                }
            }
        }
        return trim(sb.toString().trim(), MAX_CONTENT_CHARS);
    }

    /** AI 结论摘要（只保留展示需要的字段）。 */
    @SuppressWarnings("unchecked")
    private String buildResultJson(Map<String, Object> judged)
    {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("score", judged.get("score"));
        out.put("level", judged.get("level"));
        out.put("keywords", judged.get("keywords"));
        List<Map<String, Object>> issues = (List<Map<String, Object>>) judged.get("issues");
        if (issues != null && issues.size() > 5)
        {
            issues = issues.subList(0, 5);
        }
        out.put("issues", issues);
        out.put("usingMock", judged.get("usingMock"));
        return json(out);
    }

    /** 自动打回原因：分数 + 命中的问题摘要（供创作者在面板看到为什么被打回）。 */
    private String buildRejectReason(Integer score, String aiResult)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("AI审核未通过（").append(score == null ? "-" : score).append("分，低于")
                .append(AI_PASS_SCORE).append("分自动打回）");
        try
        {
            Map<?, ?> obj = objectMapper.readValue(aiResult, Map.class);
            Object kws = obj.get("keywords");
            if (kws instanceof List && !((List<?>) kws).isEmpty())
            {
                sb.append("；命中词：").append(joinLimit((List<?>) kws, 8));
            }
            Object iss = obj.get("issues");
            if (iss instanceof List && !((List<?>) iss).isEmpty())
            {
                StringBuilder quotes = new StringBuilder();
                for (Object o : (List<?>) iss)
                {
                    if (!(o instanceof Map)) continue;
                    Object q = ((Map<?, ?>) o).get("quote");
                    if (q == null || q.toString().isBlank()) continue;
                    if (quotes.length() > 0) quotes.append(" / ");
                    quotes.append(trim(q.toString(), 40));
                    if (quotes.length() > 120) break;
                }
                if (quotes.length() > 0)
                {
                    sb.append("；问题片段：").append(quotes);
                }
            }
        }
        catch (Exception ignored)
        {
            // 摘要解析失败不影响打回
        }
        return trim(sb.toString(), 500);
    }

    /** 未走 AI / AI 异常时的说明型结论。 */
    private static Map<String, Object> noteOnly(String note, String error)
    {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("note", note);
        if (error != null)
        {
            out.put("error", trim(error, 200));
        }
        return out;
    }

    private static String joinLimit(List<?> list, int max)
    {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Object o : list)
        {
            if (i++ >= max) break;
            if (sb.length() > 0) sb.append("、");
            sb.append(o);
        }
        return sb.toString();
    }

    private String json(Object value)
    {
        try
        {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception e)
        {
            return "{}";
        }
    }

    private static String trim(String text, int max)
    {
        if (text == null) return null;
        return text.length() <= max ? text : text.substring(0, max);
    }
}
