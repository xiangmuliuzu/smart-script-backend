package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.smartscript.platform.content.domain.SysWorkTimelineEvent;
import com.smartscript.platform.content.mapper.SysWorkTimelineEventMapper;
import com.smartscript.platform.content.service.IWorkTimelineService;
import com.smartscript.platform.content.service.WorkTimelineSupport;

/**
 * 作品时间线服务实现。
 */
@Service
public class WorkTimelineServiceImpl implements IWorkTimelineService
{
    private static final Logger log = LoggerFactory.getLogger(WorkTimelineServiceImpl.class);

    private final SysWorkTimelineEventMapper mapper;

    public WorkTimelineServiceImpl(SysWorkTimelineEventMapper mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveEpisodeEvents(Long workId, Integer chapterNo,
            List<SysWorkTimelineEvent> events, List<String> closes)
    {
        if (workId == null || chapterNo == null)
        {
            return;
        }
        // 整集重写：该集事件整体重建，历史集不动
        mapper.deleteByChapter(workId, chapterNo);
        if (events != null)
        {
            int seq = 1;
            for (SysWorkTimelineEvent event : events)
            {
                if (!StringUtils.hasText(event.getFact()))
                {
                    continue;
                }
                event.setWorkId(workId);
                event.setChapterNo(chapterNo);
                event.setSeq(seq++);
                if (!StringUtils.hasText(event.getEventType()))
                {
                    event.setEventType("其他");
                }
                if (!StringUtils.hasText(event.getStatus()))
                {
                    event.setStatus(SysWorkTimelineEvent.STATUS_OPEN);
                }
                mapper.insertEvent(event);
            }
        }
        if (closes != null)
        {
            for (String keyword : closes)
            {
                if (!StringUtils.hasText(keyword) || keyword.length() < 2)
                {
                    continue;
                }
                int rows = mapper.resolveByKeyword(workId, chapterNo, keyword.trim());
                if (rows > 0)
                {
                    log.info("时间线兑现 workId={} 第{}集 关键词={} 关闭{}条", workId, chapterNo, keyword, rows);
                }
            }
        }
    }

    @Override
    public List<SysWorkTimelineEvent> list(Long workId, String status, Integer beforeNo, Integer limit)
    {
        return mapper.selectByWork(workId, status, beforeNo, limit);
    }

    @Override
    public String promptBlock(Long workId, Integer beforeNo)
    {
        if (workId == null)
        {
            return "";
        }
        List<SysWorkTimelineEvent> open = mapper.selectByWork(workId,
                SysWorkTimelineEvent.STATUS_OPEN, beforeNo, 100);
        // 未兑现按集号升序：越早埋下的坑越先注入，避免只看到最近几集而漏掉开篇伏笔。
        open.sort(java.util.Comparator.comparingInt(SysWorkTimelineEvent::getChapterNo)
                .thenComparingInt(e -> e.getSeq() == null ? 0 : e.getSeq()));
        List<SysWorkTimelineEvent> recent = mapper.selectByWork(workId, null, beforeNo,
                WorkTimelineSupport.RECENT_LIMIT);
        return WorkTimelineSupport.block(recent, open);
    }

    @Override
    public int countOpen(Long workId, Integer beforeNo)
    {
        return mapper.countOpen(workId, beforeNo);
    }
}
