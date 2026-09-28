package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.dto.UserProfileDto;
import com.smartscript.platform.user.dto.UserProfileUpdateRequest;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;
import com.smartscript.platform.user.mapper.AppUserMapper;

/**
 * A5 个人简介（bio，2026-09-28 契约修订）：校验与数据隔离。
 *
 * 覆盖：新增/修改/清空/超长、字段缺省语义、以及「只能修改本人」的结构性隔离
 * （请求对象不含 userId，服务层只写 currentUserId 对应行）。
 */
class AppUserProfileBioTest
{
    // ---------------- 规范化 ----------------

    @Test
    void bioTrimsAndAllowsEmptyForClearing()
    {
        assertEquals("", AppUserProfileService.normalizeBio(null));
        assertEquals("", AppUserProfileService.normalizeBio("   "));
        assertEquals("热爱剧本创作", AppUserProfileService.normalizeBio("  热爱剧本创作  "));
        // 清空：显式空串合法，与昵称不同
        assertEquals("", AppUserProfileService.normalizeBio(""));
    }

    @Test
    void bioRejectsOverMaxLength()
    {
        assertEquals(AppUserProfileService.BIO_MAX,
                AppUserProfileService.normalizeBio("字".repeat(AppUserProfileService.BIO_MAX)).length());
        AppAuthException e = assertThrows(AppAuthException.class,
                () -> AppUserProfileService.normalizeBio("字".repeat(AppUserProfileService.BIO_MAX + 1)));
        assertEquals(400, e.getHttpStatus());
        assertEquals(AppUserErrorCodes.PARAM, e.getCode());
    }

    @Test
    void updateRequestMustNotExposeUserIdField()
    {
        // 数据隔离的结构性保证：请求对象没有 userId 字段，
        // 其他用户无法通过提交 userId 越权修改他人资料。
        assertTrue(Arrays.stream(UserProfileUpdateRequest.class.getDeclaredFields())
                .noneMatch(f -> f.getName().equalsIgnoreCase("userId")),
                "UserProfileUpdateRequest must not declare a userId field");
    }

    // ---------------- 服务行为（Mockito 隔离） ----------------

    private AppUserRecord appUser(long id, String bio)
    {
        AppUserRecord u = new AppUserRecord();
        u.setUserId(id);
        u.setUserName("app_user_" + id);
        u.setNickName("用户" + id);
        u.setUserType("01");
        u.setStatus("0");
        u.setDelFlag("0");
        u.setBio(bio);
        return u;
    }

    @Test
    void updateBioOnlyTouchesOwnRowAndOnlyBio()
    {
        AppUserMapper userMapper = Mockito.mock(AppUserMapper.class);
        AppUserCenterMapper centerMapper = Mockito.mock(AppUserCenterMapper.class);
        AppUserProfileService service = new AppUserProfileService(userMapper, centerMapper);
        when(userMapper.selectById(1001L)).thenReturn(appUser(1001L, null));

        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setBio("  新简介  ");
        UserProfileDto dto = service.updateProfile(1001L, request);

        // 只写本人行：userId 必须等于身份 id，而非请求中的任何字段
        verify(centerMapper).updateBio(eq(1001L), eq("新简介"));
        verify(centerMapper, never()).updateNickName(Mockito.anyLong(), anyString());
        verify(centerMapper, never()).updateAvatar(Mockito.anyLong(), anyString());
        assertEquals("新简介", dto.getBio());
    }

    @Test
    void clearBioPersistsEmptyString()
    {
        AppUserMapper userMapper = Mockito.mock(AppUserMapper.class);
        AppUserCenterMapper centerMapper = Mockito.mock(AppUserCenterMapper.class);
        AppUserProfileService service = new AppUserProfileService(userMapper, centerMapper);
        when(userMapper.selectById(1002L)).thenReturn(appUser(1002L, "旧简介"));

        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setBio("   ");
        UserProfileDto dto = service.updateProfile(1002L, request);

        verify(centerMapper).updateBio(eq(1002L), eq(""));
        assertNull(dto.getBio(), "空简介对外表现为 null（未填写）");
    }

    @Test
    void allFieldsAbsentIsRejected()
    {
        AppUserMapper userMapper = Mockito.mock(AppUserMapper.class);
        AppUserCenterMapper centerMapper = Mockito.mock(AppUserCenterMapper.class);
        AppUserProfileService service = new AppUserProfileService(userMapper, centerMapper);

        UserProfileUpdateRequest empty = new UserProfileUpdateRequest();
        AppAuthException e = assertThrows(AppAuthException.class,
                () -> service.updateProfile(1003L, empty));
        assertEquals(AppUserErrorCodes.PARAM, e.getCode());
        verify(centerMapper, never()).updateBio(Mockito.anyLong(), anyString());
    }

    @Test
    void bioRejectedBeforeAnyWriteWhenTooLong()
    {
        AppUserMapper userMapper = Mockito.mock(AppUserMapper.class);
        AppUserCenterMapper centerMapper = Mockito.mock(AppUserCenterMapper.class);
        AppUserProfileService service = new AppUserProfileService(userMapper, centerMapper);
        when(userMapper.selectById(1004L)).thenReturn(appUser(1004L, null));

        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setBio("字".repeat(AppUserProfileService.BIO_MAX + 1));
        assertThrows(AppAuthException.class, () -> service.updateProfile(1004L, request));
        verify(centerMapper, never()).updateBio(Mockito.anyLong(), anyString());
        // 同请求中其它字段也不应被写入（全量拒绝，不部分成功）
        verify(centerMapper, never()).updateNickName(Mockito.anyLong(), anyString());
        verify(centerMapper, never()).updateAvatar(Mockito.anyLong(), anyString());
    }
}
