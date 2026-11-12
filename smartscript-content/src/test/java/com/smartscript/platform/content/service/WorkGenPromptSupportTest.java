package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.content.domain.SysWorkLedger;
import com.smartscript.platform.content.domain.WorkGenEpisode;
import com.smartscript.platform.content.domain.WorkGenJob;

/**
 * 后台队列单集提示词的悬念排期单测（不依赖 AI 服务与数据库）。
 */
class WorkGenPromptSupportTest
{
    @Test
    void dueThreadsGoToMustHandleAndLaterOnesStayBackground()
    {
        String threads = "月圆夜告知锁灵钉来历｜孙婆婆｜第2集｜未结\n"
                + "三年之约｜柳青梧｜第40集｜未结\n"
                + "掌门为何落锁｜陈铁柱｜未定｜未结";
        String block = WorkGenPromptSupport.threadScheduleBlock(threads, 5);

        // 第2集的到期项必须在第5集给出进展/结果
        assertTrue(block.contains("【本集必须处理（已到期未结的悬念/约定）】"));
        String duePart = block.substring(block.indexOf("【本集必须处理"), block.indexOf("【背景悬念"));
        assertTrue(duePart.contains("月圆夜告知锁灵钉来历"));

        // 第40集的长线悬念和未定期限只能作背景，不得提前收线
        assertTrue(block.contains("【背景悬念（未到期，不得提前解决）】"));
        String laterPart = block.substring(block.indexOf("【背景悬念"));
        assertTrue(laterPart.contains("三年之约"));
        assertTrue(laterPart.contains("掌门为何落锁"));
        assertFalse(laterPart.contains("月圆夜告知锁灵钉来历"));
    }

    @Test
    void resolvedThreadsAreNotInjected()
    {
        String threads = "月圆夜告知锁灵钉来历｜孙婆婆｜第5集｜已结";
        assertEquals("", WorkGenPromptSupport.threadScheduleBlock(threads, 5));
    }

    @Test
    void emptyThreadsProduceNoBlock()
    {
        assertEquals("", WorkGenPromptSupport.threadScheduleBlock(null, 3));
        assertEquals("", WorkGenPromptSupport.threadScheduleBlock("  ", 3));
    }

    @Test
    void freeTextThreadsWithoutDuePointStayBackground()
    {
        String block = WorkGenPromptSupport.threadScheduleBlock("丹田里的锁灵钉是谁下的", 4);
        assertTrue(block.contains("【背景悬念（未到期，不得提前解决）】"));
        assertFalse(block.contains("【本集必须处理"));
    }

    @Test
    void episodePromptCarriesDueAndLaterThreadBlocks()
    {
        WorkGenJob job = new WorkGenJob();
        job.setOutline("第5集：灶房摊牌\n本集事件：找孙婆婆问锁灵钉来历");
        job.setCharacters("陈铁柱：杂役\n孙婆婆：膳堂");
        job.setRoster("陈铁柱、孙婆婆");

        WorkGenEpisode episode = new WorkGenEpisode();
        episode.setNo(5);
        episode.setTitle("第5集：灶房摊牌");
        episode.setCard("本集事件：找孙婆婆问锁灵钉来历");

        SysWorkLedger ledger = new SysWorkLedger();
        ledger.setThreads("月圆夜告知锁灵钉来历｜孙婆婆｜第2集｜未结\n"
                + "三年之约｜柳青梧｜第40集｜未结");
        ledger.setProps("锁灵钉｜1枚｜陈铁柱｜丹田内｜乌黑");
        ledger.setStyleGuide("第三人称，动作与对白推进");

        String prompt = WorkGenPromptSupport.buildEpisodePrompt(job, episode, ledger,
                Collections.emptyList(), null);
        assertTrue(prompt.contains("【本集必须处理（已到期未结的悬念/约定）】"));
        assertTrue(prompt.contains("月圆夜告知锁灵钉来历"));
        assertTrue(prompt.contains("【背景悬念（未到期，不得提前解决）】"));
        assertTrue(prompt.contains("三年之约"));
        // 铁律仍是「回应」，而不是「必须在当集收尾」
        assertTrue(prompt.contains("允许该悬念跨集延续，不要求在本集收尾"));
    }
}
