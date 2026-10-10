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
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.mapper.SysWorkReviewRecordMapper;
import com.smartscript.platform.content.service.IAppUserWorkService;

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
 *   approved    -> published（审核通过，只读）
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

    /** 可编辑状态：无记录（草稿）/ 待修改 / 已驳回 */
    private static final java.util.Set<String> EDITABLE_STATUS = java.util.Set.of("revision", "rejected");

    private final SysContentWorkMapper workMapper;
    private final SysWorkChapterMapper chapterMapper;
    private final SysWorkReviewRecordMapper reviewRecordMapper;

    public AppUserWorkServiceImpl(SysContentWorkMapper workMapper,
            SysWorkChapterMapper chapterMapper,
            SysWorkReviewRecordMapper reviewRecordMapper)
    {
        this.workMapper = workMapper;
        this.chapterMapper = chapterMapper;
        this.reviewRecordMapper = reviewRecordMapper;
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
        result.put("chapters", chapters);
        result.put("latestReview", latest);
        return result;
    }

    @Override
    public void updateWorkBase(Long userId, Long workId, String title, String summary, Integer genreId)
    {
        SysWork work = requireOwnWork(userId, workId);
        requireEditable(workId, userId);

        SysWork update = new SysWork();
        update.setWorkId(workId);
        update.setTitle(title);
        update.setSummary(summary);
        update.setGenreId(genreId);
        update.setUpdateBy(String.valueOf(userId));
        update.setUpdateTime(new Date());
        workMapper.updateWorkBaseInfo(update);
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
        update.setContent(content == null ? chapter.getContent() : content);
        update.setWordCount(countWords(update.getContent()));
        update.setUpdateBy(String.valueOf(userId));
        update.setUpdateTime(new Date());
        chapterMapper.updateChapter(update);
    }

    @Override
    @Transactional
    public void deleteChapter(Long userId, Long workId, Long chapterId)
    {
        requireOwnWork(userId, workId);
        requireEditable(workId, userId);
        requireOwnChapter(workId, chapterId);
        chapterMapper.deleteChapterById(chapterId);
    }

    @Override
    @Transactional
    public void submitReview(Long userId, Long workId)
    {
        requireOwnWork(userId, workId);

        // 审核中不允许重复提交
        if (reviewRecordMapper.countPendingByWorkId(workId) > 0)
        {
            throw new IllegalStateException("作品正在审核中，请勿重复提交");
        }
        // 已审核通过的作品不允许重新提交（需先走平台流程）
        List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkId(workId);
        if (!records.isEmpty() && "approved".equals(records.get(0).getStatus()))
        {
            throw new IllegalStateException("作品已审核通过，无需重复提交");
        }

        // 云端模型：只写审核记录，不修改 sys_work.status
        SysWorkReviewRecord record = new SysWorkReviewRecord();
        record.setReviewNo(generateReviewNo());
        record.setTargetType("work");
        record.setTargetId(workId);
        record.setSubmitterId(userId);
        record.setReviewResult("pending");
        record.setStatus("pending");
        reviewRecordMapper.insertRecord(record);
    }

    @Override
    public List<SysWorkReviewRecord> listReviewRecords(Long userId, Long workId)
    {
        requireOwnWork(userId, workId);
        return reviewRecordMapper.selectByWorkId(workId);
    }

    @Override
    public List<Map<String, Object>> listWorks(Long userId, String statusFilter)
    {
        List<SysWork> works = workMapper.selectWorkListByAuthor(userId);
        if (works.isEmpty())
        {
            return List.of();
        }

        // 批量取最新审核记录，按作品ID分组（记录按创建时间倒序，每组第一条即最新）
        List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkIds(
                works.stream().map(SysWork::getWorkId).toList());
        Map<Long, SysWorkReviewRecord> latestByWork = new java.util.HashMap<>();
        for (SysWorkReviewRecord record : records)
        {
            if (!latestByWork.containsKey(record.getTargetId()))
            {
                latestByWork.put(record.getTargetId(), record);
            }
        }

        String filter = statusFilter == null || statusFilter.isBlank() ? "all" : statusFilter.trim();
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (SysWork work : works)
        {
            SysWorkReviewRecord latest = latestByWork.get(work.getWorkId());
            String derived = deriveStatus(latest);
            if (!"all".equals(filter) && !filter.equals(derived))
            {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("workId", work.getWorkId());
            item.put("title", work.getTitle());
            item.put("summary", work.getSummary());
            item.put("genreId", work.getGenreId());
            item.put("genreName", work.getGenreName());
            item.put("workType", work.getWorkType());
            item.put("wordCount", work.getWordCount());
            item.put("status", derived);
            item.put("createTime", work.getCreateTime());
            item.put("updateTime", work.getUpdateTime());
            result.add(item);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 私有辅助
    // ------------------------------------------------------------------

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

    /** 校验作品可编辑：无审核记录（草稿）或最近状态为 待修改/已驳回 */
    private void requireEditable(Long workId, Long userId)
    {
        List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkId(workId);
        if (records.isEmpty())
        {
            return;
        }
        String latest = records.get(0).getStatus();
        if (!EDITABLE_STATUS.contains(latest))
        {
            throw new IllegalStateException("当前状态（" + latest + "）不允许编辑");
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
        return (int) trimmed.chars().filter(c -> !Character.isWhitespace(c)).count();
    }

    /** 生成审核编号：RV + yyyyMMdd + 3位当天序号（与云端样例 RV20260920003 一致） */
    private String generateReviewNo()
    {
        String prefix = "RV" + new SimpleDateFormat("yyyyMMdd").format(new Date());
        int today = reviewRecordMapper.countTodayByPrefix(prefix);
        return prefix + String.format("%03d", today + 1);
    }
}
