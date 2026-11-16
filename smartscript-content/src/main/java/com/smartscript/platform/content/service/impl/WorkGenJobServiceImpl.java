package com.smartscript.platform.content.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.domain.SysWorkLedger;
import com.smartscript.platform.content.domain.WorkGenEpisode;
import com.smartscript.platform.content.domain.WorkGenJob;
import com.smartscript.platform.content.dto.AppAiWriteRequest;
import com.smartscript.platform.content.dto.AppAiWriteResult;
import com.smartscript.platform.content.mapper.WorkGenJobMapper;
import com.smartscript.platform.content.service.IAppUserWorkService;
import com.smartscript.platform.content.service.IAppWorkLedgerService;
import com.smartscript.platform.content.service.IAiWriteService;
import com.smartscript.platform.content.service.IWorkGenJobService;
import com.smartscript.platform.content.service.WorkGenPromptSupport;
import com.smartscript.platform.content.service.WorkGenConsistencySupport;
import com.smartscript.platform.identity.BackgroundIdentity;

/**
 * 作品批量生成队列实现。
 *
 * 执行链路（每集）：读最新台账与上一集正文 → 组装承接提示词 → 调 AI 写正文 →
 * 去重/控字数 → 立即落库成章节 → 生成本章摘要并合并台账 → 回写进度。
 *
 * 异步线程没有 HTTP 请求上下文，这里显式绑定 App 身份，
 * 否则 AiWriteServiceImpl 内部的 identityProvider 取不到 userId（配额与归属校验都会失败）。
 *
 * @author smartscript
 */
