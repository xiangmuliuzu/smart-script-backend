package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.domain.UserCreatorProfile;
import com.smartscript.platform.user.dto.CreatorProfileDto;
import com.smartscript.platform.user.dto.UserProfileDto;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.mapper.AuthorCapabilityAdminMapper;

/**
 * A4 用户中心资料增量（开发文档 §4.3/§4.4/§5.2）：
 * 注册时间时区标注、账号状态原始代码、创作者资料投影归属与空态。
 */
class AppUserProfileExtensionTest
{
    private final AppUserMapper userMapper = Mockito.mock(AppUserMapper.class);
    private final AppUserCenterMapper centerMapper = Mockito.mock(AppUserCenterMapper.class);
    private final AuthorCapabilityAdminMapper capabilityMapper = Mockito.mock(AuthorCapabilityAdminMapper.class);
    private final AppUserProfileService service = new AppUserProfileService(userMapper, centerMapper, capabilityMapper);

    private AppUserRecord appUser(long id)
    {
        AppUserRecord u = new AppUserRecord();
        u.setUserId(id);
        u.setUserName("app_user_" + id);
        u.setNickName("用户" + id);
        u.setUserType("01");
        u.setStatus("0");
        u.setDelFlag("0");
        return u;
    }

    // ---------------- 注册时间 ----------------

    @Test
    void registeredAtIsIso8601WithGmt8Offset()
    {
        // 数据库墙钟值按 serverTimezone=GMT+8 解释，输出带 +08:00 偏移
        assertEquals("2026-09-30T10:00:00+08:00",
                AppUserProfileService.formatRegisteredAt(LocalDateTime.of(2026, 9, 30, 10, 0, 0)));
    }

    @Test
    void registeredAtNullForMissingHistoricalValue()
    {
        // 历史缺失值返回 null（前端显示「—」），不拼接伪时区
        assertNull(AppUserProfileService.formatRegisteredAt(null));
        AppUserRecord user = appUser(1L);
        user.setCreateTime(LocalDateTime.of(2026, 1, 2, 8, 30, 0));
        when(userMapper.selectById(1L)).thenReturn(user);
        assertEquals("2026-01-02T08:30:00+08:00", service.getProfile(1L).getRegisteredAt());
    }

    // ---------------- 账号状态 ----------------

    @Test
    void accountStatusIsRawCode()
    {
        // 返回 sys_user.status 原始代码，未知值由前端显示「状态未知」
        AppUserRecord user = appUser(2L);
        user.setStatus("0");
        when(userMapper.selectById(2L)).thenReturn(user);
        assertEquals("0", service.getProfile(2L).getAccountStatus());
    }

    // ---------------- 创作者资料投影 ----------------

    @Test
    void creatorProfileNullWithoutAuthorCapability()
    {
        when(userMapper.selectById(3L)).thenReturn(appUser(3L));
        when(capabilityMapper.selectEnabledByUserId(3L)).thenReturn(null);
        assertNull(service.getProfile(3L).getCreatorProfile());
    }

    @Test
    void creatorProfileNullWhenCapabilityDisabled()
    {
        when(userMapper.selectById(4L)).thenReturn(appUser(4L));
        when(capabilityMapper.selectEnabledByUserId(4L)).thenReturn(Boolean.FALSE);
        assertNull(service.getProfile(4L).getCreatorProfile());
    }

    @Test
    void creatorProfileEmptyShapeWhenCapabilityWithoutRow()
    {
        // 有能力但无资料行：返回对象本身 + 空类型列表，不虚构笔名
        when(userMapper.selectById(5L)).thenReturn(appUser(5L));
        when(capabilityMapper.selectEnabledByUserId(5L)).thenReturn(Boolean.TRUE);
        when(centerMapper.selectCreatorProfile(5L)).thenReturn(null);

        CreatorProfileDto dto = service.getProfile(5L).getCreatorProfile();
        assertNotNull(dto);
        assertNull(dto.getPenName());
        assertNull(dto.getIntroduction());
        assertTrue(dto.getSpecialties().isEmpty());
    }

    @Test
    void creatorProfileMapsRowFields()
    {
        when(userMapper.selectById(6L)).thenReturn(appUser(6L));
        when(capabilityMapper.selectEnabledByUserId(6L)).thenReturn(Boolean.TRUE);
        UserCreatorProfile row = new UserCreatorProfile();
        row.setUserId(6L);
        row.setPenName("青山");
        row.setSpecialties("G001, G002，G001");
        row.setProfileIntro("写了十年剧本");
        when(centerMapper.selectCreatorProfile(6L)).thenReturn(row);

        CreatorProfileDto dto = service.getProfile(6L).getCreatorProfile();
        assertEquals("青山", dto.getPenName());
        assertEquals("写了十年剧本", dto.getIntroduction());
        // 去重后按序解析；字典未登记的代码名称回退为代码本身
        List<CreatorProfileDto.Specialty> specialties = dto.getSpecialties();
        assertEquals(2, specialties.size());
        assertEquals("G001", specialties.get(0).getCode());
        assertEquals("G001", specialties.get(0).getName());
        assertEquals("G002", specialties.get(1).getCode());
    }

    @Test
    void specialtyParsingHandlesBlankAndDirtyInput()
    {
        assertTrue(AppUserProfileService.specialties(null).isEmpty());
        assertTrue(AppUserProfileService.specialties("  ").isEmpty());
        assertTrue(AppUserProfileService.specialties(" , ， ").isEmpty());
        assertEquals(1, AppUserProfileService.specialties("G001").size());
    }
}
