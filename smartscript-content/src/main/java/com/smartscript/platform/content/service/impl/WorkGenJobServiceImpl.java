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
        AppAiWriteResult result = callWrite(userId, workId, prompt);
        String text = result == null ? "" : result.getContent();

        // 触达单次输出上限时自动续写一次（队列无人值守，不弹确认框）。
        if (result != null && result.isTruncated() && text != null && !text.isBlank())
        {
            String continuation = WorkGenPromptSupport.buildContinuation(text, job, episode, ledger);
            AppAiWriteResult more = callWrite(userId, workId, continuation);
            if (more != null && more.getContent() != null)
            {
                text = text + WorkGenPromptSupport.stripRepeatedHead(more.getContent(), text);
            }
        }

        text = WorkGenPromptSupport.clampEpisodeText(
                WorkGenPromptSupport.stripRepeatedTail(text == null ? "" : text, prevTail));
        if (text.length() < MIN_BODY)
        {
            throw new IllegalStateException("AI 未返回可用正文");
        }

        SysWorkChapter chapter = workService.addChapter(userId, workId, episode.getTitle(), text, "", "0");
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

    private AppAiWriteResult callWrite(Long userId, Long workId, String prompt)
    {
        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt(prompt);
        request.setWorkId(workId);
        request.setType("script");
        return aiWriteService.write(request);
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
