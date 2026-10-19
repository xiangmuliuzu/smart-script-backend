package com.smartscript.platform.content.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.domain.SysWorkReviewRecord;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysTagMapper;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.mapper.SysWorkReviewRecordMapper;
import com.smartscript.platform.content.service.IAppUserWorkService;
import com.smartscript.platform.review.service.AiReviewService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

/**
 * PC 用户端「作品详情与审核流程」服务实现（A2）
 *
 * 依据：五人分工 A2 + 云端 script_platform_dev 库。
 * 云端模型：sys_work.status 只表示上架状态（on_shelf/off_shelf），
 * 作品审核状态由 sys_review_record（target_type='work'）最新一条的 status 推导：
 *   无记录      -> draft（未提交）
 *   pending / ai_reviewing -> reviewing（审核中，只读）
 *   revision    -> revision（待修改，可编辑）
 *   rejected    -> rejected（已驳回，可编辑）
 *   approved    -> published（已发布，可编辑；改动后需重新提交审核）
 * 编辑闸门：仅 reviewing（审核中）锁定，其余状态均允许编辑。
 * 提交审核只写 sys_review_record，不修改 sys_work.status。
 *
 * 鉴权：userId 由控制器从 IdentityProvider 取得后传入，本服务不做请求体信任。
 *
 * @author smartscript
 */
@Service
public class AppUserWorkServiceImpl implements IAppUserWorkService
{
    /** 审核中状态：待审核 / AI审核中（不可编辑、不可重复提交） */
    private static final java.util.Set<String> REVIEWING_STATUS = java.util.Set.of("pending", "ai_reviewing");

    /** 作品标签数量上限（接口文档 2.3.3：tagIds 最多 8 个） */
    private static final int MAX_WORK_TAGS = 8;

