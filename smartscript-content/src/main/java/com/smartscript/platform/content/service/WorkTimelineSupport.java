package com.smartscript.platform.content.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartscript.platform.content.domain.SysWorkTimelineEvent;

/**
 * 时间线事件解析与提示词拼装。
 *
 * 事件与本章摘要在同一次调用里产出（JSON 的 events 字段），不额外增加模型请求；
 * 这里只做解析、字段规整与上下文压缩，落库由 SysWorkTimelineEventMapper 完成。
 */
public final class WorkTimelineSupport
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 允许的事件类型 */
    public static final String[] EVENT_TYPES = {
            "约定", "伏笔", "道具转移", "身份变化", "关系变化", "生死退场", "能力突破", "信息揭示", "期限", "其他"};

    /** 每集最多事件条数 */
    public static final int MAX_EVENTS_PER_CHAPTER = 3;

    /** 单条事实字数上限 */
    public static final int MAX_FACT_LENGTH = 60;

    /** 注入续写的未兑现条数上限 */
    public static final int OPEN_LIMIT = 20;

    /** 注入续写的最近事件条数上限 */
    public static final int RECENT_LIMIT = 8;

    private WorkTimelineSupport()
    {
    }

    /**
     * 解析摘要返回的 events 数组。
     *
     * @param root 摘要调用的 JSON 根节点
     * @return 事件列表（chapterNo 尚未赋值，由调用方按当前集号回填）
     */
    public static List<SysWorkTimelineEvent> parseEvents(JsonNode root)
    {
        List<SysWorkTimelineEvent> list = new ArrayList<>();
        if (root == null)
        {
            return list;
        }
        JsonNode events = root.get("events");
        if (events == null || !events.isArray())
        {
            return list;
        }
        for (JsonNode node : events)
        {
            if (list.size() >= MAX_EVENTS_PER_CHAPTER)
            {
                break;
            }
            String fact = text(node, "fact");
            if (fact.isBlank())
            {
                continue;
            }
            SysWorkTimelineEvent event = new SysWorkTimelineEvent();
            event.setEventType(normalizeType(text(node, "type")));
            event.setFact(clip(fact, MAX_FACT_LENGTH));
            event.setActors(clip(text(node, "actors"), 120));
            event.setProps(clip(text(node, "props"), 120));
            event.setStatus(normalizeStatus(text(node, "status")));
            event.setStoryTime(clip(text(node, "storyTime"), 40));
            int deadline = intOf(text(node, "deadlineNo"));
            event.setDeadlineNo(deadline > 0 && deadline < 1000 ? deadline : null);
            list.add(event);
        }
        return list;
    }

    /**
     * 解析本集兑现的旧事件关键词（closes 数组），用于把历史「未兑现」标为「已兑现」。
     * 关键词必须能在事实原文里命中，太短（&lt;2 字）的丢弃，避免误伤。
     */
    public static List<String> parseCloses(JsonNode root)
    {
        List<String> list = new ArrayList<>();
        if (root == null)
        {
            return list;
        }
        JsonNode closes = root.get("closes");
        if (closes == null || !closes.isArray())
        {
            return list;
        }
        for (JsonNode node : closes)
        {
            String keyword = node.isTextual() ? node.asText().trim() : "";
            if (keyword.length() >= 2 && !list.contains(keyword))
            {
                list.add(keyword);
            }
        }
        return list;
    }

    /** 摘要提示词里的 events/closes 字段说明（供系统提示词复用） */
    public static String schemaHint()
    {
        return "4. events：本集新增的 1~3 条重要事件（除整集纯过场外，至少写 1 条）。"
                + "优先记：赌约/承诺/期限、未解线索或可疑物、道具与信物交接、身份或关系变化、"
                + "生死退场、能力或底牌突破、当众揭穿的关键信息；"
                + "不记单集内解决的小冲突、过场打斗、路人、纯情绪。每条：\n"
                + "   type 取「约定/伏笔/道具转移/身份变化/关系变化/生死退场/能力突破/信息揭示/期限/其他」之一；\n"
                + "   fact 一句话写清「谁 + 做了什么 + 条件或代价」，不超过 40 字；\n"
                + "   actors 涉及人物（顿号分隔），props 涉及道具或线索（顿号分隔，没有填空串）；\n"
                + "   status 取「未兑现/已兑现/已失效」：信息揭示、道具转移、关系变化、生死退场、"
                + "能力突破这类当场已成立的写「已兑现」；只有还要在后文兑现的约定、伏笔、期限或遗留问题才写「未兑现」；\n"
                + "   deadlineNo 计划兑现的集号（整数，实在未定省略该键）；storyTime 故事内时间（可省略）。\n"
                + "   closes：本集真正兑现掉的旧事件关键词数组（从既有未兑现事件的事实里摘 2~8 字原文，"
                + "本集没兑现就填空数组 []）。不要为了收线硬填，没写就不填。\n";
    }

    /** 空的事件骨架，放在摘要 JSON 示例里 */
    public static String emptyEventsJson()
    {
        ObjectNode node = MAPPER.createObjectNode();
        node.putArray("events");
        node.putArray("closes");
        return node.toString();
    }

    /**
     * 组装注入续写的时间线上下文：最近事件 + 全部未兑现（按集号倒序，注水前先压缩）。
     *
     * @param recent 最近事件（已按集号倒序）
     * @param open 未兑现事件（已按集号倒序）
     * @return 提示词片段；无事件时返回空串
     */
    public static String block(List<SysWorkTimelineEvent> recent, List<SysWorkTimelineEvent> open)
    {
        if ((recent == null || recent.isEmpty()) && (open == null || open.isEmpty()))
        {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【时间线（已发生的事实，不可改写；括号内为状态）】\n");
        Set<Long> seen = new LinkedHashSet<>();
        int count = 0;
        if (open != null)
        {
            for (SysWorkTimelineEvent e : open)
            {
                if (count >= OPEN_LIMIT)
                {
                    break;
                }
                if (e.getEventId() != null && !seen.add(e.getEventId()))
                {
                    continue;
                }
                sb.append(line(e)).append('\n');
                count++;
            }
        }
        if (recent != null)
        {
            int recentCount = 0;
            for (SysWorkTimelineEvent e : recent)
            {
                if (recentCount >= RECENT_LIMIT)
                {
                    break;
                }
                if (e.getEventId() != null && !seen.add(e.getEventId()))
                {
                    continue;
                }
                sb.append(line(e)).append('\n');
                recentCount++;
            }
        }
        sb.append("未兑现的约定/伏笔/期限，如果本集剧情能自然兑现，优先兑现；不能自然兑现就保留，不许硬收。\n");
        return sb.toString();
    }

    /** 单行格式：第N集｜类型｜事实｜人物（状态，应在第M集兑现） */
    public static String line(SysWorkTimelineEvent e)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("第").append(e.getChapterNo()).append("集｜").append(e.getEventType()).append('｜')
                .append(e.getFact());
        if (e.getActors() != null && !e.getActors().isBlank())
        {
            sb.append('｜').append(e.getActors());
        }
        sb.append("（").append(e.getStatus());
        if (e.getDeadlineNo() != null && SysWorkTimelineEvent.STATUS_OPEN.equals(e.getStatus()))
        {
            sb.append("，应在第").append(e.getDeadlineNo()).append("集兑现");
        }
        sb.append('）');
        return sb.toString();
    }

    private static String text(JsonNode node, String field)
    {
        JsonNode value = node.get(field);
        if (value == null || value.isNull())
        {
            return "";
        }
        if (value.isArray())
        {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : value)
            {
                String s = item.asText("").trim();
                if (!s.isEmpty())
                {
                    parts.add(s);
                }
            }
            return String.join("、", parts);
        }
        return value.asText("").trim();
    }

    private static int intOf(String value)
    {
        if (value == null)
        {
            return 0;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 3)
        {
            return 0;
        }
        try
        {
            return Integer.parseInt(digits);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private static String normalizeType(String type)
    {
        for (String allowed : EVENT_TYPES)
        {
            if (allowed.equals(type))
            {
                return allowed;
            }
        }
        return "其他";
    }

    private static String normalizeStatus(String status)
    {
        if (SysWorkTimelineEvent.STATUS_DONE.equals(status)
                || SysWorkTimelineEvent.STATUS_VOID.equals(status))
        {
            return status;
        }
        return SysWorkTimelineEvent.STATUS_OPEN;
    }

    private static String clip(String value, int max)
    {
        if (value == null)
        {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
