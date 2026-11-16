package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.WorkGenJob;

/**
 * 作品批量生成任务 Mapper。
 *
 * @author smartscript
 */
public interface WorkGenJobMapper
{
    /** 新增任务（useGeneratedKeys 回填 jobId） */
    int insertJob(WorkGenJob job);

    /** 按作品与任务ID查询 */
    WorkGenJob selectByJobId(@Param("workId") Long workId, @Param("jobId") Long jobId);

    /** 查作品的最近一条任务（页面刷新后恢复进度） */
    WorkGenJob selectLatestByWorkId(@Param("workId") Long workId);

    /** 查同一作品的执行中任务（防止重复提交并发写同一作品） */
    WorkGenJob selectRunningByWorkId(@Param("workId") Long workId);

    /** 更新任务进度与队列快照 */
    int updateProgress(WorkGenJob job);

    /** 台账基线起草后，回填补全的人物设定与白名单（只写这两列） */
    int updateContext(WorkGenJob job);

    /** 列表（管理/调试用） */
    List<WorkGenJob> selectByWorkId(@Param("workId") Long workId);
}
