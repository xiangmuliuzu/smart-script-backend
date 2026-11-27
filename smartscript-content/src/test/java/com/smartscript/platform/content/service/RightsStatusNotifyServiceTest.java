package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.smartscript.platform.content.domain.SysCopyrightApplication;
import com.smartscript.platform.content.domain.SysCopyrightSeal;
import com.smartscript.platform.notify.UserInboxNotifier;

class RightsStatusNotifyServiceTest
{
    private UserInboxNotifier inbox;
    private RightsStatusNotifyService service;

    @BeforeEach
    void setUp()
    {
        inbox = mock(UserInboxNotifier.class);
        service = new RightsStatusNotifyService();
        ReflectionTestUtils.setField(service, "inboxNotifier", inbox);
    }

    @Test
    void sealApprovedCarriesNameAndSealLanding()
    {
        SysCopyrightSeal seal = new SysCopyrightSeal();
        seal.setSealId(9L);
        seal.setUserId(100L);
        seal.setSealName("合同章");
        seal.setReviewTime(new Date(1_700_000_000_000L));

        service.notifySealReviewed(seal, true);

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bizType = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bizId = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> users = ArgumentCaptor.forClass(List.class);
        verify(inbox).notify(anyString(), eq("SEAL"), title.capture(), content.capture(),
                bizType.capture(), bizId.capture(), users.capture());
        assertEquals("印章审核已通过", title.getValue());
        assertTrue(content.getValue().contains("合同章"));
        assertEquals("SEAL", bizType.getValue());
        assertEquals("9", bizId.getValue());
        assertEquals(List.of(100L), users.getValue());
    }

    @Test
    void copyrightCertificatedUsesCertIdLanding()
    {
        SysCopyrightApplication app = new SysCopyrightApplication();
        app.setApplyId(15L);
        app.setUserId(88L);
        app.setWorkTitle("短剧甲");
        app.setCertNo("CN-001");

        service.notifyCopyrightCertificated(app, 42L, "sync");

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(inbox).notify(anyString(), eq("COPYRIGHT"), eq("版权证书已颁发"), content.capture(),
                eq("COPYRIGHT_CERT"), eq("42"), eq(List.of(88L)));
        assertTrue(content.getValue().contains("短剧甲"));
        assertTrue(content.getValue().contains("CN-001"));
    }

    @Test
    void missingReceiverSkipsNotify()
    {
        SysCopyrightSeal seal = new SysCopyrightSeal();
        seal.setSealId(1L);
        service.notifySealReviewed(seal, false);
        verify(inbox, never()).notify(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyList());
    }
}
