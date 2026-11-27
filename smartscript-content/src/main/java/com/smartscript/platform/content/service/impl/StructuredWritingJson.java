package com.smartscript.platform.content.service.impl;

import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartscript.platform.content.exception.AiServiceUnavailableException;

final class StructuredWritingJson
{
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private StructuredWritingJson() { }

    static ObjectNode object()
    {
        return JSON.createObjectNode();
    }

    static ObjectNode parse(String raw)
    {
        String text = raw == null ? "" : raw.strip();
        ObjectNode result = readObject(text);
        if (result == null)
        {
            // 兼容模型在完整 JSON 对象外添加的代码围栏或短说明。
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start)
            {
                result = readObject(text.substring(start, end + 1));
            }
        }
        if (result == null)
        {
            throw invalidResult();
        }
        return result;
    }

    private static ObjectNode readObject(String text)
    {
        try
        {
            return JSON.readTree(text) instanceof ObjectNode object ? object : null;
        }
        catch (JsonProcessingException e)
        {
            return null;
        }
    }

    static AiServiceUnavailableException invalidResult()
    {
        return new AiServiceUnavailableException("AI 返回的资料不完整或格式错误，请重试");
    }

    static Map<String, Object> asMap(ObjectNode node)
    {
        return JSON.convertValue(node, new TypeReference<Map<String, Object>>() { });
    }

    static ObjectNode fromMap(Map<String, ?> map)
    {
        return JSON.valueToTree(map);
    }

    static String text(JsonNode value)
    {
        return value.isTextual() ? value.asText().strip() : "";
    }

    static ObjectNode textFields(JsonNode value, String... fields)
    {
        ObjectNode result = object();
        for (String field : fields)
        {
            String content = text(value.path(field));
            if (!content.isEmpty())
            {
                result.put(field, content);
            }
        }
        return result;
    }

    static String limit(String value, int max)
    {
        return value.codePointCount(0, value.length()) <= max
                ? value : value.substring(0, value.offsetByCodePoints(0, max));
    }

    /** 与现有作品工作台按名称生成 key 的算法一致。 */
    static String nameKey(String prefix, String name)
    {
        long hash = 0;
        for (int offset = 0; offset < name.length(); )
        {
            int codePoint = name.codePointAt(offset);
            hash = (hash * 31 + codePoint) % Integer.MAX_VALUE;
            offset += Character.charCount(codePoint);
        }
        return prefix + "-" + Long.toString(hash, 36);
    }
}
