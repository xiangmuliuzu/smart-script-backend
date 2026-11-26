package com.smartscript.platform.trade.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.content.domain.SysTag;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.dto.AppChapterDto;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppPreviewDto;
import com.smartscript.platform.content.dto.AppStoryboardDto;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.service.IAppBookstoreService;
import com.smartscript.platform.content.service.IAppWorkStoryboardService;
import com.smartscript.platform.trade.service.TradeInquiryService;

class EnterpriseAgentToolRegistryTest
{
    private final IAppBookstoreService books = mock(IAppBookstoreService.class);
    private final SysWorkChapterMapper chapters = mock(SysWorkChapterMapper.class);
    private final IAppWorkStoryboardService boards = mock(IAppWorkStoryboardService.class);
    private final TradeInquiryService inquiries = mock(TradeInquiryService.class);
    private final EnterpriseAgentToolRegistry tools = new EnterpriseAgentToolRegistry(books, chapters, boards, inquiries);

    @Test
    void searchesAcrossPagesWithBudgetEpisodesTradeAndShelfFilters()
    {
        AppWorkDto expensive = work(2, 51000, 60, true);
        AppWorkDto shortWork = work(3, 1000, 49, true);
        AppWorkDto closed = work(4, 40000, 60, false);
        AppWorkDto hidden = work(5, 49000, 80, true);
        hidden.setStatus("off_shelf");
        AppWorkDto accepted = work(6, 50000, 80, true);
        accepted.setCover("https://cdn.example.com/covers/work-6.jpg");
        accepted.setSummary("悬疑".repeat(120));
        accepted.setCopyrightRegistrationStatus("registered");
        accepted.setCopyrightCertNo("TEST-6");
        when(books.pageWorks(any(), eq(1), eq(50))).thenReturn(AppPageResult.of(51,
            List.of(expensive, shortWork, closed, hidden)));
        when(books.pageWorks(any(), eq(2), eq(50))).thenReturn(AppPageResult.of(51, List.of(accepted)));
        when(books.getWork(6L)).thenReturn(accepted);
        when(inquiries.hasCopyright(6L)).thenReturn(true);

        var result = tools.execute("search_works",
            "{\"keyword\":\"悬疑\",\"maxPriceYuan\":50000,\"minEpisodes\":50,\"maxEpisodes\":80,\"tradeOnly\":true}", 1001);
        assertEquals(6, tools.definitions().size());
        assertEquals("work_list", result.card().type());
        var found = rows(result.data(), "works");
        assertEquals(1, found.size());
        var hit = found.get(0);
        assertEquals(6L, hit.get("workId"));
        assertEquals("https://cdn.example.com/covers/work-6.jpg", hit.get("cover"));
        assertEquals(200, hit.get("summary").toString().length());
        assertEquals("registered", hit.get("copyrightRegistrationStatus"));
        assertEquals("TEST-6", hit.get("copyrightCertNo"));
        assertEquals(true, hit.get("requiresLicenseType"));
        assertEquals("待核实", hit.get("exclusiveStatus"));
        verify(books, never()).getWorkDetail(anyLong());
        verify(books, never()).getWork(5L);
    }

    @Test
    void defaultSearchIncludesNonTradeWorksExpandsSiblingTagsAndMatchesGenreKeyword()
    {
        SysTag tag10 = tag(10L, "种田");
        SysTag tag291 = tag(291L, "种田");
        when(books.listTags(null, null)).thenReturn(List.of(tag10, tag291));

        AppWorkDto farmBook = work(73, 888, 3, false);
        farmBook.setTitle("1111");
        when(books.pageWorks(argThat(q -> q != null && Long.valueOf(10L).equals(q.getTagId())
            && (q.getKeyword() == null || q.getKeyword().isEmpty())), eq(1), eq(50)))
            .thenReturn(AppPageResult.of(1, List.of(farmBook)));
        when(books.pageWorks(argThat(q -> q != null && !Long.valueOf(10L).equals(q.getTagId())), eq(1), eq(50)))
            .thenReturn(AppPageResult.of(0, List.of()));
        when(books.getWork(73L)).thenReturn(farmBook);

        // 1) 直接搜「种田文」自动匹配到「种田」标签，且默认包含 tradeEnabled=0 的在架作品
        var byKeyword = tools.execute("search_works", "{\"keyword\":\"种田文\"}", 1001);
        var keywordWorks = rows(byKeyword.data(), "works");
        assertEquals(1, keywordWorks.size());
        assertEquals(73L, keywordWorks.get(0).get("workId"));
        assertEquals(false, keywordWorks.get(0).get("canInquire"));

        // 2) 指定同名标签 291（即使带冗余关键词「种田文」）也能自动扩展查到标签 10 下的作品
        var bySiblingTag = tools.execute("search_works", "{\"tagId\":291,\"keyword\":\"种田文\"}", 1001);
        var siblingWorks = rows(bySiblingTag.data(), "works");
        assertEquals(1, siblingWorks.size());
        assertEquals(73L, siblingWorks.get(0).get("workId"));
    }

