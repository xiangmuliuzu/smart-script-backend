package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.user.domain.UserRealNameAuth;
import com.smartscript.platform.user.dto.RealNameSubmitRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.integration.AliyunRealNameVerifier;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;

class AppRealNameServiceTest
{
    private final AppUserCenterMapper mapper = mock(AppUserCenterMapper.class);

    @Test
    void submitAndResubmitVerifyNameAndIdWithoutMaterials()
    {
        var verifier = mock(AliyunRealNameVerifier.class);
        var reviews = mock(RealNameReviewService.class);
        var realName = new AppRealNameService(mapper, verifier, reviews);
        var request = new RealNameSubmitRequest();
        request.setRealName("测试用户");
        request.setIdNumber("11010119900101123X");
        doAnswer(call -> {
            ((UserRealNameAuth) call.getArgument(0)).setApplicationId(77L);
            return 1;
        }).when(mapper).insertRealName(any());
        realName.submit(12L, request);
        when(mapper.selectLatestRealNameStatus(12L)).thenReturn("REJECTED");
        realName.resubmit(12L, request);
        verify(verifier, times(2)).verify("测试用户", "11010119900101123X");
        verify(mapper, times(2)).insertRealName(argThat(row -> row.getMaterialRef() == null
                && row.getUserId().equals(12L) && "PENDING".equals(row.getStatus())));
        verify(reviews, times(2)).decide(77L, "APPROVE", null, null, "PENDING");
    }

    @Test
    void enabledVerificationUsesExistingReviewFlow() throws Exception
    {
        var verifier = mock(AliyunRealNameVerifier.class);
        var reviews = mock(RealNameReviewService.class);
        var realName = new AppRealNameService(mapper, verifier, reviews);
        doAnswer(call -> {
            ((UserRealNameAuth) call.getArgument(0)).setApplicationId(77L);
            return 1;
        }).when(mapper).insertRealName(any());
        realName.submit(12L, request());
        verify(reviews).decide(77L, "APPROVE", null, null, "PENDING");
        when(verifier.verify(anyString(), anyString())).thenReturn("姓名与身份证号码不匹配");
        realName.resubmit(12L, request());
        verify(reviews).decide(77L, "REJECT", "姓名与身份证号码不匹配", null, "PENDING");
    }

    @Test
    void unavailableVerifierDoesNotCreateOrApproveApplication()
    {
        var verifier = mock(AliyunRealNameVerifier.class);
        var reviews = mock(RealNameReviewService.class);
        var realName = new AppRealNameService(mapper, verifier, reviews);
        when(verifier.verify(anyString(), anyString()))
                .thenThrow(new AppAuthException(50000, 503, "实名认证服务暂不可用"));
        assertThrows(AppAuthException.class, () -> realName.submit(12L, request()));
        verify(mapper, never()).insertRealName(any());
        verifyNoInteractions(reviews);
    }

    private RealNameSubmitRequest request()
    {
        var request = new RealNameSubmitRequest();
        request.setRealName("测试用户");
        request.setIdNumber("110101199001011234");
        return request;
    }
}
