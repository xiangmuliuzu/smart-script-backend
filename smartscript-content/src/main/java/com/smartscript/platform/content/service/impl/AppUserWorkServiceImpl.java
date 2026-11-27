package com.smartscript.platform.content.service.impl;

import java.text.SimpleDateFormat;
import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysTag;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.domain.SysWorkReviewRecord;
import com.smartscript.platform.content.exception.AiServiceUnavailableException;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysTagMapper;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.mapper.SysWorkReviewRecordMapper;
import com.smartscript.platform.content.service.IAppUserWorkService;
import com.smartscript.platform.content.util.ChapterTrialAccess;
import com.smartscript.platform.content.util.WorkStatusDeriver;
import com.smartscript.platform.review.event.WorkPublishedEvent;
import com.smartscript.platform.review.service.AiReviewService;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

/**
 * PC 用户端「作品详情与审核流程」服务实现（A2）
 *
 * 依据：五人分工 A2 + 云端 script_platform_dev 库。
 * 两个正交维度（口径唯一定义处：{@link WorkStatusDeriver}）：
 *   1. sys_work.status 只表示可见性开关（on_shelf/off_shelf），不承载生命周期；
 *   2. 生命周期由 sys_review_record（target_type='work'）最新一条的 status 派生：
 *      无记录 -> draft；pending/ai_reviewing -> reviewing；approved -> published；
 *      revision / rejected 原样返回。
 * 编辑闸门：仅 reviewing（审核中）锁定，其余状态均允许编辑。
 * 提交审核只写 sys_review_record，不修改 sys_work.status；审核通过后由作者主动上架。
 * 上下架语义：作者「下架」只降低可见性、无前置；「重新上架」要求最新审核为 approved
 * 且 reject_reason 为空（平台强制下架的作品禁止作者自助上架）。
 * 章节两态：普通章节默认 status='1'（草稿，仅作者可见）；设置 is_free='1' 的试读章节
 * 章节审核结果继承整部作品审核：通过写 status='0'，驳回/发回写 status='2'，
 * 新增或修改正文写 status='1'；试读设置只能单独修改审核通过章节。
 *
 * 鉴权：userId 由控制器从 IdentityProvider 取得后传入，本服务不做请求体信任。
 *
 * @author smartscript
 */
@Service
public class AppUserWorkServiceImpl implements IAppUserWorkService
{
    /** 作品标签维度上限：主分类 1 + 主题 2 + 角色 2 + 情节 2 = 最多 7 个 */
    private static final Map<String, Integer> MAX_TAGS_BY_TYPE = Map.of(
            "hot", 1, "theme", 2, "role", 2, "plot", 2);
    private static final int MAX_WORK_TAGS = 7;
    private static final Map<String, String> TAG_TYPE_NAMES = Map.of(
            "hot", "主分类", "theme", "主题", "role", "角色", "plot", "情节");

