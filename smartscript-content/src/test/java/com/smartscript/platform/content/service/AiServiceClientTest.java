package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartscript.platform.content.exception.AiServiceUnavailableException;

/**
 * AI 大模型客户端纯逻辑测试（不发起真实网络请求）。
 *
 * 覆盖：
 *   - 密钥缺失时 write/polish/outline 一律快速失败，不调用外部服务；
 *   - 大纲 JSON 解析：去除 Markdown 代码块、解析裸 JSON、非法文本兜底为 {text}。
 */
class AiServiceClientTest
{
    private static AiServiceClient clientWithNoKey()
    {
        return new AiServiceClient("https://api.deepseek.com/", "", "deepseek-flash");
    }

    @Test
    void missingApiKeyWriteThrows()
    {
        AiServiceClient client = clientWithNoKey();
        assertThrows(AiServiceUnavailableException.class,
                () -> client.write("写一段正文", null, null));
    }

    @Test
    void missingApiKeyPolishThrows()
    {
        AiServiceClient client = clientWithNoKey();
        assertThrows(AiServiceUnavailableException.class,
                () -> client.polish("一段文本", "文学优美", null));
    }

    @Test
    void missingApiKeyOutlineThrows()
    {
        AiServiceClient client = clientWithNoKey();
        assertThrows(AiServiceUnavailableException.class,
                () -> client.outline("一个故事灵感", "都市", "爽文", null));
    }

    @Test
    void parseJsonStripsMarkdownFence()
    {
        AiServiceClient client = clientWithNoKey();
        JsonNode node = client.parseJson("```json\n"
                + "{\"title\":\"重来一天\",\"acts\":[{\"name\":\"第一幕\"},{\"name\":\"第二幕\"}]}\n"
                + "```");
        assertEquals("重来一天", node.path("title").asText());
        assertEquals(2, node.path("acts").size());
    }

    @Test
    void parseJsonPlainObject()
    {
        AiServiceClient client = clientWithNoKey();
        JsonNode node = client.parseJson("{\"title\":\"直接返回\",\"logline\":\"一句话\"}");
        assertEquals("直接返回", node.path("title").asText());
        assertEquals("一句话", node.path("logline").asText());
    }

    @Test
    void parseJsonInvalidFallsBackToText()
    {
        AiServiceClient client = clientWithNoKey();
        JsonNode node = client.parseJson("抱歉，我无法生成该内容。");
        assertTrue(node.path("text").asText().contains("无法生成"));
    }

    @Test
    void parseSseDeltaExtractsContentIncrement()
    {
        String delta = AiServiceClient.parseSseDelta(
                "{\"choices\":[{\"delta\":{\"content\":\"你好，世界\"}}]}");
        assertEquals("你好，世界", delta);
    }

    @Test
    void parseSseDeltaHandlesEmptyAndDone()
    {
        assertEquals("", AiServiceClient.parseSseDelta(""));
        assertEquals("", AiServiceClient.parseSseDelta("[DONE]"));
        // 无 content 字段的块返回空串而非异常
        assertEquals("", AiServiceClient.parseSseDelta(
                "{\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}"));
    }

    @Test
    void parseSseDeltaSkipsMalformedBlock()
    {
        // 无法解析的块返回 null，调用方跳过
        assertEquals(null, AiServiceClient.parseSseDelta("not-a-json"));
    }

    @Test
    void sseFinishDetectsStopReason()
    {
        assertTrue(AiServiceClient.isSseFinish(
                "{\"choices\":[{\"finish_reason\":\"stop\"}]}"));
        assertTrue(AiServiceClient.isSseFinish(
                "{\"choices\":[{\"finish_reason\":\"length\"}]}"));
        assertFalse(AiServiceClient.isSseFinish(
                "{\"choices\":[{\"delta\":{\"content\":\"继续\"}}]}"));
    }

    @Test
    void missingApiKeyWriteStreamThrows()
    {
        AiServiceClient client = clientWithNoKey();
        assertThrows(AiServiceUnavailableException.class,
                () -> client.writeStream("写一段正文", "write", null, ignored -> {}));
    }

    @Test
    void missingApiKeyPolishStreamThrows()
    {
        AiServiceClient client = clientWithNoKey();
        assertThrows(AiServiceUnavailableException.class,
                () -> client.polishStream("一段文本", "文学优美", null, ignored -> {}));
    }

    @Test
    void missingApiKeyOutlineStreamThrows()
    {
        AiServiceClient client = clientWithNoKey();
        assertThrows(AiServiceUnavailableException.class,
                () -> client.outlineStream("一个故事灵感", "都市", "爽文", null, ignored -> {}));
    }

    @Test
    void missingApiKeyStoryboardThrows()
    {
        AiServiceClient client = clientWithNoKey();
        assertThrows(AiServiceUnavailableException.class,
                () -> client.storyboard("第一幕剧本", "电影感", null));
    }

    @Test
    void missingApiKeyStoryboardStreamThrows()
    {
        AiServiceClient client = clientWithNoKey();
        assertThrows(AiServiceUnavailableException.class,
                () -> client.storyboardStream("第一幕剧本", "电影感", null, ignored -> {}));
    }
}