    private final SysContentWorkMapper workMapper;
    private final SysWorkChapterMapper chapterMapper;
    private final SysWorkReviewRecordMapper reviewRecordMapper;
    private final SysTagMapper tagMapper;
    private final AiReviewService aiReviewService;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AppUserWorkServiceImpl.class);

    public AppUserWorkServiceImpl(SysContentWorkMapper workMapper,
            SysWorkChapterMapper chapterMapper,
            SysWorkReviewRecordMapper reviewRecordMapper,
            SysTagMapper tagMapper,
            AiReviewService aiReviewService)
    {
        this.workMapper = workMapper;
        this.chapterMapper = chapterMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.tagMapper = tagMapper;
        this.aiReviewService = aiReviewService;
    }

    @Override
    public Map<String, Object> getWorkDetail(Long userId, Long workId)
    {
        SysWork work = requireOwnWork(userId, workId);
        List<SysWorkChapter> chapters = chapterMapper.selectChapterListWithContentByWorkId(workId);

        List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkId(workId);
        SysWorkReviewRecord latest = records.isEmpty() ? null : records.get(0);

        Map<String, Object> result = new LinkedHashMap<>();
        // 以审核派生状态覆盖 sys_work 原始上架状态，供前端直接渲染
        work.setStatus(deriveStatus(latest));
        result.put("work", work);
        // 作品标签（接口文档 2.3.2 输出参数 tags）：仅启用标签，供设置页回填已选标签
        result.put("tags", tagMapper.selectWorkTagRows(List.of(workId)));
        result.put("chapters", chapters);
        result.put("latestReview", latest);
        return result;
    }

    @Override
    @Transactional
    public void updateWorkBase(Long userId, Long workId, String title, String summary, Integer genreId, String cover,
            List<Integer> tagIds)
    {
        requireOwnWork(userId, workId);
        requireEditable(workId, userId);

        SysWork update = new SysWork();
        update.setWorkId(workId);
        update.setTitle(title);
        update.setSummary(summary);
        update.setGenreId(genreId);
        update.setCover(cover);
        update.setUpdateBy(String.valueOf(userId));
        update.setUpdateTime(new Date());
        workMapper.updateWorkBaseInfo(update);

        if (tagIds != null)
        {
            replaceWorkTags(userId, workId, tagIds);
        }
    }

    /**
     * 全量覆盖作品标签（接口文档 2.3.3：tagIds 为全量覆盖语义，最多 8 个）。
     *
     * 先清空该作品现有标签再批量写入，空列表即清空；重复ID去重，避免撞 uk_work_tag 唯一索引。
     */
    private void replaceWorkTags(Long userId, Long workId, List<Integer> tagIds)
    {
        java.util.Set<Integer> uniqueTagIds = new java.util.LinkedHashSet<>();
        for (Integer tagId : tagIds)
        {
            if (tagId != null)
            {
                uniqueTagIds.add(tagId);
            }
        }
        if (uniqueTagIds.size() > MAX_WORK_TAGS)
        {
            throw new IllegalArgumentException("标签最多选择 " + MAX_WORK_TAGS + " 个");
        }
        tagMapper.deleteWorkTagByWorkId(workId);
        if (!uniqueTagIds.isEmpty())
        {
            tagMapper.batchInsertWorkTag(workId, uniqueTagIds, String.valueOf(userId));
        }
        // 标签引用已变化：全量刷新使用数量，保证 use_count 与作品实际引用接轨（不再恒为 0）
        tagMapper.syncTagUseCount();
    }

    @Override
    public List<SysWorkChapter> listChapters(Long userId, Long workId)
    {
        requireOwnWork(userId, workId);
        return chapterMapper.selectChapterListWithContentByWorkId(workId);
    }

    @Override
    @Transactional
    public SysWorkChapter addChapter(Long userId, Long workId, String chapterTitle, String content)
    {
        requireOwnWork(userId, workId);
        requireEditable(workId, userId);

        int maxNo = chapterMapper.selectMaxChapterNo(workId);
        SysWorkChapter chapter = new SysWorkChapter();
        chapter.setWorkId(workId);
        chapter.setChapterNo(maxNo + 1);
        chapter.setChapterTitle(chapterTitle == null ? "" : chapterTitle);
        chapter.setContent(content == null ? "" : content);
        chapter.setWordCount(countWords(chapter.getContent()));
        chapter.setIsFree("0");
        chapter.setStatus("0");
        chapter.setCreateBy(String.valueOf(userId));
        chapter.setCreateTime(new Date());
        chapterMapper.insertChapter(chapter);
        // 章节增删改后回写作品聚合列（word_count/episode_count），否则作品恒显示 0 字
        workMapper.refreshWorkAggregates(workId);
        return chapter;
    }

    @Override
    @Transactional
    public void updateChapter(Long userId, Long workId, Long chapterId, String chapterTitle, String content)
    {
        requireOwnWork(userId, workId);
        requireEditable(workId, userId);

        SysWorkChapter chapter = requireOwnChapter(workId, chapterId);
        SysWorkChapter update = new SysWorkChapter();
        update.setChapterId(chapterId);
        update.setChapterTitle(chapterTitle == null ? chapter.getChapterTitle() : chapterTitle);
        update.setContent(content == null ? chapter.getStorageContent() : content);
        update.setWordCount(countWords(update.getContent()));
        update.setUpdateBy(String.valueOf(userId));
        update.setUpdateTime(new Date());
        chapterMapper.updateChapter(update);
        workMapper.refreshWorkAggregates(workId);
    }

    @Override
    @Transactional
    public void deleteChapter(Long userId, Long workId, Long chapterId)
    {
        requireOwnWork(userId, workId);
        requireEditable(workId, userId);
        requireOwnChapter(workId, chapterId);
        chapterMapper.deleteChapterById(chapterId);
        workMapper.refreshWorkAggregates(workId);
    }

    @Override
    @Transactional
    public void submitReview(Long userId, Long workId)
    {
        SysWork work = requireOwnWork(userId, workId);

        // 审核中不允许重复提交（pending / ai_reviewing 都算）
        if (reviewRecordMapper.countPendingByWorkId(workId) > 0)
        {
            throw new IllegalStateException("作品正在审核中，请勿重复提交");
        }

        // 云端模型：先落审核记录，状态置为 ai_reviewing，异步触发 AI 初审
        SysWorkReviewRecord record = new SysWorkReviewRecord();
        record.setReviewNo(generateReviewNo());
        record.setTargetType("work");
        record.setTargetId(workId);
        record.setSubmitterId(userId);
        record.setReviewResult("ai_reviewing");
        record.setStatus("ai_reviewing");
        reviewRecordMapper.insertRecord(record);

        // 拼待审核内容：作品标题+简介 + 前若干章正文（每章截断，避免 token 过长）
        String content = buildAiReviewContent(work);
        final Long reviewId = record.getReviewId();
        log.info("作品提交审核，reviewId={}, workId={}，异步触发AI初审", reviewId, workId);

        // 事务提交后再异步调 AI；无事务同步时（如单测）直接异步执行
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    runAiReviewAsync(reviewId, content);
                }
            });
        }
        else
        {
            runAiReviewAsync(reviewId, content);
        }
    }

    private void runAiReviewAsync(Long reviewId, String content)
    {
        CompletableFuture.runAsync(() -> {
            try
            {
                aiReviewService.score(reviewId, content);
            }
            catch (Exception e)
            {
                log.warn("AI初审异步执行失败 reviewId={}: {}", reviewId, e.getMessage());
            }
        });
    }

    /** 拼接 AI 初审待审内容：标题 + 简介 + 前5章正文（每章最多800字） */
    private String buildAiReviewContent(SysWork work)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("标题：").append(work.getTitle() == null ? "" : work.getTitle()).append("\n");
        if (work.getSummary() != null && !work.getSummary().isBlank())
        {
            sb.append("简介：").append(work.getSummary()).append("\n");
        }
        List<SysWorkChapter> chapters = chapterMapper.selectChapterListWithContentByWorkId(work.getWorkId());
        int limit = Math.min(chapters == null ? 0 : chapters.size(), 5);
        for (int i = 0; i < limit; i++)
        {
            SysWorkChapter ch = chapters.get(i);
            sb.append("\n【第").append(ch.getChapterNo() == null ? (i + 1) : ch.getChapterNo())
              .append("章 ").append(ch.getChapterTitle() == null ? "" : ch.getChapterTitle()).append("】\n");
            String body = ch.getContent();
            if (body != null)
            {
                sb.append(body.length() > 800 ? body.substring(0, 800) : body);
            }
        }
        return sb.length() > 6000 ? sb.substring(0, 6000) : sb.toString();
    }

    @Override
    public List<SysWorkReviewRecord> listReviewRecords(Long userId, Long workId)
    {
        requireOwnWork(userId, workId);
        return reviewRecordMapper.selectByWorkId(workId);
    }

    @Override
    public List<Map<String, Object>> listWorks(Long userId, String statusFilter, String keyword, String workType)
    {
        List<SysWork> works = workMapper.selectWorkListByAuthor(userId);
        if (works.isEmpty())
        {
            return List.of();
        }

        Map<Long, SysWorkReviewRecord> latestByWork = latestRecordsByWork(works);

        // 状态过滤支持逗号多值（PC「待修改」传 revision,rejected）；空集表示不过滤
        java.util.Set<String> filters = parseStatusFilter(statusFilter);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase();
        String wt = workType == null || workType.isBlank() ? null : workType.trim();

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (SysWork work : works)
        {
            if (wt != null && !wt.equals(work.getWorkType()))
            {
                continue;
            }
            if (kw != null && (work.getTitle() == null || !work.getTitle().toLowerCase().contains(kw)))
            {
                continue;
            }
            SysWorkReviewRecord latest = latestByWork.get(work.getWorkId());
            String derived = deriveStatus(latest);
            if (!filters.isEmpty() && !filters.contains(derived))
            {
                continue;
            }
            result.add(toListItem(work, derived, latest));
        }
        return result;
    }

    @Override
    public Map<String, Object> workStatistics(Long userId)
    {
        List<SysWork> works = workMapper.selectWorkListByAuthor(userId);
        int draft = 0;
        int reviewing = 0;
        int revision = 0;
        int published = 0;
        if (!works.isEmpty())
        {
            Map<Long, SysWorkReviewRecord> latestByWork = latestRecordsByWork(works);
            for (SysWork work : works)
            {
                switch (deriveStatus(latestByWork.get(work.getWorkId())))
                {
                    case "draft" -> draft++;
                    case "reviewing" -> reviewing++;
                    // 「待修改」归并 revision 与 rejected（与列表页 Tab 同口径）
                    case "revision", "rejected" -> revision++;
                    case "published" -> published++;
                    default -> { }
                }
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("workTotal", works.size());
        stats.put("draftCount", draft);
        stats.put("reviewingCount", reviewing);
        stats.put("revisionCount", revision);
        stats.put("publishedCount", published);
        return stats;
    }

    // ------------------------------------------------------------------
    // 私有辅助
    // ------------------------------------------------------------------

    /** 批量取最新审核记录并按作品ID分组（记录按创建时间倒序，每组第一条即最新） */
    private Map<Long, SysWorkReviewRecord> latestRecordsByWork(List<SysWork> works)
    {
        List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkIds(
                works.stream().map(SysWork::getWorkId).toList());
        Map<Long, SysWorkReviewRecord> latestByWork = new java.util.HashMap<>();
        for (SysWorkReviewRecord record : records)
        {
            latestByWork.putIfAbsent(record.getTargetId(), record);
        }
        return latestByWork;
    }

    /** 解析状态过滤：逗号分隔多值，忽略空白与 all；返回空集表示不过滤 */
    private java.util.Set<String> parseStatusFilter(String statusFilter)
    {
        if (statusFilter == null || statusFilter.isBlank())
        {
            return java.util.Set.of();
        }
        java.util.Set<String> filters = new java.util.HashSet<>();
        for (String part : statusFilter.split(","))
        {
            String value = part.trim();
            if (!value.isEmpty() && !"all".equals(value))
            {
                filters.add(value);
            }
        }
        return filters;
    }

    /** 组装列表条目（字段口径与 App 端「我的作品」列表保持一致，新增 reviewSummary） */
    private Map<String, Object> toListItem(SysWork work, String derivedStatus, SysWorkReviewRecord latest)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("workId", work.getWorkId());
        item.put("title", work.getTitle());
        item.put("cover", work.getCover());
        item.put("summary", work.getSummary());
        item.put("genreId", work.getGenreId());
        item.put("genreName", work.getGenreName());
        item.put("workType", work.getWorkType());
        item.put("wordCount", work.getWordCount());
        item.put("status", derivedStatus);
        item.put("reviewSummary", reviewSummary(latest));
        item.put("createTime", work.getCreateTime());
        item.put("updateTime", work.getUpdateTime());
        return item;
    }

    /** 最近审核意见摘要：优先取审核意见原文，无意见时按审核状态给出默认文案 */
    private String reviewSummary(SysWorkReviewRecord latest)
    {
        if (latest == null)
        {
            return null;
        }
        String opinion = latest.getReviewOpinion();
        if (opinion != null && !opinion.isBlank())
        {
            return opinion;
        }
        String status = latest.getStatus();
        if (status == null)
        {
            return null;
        }
        return switch (status)
        {
            case "pending" -> "等待人工审核";
            case "ai_reviewing" -> "AI 审核中";
            case "revision" -> "需修改后重新提交";
            case "rejected" -> "审核未通过";
            case "approved" -> "审核通过";
            default -> null;
        };
    }

    /** 校验作品存在且归属当前用户（selectWorkById 已含 is_deleted=0 过滤） */
    private SysWork requireOwnWork(Long userId, Long workId)
    {
        SysWork work = workMapper.selectWorkById(workId);
        if (work == null)
        {
            throw new IllegalArgumentException("作品不存在");
        }
        if (work.getAuthorId() == null || !work.getAuthorId().equals(userId))
        {
            throw new SecurityException("无权访问该作品");
        }
        return work;
    }

    /** 校验章节存在且属于该作品 */
    private SysWorkChapter requireOwnChapter(Long workId, Long chapterId)
    {
        SysWorkChapter chapter = chapterMapper.selectChapterDetail(chapterId);
        if (chapter == null || !workId.equals(chapter.getWorkId()))
        {
            throw new IllegalArgumentException("章节不存在");
        }
        return chapter;
    }

    /** 校验作品可编辑：仅「审核中」锁定，其余状态（含已发布）均允许编辑 */
    private void requireEditable(Long workId, Long userId)
    {
        List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkId(workId);
        if (records.isEmpty())
        {
            return;
        }
        String latest = records.get(0).getStatus();
        if (REVIEWING_STATUS.contains(latest))
        {
            throw new IllegalStateException("作品正在审核中，不允许编辑");
        }
    }

    /** 由最新审核记录推导作品状态（云端模型） */
    private String deriveStatus(SysWorkReviewRecord latest)
    {
        if (latest == null || latest.getStatus() == null)
        {
            return "draft";
        }
        String status = latest.getStatus();
        if (REVIEWING_STATUS.contains(status))
        {
            return "reviewing";
        }
        if ("approved".equals(status))
        {
            return "published";
        }
        // revision / rejected 原样返回
        return status;
    }

    /** 统计字数（按非空白字符数近似） */
    private int countWords(String content)
    {
        if (content == null || content.isBlank())
        {
            return 0;
        }
        String trimmed = content.trim();
        return (int) trimmed.chars().filter(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c)).count();
    }

    /** 生成审核编号：RV + yyyyMMdd + 3位当天序号（与云端样例 RV20260920003 一致） */
    private String generateReviewNo()
    {
        String prefix = "RV" + new SimpleDateFormat("yyyyMMdd").format(new Date());
        int today = reviewRecordMapper.countTodayByPrefix(prefix);
        return prefix + String.format("%03d", today + 1);
    }
}
