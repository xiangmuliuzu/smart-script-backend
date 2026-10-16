package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.dto.UserPublicProfileDto;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.UserFollowMapper;

/**
 * 个人主页与关注：状态机、幂等、自己/他人区分与真实计数。
 */
class UserSocialServiceTest
{
    private final UserFollowMapper followMapper = Mockito.mock(UserFollowMapper.class);
    private final AppUserProfileService profileService = Mockito.mock(AppUserProfileService.class);
    private final UserSocialService service = new UserSocialService(followMapper, profileService);

    private AppUserRecord user(long id)
    {
        AppUserRecord u = new AppUserRecord();
        u.setUserId(id);
        u.setNickName("用户" + id);
        u.setUserType("01");
        u.setStatus("0");
        u.setDelFlag("0");
        return u;
    }

    @Test
    void publicProfileCarriesRealCountsAndOtherFlags()
    {
        when(followMapper.selectPublicUser(200L)).thenReturn(user(200L));
        when(followMapper.countFollowing(200L)).thenReturn(3);
        when(followMapper.countFollowers(200L)).thenReturn(5);
        when(followMapper.countFollowRelation(100L, 200L)).thenReturn(1);

        UserPublicProfileDto dto = service.publicProfile(100L, 200L);

        assertEquals(200L, dto.getUserId());
        assertEquals(3, dto.getFollowingCount());
        assertEquals(5, dto.getFollowerCount());
        assertTrue(dto.getIsFollowing(), "100 已关注 200");
        assertFalse(dto.getIsSelf());
    }

    @Test
    void ownProfileIsSelfAndNotFollowing()
    {
        when(followMapper.selectPublicUser(100L)).thenReturn(user(100L));
        UserPublicProfileDto dto = service.publicProfile(100L, 100L);
        assertTrue(dto.getIsSelf());
        assertFalse(dto.getIsFollowing());
        verify(followMapper, never()).countFollowRelation(any(), any());
    }

    @Test
    void followSelfRejected()
    {
        AppAuthException e = assertThrows(AppAuthException.class, () -> service.follow(100L, 100L));
        assertEquals(400, e.getHttpStatus());
        verify(followMapper, never()).insertFollow(any(), any());
    }

    @Test
    void followMissingTargetRejected()
    {
        when(followMapper.selectPublicUser(999L)).thenReturn(null);
        AppAuthException e = assertThrows(AppAuthException.class, () -> service.follow(100L, 999L));
        assertEquals(404, e.getHttpStatus());
    }

    @Test
    void followIsIdempotentReturnsChanged()
    {
        when(followMapper.selectPublicUser(200L)).thenReturn(user(200L));
        when(followMapper.insertFollow(100L, 200L)).thenReturn(1).thenReturn(0);
        assertTrue(service.follow(100L, 200L), "首次关注 changed=true");
        assertFalse(service.follow(100L, 200L), "重复关注 changed=false");
    }

    @Test
    void unfollowReturnsChanged()
    {
        when(followMapper.deleteFollow(100L, 200L)).thenReturn(1).thenReturn(0);
        assertTrue(service.unfollow(100L, 200L));
        assertFalse(service.unfollow(100L, 200L));
    }
}
