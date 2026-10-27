package com.smartscript.platform.content.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AI 生成字数上限限长器单元测试（防平台亏损）。
 *
 * 验证流式增量转发在达到上限后停止并标记截断；0 表示不限。
 */
class DeltaLimitTruncatorTest
{
    @Test
    @DisplayName("未超上限：增量全部转发，不截断")
    void belowLimit_allForwarded()
    {
        List<String> received = new ArrayList<>();
        DeltaLimitTruncator limiter = new DeltaLimitTruncator(received::add, 100);
        limiter.accept("你好");
        limiter.accept("世界");
        assertEquals("你好世界", String.join("", received));
        assertEquals(4, limiter.emittedChars());
        assertFalse(limiter.isTruncated());
    }

    @Test
    @DisplayName("跨块超限：只转发到上限并置截断标记，其后不再转发")
    void overLimit_truncatedAtBoundary()
    {
        List<String> received = new ArrayList<>();
        DeltaLimitTruncator limiter = new DeltaLimitTruncator(received::add, 5);
        limiter.accept("ABC");   // 已 3
        limiter.accept("DEFG");  // 只转发 DE（到 5），截断
        limiter.accept("HIJK");  // 不再转发
        assertEquals("ABCDE", String.join("", received));
        assertEquals(5, limiter.emittedChars());
        assertTrue(limiter.isTruncated());
    }

    @Test
    @DisplayName("恰好等于上限：全部转发，不截断")
    void exactlyAtLimit_allForwarded()
    {
        List<String> received = new ArrayList<>();
        DeltaLimitTruncator limiter = new DeltaLimitTruncator(received::add, 3);
        limiter.accept("ABC");
        assertEquals("ABC", String.join("", received));
        assertFalse(limiter.isTruncated());
    }

    @Test
    @DisplayName("上限为0：不限，全部转发")
    void zeroLimit_unlimited()
    {
        List<String> received = new ArrayList<>();
        DeltaLimitTruncator limiter = new DeltaLimitTruncator(received::add, 0);
        for (int i = 0; i < 50; i++)
        {
            limiter.accept("字");
        }
        assertEquals(50, received.size());
        assertEquals(50, limiter.emittedChars());
        assertFalse(limiter.isTruncated());
    }

    @Test
    @DisplayName("空增量/空串：不转发也不计入")
    void emptyDelta_ignored()
    {
        List<String> received = new ArrayList<>();
        DeltaLimitTruncator limiter = new DeltaLimitTruncator(received::add, 5);
        limiter.accept(null);
        limiter.accept("");
        assertEquals(0, received.size());
        assertEquals(0, limiter.emittedChars());
        assertFalse(limiter.isTruncated());
    }

    @Test
    @DisplayName("truncate 静态方法：超限截断、未超限原样、0 不限、null 空串")
    void truncateStatic()
    {
        assertEquals("abc", DeltaLimitTruncator.truncate("abcdef", 3));
        assertEquals("abcdef", DeltaLimitTruncator.truncate("abcdef", 10));
        assertEquals("abcdef", DeltaLimitTruncator.truncate("abcdef", 0));
        assertEquals("", DeltaLimitTruncator.truncate(null, 10));
    }
}