@Service
public class WorkGenJobServiceImpl implements IWorkGenJobService
{
    private static final Logger log = LoggerFactory.getLogger(WorkGenJobServiceImpl.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 单集最多尝试次数（首次 + 重试 1 次） */
    private static final int MAX_ATTEMPTS = 2;

    /** 生成正文的最短可接受长度 */
    private static final int MIN_BODY = 50;

    /** 硬事实冲突后的补写次数（只重写该集，不重跑队列） */
    private static final int CONSISTENCY_RETRY = 1;

    @Autowired
    private WorkGenJobMapper jobMapper;

    @Autowired
    private IAppUserWorkService workService;

    @Autowired
    private IAiWriteService aiWriteService;

    @Autowired
    private IAppWorkLedgerService ledgerService;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor executor;

    @Override
    public WorkGenJob create(Long userId, Long workId, WorkGenJob request)
    {
        if (userId == null || workId == null || request == null)
        {
            throw new IllegalArgumentException("缺少作品或任务参数");
        }
        WorkGenJob running = jobMapper.selectRunningByWorkId(workId);
        if (running != null)
        {
            throw new IllegalStateException("该作品已有生成任务在执行（任务 " + running.getJobId() + "）");
        }
        List<WorkGenEpisode> episodes = normalize(request.getEpisodes());
        if (episodes.isEmpty())
        {
            throw new IllegalArgumentException("待生成集列表不能为空");
        }
        WorkGenJob job = new WorkGenJob();
        job.setWorkId(workId);
        job.setUserId(userId);
        job.setStatus(WorkGenJob.STATUS_QUEUED);
        job.setTotal(episodes.size());
        job.setDoneCount(0);
        job.setOutline(request.getOutline());
        job.setCharacters(request.getCharacters());
        job.setRoster(request.getRoster());
        job.setGenre(request.getGenre());
        job.setUserPrompt(request.getUserPrompt());
        job.setModel(request.getModel());
        job.setEpisodes(writeEpisodes(episodes));
        jobMapper.insertJob(job);
        start(workId, job.getJobId());
        return job;
    }

    @Override
    public WorkGenJob get(Long workId, Long jobId)
    {
        return jobMapper.selectByJobId(workId, jobId);
    }

    @Override
    public WorkGenJob latest(Long workId)
    {
        return jobMapper.selectLatestByWorkId(workId);
    }

    @Override
    public WorkGenJob resume(Long userId, Long workId, Long jobId)
    {
        WorkGenJob job = jobMapper.selectByJobId(workId, jobId);
        if (job == null)
        {
            throw new IllegalArgumentException("任务不存在");
        }
        if (WorkGenJob.STATUS_RUNNING.equals(job.getStatus())
                || WorkGenJob.STATUS_QUEUED.equals(job.getStatus()))
        {
            return job;
        }
        List<WorkGenEpisode> episodes = parseEpisodes(job.getEpisodes());
        for (WorkGenEpisode episode : episodes)
        {
            if (WorkGenEpisode.STATUS_FAILED.equals(episode.getStatus())
                    || WorkGenEpisode.STATUS_RUNNING.equals(episode.getStatus()))
            {
                episode.setStatus(WorkGenEpisode.STATUS_PENDING);
                episode.setAttempts(0);
                episode.setError(null);
            }
        }
        job.setStatus(WorkGenJob.STATUS_RUNNING);
        job.setEpisodes(writeEpisodes(episodes));
        job.setLastError(null);
        jobMapper.updateProgress(job);
        start(workId, jobId);
        return job;
    }

    @Override
    public WorkGenJob cancel(Long workId, Long jobId)
    {
        WorkGenJob job = jobMapper.selectByJobId(workId, jobId);
        if (job == null)
        {
            throw new IllegalArgumentException("任务不存在");
        }
        if (!WorkGenJob.STATUS_SUCCEEDED.equals(job.getStatus())
                && !WorkGenJob.STATUS_CANCELLED.equals(job.getStatus()))
        {
            job.setStatus(WorkGenJob.STATUS_CANCELLED);
            job.setLastError("已取消");
            jobMapper.updateProgress(job);
        }
        return job;
    }

    @Override
    public List<WorkGenEpisode> parseEpisodes(String json)
    {
        if (json == null || json.isBlank())
        {
            return new ArrayList<>();
        }
        try
        {
            return JSON.readValue(json, JSON.getTypeFactory().constructCollectionType(List.class, WorkGenEpisode.class));
        }
        catch (Exception e)
        {
            log.warn("解析生成队列失败：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public String writeEpisodes(List<WorkGenEpisode> episodes)
    {
        try
        {
            return JSON.writeValueAsString(episodes);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("序列化生成队列失败");
        }
    }

    /** 提交到线程池（不阻塞 HTTP 请求）。 */
    private void start(Long workId, Long jobId)
    {
        executor.execute(() -> run(workId, jobId));
    }

    /** 任务主循环。 */
    private void run(Long workId, Long jobId)
    {
        WorkGenJob job = jobMapper.selectByJobId(workId, jobId);
        if (job == null)
        {
            return;
        }
        bindIdentity(job.getUserId());
        try
        {
            job.setStatus(WorkGenJob.STATUS_RUNNING);
            jobMapper.updateProgress(job);
            bootstrap(job);
            loop(job);
        }
        catch (Exception e)
        {
            log.warn("生成任务异常中止 jobId={}：{}", jobId, e.getMessage());
            job.setStatus(WorkGenJob.STATUS_PAUSED);
            job.setLastError(clip(e.getMessage(), 900));
            jobMapper.updateProgress(job);
        }
        finally
        {
            BackgroundIdentity.clear();
        }
    }

    /** 逐集执行，每集前后都从库里读一次状态（取消/续跑即时生效）。 */
    private void loop(WorkGenJob job)
    {
        while (true)
        {
            WorkGenJob latest = jobMapper.selectByJobId(job.getWorkId(), job.getJobId());
            if (latest == null || WorkGenJob.STATUS_CANCELLED.equals(latest.getStatus()))
            {
                return;
            }
            List<WorkGenEpisode> episodes = parseEpisodes(latest.getEpisodes());
            WorkGenEpisode next = null;
            for (WorkGenEpisode episode : episodes)
            {
                if (!WorkGenEpisode.STATUS_SUCCEEDED.equals(episode.getStatus()))
                {
                    next = episode;
                    break;
                }
            }
            if (next == null)
            {
                latest.setStatus(WorkGenJob.STATUS_SUCCEEDED);
                latest.setDoneCount(episodes.size());
                latest.setCurrentNo(null);
                latest.setCurrentTitle(null);
                latest.setLastError(null);
                latest.setEpisodes(writeEpisodes(episodes));
                jobMapper.updateProgress(latest);
                return;
            }

            latest.setCurrentNo(next.getNo());
            latest.setCurrentTitle(next.getTitle());
            next.setStatus(WorkGenEpisode.STATUS_RUNNING);
            latest.setEpisodes(writeEpisodes(episodes));
            jobMapper.updateProgress(latest);

            Exception failure = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)
            {
                try
                {
                    executeEpisode(latest, next);
                    next.setStatus(WorkGenEpisode.STATUS_SUCCEEDED);
                    next.setAttempts(attempt);
                    failure = null;
                    break;
                }
                catch (Exception e)
                {
                    failure = e;
                    next.setAttempts(attempt);
                    log.warn("第{}集生成失败（第{}次）jobId={}：{}", next.getNo(), attempt, job.getJobId(), e.getMessage());
                }
            }

            if (failure != null)
            {
                next.setStatus(WorkGenEpisode.STATUS_FAILED);
                next.setError(clip(failure.getMessage(), 300));
                latest.setStatus(WorkGenJob.STATUS_PAUSED);
                latest.setLastError(clip("第" + next.getNo() + "集失败：" + failure.getMessage(), 900));
            }
            latest.setDoneCount(countDone(episodes));
            latest.setEpisodes(writeEpisodes(episodes));
            jobMapper.updateProgress(latest);
            if (failure != null)
            {
                return;
            }
        }
    }

    /** 生成并落库一集。 */
    private void executeEpisode(WorkGenJob job, WorkGenEpisode episode)
    {
        Long userId = job.getUserId();
        Long workId = job.getWorkId();
        List<SysWorkChapter> chapters = workService.listChapters(userId, workId);
        SysWorkLedger ledger = ledgerService.getLedger(workId);
        List<Map<String, Object>> volumes = aiWriteService.listVolumes(workId);
        String prevTail = chapters == null || chapters.isEmpty()
                ? ""
                : WorkGenPromptSupport.chapterTail(chapters.get(chapters.size() - 1).getContent(), 500);

        String prompt = WorkGenPromptSupport.buildEpisodePrompt(job, episode, ledger, chapters, volumes);
        AppAiWriteResult result = callWrite(job, prompt);
        String text = result == null ? "" : result.getContent();

        // 触达单次输出上限时自动续写一次（队列无人值守，不弹确认框）。
        if (result != null && result.isTruncated() && text != null && !text.isBlank())
        {
            String continuation = WorkGenPromptSupport.buildContinuation(text, job, episode, ledger);
            AppAiWriteResult more = callWrite(job, continuation);
            if (more != null && more.getContent() != null)
            {
                text = text + WorkGenPromptSupport.stripRepeatedHead(more.getContent(), text);
            }
        }

        text = cleanEpisode(text, prevTail);
        if (text.length() < MIN_BODY)
        {
            throw new IllegalStateException("AI 未返回可用正文");
        }

        // 硬事实守卫：只重写有冲突的这一集，不重跑队列；最多补写一次，避免无人值守时无限烧 token。
        List<String> conflicts = WorkGenConsistencySupport.check(text, ledger, episode.getNo());
        if (!conflicts.isEmpty())
        {
            log.warn("第{}集硬事实冲突 workId={}：{}", episode.getNo(), workId, conflicts);
        }
        for (int retry = 0; retry < CONSISTENCY_RETRY && !conflicts.isEmpty(); retry++)
        {
            String retryPrompt = prompt
                    + "\n\n【上一版与硬事实清单冲突，必须改正后重写】\n- " + String.join("\n- ", conflicts)
                    + "\n只改冲突处，其余情节按事件卡保留；若确需改变该项状态，必须补写触发它的过渡事件。";
            AppAiWriteResult again = callWrite(job, retryPrompt);
            String next = cleanEpisode(again == null ? "" : again.getContent(), prevTail);
            if (next.length() >= MIN_BODY)
            {
                text = next;
                conflicts = WorkGenConsistencySupport.check(text, ledger, episode.getNo());
            }
        }
        if (!conflicts.isEmpty())
        {
            log.warn("第{}集重写后仍有硬事实冲突，已落库待人工复核：{}", episode.getNo(), conflicts);
        }

        // 标题带上集号：addChapter 靠「第N集」找到页面已保存的同序号空章并复用，否则每集会多建一章
        SysWorkChapter chapter = workService.addChapter(userId, workId,
                "第" + episode.getNo() + "集：" + episode.getTitle(), text, "", "0");
        episode.setChapterId(chapter.getChapterId());

        // 摘要 + 台账合并：下一集的「前情」与「道具状态」靠它，失败不回滚章节，只记录警告。
        try
        {
            aiWriteService.summarizeChapter(workId, chapter.getChapterId(), text, null);
        }
        catch (Exception e)
        {
            log.warn("第{}集摘要生成失败 workId={}：{}", episode.getNo(), workId, e.getMessage());
            episode.setError(clip("摘要/台账更新失败：" + e.getMessage(), 300));
        }
    }

    /** 正文清洗：去上一集首尾重复 → 超长按句收束；首版与重写版共用同一口径。 */
    private String cleanEpisode(String raw, String prevTail)
    {
        return WorkGenPromptSupport.clampEpisodeText(
                WorkGenPromptSupport.stripRepeatedTail(
                        WorkGenPromptSupport.stripRepeatedHead(raw == null ? "" : raw, prevTail), prevTail));
    }

    private AppAiWriteResult callWrite(WorkGenJob job, String prompt)
    {
        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt(prompt);
        request.setWorkId(job.getWorkId());
        request.setType("script");
        request.setModel(job.getModel());
        return aiWriteService.write(request);
    }

    /**
     * 台账基线起草：用户只给题材与要求，不填台账，这里用同一输入与同一模型起草三件套。
     *
     * 只在对应字段为空时写入，已有台账（含人工填写）一律保留；
     * 三项都已存在时直接跳过，不产生额外 AI 调用与扣费。
     */
    private void bootstrap(WorkGenJob job)
    {
        SysWorkLedger ledger = ledgerService.getLedger(job.getWorkId());
        if (ledger == null)
        {
            ledger = new SysWorkLedger();
        }
        boolean needProps = isBlank(ledger.getProps());
        boolean needStyle = isBlank(ledger.getStyleGuide());
        boolean needCharacters = isBlank(ledger.getCharacters()) && isBlank(job.getCharacters());
        if (!needProps && !needStyle && !needCharacters)
        {
            return;
        }
        String prompt = WorkGenPromptSupport.buildBootstrapPrompt(job);
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)
        {
            try
            {
                AppAiWriteResult result = callWrite(job, prompt);
                String raw = result == null ? "" : result.getContent();
                Map<String, String> patch = WorkGenPromptSupport.parseBootstrap(raw);
                boolean hasAny = !patch.get("props").isEmpty() || !patch.get("styleGuide").isEmpty()
                        || !patch.get("characters").isEmpty();
                if (!hasAny)
                {
                    throw new IllegalStateException("AI 未返回可用的台账基线");
                }
                if (needProps && !patch.get("props").isEmpty())
                {
                    ledger.setProps(patch.get("props"));
                }
                if (needStyle && !patch.get("styleGuide").isEmpty())
                {
                    ledger.setStyleGuide(ensureStyleRange(patch.get("styleGuide")));
                }
                if (needCharacters && !patch.get("characters").isEmpty())
                {
                    ledger.setCharacters(patch.get("characters"));
                    if (isBlank(job.getCharacters()))
                    {
                        job.setCharacters(patch.get("characters"));
                    }
                }
                if (isBlank(job.getRoster()) && !patch.get("roster").isEmpty())
                {
                    job.setRoster(patch.get("roster"));
                }
                ledgerService.saveLedger(job.getWorkId(), ledger);
                jobMapper.updateContext(job);
                log.info("台账基线起草完成 workId={}：props={} styleGuide={} characters={}",
                        job.getWorkId(), !patch.get("props").isEmpty(),
                        !patch.get("styleGuide").isEmpty(), !patch.get("characters").isEmpty());
                return;
            }
            catch (Exception e)
            {
                last = e;
                log.warn("台账基线起草失败（第{}次）workId={}：{}", attempt, job.getWorkId(), e.getMessage());
            }
        }
        throw new IllegalStateException("台账基线起草失败：" + (last == null ? "未知原因" : last.getMessage()));
    }

    private static boolean isBlank(String text)
    {
        return text == null || text.isBlank();
    }

    /** 写法基线的字数区间由系统统一，模型写成长篇字数时补一句覆盖，避免与 700-900 字冲突。 */
    private static String ensureStyleRange(String style)
    {
        String text = style == null ? "" : style.trim();
        if (text.isEmpty() || text.matches("(?s).*700\\s*[-~～至到]\\s*900.*"))
        {
            return text;
        }
        return text + " 每集正文 700-900 字，超过 1000 字即不合格。";
    }

    /**
     * 异步线程绑定 App 身份。
     *
     * AiWriteServiceImpl 通过 identityProvider 读 userId 做配额与归属判定；
     * 后台线程没有 HTTP 安全上下文，这里用线程绑定身份，执行结束必须清理。
     */
    private void bindIdentity(Long userId)
    {
        BackgroundIdentity.set(userId);
    }

    /** 规整前端提交的集列表：去空、补状态、按集号排序。 */
    private List<WorkGenEpisode> normalize(String json)
    {
        List<WorkGenEpisode> episodes = parseEpisodes(json);
        List<WorkGenEpisode> valid = new ArrayList<>();
        for (WorkGenEpisode episode : episodes)
        {
            if (episode == null || episode.getTitle() == null || episode.getTitle().isBlank())
            {
                continue;
            }
            if (episode.getNo() == null)
            {
                episode.setNo(valid.size() + 1);
            }
            episode.setStatus(WorkGenEpisode.STATUS_PENDING);
            episode.setAttempts(0);
            episode.setError(null);
            episode.setChapterId(null);
            valid.add(episode);
        }
        valid.sort((a, b) -> Integer.compare(a.getNo(), b.getNo()));
        return valid;
    }

    private int countDone(List<WorkGenEpisode> episodes)
    {
        int done = 0;
        for (WorkGenEpisode episode : episodes)
        {
            if (WorkGenEpisode.STATUS_SUCCEEDED.equals(episode.getStatus()))
            {
                done++;
            }
        }
        return done;
    }

    private static String clip(String text, int limit)
    {
        if (text == null)
        {
            return null;
        }
        return text.length() > limit ? text.substring(0, limit) : text;
    }

    /** 供 Controller 组装进度响应（不含大字段）。 */
    public static Map<String, Object> toProgress(WorkGenJob job, List<WorkGenEpisode> episodes)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", job.getJobId());
        data.put("workId", job.getWorkId());
        data.put("status", job.getStatus());
        data.put("total", job.getTotal());
        data.put("doneCount", job.getDoneCount());
        data.put("currentNo", job.getCurrentNo());
        data.put("currentTitle", job.getCurrentTitle());
        data.put("lastError", job.getLastError());
        data.put("createTime", job.getCreateTime());
        data.put("updateTime", job.getUpdateTime());
        List<Map<String, Object>> list = new ArrayList<>();
        for (WorkGenEpisode episode : episodes)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("no", episode.getNo());
            item.put("title", episode.getTitle());
            item.put("status", episode.getStatus());
            item.put("chapterId", episode.getChapterId());
            item.put("error", episode.getError());
            item.put("attempts", episode.getAttempts());
            list.add(item);
        }
        data.put("episodes", list);
        return data;
    }
}
