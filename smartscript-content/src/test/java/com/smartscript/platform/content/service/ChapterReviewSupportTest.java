package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 每集验收 / 卷快照的解析与判定单元测试（不依赖 AI 与数据库）。
 */
class ChapterReviewSupportTest
{
    @Test
    void passRequiresAtLeastFourOkItems()
    {
        JsonNode fourOk = ChapterReviewSupport.parse(
                "{\"score\":4,\"issues\":[{\"item\":\"continuity\",\"level\":\"fail\",\"detail\":\"道具位置前后矛盾\"}]}");
        assertTrue(ChapterReviewSupport.pass(fourOk));
        assertEquals(4, ChapterReviewSupport.passScore(fourOk));

        JsonNode threeOk = ChapterReviewSupport.parse("{\"issues\":["
                + "{\"item\":\"identity\",\"level\":\"fail\",\"detail\":\"多出设定外角色\"},"
                + "{\"item\":\"causality\",\"level\":\"fail\",\"detail\":\"没接上集钩子\"}]}");
        assertFalse(ChapterReviewSupport.pass(threeOk));
        assertEquals(3, ChapterReviewSupport.passScore(threeOk));
    }

    @Test
    void issuesTextUsesChineseLabelsAndSkipsOkItems()
    {
        JsonNode root = ChapterReviewSupport.parse("{\"issues\":["
                + "{\"item\":\"identity\",\"level\":\"fail\",\"detail\":\"出现未登记的舅舅\"},"
                + "{\"item\":\"foreshadow\",\"level\":\"ok\"}]}");
        String text = ChapterReviewSupport.issuesText(root);
        assertTrue(text.contains("人物：出现未登记的舅舅"));
        assertFalse(text.contains("伏笔"));
    }

    @Test
    void unparseableReviewIsTreatedAsPassWithFullScore()
    {
        // 解析失败时不阻断创作：按 5 项全通过处理，避免模型偶发格式问题卡住整集
        JsonNode root = ChapterReviewSupport.parse("模型没有按 JSON 输出");
        assertTrue(ChapterReviewSupport.pass(root));
        assertEquals(5, ChapterReviewSupport.passScore(root));
    }

    @Test
    void snapshotIsClippedToLimit()
    {
        String longText = "字".repeat(ChapterReviewSupport.SNAPSHOT_MAX + 50);
        JsonNode root = ChapterReviewSupport.parse("{\"snapshot\":\"" + longText + "\"}");
        assertEquals(ChapterReviewSupport.SNAPSHOT_MAX, ChapterReviewSupport.snapshotOf(root).length());
    }

    @Test
    void snapshotFallsBackToSummaryKey()
    {
        JsonNode root = ChapterReviewSupport.parse("{\"summary\":\"第一卷末，主角被通缉\"}");
        assertEquals("第一卷末，主角被通缉", ChapterReviewSupport.snapshotOf(root));
    }
}
