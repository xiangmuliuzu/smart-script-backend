package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.smartscript.platform.user.dto.RealNameSubmitRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.integration.AliyunRealNameVerifier;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;

class AppRealNameServiceTest {
    private final AppUserCenterMapper mapper = mock(AppUserCenterMapper.class);
    private final AliyunRealNameVerifier verifier = mock(AliyunRealNameVerifier.class);
    private final RealNameMaterialStorage storage = mock(RealNameMaterialStorage.class);
    private final AppRealNameService service = new AppRealNameService(mapper, verifier, storage);
    private final MockMultipartFile front = new MockMultipartFile("front", new byte[]{1});
    private final MockMultipartFile back = new MockMultipartFile("back", new byte[]{2});

    private RealNameSubmitRequest request() {
        var request = new RealNameSubmitRequest();
        request.setRealName("测试用户");
        request.setIdNumber("11010119900101123X");
        return request;
    }
    @Test void verifiedSubmissionRemainsPendingWithBothMaterials() {
        when(storage.validate(front)).thenReturn(new byte[]{1});
        when(storage.validate(back)).thenReturn(new byte[]{2});
        when(storage.save(eq(12L), any())).thenReturn("front-ref", "back-ref");
        service.submitWithMaterials(12L, request(), front, back);
        verify(verifier).verify("测试用户", "11010119900101123X");
        verify(mapper).insertRealName(argThat(row -> "PENDING".equals(row.getStatus())
            && "front-ref".equals(row.getMaterialRef()) && "back-ref".equals(row.getMaterialBackRef())
            && "测试用户".equals(row.getPendingRealName())));
        verify(mapper, never()).markRealNameVerified(any(), any());
    }
    @Test void verificationFailureDoesNotSaveMaterialsOrCreateApplication() {
        when(verifier.verify(anyString(), anyString())).thenReturn("姓名与身份证号码不匹配");
        assertThrows(AppAuthException.class, () -> service.submitWithMaterials(12L, request(), front, back));
        verify(storage, never()).save(any(), any());
        verify(mapper, never()).insertRealName(any());
    }
    @Test void missingMaterialRejectedBeforeVerification() {
        when(storage.validate(null)).thenThrow(new AppAuthException(40000, 400, "请上传身份证正反面"));
        assertThrows(AppAuthException.class, () -> service.submit(12L, request()));
        verifyNoInteractions(verifier);
    }
}
