package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.WorkGenEpisode;
import com.smartscript.platform.content.domain.WorkGenJob;

/**
 * 作品批量生成任务（后台队列）。
 *
 * 与前端「前台 for 循环逐集调 SSE」的区别：
 *   1. 任务落库，提交后由后端线程池执行，关闭页面/刷新/断网不中断；
 *   2. 每集生成后立即落库，不依赖前端保存草稿；
 *   3. 每集结束回写进度，重开页面可查「跑到第几集」；
 *   4. 单集失败自动重试一次，仍失败则暂停并保留已完成章节，可续跑。
 *
 * @author smartscript
 */
public interface IWorkGenJobService
{
    /** 创建并启动任务（同一作品同时只允许一个执行中任务）。 */
    WorkGenJob create(Long userId, Long workId, WorkGenJob request);

    /** 查询任务（作品与任务ID）。 */
    WorkGenJob get(Long workId, Long jobId);

    /** 查作品最近一条任务（页面刷新后恢复进度）。 */
    WorkGenJob latest(Long workId);

    /** 续跑暂停/失败/取消的任务（已完成集不重复生成）。 */
    WorkGenJob resume(Long userId, Long workId, Long jobId);

    /** 取消任务（当前集完成后停止）。 */
    WorkGenJob cancel(Long workId, Long jobId);

    /** 解析 episodes JSON。 */
    List<WorkGenEpisode> parseEpisodes(String json);

    /** 序列化 episodes。 */
    String writeEpisodes(List<WorkGenEpisode> episodes);
}
