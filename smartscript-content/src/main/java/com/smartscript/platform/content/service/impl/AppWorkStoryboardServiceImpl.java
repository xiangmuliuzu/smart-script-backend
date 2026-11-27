package com.smartscript.platform.content.service.impl;

import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.util.ChapterTrialAccess;
import com.smartscript.platform.content.domain.SysWorkReviewRecord;
import com.smartscript.platform.content.domain.SysWorkStoryboard;
import com.smartscript.platform.content.dto.AppStoryboardDto;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.mapper.SysWorkReviewRecordMapper;
import com.smartscript.platform.content.mapper.SysWorkStoryboardMapper;
import com.smartscript.platform.content.service.IAppWorkStoryboardService;
import com.smartscript.platform.content.util.WorkStatusDeriver;
import com.smartscript.platform.review.service.AiReviewService;
import com.smartscript.platform.content.mapper.AppWorkAuthorizationMapper;

/** 作品分镜目录服务实现。 */
@Service
public class AppWorkStoryboardServiceImpl implements IAppWorkStoryboardService
{
    private static final Logger log = LoggerFactory.getLogger(AppWorkStoryboardServiceImpl.class);

    private final SysContentWorkMapper workMapper;
    private final SysWorkChapterMapper chapterMapper;
    private final SysWorkReviewRecordMapper reviewRecordMapper;
    private final SysWorkStoryboardMapper storyboardMapper;
    private final AiReviewService aiReviewService;
    private final AppWorkAuthorizationMapper authorizationMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AppWorkStoryboardServiceImpl(SysContentWorkMapper workMapper,
            SysWorkChapterMapper chapterMapper, SysWorkReviewRecordMapper reviewRecordMapper,
            SysWorkStoryboardMapper storyboardMapper, AiReviewService aiReviewService,
            AppWorkAuthorizationMapper authorizationMapper)
    {
        this.workMapper = workMapper;
        this.chapterMapper = chapterMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.storyboardMapper = storyboardMapper;
        this.aiReviewService = aiReviewService;
        this.authorizationMapper = authorizationMapper;
    }

    @Override
    public List<AppStoryboardDto> list(Long userId, Long workId)
    {
        SysWork work = workMapper.selectWorkById(workId);
        if (work == null)
        {
            return List.of();
        }

        boolean owner = userId != null && userId.equals(work.getAuthorId());
        if (owner)
        {
            return toDtos(storyboardMapper.selectByWorkId(workId), true, true, work);
        }

        // 作品详情本身只对已上架作品公开；分镜目录沿用同一可见性口径。
        if (workMapper.selectAppWorkById(workId) == null)
        {
            return List.of();
        }

        boolean authorized = isWorkAuthorized(workId, userId);
        return toDtos(storyboardMapper.selectApprovedByWorkId(workId), authorized, false, work);
    }

    @Override
    public int countPublic(Long workId)
    {
        SysWork work = workMapper.selectWorkById(workId);
        if (work == null || workMapper.selectAppWorkById(workId) == null)
        {
            return 0;
        }
        return storyboardMapper.countApprovedByWorkId(workId);
    }

    private List<AppStoryboardDto> toDtos(List<SysWorkStoryboard> rows, boolean fullAccess, boolean owner,
            SysWork work)
    {
        if (rows == null || rows.isEmpty())
        {
            return List.of();
        }
        SysWorkChapter query = new SysWorkChapter();
        query.setWorkId(work.getWorkId());
        if (!owner) query.setStatus("0");
        Map<Long, SysWorkChapter> chapters = new HashMap<>();
        for (SysWorkChapter chapter : chapterMapper.selectChapterListByWorkId(query))
        {
            chapters.put(chapter.getChapterId(), chapter);
        }
        List<AppStoryboardDto> result = new java.util.ArrayList<>(rows.size());
        for (SysWorkStoryboard row : rows)
        {
            SysWorkChapter chapter = chapters.get(row.getChapterId());
            if (chapter == null)
            {
                continue;
            }
            boolean trialEnabled = ChapterTrialAccess.isOpen(chapter, work.getPreviewEnabled(), work.getPreviewEpisodes());
            result.add(toDto(row, chapter, fullAccess || trialEnabled, trialEnabled, owner));
        }
        return result;
    }

