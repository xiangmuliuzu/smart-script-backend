package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.domain.UserCreatorApplication;
import com.smartscript.platform.user.dto.CreatorApplyStatusDto;
import com.smartscript.platform.user.dto.CreatorApplySubmitRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;

/**
 * App 侧创作者申请：状态机、字段校验与状态回显。
 */
class CreatorApplyServiceTest
{
    private final AppUserCenterMapper centerMapper = Mockito.mock(AppUserCenterMapper.class);
    private final CreatorApplyService service = new CreatorApplyService(centerMapper);

    private CreatorApplySubmitRequest request()
    {
        CreatorApplySubmitRequest r = new CreatorApplySubmitRequest();
        r.setPenName("青山");
        r.setDirection("都市/悬疑");
        r.setPortfolio("《夜行》");
        r.setIntro("写过若干短篇，希望加入创作");
        r.setContact("13800000000");
        return r;
    }

    @Test
    void notSubmittedReturnsPlaceholder()
    {
        when(centerMapper.selectLatestCreatorApplication(1L)).thenReturn(null);
        CreatorApplyStatusDto dto = service.getStatus(1L);
        assertEquals("NOT_SUBMITTED", dto.getStatus());
    }

    @Test
    void pendingSubmitRejectedWith409()
    {
        when(centerMapper.selectLatestCreatorApplicationStatus(2L)).thenReturn("PENDING");
        AppAuthException e = assertThrows(AppAuthException.class, () -> service.submit(2L, request()));
        assertEquals(409, e.getHttpStatus());
        assertEquals(AppUserErrorCodes.CREATOR_APPLY_CONFLICT, e.getCode());
        verify(centerMapper, never()).insertCreatorApplication(Mockito.any());
    }

    @Test
    void approvedSubmitRejectedWith409()
    {
        when(centerMapper.selectLatestCreatorApplicationStatus(3L)).thenReturn("APPROVED");
        assertThrows(AppAuthException.class, () -> service.submit(3L, request()));
        verify(centerMapper, never()).insertCreatorApplication(Mockito.any());
    }

    @Test
    void rejectedCanResubmit()
    {
        when(centerMapper.selectLatestCreatorApplicationStatus(4L)).thenReturn("REJECTED");
        when(centerMapper.selectLatestCreatorApplication(4L)).thenReturn(new UserCreatorApplication());

        service.submit(4L, request());

        ArgumentCaptor<UserCreatorApplication> captor = ArgumentCaptor.forClass(UserCreatorApplication.class);
        verify(centerMapper).insertCreatorApplication(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals("青山", captor.getValue().getPenName());
    }

    @Test
    void notSubmittedCanSubmit()
    {
        when(centerMapper.selectLatestCreatorApplicationStatus(5L)).thenReturn(null);
        when(centerMapper.selectLatestCreatorApplication(5L)).thenReturn(new UserCreatorApplication());

        service.submit(5L, request());

        verify(centerMapper).insertCreatorApplication(Mockito.any());
    }

    @Test
    void blankPenNameRejectedWith400()
    {
        CreatorApplySubmitRequest r = request();
        r.setPenName("   ");
        AppAuthException e = assertThrows(AppAuthException.class, () -> service.submit(6L, r));
        assertEquals(400, e.getHttpStatus());
    }

    @Test
    void statusEchoesSubmittedFields()
    {
        UserCreatorApplication row = new UserCreatorApplication();
        row.setStatus("REJECTED");
        row.setPenName("青山");
        row.setDirection("都市");
        row.setPortfolio("《夜行》");
        row.setIntro("说明");
        row.setContact("13800000000");
        row.setRejectReason("信息不全");
        when(centerMapper.selectLatestCreatorApplication(7L)).thenReturn(row);

        CreatorApplyStatusDto dto = service.getStatus(7L);
        assertEquals("REJECTED", dto.getStatus());
        assertEquals("青山", dto.getPenName());
        assertEquals("信息不全", dto.getRejectReason());
        assertEquals("13800000000", dto.getContact());
    }
}
