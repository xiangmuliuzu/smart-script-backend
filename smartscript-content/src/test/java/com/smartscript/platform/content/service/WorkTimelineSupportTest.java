package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.content.domain.SysWorkTimelineEvent;

/**
 * 时间线事件解析与注入块。
 */
class WorkTimelineSupportTest
{
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseEventsKeepsFactsAndNormalizesUnknownType()
    {
        String json = "{\"summary\":\"s\",\"events\":["
                + "{\"type\":\"约定\",\"fact\":\"江野与赵德贵约七日后擂台对赌三块灵石\","
                + "\"actors\":[\"江野\",\"赵德贵\"],\"props\":\"灵石\",\"status\":\"未兑现\",\"deadlineNo\":6},"
                + "{\"type\":\"乱写\",\"fact\":\"苏清鸢夜探藏经阁\",\"status\":\"已兑现\"}]}";
        List<SysWorkTimelineEvent> events = WorkTimelineSupport.parseEvents(read(json));
        assertEquals(2, events.size());
        assertEquals("约定", events.get(0).getEventType());
        assertEquals("江野、赵德贵", events.get(0).getActors());
        assertEquals(6, events.get(0).getDeadlineNo());
        assertEquals("其他", events.get(1).getEventType());
        assertEquals("已兑现", events.get(1).getStatus());
    }

    @Test
    void parseEventsCapsPerChapterAndDropsBlankFact()
    {
        String json = "{\"events\":[{\"fact\":\"\"},{\"fact\":\"1\"},{\"fact\":\"2\"},{\"fact\":\"3\"},{\"fact\":\"4\"},"
                + "{\"fact\":\"5\"}]}";
        List<SysWorkTimelineEvent> events = WorkTimelineSupport.parseEvents(read(json));
        assertEquals(WorkTimelineSupport.MAX_EVENTS_PER_CHAPTER, events.size());
        assertEquals("1", events.get(0).getFact());
    }

    @Test
    void parseClosesKeepsOnlyUsableKeywords()
    {
        String json = "{\"closes\":[\"擂台对赌\",\"\",\"a\",\"擂台对赌\"]}";
        List<String> closes = WorkTimelineSupport.parseCloses(read(json));
        assertEquals(1, closes.size());
        assertEquals("擂台对赌", closes.get(0));
    }

    @Test
    void blockListsOpenFirstThenRecentAndNotesDeadline()
    {
        List<SysWorkTimelineEvent> recent = new ArrayList<>();
        List<SysWorkTimelineEvent> open = new ArrayList<>();
        SysWorkTimelineEvent agreed = event(3L, 3, "约定", "江野与赵德贵约七日后擂台对赌", "江野、赵德贵",
                SysWorkTimelineEvent.STATUS_OPEN);
        agreed.setDeadlineNo(6);
        open.add(agreed);
        recent.add(event(9L, 8, "道具转移", "铜钥匙交到吴德贵手里", "吴德贵", SysWorkTimelineEvent.STATUS_DONE));
        recent.add(agreed);

        String text = WorkTimelineSupport.block(recent, open);
        assertTrue(text.contains("第3集｜约定｜"));
        assertTrue(text.contains("应在第6集兑现"));
        assertTrue(text.contains("第8集｜道具转移｜"));
        // 未兑现排在最近事件前面
        assertTrue(text.indexOf("第3集") < text.indexOf("第8集"));
    }

    @Test
    void emptyInputsProduceNoBlock()
    {
        assertEquals("", WorkTimelineSupport.block(null, null));
        assertTrue(WorkTimelineSupport.schemaHint().contains("closes"));
        assertTrue(ChapterSummarySupport.emptyLedgerJson().contains("\"events\""));
    }

    private com.fasterxml.jackson.databind.JsonNode read(String json)
    {
        try
        {
            return mapper.readTree(json);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private SysWorkTimelineEvent event(Long id, int no, String type, String fact, String actors, String status)
    {
        SysWorkTimelineEvent event = new SysWorkTimelineEvent();
        event.setEventId(id);
        event.setChapterNo(no);
        event.setEventType(type);
        event.setFact(fact);
        event.setActors(actors);
        event.setStatus(status);
        return event;
    }
}
