package com.smartscript.platform.content.service;

import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartscript.platform.content.domain.SysWorkLedger;

/**
 * 「本章摘要 + 创作台账变更」的解析与合并。
 *
 * 与 AI 调用解耦，便于单测：提示词只要求模型输出一段 JSON，
 * 由本类负责容错解析（去 Markdown 包裹、截取最外层大括号），
 * 合并台账时只覆盖模型明确给出的非空字段，人工写过而模型未提及的内容一律保留。
 */
public final class ChapterSummarySupport
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 模型 JSON 被截断时的兜底：直接从原文里抠 summary 字段，保证摘要不丢。 */
    private static final java.util.regex.Pattern SUMMARY_FIELD =
            java.util.regex.Pattern.compile("\"summary\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    /** 正文注入上限（字符）：超长时保留开头 1/3 与结尾 2/3，结尾常含钩子，权重更高。 */
    public static final int BODY_CLIP = 6000;

    /** 摘要落库上限（sys_work_chapter.summary 为 varchar(500)）。 */
    public static final int SUMMARY_MAX = 500;

    /** 正文过短阈值：太短的正文提炼不出可用摘要，直接拒绝，不浪费积分。 */
    public static final int MIN_BODY = 50;

    private ChapterSummarySupport()
    {
    }

    /** 压缩注入正文。 */
    public static String clipBody(String body, int max)
    {
        String text = body == null ? "" : body.trim();
        if (max <= 0 || text.length() <= max)
        {
            return text;
        }
        int head = max / 3;
        int tail = max - head;
        return text.substring(0, head) + "\n……（中间略）……\n"
                + text.substring(text.length() - tail);
    }

    /** 解析 AI 返回的 JSON：容忍 ```json 包裹与前后说明文字；解析失败返回空对象。 */
    public static JsonNode parseJson(String raw)
    {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty())
        {
            return MAPPER.createObjectNode();
        }
        if (s.startsWith("```"))
        {
            int newline = s.indexOf('\n');
            if (newline >= 0)
            {
                s = s.substring(newline + 1);
            }
            if (s.endsWith("```"))
            {
                s = s.substring(0, s.length() - 3);
            }
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start)
        {
            s = s.substring(start, end + 1);
        }
        try
        {
            return MAPPER.readTree(s);
        }
        catch (Exception e)
        {
            // 输出被 max_tokens 截断时整体解析必然失败，但 summary 通常已完整出现在开头，
            // 用正则抠出来让摘要仍能落库（台账这次放弃，下次再补）。
            return salvageSummary(s);
        }
    }

    /** 从残缺 JSON 中抠出 summary 字段；抠不到返回空对象。 */
    private static JsonNode salvageSummary(String text)
    {
        ObjectNode node = MAPPER.createObjectNode();
        java.util.regex.Matcher matcher = SUMMARY_FIELD.matcher(text);
        if (matcher.find())
        {
            node.put("summary", unescape(matcher.group(1)));
        }
        return node;
    }

    private static String unescape(String value)
    {
        return value.replace("\\r", "")
                .replace("\\n", "\n")
                .replace("\\t", " ")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    /** 取摘要文本（summary，兼容 chapterSummary），并按落库上限截断。 */
    public static String summaryOf(JsonNode root)
    {
        if (root == null || root.isMissingNode() || root.isNull())
        {
            return "";
        }
        String text = root.path("summary").asText("");
        if (text.isBlank())
        {
            text = root.path("chapterSummary").asText("");
        }
        text = text.trim();
        return text.length() <= SUMMARY_MAX ? text : text.substring(0, SUMMARY_MAX);
    }

    /** 台账补丁：仅取 ledger 对象里七类非空文本。 */
    public static Map<String, String> ledgerPatch(JsonNode root)
    {
        Map<String, String> patch = new LinkedHashMap<>();
        if (root == null || root.isMissingNode() || root.isNull())
        {
            return patch;
        }
        JsonNode ledger = root.path("ledger");
        if (!ledger.isObject())
        {
            return patch;
        }
        putIfText(patch, ledger, "storySummary");
        putIfText(patch, ledger, "characters");
        putIfText(patch, ledger, "relations");
        putIfText(patch, ledger, "foreshadow");
        putIfText(patch, ledger, "timeline");
        putIfText(patch, ledger, "items");
        putIfText(patch, ledger, "threads");
        return patch;
    }

    private static void putIfText(Map<String, String> patch, JsonNode ledger, String key)
    {
        String value = ledger.path(key).asText("").trim();
        if (!value.isEmpty())
        {
            patch.put(key, value);
        }
    }

    /**
     * 把补丁并入台账：只处理模型给出的非空字段，其余保留原值（含人工内容）。
     *
     * 模型可能给「更新后的完整文本」，也可能只给本集新增条目：
     * 完整文本（新值里出现了旧值开头）直接替换；否则按行去重后追加，
     * 这样既不会清空旧台账，也不会因为要求模型复述旧文本把输出撑爆。
     *
     * @return 是否有字段被改动
     */
    public static boolean mergeLedger(SysWorkLedger ledger, Map<String, String> patch)
    {
        if (ledger == null || patch == null || patch.isEmpty())
        {
            return false;
        }
        boolean changed = false;
        changed |= apply(ledger.getStorySummary(), patch.get("storySummary"), ledger::setStorySummary);
        changed |= apply(ledger.getCharacters(), patch.get("characters"), ledger::setCharacters);
        changed |= apply(ledger.getRelations(), patch.get("relations"), ledger::setRelations);
        changed |= apply(ledger.getForeshadow(), patch.get("foreshadow"), ledger::setForeshadow);
        changed |= apply(ledger.getTimeline(), patch.get("timeline"), ledger::setTimeline);
        changed |= apply(ledger.getItems(), patch.get("items"), ledger::setItems);
        changed |= apply(ledger.getThreads(), patch.get("threads"), ledger::setThreads);
        return changed;
    }

    /** 单字段合并：模型没给、给了空串、或与现值相同都不动；增量条目追加到旧值后面。 */
    private static boolean apply(String oldValue, String newValue, java.util.function.Consumer<String> setter)
    {
        if (newValue == null || newValue.isBlank())
        {
            return false;
        }
        String oldText = oldValue == null ? "" : oldValue.trim();
        String newText = newValue.trim();
        if (newText.equals(oldText))
        {
            return false;
        }
        if (oldText.isEmpty())
        {
            setter.accept(newText);
            return true;
        }
        // 新值是完整重写：旧值开头那段出现在新值里，直接替换。
        String oldHead = oldText.length() <= 12 ? oldText : oldText.substring(0, 12);
        if (newText.contains(oldHead))
        {
            setter.accept(newText);
            return true;
        }
        String merged = appendNewLines(oldText, newText);
        if (merged.equals(oldText))
        {
            return false;
        }
        setter.accept(merged);
        return true;
    }

    /** 逐行追加，旧值里已存在的那一行跳过，避免每集重复叠同一句。 */
    private static String appendNewLines(String oldText, String newText)
    {
        StringBuilder sb = new StringBuilder(oldText);
        for (String line : newText.split("\n"))
        {
            String l = line.trim();
            if (l.isEmpty() || oldText.contains(l))
            {
                continue;
            }
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            sb.append(l);
        }
        return sb.toString();
    }

    /** 七类字段名（供提示词与测试复用）。 */
    public static final String[] LEDGER_KEYS = {
            "storySummary", "characters", "relations", "foreshadow", "timeline", "items", "threads"};

    /** 组装一段空 JSON 骨架提示（让模型知道必须给全七个键）；summary 放最前，便于截断时兜底。 */
    public static String emptyLedgerJson()
    {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("summary", "");
        ObjectNode ledger = node.putObject("ledger");
        for (String key : LEDGER_KEYS)
        {
            ledger.put(key, "");
        }
        return node.toString();
    }
}
