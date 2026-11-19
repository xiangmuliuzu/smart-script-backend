package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysWorkTimelineEvent;

/**
 * 作品时间线服务（只追加）。
 */
public interface IWorkTimelineService
{
    /**
     * 保存某集产出的事件：先删该集原有事件（整集重写的唯一例外），再按顺序追加；
     * 同时把本集兑现的旧事件标为已兑现。
     *
     * @param closes 本集兑现的旧事件关键词，可为空
     */
    void saveEpisodeEvents(Long workId, Integer chapterNo,
            List<SysWorkTimelineEvent> events, List<String> closes);

    /** 查询事件，status/beforeNo/limit 可空 */
    List<SysWorkTimelineEvent> list(Long workId, String status, Integer beforeNo, Integer limit);

    /** 组装注入续写的时间线上下文；没有事件返回空串 */
    String promptBlock(Long workId, Integer beforeNo);

    /** 未兑现条数 */
    int countOpen(Long workId, Integer beforeNo);
}
