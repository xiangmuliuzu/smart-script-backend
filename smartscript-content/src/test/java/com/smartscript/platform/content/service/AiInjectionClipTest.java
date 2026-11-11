package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * 大纲续批注入的截断方向测试：
 *   台账取尾部最新、设定续批保留「人物/关系」而不是「故事简介」。
 */
class AiInjectionClipTest
{
    private static final String LEDGER = "故事简介：第1集的状态，早已过时\n"
            + "人物卡：林默，外卖员，第1集被撞伤\n"
            + "关系状态：林默与周岚，第1集互不信任\n"
            + "伏笔（埋在哪章/计划回收章/是否回收）：第12集埋了半张照片\n"
            + "时间线：第18集，雨夜\n"
            + "物品位置：照片在周岚包里（第20集）\n"
            + "未解线索：谁给林默发的定位（第21集）";

    private static final String SETTING = "『故事简介』\n"
            + "".repeat(1) + "外卖员林默被卷入旧案，一路查到市政工程黑幕。\n\n"
            + "『主要人物』\n"
            + "林默（26/男）：外卖员｜沉默寡言｜为妹妹治病拼命赚钱\n"
            + "周岚（29/女）：记者｜锋利｜手里有半张照片\n\n"
            + "『人物关系』\n"
            + "林默 ↔ 周岚：互相利用的同盟 + 都在瞒对方一件事\n"
            + "林默 ↔ 陈叔：师徒 + 陈叔知道他父亲怎么死的\n";

    @Test
    void ledgerKeepsLatestLinesInsteadOfFirstLines()
    {
        String clipped = AiServiceClient.clipLedgerLatest(LEDGER, 120);
        assertTrue(clipped.contains("未解线索"), "应保留最后一行（最新事实）");
        assertTrue(clipped.contains("物品位置"), "应保留最近的物品位置");
        assertFalse(clipped.contains("第1集的状态，早已过时"), "不应保留最早的旧状态");
        assertTrue(clipped.length() <= 120);
    }

    @Test
    void ledgerKeepsLabelWhenLineIsCut()
    {
        String clipped = AiServiceClient.clipLedgerLatest("故事简介：早期的旧状态\n人物卡：" + "长".repeat(200), 60);
        assertTrue(clipped.contains("人物卡："), "被截断的行仍应保留标签前缀");
        assertTrue(clipped.contains("前略"), "截断处应标注前略，避免只剩半句");
        assertTrue(clipped.length() <= 60);
    }

    @Test
    void settingContinuationKeepsCharactersAndRelations()
    {
        String clipped = AiServiceClient.settingContinuation(SETTING, 240);
        assertTrue(clipped.contains("『主要人物』"));
        assertTrue(clipped.contains("『人物关系』"));
        assertFalse(clipped.contains("外卖员林默被卷入旧案，一路查到市政工程黑幕。"), "续批不应再重复故事简介");
        assertTrue(clipped.length() <= 240);
    }

    @Test
    void settingContinuationFallsBackToTailWhenHeadingsMissing()
    {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 40; i++)
        {
            body.append("第").append(i).append("段人物设定内容。");
        }
        String clipped = AiServiceClient.settingContinuation(body.toString(), 50);
        assertEquals(50, clipped.length());
        assertFalse(clipped.startsWith("第0段"), "无标题时应保留尾部而不是头部");
    }

    @Test
    void clipTailKeepsEnd()
    {
        assertEquals("0123456789".substring(6), AiServiceClient.clipTail("0123456789", 4));
        assertEquals("短", AiServiceClient.clipTail("短", 10));
        assertEquals("", AiServiceClient.clipTail(null, 10));
    }

    @Test
    void lastEpisodesKeepsWholeEpisodeBlocks()
    {
        String outline = "第1集：开头\n本集事件：旧事\n结尾钩子：……\n\n"
                + "第2集：接头\n本集事件：拿到照片\n结尾钩子：照片是假的\n\n"
                + "第3集：翻供\n本集事件：证人改口\n结尾钩子：谁指使的\n";
        String prev = AiServiceClient.lastEpisodes(outline, 2, 1500);
        assertFalse(prev.contains("第1集"), "只应带最后 2 集");
        assertTrue(prev.contains("第2集：接头"));
        assertTrue(prev.contains("第3集：翻供"));
        assertTrue(prev.contains("结尾钩子：谁指使的"), "最后一集的钩子必须完整保留");
    }
}