    private AppStoryboardDto toDto(SysWorkStoryboard row, SysWorkChapter chapter,
            boolean readable, boolean trialEnabled, boolean owner)
    {
        AppStoryboardDto dto = new AppStoryboardDto();
        dto.setStoryboardId(row.getStoryboardId());
        dto.setWorkId(row.getWorkId());
        dto.setChapterId(row.getChapterId());
        dto.setChapterNo(chapter.getChapterNo());
        dto.setChapterTitle(chapter.getChapterTitle());
        dto.setContent(readable ? row.getContent() : null);
        dto.setWordCount(row.getWordCount());
        dto.setSource(row.getSource());
        dto.setStatus(row.getStatus());
        dto.setTrialEnabled(trialEnabled);
        dto.setReadable(readable);
        dto.setReviewerId(row.getReviewerId());
        dto.setReviewTime(row.getReviewTime());
        dto.setRejectReason(owner ? row.getRejectReason() : null);
        dto.setAiResult(owner ? row.getAiResult() : null);
        dto.setAiRiskLevel(owner ? row.getAiRiskLevel() : null);
        dto.setAiSensitiveWords(owner ? row.getAiSensitiveWords() : null);
        dto.setAiStartTime(owner ? row.getAiStartTime() : null);
        dto.setAiEndTime(owner ? row.getAiEndTime() : null);
        dto.setCreateBy(owner ? row.getCreateBy() : null);
        dto.setCreateTime(row.getCreateTime());
        dto.setUpdateBy(owner ? row.getUpdateBy() : null);
        dto.setUpdateTime(row.getUpdateTime());
        dto.setRemark(owner ? row.getRemark() : null);
        return dto;
    }

    private boolean isWorkAuthorized(Long workId, Long userId)
    {
        if (workId == null || userId == null)
        {
            return false;
        }
        return authorizationMapper.countEffectiveAuthorization(workId, userId) > 0
                || authorizationMapper.countPurchasedOrder(workId, userId) > 0;
    }

    @Override
    @Transactional
    public SysWorkStoryboard save(Long userId, Long workId, Long chapterId, Integer chapterNo, String chapterTitle,
            String content, String source)
    {
        requireOwnWork(userId, workId);
        requireEditable(workId);

        if (content == null || content.trim().isEmpty())
        {
            throw new IllegalArgumentException("分镜内容不能为空");
        }

        SysWorkChapter chapter = resolveChapter(workId, chapterId, chapterNo, chapterTitle);
        Date now = new Date();
        SysWorkStoryboard storyboard = new SysWorkStoryboard();
        storyboard.setWorkId(workId);
        storyboard.setChapterId(chapter.getChapterId());
        storyboard.setChapterTitle(chapter.getChapterTitle());
        storyboard.setContent(content);
        storyboard.setWordCount(countWords(content));
        storyboard.setSource("ai".equalsIgnoreCase(source) ? "ai" : "manual");
        storyboard.setStatus("ai_reviewing");
        storyboard.setCreateBy(String.valueOf(userId));
        storyboard.setCreateTime(now);
        storyboard.setUpdateBy(String.valueOf(userId));
        storyboard.setUpdateTime(now);
        storyboardMapper.upsert(storyboard);
        if (storyboard.getStoryboardId() == null)
        {
            throw new IllegalStateException("分镜保存失败，请稍后重试");
        }
        queueAiReview(storyboard.getStoryboardId(), content);
        return storyboardMapper.selectById(storyboard.getStoryboardId());
    }

