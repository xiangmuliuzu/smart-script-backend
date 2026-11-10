package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartscript.platform.content.domain.SysWorkLedger;

/**
 * 「本章摘要 + 台账变更」解析与合并的单元测试（不依赖 AI 服务与数据库）。
 */
class ChapterSummarySupportTest
{
    @Test
    void parseJsonToleratesCodeFenceAndSurroundingText()
    {
        String raw = "好的，结果如下：\n```json\n{\"summary\":\"本集林默与周岚对峙\",\"ledger\":{\"characters\":\"林默：外卖员\"}}\n```\n以上。";
        JsonNode root = ChapterSummarySupport.parseJson(raw);
        assertEquals("本集林默与周岚对峙", ChapterSummarySupport.summaryOf(root));
        assertTrue(ChapterSummarySupport.ledgerPatch(root).containsKey("characters"));
    }

    @Test
    void parseJsonReturnsEmptyObjectWhenUnparseable()
    {
        JsonNode root = ChapterSummarySupport.parseJson("模型这次没给 JSON");
        assertTrue(root.isObject());
        assertEquals("", ChapterSummarySupport.summaryOf(root));
        assertTrue(ChapterSummarySupport.ledgerPatch(root).isEmpty());
    }

    @Test
    void summaryIsClippedToColumnLimit()
    {
        String longText = "字".repeat(ChapterSummarySupport.SUMMARY_MAX + 40);
        String raw = "{\"summary\":\"" + longText + "\"}";
        String summary = ChapterSummarySupport.summaryOf(ChapterSummarySupport.parseJson(raw));
        assertEquals(ChapterSummarySupport.SUMMARY_MAX, summary.length());
    }

    @Test
    void ledgerPatchSkipsBlankValues()
    {
        String raw = "{\"ledger\":{\"characters\":\"林默：外卖员\",\"relations\":\"  \",\"timeline\":\"第2集 凌晨\"}}";
        Map<String, String> patch = ChapterSummarySupport.ledgerPatch(ChapterSummarySupport.parseJson(raw));
        assertEquals("林默：外卖员", patch.get("characters"));
        assertFalse(patch.containsKey("relations"));
        assertEquals("第2集 凌晨", patch.get("timeline"));
    }

    @Test
    void mergeKeepsManualContentWhenModelOmitsFields()
    {
        SysWorkLedger ledger = new SysWorkLedger();
        ledger.setWorkId(96L);
        ledger.setCharacters("旧人物卡：林默、周岚");
        ledger.setForeshadow("第3集铜铃尚未回收");
        Map<String, String> patch = Map.of("characters", "林默：外卖员，手机被扣");
        assertTrue(ChapterSummarySupport.mergeLedger(ledger, patch));
        // 模型只给新增条目时追加在旧内容后面，人工写过的旧内容不能丢
        assertTrue(ledger.getCharacters().contains("旧人物卡：林默、周岚"));
        assertTrue(ledger.getCharacters().contains("林默：外卖员，手机被扣"));
        // 模型未提及的字段必须原样保留，不能因为整条覆盖被清空
        assertEquals("第3集铜铃尚未回收", ledger.getForeshadow());
        assertNull(ledger.getItems());
    }

    @Test
    void mergeAppendsOnlyNewLinesForIncrementalPatch()
    {
        SysWorkLedger ledger = new SysWorkLedger();
        ledger.setCharacters("林默：外卖员，手机被扣，住在七号楼");
        assertTrue(ChapterSummarySupport.mergeLedger(ledger,
                Map.of("characters", "周岚：护士，负责采样\n周岚：护士，负责采样")));
        assertTrue(ledger.getCharacters().startsWith("林默：外卖员，手机被扣，住在七号楼"));
        // 重复行只追加一次
        assertEquals(1, ledger.getCharacters().split("周岚：护士，负责采样").length - 1);
    }

    @Test
    void mergeReportsNoChangeWhenIncrementalLinesAlreadyPresent()
    {
        SysWorkLedger ledger = new SysWorkLedger();
        ledger.setCharacters("林默：外卖员，手机被扣，住在七号楼\n周岚：护士，负责采样");
        assertFalse(ChapterSummarySupport.mergeLedger(ledger, Map.of("characters", "周岚：护士，负责采样")));
    }

    @Test
    void mergeReplacesWhenModelReturnsFullRewrite()
    {
        SysWorkLedger ledger = new SysWorkLedger();
        ledger.setStorySummary("林默重生，囤物资，末世开始。");
        assertTrue(ChapterSummarySupport.mergeLedger(ledger,
                Map.of("storySummary", "林默重生，囤物资，末世开始。\n第5集推进到七号楼602救援。")));
        assertTrue(ledger.getStorySummary().contains("第5集推进到七号楼602救援"));
        assertEquals(1, ledger.getStorySummary().split("末世开始").length - 1, "完整重写应替换而不是追加");
    }

    @Test
    void parseJsonSalvagesSummaryFromTruncatedOutput()
    {
        String truncated = "{\"summary\":\"凌晨四点零七分，林默与周岚在b17号柜碰头\",\"ledger\":{\"characters\":\"林默";
        JsonNode root = ChapterSummarySupport.parseJson(truncated);
        assertEquals("凌晨四点零七分，林默与周岚在b17号柜碰头", ChapterSummarySupport.summaryOf(root));
    }

    @Test
    void mergeReportsNoChangeWhenValuesIdenticalOrBlank()
    {
        SysWorkLedger ledger = new SysWorkLedger();
        ledger.setCharacters("林默：外卖员");
        assertFalse(ChapterSummarySupport.mergeLedger(ledger, Map.of("characters", "林默：外卖员")));
        assertFalse(ChapterSummarySupport.mergeLedger(ledger, Map.of()));
        assertFalse(ChapterSummarySupport.mergeLedger(null, Map.of("characters", "x")));
    }

    @Test
    void clipBodyKeepsHeadAndTail()
    {
        String body = "HEAD" + "中".repeat(10000) + "TAIL";
        String clipped = ChapterSummarySupport.clipBody(body, ChapterSummarySupport.BODY_CLIP);
        assertTrue(clipped.startsWith("HEAD"));
        assertTrue(clipped.endsWith("TAIL"));
        assertTrue(clipped.contains("中间略"));
        assertTrue(clipped.length() <= ChapterSummarySupport.BODY_CLIP + 12);
        assertEquals("短正文不动", ChapterSummarySupport.clipBody("  短正文不动  ", 100));
    }

    @Test
    void emptyLedgerJsonHasAllSevenKeys()
    {
        JsonNode root = ChapterSummarySupport.parseJson(ChapterSummarySupport.emptyLedgerJson());
        for (String key : ChapterSummarySupport.LEDGER_KEYS)
        {
            assertTrue(root.path("ledger").has(key), "缺少键: " + key);
        }
        assertTrue(root.has("summary"));
    }
}
