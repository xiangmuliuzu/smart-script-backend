package com.smartscript.platform.trade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.domain.SysDemandStage;
import com.smartscript.platform.trade.mapper.SysDemandMapper;
import com.smartscript.platform.trade.mapper.SysDemandStageMapper;

/**
 * 验收查阅交付作品（selectStageDeliveredWork）服务层单元测试。
 *
 * 交付作品一定未上架（deliverStage 拒 on_shelf 交付），content 域读者/作者接口都取不到，
 * 所以发布者只能走 trade 域这个口；口径必须锁死三件事：
 *   - 只有该任务的发布者能看，别人（含创作者本人以外的第三方）一律拒；
 *   - 正文只下发本轮 episodeTo 内的集，超范围的只给标题、readable=false、content=null；
 *   - 富文本过 ReviewContentText.plain，与送 AI 审核同一份文本，不把 HTML 原样丢给前端。
 */
class DemandDeliverableWorkVisibilityTest
{
    private static final long DEMAND_ID = 56L;
    private static final long STAGE_ID = 80L;
    private static final long PUBLISHER_ID = 9091L;
    private static final long WORK_ID = 92L;

    private SysDemandMapper demandMapper;
    private SysDemandStageMapper stageMapper;
    private TradeDemandService service;

    @BeforeEach
    void setUp()
    {
        demandMapper = mock(SysDemandMapper.class);
        stageMapper = mock(SysDemandStageMapper.class);
        service = new TradeDemandService();
        ReflectionTestUtils.setField(service, "demandMapper", demandMapper);
        ReflectionTestUtils.setField(service, "stageMapper", stageMapper);

        SysDemand demand = new SysDemand();
        demand.setDemandId(DEMAND_ID);
        demand.setClientId(PUBLISHER_ID);
        when(demandMapper.selectDemandById(DEMAND_ID)).thenReturn(demand);

        Map<String, Object> work = new HashMap<>();
        work.put("workId", WORK_ID);
        work.put("title", "待验收的剧本");
        work.put("episodeCount", 3);
        when(stageMapper.selectDeliveredWorkBrief(WORK_ID)).thenReturn(work);
    }

    private SysDemandStage stage(Integer episodeTo, Long workId)
    {
        SysDemandStage s = new SysDemandStage();
        s.setStageId(STAGE_ID);
        s.setDemandId(DEMAND_ID);
        s.setStageOrder(1);
        s.setEpisodeTo(episodeTo);
        s.setWorkId(workId);
        s.setStatus("submitted");
        s.setDeliverableDesc("第一版草稿");
        return s;
    }

