package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.domain.SysWorkReviewRecord;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.mapper.SysWorkReviewRecordMapper;
import com.smartscript.platform.content.service.impl.AppUserWorkServiceImpl;

/**
 * A2「作品详情与审核流程」服务层单元测试（云端模型版）。
 *
 * 云端模型：审核状态由 sys_review_record 最新一条 status 推导
 * （无记录=draft / pending|ai_reviewing=reviewing / revision / rejected / approved=published），
 * 提交审核只写审核记录、不修改 sys_work.status。
 */
class AppUserWorkServiceTest
{
    private static final Long OWNER = 1L;
    private static final Long OTHER = 2L;
    private static final Long WORK_ID = 1001L;

    private SysContentWorkMapper workMapper;
    private SysWorkChapterMapper chapterMapper;
    private SysWorkReviewRecordMapper reviewRecordMapper;
    private AppUserWorkServiceImpl service;

    @BeforeEach
    void setUp()
    {
        workMapper = mock(SysContentWorkMapper.class);
        chapterMapper = mock(SysWorkChapterMapper.class);
        reviewRecordMapper = mock(SysWorkReviewRecordMapper.class);
        service = new AppUserWorkServiceImpl(workMapper, chapterMapper, reviewRecordMapper);
    }

    private SysWork workOf()
    {
        SysWork work = new SysWork();
        work.setWorkId(WORK_ID);
        work.setAuthorId(OWNER);
        work.setStatus("on_shelf");
        work.setTitle("测试作品");
        return work;
    }

    private SysWorkReviewRecord recordOf(String status)
    {
        SysWorkReviewRecord rec = new SysWorkReviewRecord();
        rec.setReviewId(9L);
        rec.setStatus(status);
        return rec;
    }