    private final SysContentWorkMapper workMapper;
    private final SysWorkChapterMapper chapterMapper;
    private final SysWorkReviewRecordMapper reviewRecordMapper;
    private final SysTagMapper tagMapper;
    private final AppAiWriteRecordMapper aiRecords;
    private final AiReviewService aiReviewService;
    private final com.smartscript.platform.review.service.RiskCheckService riskCheckService;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AppUserWorkServiceImpl.class);

    /**
     * 作者手动上架成功后发布「作品发布」事件，供关注动态广播。
     *
     * 审核通过只代表审核结果，不再自动上架（见 ReviewService/AiReviewService）；
     * 所以「作品发布」的发点从审核环节移到作者真正点上架这一步。
     * 可选注入：测试或未装事件容器时不影响上下架主流程。
     */
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    public AppUserWorkServiceImpl(SysContentWorkMapper workMapper,
            SysWorkChapterMapper chapterMapper,
            SysWorkReviewRecordMapper reviewRecordMapper,
            SysTagMapper tagMapper,
            AppAiWriteRecordMapper aiRecords,
            AiReviewService aiReviewService,
            com.smartscript.platform.review.service.RiskCheckService riskCheckService)
    {
        this.workMapper = workMapper;
        this.chapterMapper = chapterMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.tagMapper = tagMapper;
        this.aiRecords = aiRecords;
        this.aiReviewService = aiReviewService;
        this.riskCheckService = riskCheckService;
    }

    @Override
    public Map<String, Object> getWorkDetail(Long userId, Long workId)
    {
        SysWork work = requireOwnWork(userId, workId);
        List<SysWorkChapter> chapters = chapterMapper.selectChapterListWithContentByWorkId(workId);

        List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkId(workId);
        SysWorkReviewRecord latest = records.isEmpty() ? null : records.get(0);

        Map<String, Object> result = new LinkedHashMap<>();
        // 详情同时返回审核派生状态和独立的上架状态，供 App/PC 区分“审核通过”和“已上架”。
        String shelfStatus = work.getStatus();
        // 以审核派生状态覆盖 sys_work 原始上架状态，供前端直接渲染
        work.setStatus(deriveStatus(latest));
        result.put("work", work);
        result.put("shelfStatus", shelfStatus);
        // 作品标签（接口文档 2.3.2 输出参数 tags）：仅启用标签，供设置页回填已选标签
        result.put("tags", tagMapper.selectWorkTagRows(List.of(workId)));
        for (SysWorkChapter chapter : chapters)
        {
            // 仅转换返回给客户端的值，保留数据库里的兼容试读配置。
            chapter.setIsFree(ChapterTrialAccess.isOpen(chapter, work.getPreviewEnabled(), work.getPreviewEpisodes())
                    && WorkStatusDeriver.PUBLISHED.equals(work.getStatus()) ? "1" : "0");
        }
        result.put("chapters", chapters);
        result.put("latestReview", latest);
        String blueprint = aiRecords.selectConfirmedBlueprint(userId, workId);
        if (blueprint != null)
        {
            try
            {
                result.put("blueprint", StructuredWritingJson.asMap(StructuredWritingJson.parse(blueprint)));
            }
            catch (AiServiceUnavailableException e)
            {
                log.warn("作品 Blueprint 数据无法解析 workId={}", workId);
            }
        }
        return result;
    }

    @Override
    @Transactional
    public void updateWorkBase(Long userId, Long workId, String title, String summary, String cover,
            List<Integer> tagIds, BigDecimal price, String tradeType, Integer channelId, Integer duration)
    {
        requireOwnWork(userId, workId);
        requireEditable(workId, userId);
        // 风控：标题/简介敏感词检查
        riskCheckService.checkContentOrThrow(userId,
                (title == null ? "" : title) + (summary == null ? "" : summary));

        if (price != null && (price.signum() < 0 || price.scale() > 2
                || price.compareTo(new BigDecimal("9999999999.99")) > 0))
        {
            throw new IllegalArgumentException("定价须为 0 到 9999999999.99，最多两位小数");
        }
        if (tradeType != null && !List.of("exclusive", "non_exclusive", "adaptation").contains(tradeType))
        {
            throw new IllegalArgumentException("请选择有效的合作方式");
        }
        if (channelId != null && workMapper.countEnabledChannel(channelId) == 0)
        {
            throw new IllegalArgumentException("频道不存在或已停用");
        }
        if (duration != null && duration <= 0)
        {
            throw new IllegalArgumentException("单集时长（秒）必须为正整数");
        }
        SysWork update = new SysWork();
        update.setPrice(price);
        update.setTradeType(tradeType);
        update.setWorkId(workId);
        update.setTitle(title);
        update.setSummary(summary);
        update.setCover(cover);
        update.setChannelId(channelId);
        update.setDuration(duration);
        update.setUpdateBy(String.valueOf(userId));
        update.setUpdateTime(new Date());
        workMapper.updateWorkBaseInfo(update);

        if (tagIds != null)
        {
            replaceWorkTags(userId, workId, tagIds);
        }
    }

    /**
     * 全量覆盖作品标签：主分类 1 + 主题 2 + 角色 2 + 情节 2（最多 7 个）。
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
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (Integer tagId : uniqueTagIds)
        {
            SysTag tag = tagMapper.selectTagById(Long.valueOf(tagId));
            if (tag == null)
            {
                throw new IllegalArgumentException("标签不存在或已删除");
            }
            int count = typeCounts.merge(tag.getTagType(), 1, Integer::sum);
            int max = MAX_TAGS_BY_TYPE.getOrDefault(tag.getTagType(), 0);
            if (count > max)
            {
                throw new IllegalArgumentException(TAG_TYPE_NAMES.getOrDefault(tag.getTagType(), tag.getTagType())
                        + "最多选择 " + max + " 个");
            }
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
    public SysWorkChapter addChapter(Long userId, Long workId, String chapterTitle, String content, String summary, String isFree)
    {
        requireOwnWork(userId, workId);
        requireEditable(workId, userId);
        // 风控：章节标题/正文敏感词检查
        riskCheckService.checkContentOrThrow(userId,
                (chapterTitle == null ? "" : chapterTitle) + (content == null ? "" : content));

        int maxNo = chapterMapper.selectMaxChapterNo(workId);
        validateTrialFlag(isFree);

        // 页面先保存过分集骨架时，库里已有同序号的空章；队列按「第N集：标题」回填，直接复用该章，
        // 否则同一集会变成"空章 + 新建正文"两章，作品集数翻倍、首页停在空白第 1 集。
        // 同一集被重新生成（队列重跑）时也要复用已有正文的章，否则每跑一次就多出一章，
        // 集号错位后验收记录、状态板都会记到不存在的集上。
        int targetNo = chapterNoFromTitle(chapterTitle);
        SysWorkChapter blank = targetNo > 0 ? findChapterByNo(workId, targetNo) : null;
        if (blank != null)
        {
            SysWorkChapter fill = new SysWorkChapter();
            fill.setChapterId(blank.getChapterId());
            fill.setChapterTitle(chapterTitle == null ? "" : chapterTitle);
            fill.setContent(content == null ? "" : content);
            fill.setSummary(clipChapterSummary(summary));
            fill.setWordCount(countWords(fill.getContent()));
            if (!Objects.equals(blank.getChapterTitle(), fill.getChapterTitle())
                    || !Objects.equals(blank.getStorageContent(), fill.getStorageContent()))
            {
                fill.setStatus(WorkStatusDeriver.CHAPTER_DRAFT);
                fill.setIsFree(resetTrialFlag(blank));
            }
            fill.setUpdateBy(String.valueOf(userId));
            chapterMapper.updateChapter(fill);
            workMapper.refreshWorkAggregates(workId);
            return chapterMapper.selectChapterDetail(blank.getChapterId());
        }

        SysWorkChapter chapter = new SysWorkChapter();
        chapter.setWorkId(workId);
        chapter.setChapterNo(maxNo + 1);
        chapter.setChapterTitle(chapterTitle == null ? "" : chapterTitle);
        chapter.setContent(content == null ? "" : content);
        chapter.setSummary(clipChapterSummary(summary));
        chapter.setWordCount(countWords(chapter.getContent()));
        chapter.setIsFree("0");
        chapter.setStatus(WorkStatusDeriver.CHAPTER_DRAFT);
        chapter.setCreateBy(String.valueOf(userId));
        chapter.setCreateTime(new Date());
        chapterMapper.insertChapter(chapter);
        // 章节增删改后回写作品聚合列（word_count/episode_count），否则作品恒显示 0 字
        workMapper.refreshWorkAggregates(workId);
        return chapter;
    }

    private static String resetTrialFlag(SysWorkChapter chapter)
    {
        return ChapterTrialAccess.EXPLICITLY_CLOSED.equals(chapter.getIsFree())
                ? ChapterTrialAccess.EXPLICITLY_CLOSED : "0";
    }

    /** 从「第N集：标题」取集号，取不到返回 0（普通新增章节不参与复用）。 */
    private int chapterNoFromTitle(String title)
    {
        if (title == null || title.isBlank())
        {
            return 0;
        }
        Matcher matcher = Pattern.compile("第\\s*(\\d{1,3})\\s*集").matcher(title);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    /**
     * 找同序号的章节：队列重生成同一集时覆盖原章，页面上已存的空骨架同样命中。
     * 同一集只保留一章，避免集号漂移（验收记录与状态板都按集号归属）。
     */
    private SysWorkChapter findChapterByNo(Long workId, int chapterNo)
    {
        SysWorkChapter query = new SysWorkChapter();
        query.setWorkId(workId);
        List<SysWorkChapter> chapters = chapterMapper.selectChapterListByWorkId(query);
        if (chapters == null)
        {
            return null;
        }
        for (SysWorkChapter item : chapters)
        {
            if (item.getChapterNo() != null && item.getChapterNo() == chapterNo)
            {
                return chapterMapper.selectChapterDetail(item.getChapterId());
            }
        }
        return null;
    }

    @Override
    @Transactional
    public void updateChapter(Long userId, Long workId, Long chapterId, String chapterTitle, String content, String summary, String isFree)
    {
        requireOwnWork(userId, workId);
        requireEditable(workId, userId);

        SysWorkChapter chapter = requireOwnChapter(workId, chapterId);
        String mergedTitle = chapterTitle == null ? chapter.getChapterTitle() : chapterTitle;
        String mergedContent = content == null ? chapter.getStorageContent() : content;
        validateTrialFlag(isFree);
        boolean contentChanged = !Objects.equals(mergedTitle, chapter.getChapterTitle())
                || !Objects.equals(mergedContent, chapter.getStorageContent());
        SysWorkChapter update = new SysWorkChapter();
        update.setChapterId(chapterId);
        if (contentChanged)
        {
            riskCheckService.checkContentOrThrow(userId, mergedTitle + mergedContent);
            update.setChapterTitle(mergedTitle);
            update.setContent(mergedContent);
            update.setWordCount(countWords(update.getContent()));
            update.setStatus(WorkStatusDeriver.CHAPTER_DRAFT);
            update.setIsFree(resetTrialFlag(chapter));
        }
        else if (isFree != null && (chapterTitle == null && content == null && summary == null
                || !Objects.equals(isFree, chapter.getIsFree())))
        {
            if (chapterMapper.setApprovedChapterTrial(chapterId, workId, isFree, String.valueOf(userId)) == 0)
            {
                throw new IllegalStateException("章节审核通过后才能设置试读，请刷新后重试");
            }
            if (summary == null)
            {
                return;
            }
        }
        if (!contentChanged && summary == null)
        {
            return;
        }
        // 摘要：null 表示本次不修改；空串按清空（落库 null），超过 500 字符截断
        if (summary != null)
        {
            update.setSummary(clipChapterSummaryForUpdate(summary));
        }
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
        // 风控：提交前整体敏感词检查
        riskCheckService.checkContentOrThrow(userId, content);
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
        item.put("workType", work.getWorkType());
        item.put("wordCount", work.getWordCount());
        item.put("status", derivedStatus);
        item.put("shelfStatus", work.getStatus());
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

    private void validateTrialFlag(String isFree)
    {
        if (isFree != null && !"0".equals(isFree) && !"1".equals(isFree))
        {
            throw new IllegalArgumentException("isFree 只能为 \"0\" 或 \"1\"");
        }
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

    /** 校验作品可编辑：仅「审核中」锁定，其余状态（含审核通过）均允许编辑 */
    private void requireEditable(Long workId, Long userId)
    {
        List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkId(workId);
        if (records.isEmpty())
        {
            return;
        }
        if (WorkStatusDeriver.isReviewing(records.get(0).getStatus()))
        {
            throw new IllegalStateException("作品正在审核中，不允许编辑");
        }
    }

    /** 由最新审核记录推导作品生命周期状态（口径见 {@link WorkStatusDeriver}） */
    private String deriveStatus(SysWorkReviewRecord latest)
    {
        return WorkStatusDeriver.derive(latest == null ? null : latest.getStatus());
    }

    /** 章节摘要：新增时空白按 null；超过 500 字符截断（列 varchar(500)） */
    private static String clipChapterSummary(String summary)
    {
        if (summary == null)
        {
            return null;
        }
        String trimmed = summary.trim();
        if (trimmed.isEmpty())
        {
            return null;
        }
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    /** 章节摘要：更新时空串保留为空串（表示清空），超过 500 字符截断 */
    private static String clipChapterSummaryForUpdate(String summary)
    {
        if (summary == null)
        {
            return null;
        }
        String trimmed = summary.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
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

    @Override
    @Transactional
    public void changeShelfStatus(Long userId, Long workId, String status)
    {
        SysWork work = requireOwnWork(userId, workId);

        if (!WorkStatusDeriver.ON_SHELF.equals(status) && !WorkStatusDeriver.OFF_SHELF.equals(status))
        {
            throw new IllegalArgumentException("非法的上下架状态");
        }

        // 重新上架：平台强制下架的作品（reject_reason 有值）禁止作者自助上架；
        // 同时必须已通过审核，避免绕过审核流程直接把未过审作品上架
        if (WorkStatusDeriver.ON_SHELF.equals(status))
        {
            if (work.getRejectReason() != null && !work.getRejectReason().isBlank())
            {
                throw new IllegalStateException("作品因违规被平台下架，请修改后重新提交审核");
            }
            List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkId(workId);
            String latest = records.isEmpty() ? null : records.get(0).getStatus();
            if (!WorkStatusDeriver.APPROVED.equals(latest))
            {
                throw new IllegalStateException("作品尚未通过审核，不能上架");
            }
        }

        // 下架只降低可见性（作者申请下架），无前置条件，也不改审核记录 → 可自助重新上架、免重审
        SysWork update = new SysWork();
        update.setWorkId(workId);
        update.setStatus(status);
        update.setUpdateBy(String.valueOf(userId));
        update.setUpdateTime(new Date());
        workMapper.updateWorkStatus(update);

        // 只有「下架 → 上架」这一次转换才广播，重复点已上架的作品不再打扰关注者
        boolean firstPublish = WorkStatusDeriver.ON_SHELF.equals(status)
                && !WorkStatusDeriver.ON_SHELF.equals(work.getStatus());
        if (firstPublish && eventPublisher != null)
        {
            eventPublisher.publishEvent(new WorkPublishedEvent(workId, work.getAuthorId(),
                    work.getTitle(), work.getAuthorName()));
        }
    }
}
