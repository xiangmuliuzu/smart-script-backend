package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import com.smartscript.platform.content.dto.AppAiOutlineRequest;
import com.smartscript.platform.content.dto.AppAiPolishRequest;
import com.smartscript.platform.content.dto.AppAiWriteRequest;
import com.smartscript.platform.content.service.impl.AiWriteServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;
import com.smartscript.platform.review.service.RiskCheckService;

class AiContentRiskTest
{
    @Test
    void eachGenerationPathChecksRiskAndStopsBeforeCallingAi()
    {
        AiWriteServiceImpl service = new AiWriteServiceImpl();
        IdentityProvider identity = mock(IdentityProvider.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RiskCheckService risk = mock(RiskCheckService.class);
        AiServiceClient ai = mock(AiServiceClient.class);
        ReflectionTestUtils.setField(service, "identityProvider", identity);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(service, "riskCheckService", risk);
        ReflectionTestUtils.setField(service, "aiServiceClient", ai);
        when(identity.currentUserId()).thenReturn(7L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1);
        doThrow(new IllegalArgumentException("blocked")).when(risk).checkContentOrThrow(7L, "故事内容");
        AppAiWriteRequest write = new AppAiWriteRequest(); write.setPrompt("故事内容");
        AppAiPolishRequest polish = new AppAiPolishRequest(); polish.setContent("故事内容");
        AppAiOutlineRequest outline = new AppAiOutlineRequest(); outline.setInspiration("故事内容");
        List<Runnable> calls = List.of(() -> service.write(write), () -> service.writeStream(write, x -> {}),
                () -> service.polish(polish), () -> service.polishStream(polish, x -> {}),
                () -> service.outline(outline), () -> service.outlineStream(outline, x -> {}));
        for (Runnable call : calls) assertThrows(IllegalArgumentException.class, call::run);
        verify(risk, times(6)).checkContentOrThrow(7L, "故事内容");
        verifyNoInteractions(ai);
    }
}
