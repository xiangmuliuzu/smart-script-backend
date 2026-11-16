package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 大纲分批续写：总集数解析与末集完整性判定。
 *
 * 背景：App 端把「请按下面格式写 3 集分集事件卡…第1集：事件名…」整段拼进 inspiration，
 * 旧解析取「最后一个 N 集」，会把模板里的「第1集」当成总集数 → 只生成 1 集。
 */
class AiOutlineBatchTest
{
    @Test
    @DisplayName("App 模板文本：取『写 3 集分集』/『共 3 集』，不被模板里的第1集干扰")
    void parsesEpisodesFromAppTemplate()
    {
        String insp = "法医沈砚回乡认亲。\n"
                + "请按下面格式写 3 集分集事件卡，从第1集起连续编号：\n"
                + "第1集：事件名\n本集事件：……\n冲突与推进：……\n结果或反转：……\n结尾钩子：……\n";
        assertEquals(3, AiServiceClient.parseTotalEpisodes("共 3 集\n" + insp));
        assertEquals(3, AiServiceClient.parseTotalEpisodes(insp));
    }

    @Test
    @DisplayName("常规写法：共 30 集 / 按 12 集")
    void parsesCommonPhrases()
    {
        assertEquals(30, AiServiceClient.parseTotalEpisodes("共 30 集，每集 60 秒"));
        assertEquals(12, AiServiceClient.parseTotalEpisodes("按 12 集写"));
    }

    @Test
    @DisplayName("集数硬上限 50：超出的写法按 50 集分批")
    void clampsEpisodesToFifty()
    {
        assertEquals(50, AiServiceClient.parseTotalEpisodes("共 100 集"));
        assertEquals(50, AiServiceClient.parseTotalEpisodes("按 200 集写"));
        assertEquals(50, AiServiceClient.MAX_EPISODES);
    }

    @Test
    @DisplayName("只有模板集号时返回 0，走单批")
    void ignoresEpisodeHeading()
    {
        assertEquals(0, AiServiceClient.parseTotalEpisodes("第1集：事件名\n第2集：事件名"));
    }

    @Test
    @DisplayName("末集完整性：含钩子或篇幅足够视为完整，半句丢弃")
    void trimsOnlyIncompleteTail()
    {
        StringBuilder complete = new StringBuilder("第1集：白骨出水\n"
                + "本集事件：水库捞出白骨。\n冲突与推进：刑警压她结案。\n"
                + "结果或反转：她找出旧伤。\n结尾钩子：骨头上的切口。\n");
        assertEquals(complete.length(),
                AiServiceClient.trimIncompleteTail(complete, 1).length());

        StringBuilder truncated = new StringBuilder("第1集：白骨出水\n本集事件：水库捞出白骨，她蹲下翻检，");
        assertTrue(AiServiceClient.trimIncompleteTail(truncated, 1).length() < truncated.length());
    }
}
