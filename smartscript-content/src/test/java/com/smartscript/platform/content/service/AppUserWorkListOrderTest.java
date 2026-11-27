package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysTagMapper;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.mapper.SysWorkReviewRecordMapper;
import com.smartscript.platform.content.service.impl.AppUserWorkServiceImpl;
import com.smartscript.platform.review.service.AiReviewService;
import com.smartscript.platform.review.service.RiskCheckService;

/**
 * 「我的作品」排序：新建作品（update_time 为 NULL 或最新）必须排在最前。
 *
 * 排序由 selectWorkListByAuthor（order by coalesce(update_time, create_time) desc, work_id desc）
 * 完成；本测试锁定服务层使用该顺序，且不回退成按 create_time 重排（后者会把新作品沉到最后）。
 */
class AppUserWorkListOrderTest
{
    private static final Long OWNER = 7L;

    private SysContentWorkMapper workMapper;
    private SysWorkReviewRecordMapper reviewRecordMapper;
    private AppUserWorkServiceImpl service;

    @BeforeEach
    void setUp()
    {
        workMapper = mock(SysContentWorkMapper.class);
        reviewRecordMapper = mock(SysWorkReviewRecordMapper.class);
        service = new AppUserWorkServiceImpl(workMapper, mock(SysWorkChapterMapper.class),
                reviewRecordMapper, mock(SysTagMapper.class), mock(AppAiWriteRecordMapper.class),
                mock(AiReviewService.class), mock(RiskCheckService.class));
        when(reviewRecordMapper.selectByWorkIds(anyList())).thenReturn(List.of());
    }

    private static SysWork workOf(long workId, String title)
    {
        SysWork work = new SysWork();
        work.setWorkId(workId);
        work.setAuthorId(OWNER);
        work.setTitle(title);
        work.setWorkType("script");
        work.setStatus("off_shelf");
        work.setWordCount(0);
        return work;
    }

    @Test
    @DisplayName("列表保持 Mapper 排序：update_time 为 NULL 的新作品仍排第一位")
    void keepsMapperOrderWithNewestFirst()
    {
        // 与 SQL 同序：新作品（update_time NULL → coalesce 取 create_time）排在最前，其次按 work_id 倒序。
        SysWork newest = workOf(9L, "刚创建");
        SysWork tied = workOf(3L, "同时间");
        SysWork older = workOf(4L, "旧作品");
        List<SysWork> ordered = new ArrayList<>(List.of(newest, tied, older));
        when(workMapper.selectWorkListByAuthor(OWNER)).thenReturn(ordered);

        List<Map<String, Object>> result = service.listWorks(OWNER, null, null, null);

        assertEquals(List.of(9L, 3L, 4L), result.stream().map(item -> item.get("workId")).toList());
    }

    @Test
    @DisplayName("状态筛选不改动剩余条目的相对顺序")
    void statusFilterKeepsOrder()
    {
        SysWork newest = workOf(9L, "刚创建");
        SysWork older = workOf(4L, "旧作品");
        when(workMapper.selectWorkListByAuthor(OWNER)).thenReturn(List.of(newest, older));

        List<Map<String, Object>> result = service.listWorks(OWNER, "draft", null, null);

        assertEquals(List.of(9L, 4L), result.stream().map(item -> item.get("workId")).toList());
        assertEquals("draft", result.get(0).get("status"));
    }
}