    private SysWorkChapter resolveChapter(Long workId, Long chapterId, Integer chapterNo, String chapterTitle)
    {
        if (chapterId != null || chapterNo != null)
        {
            SysWorkChapter chapter = chapterId == null ? chapterMapper.selectChapterByNo(workId, chapterNo)
                    : chapterMapper.selectChapterMeta(chapterId);
            if (chapter == null || !workId.equals(chapter.getWorkId())
                    || (chapterNo != null && !chapterNo.equals(chapter.getChapterNo())))
            {
                throw new IllegalArgumentException("章节不存在于当前作品，或章节 ID 与章号不一致");
            }
            return chapter;
        }
        String title = chapterTitle == null ? "" : chapterTitle.trim();
        if (title.isEmpty())
        {
            throw new IllegalArgumentException("请选择章节或提供已有章节标题");
        }
        SysWorkChapter query = new SysWorkChapter();
        query.setWorkId(workId);
        return chapterMapper.selectChapterListByWorkId(query).stream()
                .filter(item -> item.getChapterTitle() != null && title.equals(item.getChapterTitle().trim()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("分镜标题必须选择当前作品已有的章节目录标题"));
    }

    /** 分镜与作品共用同一套启用规则、敏感词库、AI评分和自动流转策略。 */
    private void queueAiReview(Long storyboardId, String content)
    {
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    startAiReview(storyboardId, content);
                }
            });
        }
        else
        {
            startAiReview(storyboardId, content);
        }
    }

    private void startAiReview(Long storyboardId, String content)
    {
        CompletableFuture.runAsync(() -> {
            try
            {
                Map<String, Object> judged = aiReviewService.scoreContent(content);
                applyAiReview(storyboardId, content, judged);
            }
            catch (Exception e)
            {
                // AI服务异常时保留给人工审核，不把用户提交的分镜丢掉。
                log.warn("分镜AI审核失败，转人工审核 storyboardId={}: {}", storyboardId, e.getMessage());
                SysWorkStoryboard fallback = new SysWorkStoryboard();
                fallback.setStoryboardId(storyboardId);
                fallback.setContent(content);
                fallback.setStatus("pending");
                fallback.setAiStartTime(new Date());
                fallback.setAiEndTime(new Date());
                storyboardMapper.updateAiReview(fallback);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void applyAiReview(Long storyboardId, String content, Map<String, Object> judged)
    {
        int score = ((Number) judged.getOrDefault("score", 100)).intValue();
        String riskLevel = String.valueOf(judged.getOrDefault("level", "low"));
        String action = aiReviewService.decideAction(score, content,
                (List<com.smartscript.platform.review.domain.AiReviewRule>) judged.get("rules"));
        String status;
        if (score >= 90 && !"auto_reject".equals(action))
        {
            status = "approved";
        }
        else if ("auto_reject".equals(action) || "high".equals(riskLevel))
        {
            status = "rejected";
        }
        else
        {
            status = "pending";
        }

        SysWorkStoryboard update = new SysWorkStoryboard();
        update.setStoryboardId(storyboardId);
        update.setContent(content);
        update.setStatus(status);
        update.setAiRiskLevel(riskLevel);
        update.setAiSensitiveWords(joinValues(judged.get("keywords")));
        update.setAiStartTime(new Date());
        update.setAiEndTime(new Date());
        update.setRejectReason("rejected".equals(status) ? "AI审核未通过，请根据审核结果修改分镜内容" : null);
        try
        {
            Map<String, Object> aiJson = new LinkedHashMap<>();
            aiJson.put("score", score);
            aiJson.put("level", riskLevel);
            aiJson.put("keywords", judged.get("keywords"));
            aiJson.put("issues", judged.get("issues"));
            aiJson.put("prompt", judged.get("prompt"));
            update.setAiResult(objectMapper.writeValueAsString(aiJson));
        }
        catch (Exception e)
        {
            update.setAiResult("{}");
        }
        if (storyboardMapper.updateAiReview(update) > 0)
        {
            log.info("分镜AI审核完成 storyboardId={}, status={}, score={}, level={}",
                    storyboardId, status, score, riskLevel);
        }
    }

    private String joinValues(Object values)
    {
        if (!(values instanceof List<?> list))
        {
            return values == null ? null : String.valueOf(values);
        }
        return list.stream().map(String::valueOf).filter(value -> !value.isBlank())
                .distinct().reduce((left, right) -> left + "," + right).orElse(null);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long workId, Long storyboardId)
    {
        requireOwnWork(userId, workId);
        requireEditable(workId);
        SysWorkStoryboard storyboard = storyboardMapper.selectById(storyboardId);
        if (storyboard == null || !workId.equals(storyboard.getWorkId()))
        {
            throw new SecurityException("分镜不存在或无权删除");
        }
        if ("approved".equals(storyboard.getStatus()))
        {
            throw new IllegalStateException("审核通过的分镜不能删除");
        }
        int affected = storyboardMapper.softDelete(storyboardId, String.valueOf(userId));
        if (affected == 0)
        {
            throw new IllegalStateException("分镜删除失败，请刷新后重试");
        }
    }

    private SysWork requireOwnWork(Long userId, Long workId)
    {
        SysWork work = workMapper.selectWorkById(workId);
        if (work == null)
        {
            throw new SecurityException("作品不存在");
        }
        if (work.getAuthorId() == null || !work.getAuthorId().equals(userId))
        {
            throw new SecurityException("无权访问该作品");
        }
        return work;
    }

    private void requireEditable(Long workId)
    {
        List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkId(workId);
        if (!records.isEmpty() && WorkStatusDeriver.isReviewing(records.get(0).getStatus()))
        {
            throw new IllegalStateException("作品正在审核中，不允许保存分镜");
        }
    }

    private int countWords(String value)
    {
        return (int) value.trim().chars()
                .filter(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c)).count();
    }
}