    @Test
    @DisplayName("作品详情：无审核记录 → 派生状态 draft，返回章节与 latestReview=null")
    void getWorkDetailDraftWhenNoRecord()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(chapterMapper.selectChapterListWithContentByWorkId(WORK_ID)).thenReturn(List.of());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of());

        Map<String, Object> result = service.getWorkDetail(OWNER, WORK_ID);

        assertEquals("draft", ((SysWork) result.get("work")).getStatus());
        assertNotNull(result.get("chapters"));
        assertNull(result.get("latestReview"));
    }

    @Test
    @DisplayName("作品详情：pending → 派生状态 reviewing")
    void getWorkDetailDerivesReviewingFromPending()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(chapterMapper.selectChapterListWithContentByWorkId(WORK_ID)).thenReturn(List.of());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("pending")));

        Map<String, Object> result = service.getWorkDetail(OWNER, WORK_ID);

        assertEquals("reviewing", ((SysWork) result.get("work")).getStatus());
        assertEquals("pending", ((SysWorkReviewRecord) result.get("latestReview")).getStatus());
    }

    @Test
    @DisplayName("作品详情：ai_reviewing → 派生状态 reviewing")
    void getWorkDetailDerivesReviewingFromAiReviewing()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(chapterMapper.selectChapterListWithContentByWorkId(WORK_ID)).thenReturn(List.of());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("ai_reviewing")));

        Map<String, Object> result = service.getWorkDetail(OWNER, WORK_ID);

        assertEquals("reviewing", ((SysWork) result.get("work")).getStatus());
    }

    @Test
    @DisplayName("作品详情：approved → 派生状态 published")
    void getWorkDetailDerivesPublishedFromApproved()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(chapterMapper.selectChapterListWithContentByWorkId(WORK_ID)).thenReturn(List.of());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("approved")));

        Map<String, Object> result = service.getWorkDetail(OWNER, WORK_ID);

        assertEquals("published", ((SysWork) result.get("work")).getStatus());
    }

    @Test
    @DisplayName("作品详情：revision → 派生状态 revision")
    void getWorkDetailDerivesRevision()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(chapterMapper.selectChapterListWithContentByWorkId(WORK_ID)).thenReturn(List.of());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("revision")));

        Map<String, Object> result = service.getWorkDetail(OWNER, WORK_ID);

        assertEquals("revision", ((SysWork) result.get("work")).getStatus());
    }

    @Test
    @DisplayName("作品详情：非本人作品抛 SecurityException")
    void getWorkDetailRejectsOtherUser()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        assertThrows(SecurityException.class, () -> service.getWorkDetail(OTHER, WORK_ID));
    }

    @Test
    @DisplayName("作品不存在抛 IllegalArgumentException")
    void getWorkDetailRejectsMissingWork()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.getWorkDetail(OWNER, WORK_ID));
    }

    @Test
    @DisplayName("修改基本信息：无审核记录（草稿）允许")
    void updateWorkBaseAllowedInDraft()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of());
        service.updateWorkBase(OWNER, WORK_ID, "新标题", "新简介", 5);
        verify(workMapper).updateWorkBaseInfo(any(SysWork.class));
    }

    @Test
    @DisplayName("修改基本信息：revision（待修改）允许")
    void updateWorkBaseAllowedInRevision()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("revision")));
        service.updateWorkBase(OWNER, WORK_ID, "新标题", "新简介", 5);
        verify(workMapper).updateWorkBaseInfo(any(SysWork.class));
    }

    @Test
    @DisplayName("修改基本信息：pending（审核中）禁止")
    void updateWorkBaseRejectedInPending()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("pending")));
        assertThrows(IllegalStateException.class,
                () -> service.updateWorkBase(OWNER, WORK_ID, "新标题", "新简介", 5));
        verify(workMapper, never()).updateWorkBaseInfo(any());
    }

    @Test
    @DisplayName("修改基本信息：approved（已通过）禁止")
    void updateWorkBaseRejectedInApproved()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("approved")));
        assertThrows(IllegalStateException.class,
                () -> service.updateWorkBase(OWNER, WORK_ID, "新标题", "新简介", 5));
        verify(workMapper, never()).updateWorkBaseInfo(any());
    }

    @Test
    @DisplayName("新增章节：章节序号从 max+1 递增，字数按非空白字符统计")
    void addChapterIncrementsChapterNoAndCountsWords()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of());
        when(chapterMapper.selectMaxChapterNo(WORK_ID)).thenReturn(3);

        SysWorkChapter created = service.addChapter(OWNER, WORK_ID, "第四章", "这是第四章内容。Hello world!");

        assertEquals(4, created.getChapterNo());
        assertEquals("这是第四章内容。Helloworld!", created.getContent().replace(" ", ""));
        verify(chapterMapper).insertChapter(any(SysWorkChapter.class));
    }

    @Test
    @DisplayName("删除章节：非本人作品章节抛 SecurityException")
    void deleteChapterRejectsOtherUser()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        assertThrows(SecurityException.class, () -> service.deleteChapter(OTHER, WORK_ID, 1L));
        verify(chapterMapper, never()).deleteChapterById(anyLong());
    }

    @Test
    @DisplayName("删除章节：章节不属于该作品抛 IllegalArgumentException")
    void deleteChapterRejectsWrongChapter()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of());
        SysWorkChapter otherChapter = new SysWorkChapter();
        otherChapter.setChapterId(55L);
        otherChapter.setWorkId(999L);
        when(chapterMapper.selectChapterDetail(55L)).thenReturn(otherChapter);
        assertThrows(IllegalArgumentException.class, () -> service.deleteChapter(OWNER, WORK_ID, 55L));
        verify(chapterMapper, never()).deleteChapterById(anyLong());
    }

    @Test
    @DisplayName("提交审核：无记录可提交，写入 pending 审核记录，不改 sys_work.status")
    void submitReviewWritesRecordOnly()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.countPendingByWorkId(WORK_ID)).thenReturn(0);
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of());
        when(reviewRecordMapper.countTodayByPrefix(anyString())).thenReturn(2);

        service.submitReview(OWNER, WORK_ID);

        verify(reviewRecordMapper).insertRecord(any(SysWorkReviewRecord.class));
    }

    @Test
    @DisplayName("提交审核：revision（待修改）可重新提交")
    void submitReviewAllowedFromRevision()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.countPendingByWorkId(WORK_ID)).thenReturn(0);
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("revision")));

        service.submitReview(OWNER, WORK_ID);

        verify(reviewRecordMapper).insertRecord(any(SysWorkReviewRecord.class));
    }

    @Test
    @DisplayName("提交审核：rejected（已驳回）可重新提交")
    void submitReviewAllowedFromRejected()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.countPendingByWorkId(WORK_ID)).thenReturn(0);
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("rejected")));

        service.submitReview(OWNER, WORK_ID);

        verify(reviewRecordMapper).insertRecord(any(SysWorkReviewRecord.class));
    }

    @Test
    @DisplayName("提交审核：存在审核中记录时拦截")
    void submitReviewRejectedWhenPendingRecordExists()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.countPendingByWorkId(WORK_ID)).thenReturn(1);
        assertThrows(IllegalStateException.class, () -> service.submitReview(OWNER, WORK_ID));
        verify(reviewRecordMapper, never()).insertRecord(any());
    }

    @Test
    @DisplayName("提交审核：approved（已通过）禁止重复提交")
    void submitReviewRejectedWhenApproved()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.countPendingByWorkId(WORK_ID)).thenReturn(0);
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("approved")));
        assertThrows(IllegalStateException.class, () -> service.submitReview(OWNER, WORK_ID));
        verify(reviewRecordMapper, never()).insertRecord(any());
    }

    @Test
    @DisplayName("审核记录：仅本人作品可查询")
    void reviewRecordsRejectsOtherUser()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        assertThrows(SecurityException.class, () -> service.listReviewRecords(OTHER, WORK_ID));
    }

    // ------------------------------------------------------------------
    // 我的作品列表（阶段5新增）
    // ------------------------------------------------------------------

    private SysWork workWith(Long id, String title)
    {
        SysWork work = new SysWork();
        work.setWorkId(id);
        work.setAuthorId(OWNER);
        work.setTitle(title);
        return work;
    }

    private SysWorkReviewRecord recordWith(Long targetId, String status)
    {
        SysWorkReviewRecord rec = new SysWorkReviewRecord();
        rec.setReviewId(targetId);
        rec.setTargetId(targetId);
        rec.setStatus(status);
        return rec;
    }

    @Test
    @DisplayName("作品列表：无审核记录 → 全部派生 draft，status=all 不过滤")
    void listWorksDerivesDraftWhenNoRecord()
    {
        when(workMapper.selectWorkListByAuthor(OWNER))
                .thenReturn(List.of(workWith(101L, "作品A"), workWith(102L, "作品B")));
        when(reviewRecordMapper.selectByWorkIds(any())).thenReturn(List.of());

        List<Map<String, Object>> list = service.listWorks(OWNER, "all");

        assertEquals(2, list.size());
        assertEquals("draft", list.get(0).get("status"));
        assertEquals("draft", list.get(1).get("status"));
        assertEquals("作品A", list.get(0).get("title"));
    }

    @Test
    @DisplayName("作品列表：按派生状态过滤 reviewing，仅返回审核中作品")
    void listWorksFiltersByStatus()
    {
        when(workMapper.selectWorkListByAuthor(OWNER))
                .thenReturn(List.of(workWith(101L, "草稿作品"), workWith(102L, "审核作品")));
        when(reviewRecordMapper.selectByWorkIds(any()))
                .thenReturn(List.of(recordWith(102L, "pending")));

        List<Map<String, Object>> list = service.listWorks(OWNER, "reviewing");

        assertEquals(1, list.size());
        assertEquals(102L, list.get(0).get("workId"));
        assertEquals("reviewing", list.get(0).get("status"));
    }

    @Test
    @DisplayName("作品列表：approved 记录 → published；空作品集合返回空列表")
    void listWorksPublishedAndEmpty()
    {
        when(workMapper.selectWorkListByAuthor(OWNER))
                .thenReturn(List.of(workWith(103L, "已上架作品")));
        when(reviewRecordMapper.selectByWorkIds(any()))
                .thenReturn(List.of(recordWith(103L, "approved")));

        List<Map<String, Object>> published = service.listWorks(OWNER, "published");
        assertEquals(1, published.size());
        assertEquals("published", published.get(0).get("status"));

        when(workMapper.selectWorkListByAuthor(OWNER)).thenReturn(List.of());
        assertEquals(0, service.listWorks(OWNER, "all").size());
    }
}
