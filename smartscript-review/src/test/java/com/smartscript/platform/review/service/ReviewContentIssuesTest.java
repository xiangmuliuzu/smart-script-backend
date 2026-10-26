package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.ReviewRecord;
import com.smartscript.platform.review.domain.vo.WorkChapterVO;
import com.smartscript.platform.review.domain.vo.WorkContentVO;
import com.smartscript.platform.review.mapper.ReviewRecordMapper;
import com.smartscript.platform.review.mapper.ReviewWorkContentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 审核内容接口的章节级问题定位测试
 * 覆盖：命中词定位到具体章节/片段、无问题章节不输出、ai_result 缺失时的兜底、纯文本清洗
 */
@ExtendWith(MockitoExtension.class)
class ReviewContentIssuesTest {

    @Mock
    private ReviewRecordMapper reviewRecordMapper;

    @Mock
    private ReviewWorkContentMapper reviewWorkContentMapper;

    @InjectMocks
    private ReviewService reviewService;

    private ReviewRecord record;
    private WorkChapterVO c1;
    private WorkChapterVO c2;
    private List<WorkChapterVO> chapters;

    @BeforeEach
    void setUp() {
        record = new ReviewRecord();
        record.setReviewId(99L);
        record.setTargetId(7L);
        record.setStatus("rejected");

        c1 = new WorkChapterVO();
        c1.setChapterId(11L);
        c1.setChapterNo(1);
        c1.setChapterTitle("第一章");
        c1.setContent("<div data-script-document=\"1\"><p>他拿起刀，使用暴力解决了所有冲突，甚至开始赌博。</p></div>");

        c2 = new WorkChapterVO();
        c2.setChapterId(12L);
        c2.setChapterNo(2);
        c2.setChapterTitle("第二章");
        c2.setContent("<div data-script-document=\"1\"><p>阳光下的日常，没有任何问题。</p></div>");

        chapters = new ArrayList<>(Arrays.asList(c1, c2));
    }

    private void stubContent() {
        WorkContentVO work = new WorkContentVO();
        work.setSummary("一部充满暴力与赌博元素的黑帮故事。");
        when(reviewRecordMapper.selectReviewRecordById(99L)).thenReturn(record);
        when(reviewWorkContentMapper.selectWorkContent(7L)).thenReturn(work);
        when(reviewWorkContentMapper.selectWorkChapters(7L)).thenReturn(chapters);
    }

    @Test
    void selectReviewContent_命中章节输出问题定位() {
        record.setAiResult("{\"score\":45,\"level\":\"high\",\"keywords\":[\"暴力\",\"赌博\"]}");
        record.setAiSensitiveWords("暴力,赌博");
        stubContent();

        Map<String, Object> data = reviewService.selectReviewContent(99L);

        assertNotNull(data);
        // aiInfo 从 ai_result JSON 解析
        Map<String, Object> aiInfo = (Map<String, Object>) data.get("aiInfo");
        assertEquals(45, aiInfo.get("score"));
        assertEquals("high", aiInfo.get("level"));
        // issues 首项为简介（命中暴力/赌博），随后为问题章节 1，章节 2 不出现
        List<Map<String, Object>> issues = (List<Map<String, Object>>) data.get("issues");
        assertEquals(2, issues.size());
        Map<String, Object> intro = issues.get(0);
        assertEquals(0, intro.get("chapterNo"));
        assertEquals("简介", intro.get("chapterTitle"));
        Map<String, Object> issue = issues.get(1);
        assertEquals(11L, issue.get("chapterId"));
        assertEquals(1, issue.get("chapterNo"));
        // hits 含命中的词与上下文片段（片段必须包含该词）
        List<Map<String, Object>> hits = (List<Map<String, Object>>) issue.get("hits");
        assertTrue(hits.size() >= 2);
        boolean hasViolence = false;
        boolean hasGamble = false;
        for (Map<String, Object> hit : hits) {
            String word = (String) hit.get("word");
            String snippet = (String) hit.get("snippet");
            assertNotNull(snippet);
            assertTrue(snippet.contains(word), "片段应包含命中词");
            if ("暴力".equals(word)) hasViolence = true;
            if ("赌博".equals(word)) hasGamble = true;
        }
        assertTrue(hasViolence);
        assertTrue(hasGamble);
        // 章节内容已转纯文本（无 <p> 标签）
        List<WorkChapterVO> chs = (List<WorkChapterVO>) data.get("chapters");
        assertFalse(chs.get(0).getContent().contains("<p>"));
    }

    @Test
    void selectReviewContent_无命中词_issues为空() {
        record.setAiResult("{\"score\":100,\"level\":\"low\",\"keywords\":[]}");
        record.setAiSensitiveWords(null);
        stubContent();

        Map<String, Object> data = reviewService.selectReviewContent(99L);

        assertNotNull(data);
        assertEquals(0, ((List<?>) data.get("issues")).size());
        assertEquals(100, ((Map<String, Object>) data.get("aiInfo")).get("score"));
    }

    @Test
    void selectReviewContent_aiResult为null_不抛错且用敏感词列定位() {
        record.setAiResult(null);
        record.setAiSensitiveWords("暴力");
        stubContent();

        Map<String, Object> data = reviewService.selectReviewContent(99L);

        assertNotNull(data);
        List<Map<String, Object>> issues = (List<Map<String, Object>>) data.get("issues");
        assertEquals(2, issues.size()); // 简介 + 章节1 均命中
        Map<String, Object> intro = issues.get(0);
        assertEquals("简介", intro.get("chapterTitle"));
        assertEquals(1, ((List<?>) intro.get("hits")).size());
        Map<String, Object> chapterIssue = issues.get(1);
        List<Map<String, Object>> hits = (List<Map<String, Object>>) chapterIssue.get("hits");
        assertEquals(1, hits.size());
        assertEquals("暴力", hits.get(0).get("word"));
    }

    @Test
    void selectReviewContent_语义化关键词_拆词定位到原文词() {
        // AI 关键词是语义描述（正文中无整句），应拆出"斗破苍穹"等原文词并命中章节
        record.setAiResult("{\"score\":40,\"level\":\"high\",\"keywords\":[\"疑似《斗破苍穹》原文，版权风险\"]}");
        record.setAiSensitiveWords(null);
        c1.setContent("<div data-script-document=\"1\"><p>主角身处斗破苍穹的世界，展开冒险。</p></div>");
        stubContent();

        Map<String, Object> data = reviewService.selectReviewContent(99L);

        assertNotNull(data);
        Map<String, Object> aiInfo = (Map<String, Object>) data.get("aiInfo");
        // aiInfo.keywords 保留原始语义关键词
        List<String> kws = (List<String>) aiInfo.get("keywords");
        assertTrue(kws.contains("疑似《斗破苍穹》原文，版权风险"));
        // 问题定位命中拆词后的"斗破苍穹"
        List<Map<String, Object>> issues = (List<Map<String, Object>>) data.get("issues");
        assertTrue(issues.size() >= 1);
        Map<String, Object> issue = issues.get(0);
        List<Map<String, Object>> hits = (List<Map<String, Object>>) issue.get("hits");
        assertTrue(hits.stream().anyMatch(h -> "斗破苍穹".equals(h.get("word"))));
    }

    @Test
    void selectReviewContent_记录不存在_返回null() {
        when(reviewRecordMapper.selectReviewRecordById(404L)).thenReturn(null);
        assertNull(reviewService.selectReviewContent(404L));
    }
}
