package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import com.smartscript.platform.content.dto.AppAiWriteRecordInsert;
import com.smartscript.platform.content.dto.AppAiWriteRequest;
import com.smartscript.platform.content.mapper.AppAiRequestMapper;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.service.impl.AiWriteServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;

class AiContinuationIsolationTest
{
    private final AiWriteServiceImpl service = new AiWriteServiceImpl();
    private final AiServiceClient client = mock(AiServiceClient.class);
    private final IdentityProvider identity = mock(IdentityProvider.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AppAiWriteRecordMapper records = mock(AppAiWriteRecordMapper.class);
    private final AppAiRequestMapper requests = mock(AppAiRequestMapper.class);

    @BeforeEach
    void setUp() throws Exception
    {
        ReflectionTestUtils.setField(service, "aiServiceClient", client);
        ReflectionTestUtils.setField(service, "identityProvider", identity);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "writeRecordMapper", records);
        ReflectionTestUtils.setField(service, "aiRequestMapper", requests);
        ReflectionTestUtils.setField(service, "aiModel", "test-model");
        when(identity.currentUserId()).thenReturn(101L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Long.class))).thenReturn(100);
        when(client.write(anyString(), nullable(String.class)))
                .thenReturn(new AiCallResult("synthetic story", 100, "test-model"));
        when(client.writeStream(anyString(), nullable(String.class), any()))
                .thenReturn(new AiCallResult("synthetic story", 100, "test-model"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"继续", " \n继续！ ", "继续写", "继续写下去", "接着写", "请续写。", "continue", "Continue writing!"})
    void bareContinuationDoesNotCallAiStoreRecordsOrChargeQuota(String prompt)
    {
        AppAiWriteRequest request = request(prompt);
        assertThrows(IllegalArgumentException.class, () -> service.write(request));
        assertThrows(IllegalArgumentException.class, () -> service.writeStream(request, ignored -> {}));
        verifyNoInteractions(client, jdbc, records, requests);
    }

    @Test
    void anotherUsersBareContinueCannotUseThePreviousGeneration()
    {
        service.write(request("Write a story about the synthetic character ALPHA-101."));
        clearInvocations(client, jdbc, records, requests);
        when(identity.currentUserId()).thenReturn(202L);

        assertThrows(IllegalArgumentException.class, () -> service.write(request("继续")));
        verifyNoInteractions(client, jdbc, records, requests);
    }

    @Test
    void explicitContextStillWorksAndRecordsUseTheCurrentUser()
    {
        when(identity.currentUserId()).thenReturn(202L);
        String prompt = "已有正文：\nSynthetic BETA-202 stood beside the river.\n续写要求：\n继续";
        AppAiWriteRequest request = request(prompt);

        assertEquals("synthetic story", service.write(request).getContent());
        Consumer<String> delta = ignored -> {};
        assertEquals("synthetic story", service.writeStream(request, delta).getContent());
        verify(client).write(prompt, null);
        verify(client).writeStream(eq(prompt), nullable(String.class),
                org.mockito.ArgumentMatchers.<Consumer<String>>any());
        var saved = org.mockito.ArgumentCaptor.forClass(AppAiWriteRecordInsert.class);
        verify(records, org.mockito.Mockito.times(2)).insertWriteRecord(saved.capture());
        saved.getAllValues().forEach(row -> assertEquals(202L, row.getUserId()));
        verify(records, never()).selectWriteRecordsByUser(any());
        verifyNoInteractions(requests);
    }

    private static AppAiWriteRequest request(String prompt)
    {
        AppAiWriteRequest request = new AppAiWriteRequest();
        request.setPrompt(prompt);
        return request;
    }
}
