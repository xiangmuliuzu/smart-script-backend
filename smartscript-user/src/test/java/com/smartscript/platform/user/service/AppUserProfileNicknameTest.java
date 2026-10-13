package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.dto.UserProfileUpdateRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;
import com.smartscript.platform.user.mapper.AuthorCapabilityAdminMapper;

class AppUserProfileNicknameTest
{
    @Test
    void duplicateNameReturnsReadableConflictBeforeOtherFieldsAreWritten()
    {
        AppUserMapper users = mock(AppUserMapper.class);
        AppUserCenterMapper center = mock(AppUserCenterMapper.class);
        AppUserRecord user = new AppUserRecord();
        user.setUserId(701L);
        user.setUserType("01");
        user.setStatus("0");
        user.setDelFlag("0");
        when(users.selectById(701L)).thenReturn(user);
        when(center.updateNickName(701L, "新名称"))
                .thenThrow(new DuplicateKeyException("database detail must not be exposed"));
        AppUserProfileService service = new AppUserProfileService(users, center, mock(AuthorCapabilityAdminMapper.class));
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setNickname("  新名称  ");
        request.setBio("新简介");
        AppAuthException error = assertThrows(AppAuthException.class, () -> service.updateProfile(701L, request));
        assertEquals(409, error.getHttpStatus());
        assertEquals(AppUserErrorCodes.NICKNAME_TAKEN, error.getCode());
        assertEquals("用户名称已被使用，请更换名称", error.getMessage());
        verify(center, never()).updateBio(anyLong(), anyString());
    }
}
