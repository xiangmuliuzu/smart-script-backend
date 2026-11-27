package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.SysWorkTimelineEvent;

/**
 * 作品时间线事件 Mapper（只追加）。
 */
public interface SysWorkTimelineEventMapper
{
    /** 整集重写前清空该集事件（唯一允许的删除） */
    int deleteByChapter(@Param("workId") Long workId, @Param("chapterNo") Integer chapterNo);

    /** 追加一条事件 */
    int insertEvent(SysWorkTimelineEvent event);

    /**
     * 按作品查询事件。
     *
     * @param status 非空时按状态过滤（未兑现/已兑现/已失效）
     * @param beforeNo 非空时只返回 chapter_no < beforeNo 的事件
     */
    List<SysWorkTimelineEvent> selectByWork(@Param("workId") Long workId,
            @Param("status") String status, @Param("beforeNo") Integer beforeNo,
            @Param("limit") Integer limit);

    /** 本集兑现旧事件：把该集之前、状态未兑现、fact 命中关键词的事件标为已兑现 */
    int resolveByKeyword(@Param("workId") Long workId, @Param("beforeNo") Integer beforeNo,
            @Param("keyword") String keyword);

    /** 统计未兑现条数（收尾与验收用） */
    int countOpen(@Param("workId") Long workId, @Param("beforeNo") Integer beforeNo);
}