    @Test
    void comparesReferencePricesAgainstAnExplicitBudget()
    {
        when(books.getWork(6L)).thenReturn(work(6, 50000, 80, true));
        when(books.getWork(7L)).thenReturn(work(7, 50001, 80, true));
        var result = tools.execute("compare_works",
            "{\"workIds\":[6,7],\"criteria\":\"悬疑，预算上限：50000 元\"}", 1001);
        assertEquals("work_comparison", result.card().type());
        var found = rows(result.data(), "works");
        assertEquals(true, found.get(0).get("budgetMatch"));
        assertEquals(false, found.get(1).get("budgetMatch"));
    }

    @Test
    void publicPreviewOmitsPaidChaptersAndUnapprovedStoryboardContent()
    {
        AppWorkDto work = work(6, 1000, 30, true);
        work.setPreviewEnabled("1");
        work.setPreviewEpisodes(4);
        when(books.getWork(6L)).thenReturn(work);
        var preview = new AppPreviewDto();
        preview.setPreviewChapters(List.of(candidate(1, false), candidate(2, true), candidate(3, true),
            candidate(4, true), candidate(5, true)));
        when(books.getPreview(work)).thenReturn(preview);
        when(chapters.selectChapterDetail(2L)).thenReturn(chapter(2, "2", "CLOSED"));
        when(chapters.selectChapterDetail(3L)).thenReturn(chapter(3, "1", "文".repeat(1700)));
        when(chapters.selectChapterDetail(4L)).thenReturn(chapter(4, "0", "明确开放的作品试读范围"));
        when(chapters.selectChapterDetail(5L)).thenReturn(chapter(5, "1", "超过两章上限"));
        var approved = new AppStoryboardDto();
        approved.setStatus("approved");
        approved.setChapterTitle("第一条分镜");
        approved.setContent("SECRET STORYBOARD BODY");
        var pending = new AppStoryboardDto();
        pending.setStatus("pending");
        when(boards.list(null, 6L)).thenReturn(List.of(approved, pending));

        var result = tools.execute("get_work_preview", "{\"workId\":6}", 1001);
        var publicChapters = rows(result.data(), "chapters");
        assertEquals(2, publicChapters.size());
        assertEquals(1500, publicChapters.get(0).get("content").toString().length());
        assertFalse(result.data().toString().contains("CLOSED"));
        assertEquals(1, rows(result.data(), "storyboards").size());
        assertFalse(result.data().toString().contains("SECRET STORYBOARD BODY"));
        verify(chapters, never()).selectChapterDetail(1L);
        verify(chapters, never()).selectChapterDetail(5L);
        verify(books, never()).getChapterContent(anyLong());
    }

    @Test
    void validatesAndBuildsBriefWithoutAnyBusinessWrite()
    {
        String brief = """
            {"title":"古装权谋短剧","taskType":"script","genreTags":["权谋"],"totalEpisodes":60,"budgetYuan":45000.50,
             "durationDays":30,"commercialGoal":"面向短剧投流","requirement":"三幕主线","deliveryChecklist":["人物小传"]}
            """;
        var result = tools.execute("draft_procurement_brief", brief, 1001);
        assertEquals("demand_brief", result.card().type());
        assertEquals(new BigDecimal("45000.50"), result.card().payload().get("budgetYuan"));
        var invalid = tools.execute("draft_procurement_brief", brief.replace("45000.50", "-1"), 1001);
        assertNull(invalid.card());
        assertTrue(invalid.summary().contains("金额"));
        assertNull(tools.execute("draft_procurement_brief", brief.replace("\"script\"", "\"publish\""), 1001).card());
        verifyNoInteractions(books, inquiries, chapters, boards);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Object payload, String key)
    {
        return (List<Map<String, Object>>) ((Map<?, ?>) payload).get(key);
    }

    private static AppWorkDto work(long id, int price, int episodes, boolean trade)
    {
        var work = new AppWorkDto();
        work.setWorkId(id);
        work.setAuthorId(1L);
        work.setTitle("悬疑剧 " + id);
        work.setStatus("on_shelf");
        work.setPrice(BigDecimal.valueOf(price));
        work.setTradeEnabled(trade ? "1" : "0");
        work.setEpisodeCount(episodes);
        return work;
    }

    private static AppChapterDto candidate(long id, boolean readable)
    {
        var row = new AppChapterDto();
        row.setChapterId(id);
        row.setChapterNo((int) id);
        row.setReadable(readable);
        return row;
    }

    private static SysWorkChapter chapter(int no, String isFree, String text)
    {
        var chapter = new SysWorkChapter();
        chapter.setWorkId(6L);
        chapter.setStatus("0");
        chapter.setChapterNo(no);
        chapter.setIsFree(isFree);
        chapter.setContent(text);
        return chapter;
    }

    private static SysTag tag(long id, String name)
    {
        var tag = new SysTag();
        tag.setTagId(id);
        tag.setTagName(name);
        tag.setStatus("0");
        return tag;
    }
}
