package com.smartscript.platform.content.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 作品 / 章节状态口径派生工具单元测试（纯 JUnit，无 Spring 容器）。
 *
 * 覆盖 derive 派生、isReviewing 审核中判定、isReaderVisible 读者可见真值表、
 * isChapterReaderVisible 章节可见判定。口径需与 SQL 侧 readerVisibleWork 片段保持一致。
 */
class WorkStatusDeriverTest
{
    @Test
    @DisplayName("derive：无记录（null/空串/空白）→ draft")
    void deriveBlankToDraft()
    {
        assertEquals("draft", WorkStatusDeriver.derive(null));
        assertEquals("draft", WorkStatusDeriver.derive(""));
        assertEquals("draft", WorkStatusDeriver.derive("   "));
    }

    @Test
    @DisplayName("derive：pending/ai_reviewing → reviewing")
    void deriveReviewing()
    {
        assertEquals("reviewing", WorkStatusDeriver.derive("pending"));
        assertEquals("reviewing", WorkStatusDeriver.derive("ai_reviewing"));
    }

    @Test
    @DisplayName("derive：approved → published")
    void deriveApprovedToPublished()
    {
        assertEquals("published", WorkStatusDeriver.derive("approved"));
    }

    @Test
    @DisplayName("derive：revision/rejected 原样返回")
    void derivePassThrough()
    {
        assertEquals("revision", WorkStatusDeriver.derive("revision"));
        assertEquals("rejected", WorkStatusDeriver.derive("rejected"));
    }

    @Test
    @DisplayName("isReviewing：pending/ai_reviewing 为真，其余为假")
    void isReviewingOnlyForPendingOrAiReviewing()
    {
        assertTrue(WorkStatusDeriver.isReviewing("pending"));
        assertTrue(WorkStatusDeriver.isReviewing("ai_reviewing"));
        assertFalse(WorkStatusDeriver.isReviewing("approved"));
        assertFalse(WorkStatusDeriver.isReviewing("rejected"));
        assertFalse(WorkStatusDeriver.isReviewing(null));
    }

    @Test
    @DisplayName("isReaderVisible：仅 on_shelf 且最新审核 approved 时读者可见")
    void isReaderVisibleTruthTable()
    {
        assertTrue(WorkStatusDeriver.isReaderVisible("on_shelf", "approved"));
        assertFalse(WorkStatusDeriver.isReaderVisible("on_shelf", "pending"));
        assertFalse(WorkStatusDeriver.isReaderVisible("off_shelf", "approved"));
        assertFalse(WorkStatusDeriver.isReaderVisible(null, "approved"));
        assertFalse(WorkStatusDeriver.isReaderVisible("on_shelf", null));
    }

    @Test
    @DisplayName("isChapterReaderVisible：非草稿（非 \"1\"）章节可见")
    void isChapterReaderVisibleOnlyDraftHidden()
    {
        assertTrue(WorkStatusDeriver.isChapterReaderVisible("0"));
        assertFalse(WorkStatusDeriver.isChapterReaderVisible("1"));
        assertTrue(WorkStatusDeriver.isChapterReaderVisible(null));
    }
}
