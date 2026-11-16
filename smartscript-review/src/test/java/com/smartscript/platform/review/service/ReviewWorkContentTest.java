package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.ReviewRecord;
import com.smartscript.platform.review.domain.vo.WorkChapterVO;
import com.smartscript.platform.review.domain.vo.WorkContentVO;
import com.smartscript.platform.review.mapper.ReviewRecordMapper;
import com.smartscript.platform.review.mapper.ReviewWorkContentMapper;
import com.smartscript.platform.review.util.ReviewContentText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 人工审核作品内容查询测试。
 * 覆盖：内容组装、章节正文富文本转纯文本、记录不存在返回 null。
 */
@ExtendWith(MockitoExtension.class)
class ReviewWorkContentTest {

    @Mock
    private ReviewRecordMapper reviewRecordMapper;

    @Mock
    private ReviewWorkContentMapper reviewWorkContentMapper;

    @InjectMocks
    private ReviewService reviewService;

    private WorkChapterVO chapter(Long id, int no, String title, String raw) {
        WorkChapterVO c = new WorkChapterVO();
        c.setChapterId(id);
        c.setChapterNo(no);
        c.setChapterTitle(title);
        c.setContent(raw);
        return c;
    }

    @Test
    void 富文本正文_转纯文本() {
        String html = "<div data-script-document=\"1\"><p>第一章内容</p><p>第二段</p></div>";
        String plain = ReviewContentText.plain(html);
        assertTrue(plain.contains("第一章内容"));
        assertTrue(plain.contains("第二段"));
        assertFalse(plain.contains("<"));
    }

    @Test
    void 历史纯文本正文_原样返回() {
        assertEquals("普通正文", ReviewContentText.plain("普通正文"));
        assertEquals("", ReviewContentText.plain(null));
    }

    @Test
    void 组装审核内容_章节正文已转纯文本() {
        ReviewRecord rec = new ReviewRecord();
        rec.setReviewId(7L);
        rec.setTargetType("work");
        rec.setTargetId(84L);
        when(reviewRecordMapper.selectReviewRecordById(7L)).thenReturn(rec);

        WorkContentVO work = new WorkContentVO();
        work.setWorkId(84L);
        work.setTitle("测试剧本");
        work.setSummary("一个测试简介");
        when(reviewWorkContentMapper.selectWorkContent(84L)).thenReturn(work);

        String html = "<div data-script-document=\"1\"><p>正文A</p></div>";
        List<WorkChapterVO> chapters = Arrays.asList(
                chapter(1L, 1, "第一章", html),
                chapter(2L, 2, "第二章", "正文B"));
        when(reviewWorkContentMapper.selectWorkChapters(84L)).thenReturn(chapters);

        Map<String, Object> data = reviewService.selectReviewContent(7L);

        assertNotNull(data);
        assertEquals(7L, data.get("reviewId"));
        assertEquals(84L, data.get("workId"));
        assertSame(work, data.get("work"));
        @SuppressWarnings("unchecked")
        List<WorkChapterVO> out = (List<WorkChapterVO>) data.get("chapters");
        assertEquals(2, out.size());
        assertEquals("正文A", out.get(0).getContent());
        assertEquals("正文B", out.get(1).getContent());
    }

    @Test
    void 审核记录不存在_返回null() {
        when(reviewRecordMapper.selectReviewRecordById(99L)).thenReturn(null);
        assertNull(reviewService.selectReviewContent(99L));
    }
}