    /**
     * 造章节行。故意把三行的 content 都填上正文（相当于 SQL 那边的 CASE 截断失效），
     * 用来验证服务层是第二道闸：readable=0 的行就算拿到正文也不能下发。
     */
    private List<Map<String, Object>> chapters(Integer episodeTo)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int no = 1; no <= 3; no++)
        {
            boolean readable = episodeTo == null || no <= episodeTo;
            Map<String, Object> row = new HashMap<>();
            row.put("chapterId", (long) no);
            row.put("chapterNo", no);
            row.put("chapterTitle", "第" + no + "集");
            row.put("wordCount", 100 * no);
            row.put("readable", readable ? 1 : 0);
            // 第 1 集是带版本标记的富文本（走纯文本转换），第 2 集是历史正文（原样透传）
            row.put("content", no == 1
                    ? "<div data-script-document=\"1\"><p>第1集正文&amp;台词</p></div>"
                    : "第" + no + "集正文");
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> chaptersOf(Map<String, Object> result)
    {
        return (List<Map<String, Object>>) result.get("chapters");
    }

    @Test
    @DisplayName("非发布者查不到交付作品，且不落到章节查询")
    void rejectsNonPublisher()
    {
        when(stageMapper.selectByStageId(STAGE_ID)).thenReturn(stage(2, WORK_ID));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.selectStageDeliveredWork(DEMAND_ID, STAGE_ID, 8888L));

        assertTrue(e.getMessage().contains("只有发布者"), e.getMessage());
        verify(stageMapper, never()).selectChaptersForReview(anyLong(), any());
    }

    @Test
    @DisplayName("轮次不属于该任务时拒绝，防止越权拿别单的稿子")
    void rejectsStageOfAnotherDemand()
    {
        SysDemandStage other = stage(2, WORK_ID);
        other.setDemandId(999L);
        when(stageMapper.selectByStageId(STAGE_ID)).thenReturn(other);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.selectStageDeliveredWork(DEMAND_ID, STAGE_ID, PUBLISHER_ID));
        assertTrue(e.getMessage().contains("轮次与任务不匹配"), e.getMessage());
    }

    @Test
    @DisplayName("本轮还没交付作品时报文案，而不是空壳页")
    void rejectsUndeliveredStage()
    {
        when(stageMapper.selectByStageId(STAGE_ID)).thenReturn(stage(2, null));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.selectStageDeliveredWork(DEMAND_ID, STAGE_ID, PUBLISHER_ID));
        assertTrue(e.getMessage().contains("本轮尚未交付作品"), e.getMessage());
    }

    @Test
    @DisplayName("作品已被删除时拒绝下发")
    void rejectsMissingWork()
    {
        when(stageMapper.selectByStageId(STAGE_ID)).thenReturn(stage(2, WORK_ID));
        when(stageMapper.selectDeliveredWorkBrief(WORK_ID)).thenReturn(null);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.selectStageDeliveredWork(DEMAND_ID, STAGE_ID, PUBLISHER_ID));
        assertTrue(e.getMessage().contains("交付作品不存在"), e.getMessage());
    }

    @Test
    @DisplayName("正文只到本轮 episodeTo，之后的集留标题但读不到内容")
    void truncatesContentAtEpisodeTo()
    {
        when(stageMapper.selectByStageId(STAGE_ID)).thenReturn(stage(2, WORK_ID));
        when(stageMapper.selectChaptersForReview(WORK_ID, 2)).thenReturn(chapters(2));

        Map<String, Object> result =
                service.selectStageDeliveredWork(DEMAND_ID, STAGE_ID, PUBLISHER_ID);
        List<Map<String, Object>> chapters = chaptersOf(result);

        assertEquals(3, chapters.size());
        assertEquals(2, result.get("readableCount"));
        assertEquals(2, result.get("episodeTo"));

        Map<String, Object> first = chapters.get(0);
        assertEquals(Boolean.TRUE, first.get("readable"));
        // 富文本按送 AI 审核同一口径转纯文本，标签与转义都不留给前端渲染
        assertEquals("第1集正文&台词", first.get("content"));
        // 无版本标记的历史正文原样透传，不做二次处理
        assertEquals("第2集正文", chapters.get(1).get("content"));

        Map<String, Object> last = chapters.get(2);
        assertEquals(Boolean.FALSE, last.get("readable"));
        assertNull(last.get("content"), "超出本轮集数的章节不得下发正文（即使 SQL 漏卡边界，服务层也要置空）");
        assertEquals("第3集", last.get("chapterTitle"), "未交付的集要保留标题，让发布者知道后面还剩几集");
    }

    @Test
    @DisplayName("未限定集数的历史轮次全本可读")
    void wholeWorkReadableWhenEpisodeToIsNull()
    {
        when(stageMapper.selectByStageId(STAGE_ID)).thenReturn(stage(null, WORK_ID));
        when(stageMapper.selectChaptersForReview(WORK_ID, null)).thenReturn(chapters(null));

        Map<String, Object> result =
                service.selectStageDeliveredWork(DEMAND_ID, STAGE_ID, PUBLISHER_ID);

        assertEquals(3, result.get("readableCount"));
        assertTrue(chaptersOf(result).stream().allMatch(c -> Boolean.TRUE.equals(c.get("readable"))));
    }

    @Test
    @DisplayName("出参带上轮次与交付信息，验收页不用再回查轮次接口")
    void returnsStageContext()
    {
        when(stageMapper.selectByStageId(STAGE_ID)).thenReturn(stage(2, WORK_ID));
        when(stageMapper.selectChaptersForReview(eq(WORK_ID), eq(2))).thenReturn(chapters(2));

        Map<String, Object> result =
                service.selectStageDeliveredWork(DEMAND_ID, STAGE_ID, PUBLISHER_ID);

        assertEquals(STAGE_ID, result.get("stageId"));
        assertEquals(1, result.get("stageOrder"));
        assertEquals("submitted", result.get("stageStatus"));
        assertEquals("第一版草稿", result.get("deliverableDesc"));
        assertEquals("待验收的剧本", result.get("title"));
        assertFalse(result.containsKey("content"), "作品概要不应混入整篇正文字段");
    }
}
