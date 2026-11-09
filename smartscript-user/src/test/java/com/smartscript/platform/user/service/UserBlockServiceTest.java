package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.UserBlockMapper;
import com.smartscript.platform.user.mapper.UserFollowMapper;

/**
 * 个人黑名单：自己/不存在校验、幂等与双向拦截文案。
 */
class UserBlockServiceTest
{
    private final UserBlockMapper blockMapper = Mockito.mock(UserBlockMapper.class);
    private final UserFollowMapper followMapper = Mockito.mock(UserFollowMapper.class);
    private final UserBlockService service = new UserBlockService(blockMapper, followMapper);

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
    void blockSelfRejected()
    {
        AppAuthException e = assertThrows(AppAuthException.class, () -> service.block(100L, 100L));
        assertEquals(400, e.getHttpStatus());
        verify(blockMapper, never()).insertBlock(any(), any());
    }

    @Test
    void blockMissingTargetRejected()
    {
        when(followMapper.selectPublicUser(999L)).thenReturn(null);
        AppAuthException e = assertThrows(AppAuthException.class, () -> service.block(100L, 999L));
        assertEquals(404, e.getHttpStatus());
    }

    @Test
    void blockIsIdempotentAndUnblockReturnsChanged()
    {
        when(followMapper.selectPublicUser(200L)).thenReturn(user(200L));
        when(blockMapper.insertBlock(100L, 200L)).thenReturn(1).thenReturn(0);
        assertTrue(service.block(100L, 200L), "首次拉黑 changed=true");
        assertFalse(service.block(100L, 200L), "重复拉黑 changed=false");
        when(blockMapper.deleteBlock(100L, 200L)).thenReturn(1).thenReturn(0);
        assertTrue(service.unblock(100L, 200L));
        assertFalse(service.unblock(100L, 200L));
    }

    @Test
    void requireNotBlockedRejectsBothDirectionsWithDistinctMessages()
    {
        when(blockMapper.countBlockRelation(100L, 200L)).thenReturn(1);
        AppAuthException mine = assertThrows(AppAuthException.class,
                () -> service.requireNotBlocked(100L, 200L, "发起询盘"));
        assertEquals(403, mine.getHttpStatus());
        assertTrue(mine.getMessage().contains("你已将对方加入黑名单"));

        when(blockMapper.countBlockRelation(300L, 400L)).thenReturn(0);
        when(blockMapper.countBlockRelation(400L, 300L)).thenReturn(1);
        AppAuthException theirs = assertThrows(AppAuthException.class,
                () -> service.requireNotBlocked(300L, 400L, "发起询盘"));
        assertEquals(403, theirs.getHttpStatus());
        assertTrue(theirs.getMessage().contains("对方已将你加入黑名单"));
    }
}
