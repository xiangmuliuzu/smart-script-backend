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
    void passRequiresAtLeastFiveOfSevenOkItems()
    {
        // 7 项里 6 项 ok：通过
        JsonNode sixOk = ChapterReviewSupport.parse(
                "{\"score\":6,\"issues\":[{\"item\":\"continuity\",\"level\":\"fail\",\"detail\":\"道具位置前后矛盾\"}]}");
        assertTrue(ChapterReviewSupport.pass(sixOk));
        assertEquals(6, ChapterReviewSupport.passScore(sixOk));

        // 7 项里 5 项 ok：刚好达到阈值
        JsonNode fiveOk = ChapterReviewSupport.parse("{\"issues\":["
                + "{\"item\":\"identity\",\"level\":\"fail\",\"detail\":\"多出设定外角色\"},"
                + "{\"item\":\"causality\",\"level\":\"fail\",\"detail\":\"没接上集钩子\"}]}");
        assertTrue(ChapterReviewSupport.pass(fiveOk));
        assertEquals(5, ChapterReviewSupport.passScore(fiveOk));

        // 7 项里 4 项 ok：不通过（道具、写法两项也要计入）
        JsonNode fourOk = ChapterReviewSupport.parse("{\"issues\":["
                + "{\"item\":\"identity\",\"level\":\"fail\",\"detail\":\"多出设定外角色\"},"
                + "{\"item\":\"causality\",\"level\":\"fail\",\"detail\":\"没接上集钩子\"},"
                + "{\"item\":\"props\",\"level\":\"fail\",\"detail\":\"十三面与台账十二面不符\"}]}");
        assertFalse(ChapterReviewSupport.pass(fourOk));
        assertEquals(4, ChapterReviewSupport.passScore(fourOk));
    }

    @Test
    void propsAndStyleFailuresAreLabeled()
    {
        JsonNode root = ChapterReviewSupport.parse("{\"issues\":["
                + "{\"item\":\"props\",\"level\":\"fail\",\"detail\":\"钥匙重复交付\"},"
                + "{\"item\":\"style\",\"level\":\"fail\",\"detail\":\"改成括注体\"}]}");
        String text = ChapterReviewSupport.issuesText(root);
        assertTrue(text.contains("道具：钥匙重复交付"));
        assertTrue(text.contains("写法：改成括注体"));
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
        // 解析失败时不阻断创作：按 7 项全通过处理，避免模型偶发格式问题卡住整集
        JsonNode root = ChapterReviewSupport.parse("模型没有按 JSON 输出");
        assertTrue(ChapterReviewSupport.pass(root));
        assertEquals(7, ChapterReviewSupport.passScore(root));
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
