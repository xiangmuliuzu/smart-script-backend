package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
import com.smartscript.platform.content.mapper.SysTagMapper;
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
    private SysTagMapper tagMapper;
    private com.smartscript.platform.review.service.AiReviewService aiReviewService;
    private AppUserWorkServiceImpl service;

    @BeforeEach
    void setUp()
    {
        workMapper = mock(SysContentWorkMapper.class);
        chapterMapper = mock(SysWorkChapterMapper.class);
        reviewRecordMapper = mock(SysWorkReviewRecordMapper.class);
        tagMapper = mock(SysTagMapper.class);
        aiReviewService = mock(com.smartscript.platform.review.service.AiReviewService.class);
        service = new AppUserWorkServiceImpl(workMapper, chapterMapper, reviewRecordMapper, tagMapper, aiReviewService);
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

    /** 带 rejectReason 的作品（平台强制下架场景） */
    private SysWork workOf(String rejectReason)
    {
        SysWork work = workOf();
        work.setRejectReason(rejectReason);
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
        service.updateWorkBase(OWNER, WORK_ID, "新标题", "新简介", 5, null, null);
        verify(workMapper).updateWorkBaseInfo(any(SysWork.class));
    }

    @Test
    @DisplayName("修改基本信息：revision（待修改）允许")
    void updateWorkBaseAllowedInRevision()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("revision")));
        service.updateWorkBase(OWNER, WORK_ID, "新标题", "新简介", 5, null, null);
        verify(workMapper).updateWorkBaseInfo(any(SysWork.class));
    }

    @Test
    @DisplayName("修改基本信息：pending（审核中）禁止")
    void updateWorkBaseRejectedInPending()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("pending")));
        assertThrows(IllegalStateException.class,
                () -> service.updateWorkBase(OWNER, WORK_ID, "新标题", "新简介", 5, null, null));
        verify(workMapper, never()).updateWorkBaseInfo(any());
    }

    @Test
    @DisplayName("修改基本信息：approved（已发布）可编辑封面后重新送审")
    void updateWorkBaseAllowedInApproved()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("approved")));
        service.updateWorkBase(OWNER, WORK_ID, "新标题", "新简介", 5, "/profile/upload/cover.png", null);
        verify(workMapper).updateWorkBaseInfo(argThat(work -> "/profile/upload/cover.png".equals(work.getCover())));
    }

    @Test
    @DisplayName("设置标签：全量覆盖，先清空再批量写入，重复ID去重")
    void updateWorkBaseReplacesTags()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of());

        service.updateWorkBase(OWNER, WORK_ID, "新标题", "新简介", 5, null, List.of(3, 1, 3));

        verify(tagMapper).deleteWorkTagByWorkId(WORK_ID);
        // 3 重复出现两次，去重后只写 2 条，避免撞 uk_work_tag(work_id, tag_id)
        verify(tagMapper).batchInsertWorkTag(anyLong(),
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(1, 3))), anyString());
        // 标签引用已变化：必须刷新使用数量，保证 use_count 与实际引用接轨
        verify(tagMapper).syncTagUseCount();
    }

    @Test
    @DisplayName("设置标签：空列表表示清空，且不写入新关联")
    void updateWorkBaseClearsTags()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of());

        service.updateWorkBase(OWNER, WORK_ID, "新标题", "新简介", 5, null, List.of());

        verify(tagMapper).deleteWorkTagByWorkId(WORK_ID);
        verify(tagMapper, never()).batchInsertWorkTag(anyLong(), any(), anyString());
        // 清空后引用数归零：同样要刷新使用数量
        verify(tagMapper).syncTagUseCount();
    }

    @Test
    @DisplayName("设置标签：tagIds 未下发（null）时不动标签")
    void updateWorkBaseSkipsTagsWhenAbsent()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of());

        service.updateWorkBase(OWNER, WORK_ID, "新标题", "新简介", 5, null, null);

        verify(tagMapper, never()).deleteWorkTagByWorkId(anyLong());
        // 标签未变动，不应触发使用数量刷新
        verify(tagMapper, never()).syncTagUseCount();
    }

    @Test
    @DisplayName("设置标签：超过 8 个拒绝，且不产生任何删除")
    void updateWorkBaseRejectsTooManyTags()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.updateWorkBase(
                OWNER, WORK_ID, "新标题", "新简介", 5, null, List.of(1, 2, 3, 4, 5, 6, 7, 8, 9)));

        verify(tagMapper, never()).deleteWorkTagByWorkId(anyLong());
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
    @DisplayName("提交审核：写入 ai_reviewing 审核记录，触发异步AI初审")
    void submitReviewWritesRecordOnly()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.countPendingByWorkId(WORK_ID)).thenReturn(0);
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of());
        when(reviewRecordMapper.countTodayByPrefix(anyString())).thenReturn(2);
        when(chapterMapper.selectChapterListWithContentByWorkId(WORK_ID)).thenReturn(List.of());

        service.submitReview(OWNER, WORK_ID);

        org.mockito.ArgumentCaptor<SysWorkReviewRecord> captor =
                org.mockito.ArgumentCaptor.forClass(SysWorkReviewRecord.class);
        verify(reviewRecordMapper).insertRecord(captor.capture());
        assertEquals("ai_reviewing", captor.getValue().getStatus());
        assertEquals("ai_reviewing", captor.getValue().getReviewResult());
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
    @DisplayName("提交审核：approved（已发布）允许改动后重新送审")
    void submitReviewAllowedWhenApproved()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.countPendingByWorkId(WORK_ID)).thenReturn(0);
        service.submitReview(OWNER, WORK_ID);
        verify(reviewRecordMapper).insertRecord(argThat(record -> "ai_reviewing".equals(record.getStatus())));
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

        List<Map<String, Object>> list = service.listWorks(OWNER, "all", null, null);

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

        List<Map<String, Object>> list = service.listWorks(OWNER, "reviewing", null, null);

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

        List<Map<String, Object>> published = service.listWorks(OWNER, "published", null, null);
        assertEquals(1, published.size());
        assertEquals("published", published.get(0).get("status"));

        when(workMapper.selectWorkListByAuthor(OWNER)).thenReturn(List.of());
        assertEquals(0, service.listWorks(OWNER, "all", null, null).size());
    }

    // ------------------------------------------------------------------
    // 上下架与章节两态（作品生命周期重构）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("下架：作者申请下架无前置条件，写入 off_shelf")
    void changeShelfStatusOffShelfSuccess()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("approved")));

        service.changeShelfStatus(OWNER, WORK_ID, "off_shelf");

        verify(workMapper).updateWorkStatus(argThat(w -> "off_shelf".equals(w.getStatus())));
    }

    @Test
    @DisplayName("重新上架：最新审核 approved 且无违规原因，写入 on_shelf")
    void changeShelfStatusOnShelfSuccess()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("approved")));

        service.changeShelfStatus(OWNER, WORK_ID, "on_shelf");

        verify(workMapper).updateWorkStatus(argThat(w -> "on_shelf".equals(w.getStatus())));
    }

    @Test
    @DisplayName("重新上架：平台强制下架（reject_reason 非空）→ IllegalStateException 且不写库")
    void changeShelfStatusOnShelfBlockedByRejectReason()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf("违规内容"));

        assertThrows(IllegalStateException.class, () -> service.changeShelfStatus(OWNER, WORK_ID, "on_shelf"));
        verify(workMapper, never()).updateWorkStatus(any());
    }

    @Test
    @DisplayName("重新上架：最新审核非 approved → IllegalStateException 且不写库")
    void changeShelfStatusOnShelfBlockedWhenNotApproved()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of(recordOf("pending")));

        assertThrows(IllegalStateException.class, () -> service.changeShelfStatus(OWNER, WORK_ID, "on_shelf"));
        verify(workMapper, never()).updateWorkStatus(any());
    }

    @Test
    @DisplayName("上下架：非法 status 参数 → IllegalArgumentException 且不写库")
    void changeShelfStatusRejectsIllegalStatus()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());

        assertThrows(IllegalArgumentException.class, () -> service.changeShelfStatus(OWNER, WORK_ID, "draft"));
        verify(workMapper, never()).updateWorkStatus(any());
    }

    @Test
    @DisplayName("新增章节：默认 status='1'（草稿，仅作者可见）")
    void addChapterDefaultsToDraftStatus()
    {
        when(workMapper.selectWorkById(WORK_ID)).thenReturn(workOf());
        when(reviewRecordMapper.selectByWorkId(WORK_ID)).thenReturn(List.of());
        when(chapterMapper.selectMaxChapterNo(WORK_ID)).thenReturn(0);
        when(chapterMapper.insertChapter(any(SysWorkChapter.class))).thenReturn(1);

        service.addChapter(OWNER, WORK_ID, "第一章", "内容");

        verify(chapterMapper).insertChapter(argThat(c -> "1".equals(c.getStatus())));
    }
}
