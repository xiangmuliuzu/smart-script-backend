package com.smartscript.platform.content.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import com.smartscript.platform.content.domain.WorkSummary;

/**
 * 示例作品仓储（A6 演示用，只读）。
 *
 * 为什么是内存实现：内容表结构与字段命名尚未确认（A6-Q1），按 `rules.md` §2.3
 * 不得为演示编造表结构与字段，因此先用内存只读数据打通「统一身份 -> 业务模块 -> App」
 * 这条链路。**这是明确的临时实现**，内容表建立后应替换为 MyBatis 查询，
 * 并同步删除本类（已登记在 A5-A7-后续加固清单 A6-02）。
 *
 * 语义约束：本仓储只提供**公开可读**的作品摘要，不承载用户私有数据，
 * 因此不涉及数据归属判断；私有数据（书架等）由服务层按身份归属处理。
 */
@Repository
public class SampleWorkRepository
{
    private static final List<WorkSummary> WORKS = List.of(
            new WorkSummary(1L, "示例剧本·长夜", "示例作者甲", "都市", 128000, true),
            new WorkSummary(2L, "示例剧本·潮汐", "示例作者乙", "悬疑", 96000, true),
            new WorkSummary(3L, "示例剧本·归途", "示例作者丙", "年代", 154000, false),
            new WorkSummary(4L, "示例剧本·星轨", "示例作者丁", "科幻", 88000, true));

    /** 公开作品列表；分页由调用方按 page/pageSize 裁剪，此处返回全量示例数据。 */
    public List<WorkSummary> listPublic()
    {
        return WORKS;
    }

    /** 按 ID 查询公开作品；不存在返回空。 */
    public Optional<WorkSummary> findPublic(Long workId)
    {
        if (workId == null)
        {
            return Optional.empty();
        }
        return WORKS.stream().filter(w -> workId.equals(w.getWorkId())).findFirst();
    }

    /** 示例数据总量，用于分页返回 total。 */
    public int count()
    {
        return WORKS.size();
    }
}
