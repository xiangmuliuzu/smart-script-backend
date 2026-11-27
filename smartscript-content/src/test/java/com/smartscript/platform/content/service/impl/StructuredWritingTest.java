package com.smartscript.platform.content.service.impl;

import static com.smartscript.platform.content.service.impl.StructuredWritingJson.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartscript.platform.content.exception.AiServiceUnavailableException;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper.BlueprintRecord;
import com.smartscript.platform.content.service.AiCallResult;
import com.smartscript.platform.identity.IdentityProvider;

class StructuredWritingTest
{
    @Test
    void keyedRegenerationKeepsOtherEntriesAndRelationshipKeys()
    {
        ObjectNode blueprint = object();
        BlueprintStructure.mergeRoster(blueprint, parse("""
                {"characters":[{"key":"a","name":"阿甲","role":"记者"},{"key":"b","name":"阿乙"}],
                 "items":[{"name":"钥匙"}],"relations":[{"a":"a","b":"b","label":"搭档"}]}
                """), object());
        ObjectNode before = blueprint.deepCopy();
        String key = nameKey("c", "阿甲");
        BlueprintStructure.merge(blueprint, "characters", key, parse("""
                {"characters":[{"name":"改名","goal":"调查"}]}
                """).path("characters"));
        assertEquals(key, blueprint.at("/characters/0/key").asText());
        assertEquals("改名", blueprint.at("/characters/0/name").asText());
        assertEquals("记者", blueprint.at("/characters/0/role").asText());
        assertEquals(before.at("/characters/1"), blueprint.at("/characters/1"));
        assertEquals(before.path("items"), blueprint.path("items"));
        assertEquals(key, blueprint.at("/relations/0/a").asText());
    }

    @Test
    void acceptsFencedJsonButRejectsMissingActsAndDropsEpisodeLevelOutput()
    {
        var raw = parse("""
                ```json
                {"outline":{"acts":[{"summary":"起"},{"summary":"承"},{"summary":"结"}],"episodes":[{"chapterNo":1}]}}
                ```
                """);
        var blueprint = BlueprintStructure.normalize(raw, List.of("outline"));
        assertEquals(3, blueprint.at("/outline/acts").size());
        assertFalse(blueprint.path("outline").has("episodes"));
        assertThrows(AiServiceUnavailableException.class, () -> BlueprintStructure.normalize(parse("{}"), List.of("outline")));
        assertThrows(AiServiceUnavailableException.class, () -> parse("{\"outline\":{\"acts\":["));
    }

    @Test
    void regenerateAcceptsUnsetSkillInBothRequestAndSavedSetup()
    {
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var ai = mock(AiWriteServiceImpl.class);
        var records = mock(AppAiWriteRecordMapper.class);
        var identity = mock(IdentityProvider.class);
        when(identity.currentUserId()).thenReturn(9L);
        var draft = new BlueprintRecord(33L, null, "{\"title\":\"旧作\"}", "{\"world\":{}}", "draft", "2026-01-01");
        when(records.selectBlueprint(eq(9L), eq(33L), anyBoolean())).thenReturn(draft);
        var response = new AiCallResult("{\"world\":{\"setting\":\"古城\"}}", 25, "test");
        when(ai.structuredStream(anyString(), anyString(), isNull(), isNull(), any())).thenAnswer(call -> {
            verify(transactions, never()).getTransaction(any());
            return response;
        });
        when(ai.resolveModel(response)).thenReturn("test");
        when(records.updateBlueprint(eq(9L), eq(33L), anyString(), anyString(), anyString(), eq("test"), eq(25))).thenReturn(1);
        var service = new BlueprintService(ai, records, null, null, identity, transactions);

        var result = service.regenerate(new BlueprintService.RegenerateRequest(33L, "world", null, null, null, null),
                ignored -> { }, ignored -> { });

        assertEquals("古城", result.at("/blueprint/world/setting").asText());
        verify(ai).consumeStructuredPoints(9L, "blueprint", 33L, 25);
    }

    @Test
    void chapterDraftFiltersUnknownRosterWhileLegacyBackfillUsesChapterEvidence()
    {
        var context = parse("""
                {"roster":[{"key":"c-a","name":"阿甲"}],"itemRoster":[{"key":"i-a","name":"钥匙"}]}
                """);
        var output = parse("""
                {"cast":[{"name":"阿甲"},{"name":"阿乙"},{"key":"c-a","name":"不匹配"}],
                 "items":[{"name":"钥匙"},{"name":"雨伞"}],
                 "edges":[{"a":"阿甲","b":"阿乙","label":"搭档"}]}
                """);
        var draft = ChapterDraftService.aiMeta(output, context, "");
        assertEquals(1, draft.path("cast").size());
        assertEquals(1, draft.path("items").size());
        assertTrue(draft.path("edges").isEmpty());

        var backfill = ChapterDraftService.aiMeta(output, context, "阿甲和阿乙一起找到了钥匙。");
        assertEquals(2, backfill.path("cast").size());
        assertEquals(1, backfill.path("items").size());
        assertEquals(nameKey("c", "阿乙"), backfill.at("/edges/0/b").asText());
    }
}
