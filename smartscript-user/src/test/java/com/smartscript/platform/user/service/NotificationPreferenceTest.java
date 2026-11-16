package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.user.domain.UserNotificationPreference;
import com.smartscript.platform.user.dto.NotificationPreferenceDto;
import com.smartscript.platform.user.dto.NotificationPreferenceUpdateRequest;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;

class NotificationPreferenceTest
{
    @Test
    void specificSettingsOverrideLegacyTransactionPreference()
    {
        AppUserCenterMapper mapper = mock(AppUserCenterMapper.class);
        UserNotificationPreference legacy = new UserNotificationPreference();
        legacy.setChannel("INBOX"); legacy.setType("TRANSACTION"); legacy.setEnabled(false);
        UserNotificationPreference order = new UserNotificationPreference();
        order.setChannel("INBOX"); order.setType("ORDER"); order.setEnabled(true);
        when(mapper.selectPreferences(1L)).thenReturn(List.of(legacy, order));
        UserMessageService service = new UserMessageService(mapper);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> preferences = (List<Map<String, Object>>) service.getPreferences(1L).get("preferences");
        assertEquals(false, enabled(preferences, "INQUIRY"));
        assertEquals(true, enabled(preferences, "ORDER"));
        assertEquals(false, enabled(preferences, "CONTRACT"));

        NotificationPreferenceDto item = new NotificationPreferenceDto();
        item.setChannel("INBOX"); item.setType("ORDER"); item.setEnabled(true);
        NotificationPreferenceUpdateRequest request = new NotificationPreferenceUpdateRequest();
        request.setPreferences(List.of(item));
        service.updatePreferences(1L, request);
        verify(mapper).upsertPreference(1L, "INBOX", "ORDER", true);
    }

    private Object enabled(List<Map<String, Object>> preferences, String type)
    {
        return preferences.stream().filter(p -> "INBOX".equals(p.get("channel")) && type.equals(p.get("type")))
                .findFirst().orElseThrow().get("enabled");
    }
}
