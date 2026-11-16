package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.content.domain.SysWorkLedger;

/**
 * 一致性守卫单测：只验证「抽取—注入—比对」这条链，不依赖 AI 与数据库。
 *
 * 用例全部取自《丹炉血》复测里真实出现过的冲突形态。
 */
class WorkGenConsistencySupportTest
{
    private SysWorkLedger danluLedger()
    {
        SysWorkLedger ledger = new SysWorkLedger();
        ledger.setProps("丹母（残）｜1｜林砚｜贴身｜裂纹逐集加长\n"
                + "丹母（残）｜1｜韩遂｜嵌在腕上｜与林砚那枚成对\n"
                + "填炉共三十七人｜单集未拆｜韩遂｜试药房实点");
        ledger.setTimeline("第13集｜丹会提前，初八改作廿八\n"
                + "第14集｜封炉，火不许断");
        ledger.setCharacters("林砚｜炉前童子｜膝伤，行动受限\n"
                + "薛衡｜炉底囚徒｜被压住半边身子");
        ledger.setItems("丹母：林砚贴身（第28集吞下）");
        return ledger;
    }

    @Test
    void retiredDateStillInUseIsReported()
    {
        List<String> issues = WorkGenConsistencySupport.check(
                "韩遂：这一炉是引子。初八之前，火不许断。", danluLedger(), 14);

        assertTrue(issues.stream().anyMatch(issue -> issue.contains("初八")));
    }

    @Test
    void quotingTheChangeItselfIsAllowed()
    {
        List<String> issues = WorkGenConsistencySupport.check(
                "韩遂：丹会提前，初八改作廿八，今日起昼夜不歇。", danluLedger(), 13);

        assertFalse(issues.stream().anyMatch(issue -> issue.startsWith("日期：")));
    }

    @Test
    void propCountMismatchIsReported()
    {
        // 台账两条「丹母（残）」各 1 件，合并为 2 件；正文写三枚即冲突
        List<String> issues = WorkGenConsistencySupport.check(
                "林砚摸出三枚丹母，裂纹一对。", danluLedger(), 25);

        assertTrue(issues.stream().anyMatch(issue -> issue.contains("丹母")));
    }

    @Test
    void bracketedPropNameStillMatchesPlainText()
    {
        List<String> issues = WorkGenConsistencySupport.check(
                "林砚摸出两枚丹母，裂纹一对。", danluLedger(), 25);

        assertFalse(issues.stream().anyMatch(issue -> issue.contains("道具：丹母")));
    }

    @Test
    void halfPieceIsNotTreatedAsCountMismatch()
    {
        List<String> issues = WorkGenConsistencySupport.check(
                "林砚怀里那半枚丹母又烫了一下。", danluLedger(), 5);

        assertFalse(issues.stream().anyMatch(issue -> issue.contains("道具：丹母")));
    }

    @Test
    void statusChangeWithoutTransitionIsReported()
    {
        List<String> issues = WorkGenConsistencySupport.check(
                "薛衡站起来，抖了抖袖子，往门外走。", danluLedger(), 27);

        assertTrue(issues.stream().anyMatch(issue -> issue.contains("薛衡")));
    }

    @Test
    void statusChangeWithTransitionIsAllowed()
    {
        List<String> issues = WorkGenConsistencySupport.check(
                "赵九带人卸了链，把薛衡拖走，押去前殿问话。", danluLedger(), 27);

        assertFalse(issues.stream().anyMatch(issue -> issue.startsWith("人物状态：")));
    }

    @Test
    void totalPeopleMismatchIsReported()
    {
        List<String> issues = WorkGenConsistencySupport.check(
                "赵九点了点：合计四十三人，一个不少。", danluLedger(), 6);

        assertTrue(issues.stream().anyMatch(issue -> issue.contains("人数")));
    }

    @Test
    void consistentEpisodePasses()
    {
        List<String> issues = WorkGenConsistencySupport.check(
                "林砚把那半枚丹母塞进炉灰，赵九搜完鞋袜什么也没摸到。", danluLedger(), 14);

        assertTrue(issues.isEmpty(), "正常正文不应报冲突：" + issues);
    }

    @Test
    void hardFactsBlockOnlyListsWhatWasExtracted()
    {
        String block = WorkGenConsistencySupport.hardFactsBlock(danluLedger(), 14);

        assertTrue(block.contains("丹母 2 件"));
        assertTrue(block.contains("已作废日期：初八"));
        assertTrue(block.contains("薛衡"));
        assertTrue(block.contains("37 人"));
        assertTrue(block.contains("过渡事件"));
    }

    @Test
    void emptyLedgerProducesNoBlockAndNoIssues()
    {
        SysWorkLedger empty = new SysWorkLedger();

        assertTrue(WorkGenConsistencySupport.hardFactsBlock(empty, 1).isEmpty());
        assertTrue(WorkGenConsistencySupport.check("随便一集正文。", empty, 1).isEmpty());
        assertTrue(WorkGenConsistencySupport.check("正文", null, 1).isEmpty());
    }
}
