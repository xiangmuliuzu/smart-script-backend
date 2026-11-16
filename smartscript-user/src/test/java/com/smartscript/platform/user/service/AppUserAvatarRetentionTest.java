package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import com.ruoyi.common.config.RuoYiConfig;
import com.smartscript.platform.user.domain.AppUserRecord;
import com.smartscript.platform.user.dto.UserProfileUpdateRequest;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;
import com.smartscript.platform.user.mapper.AppUserMapper;
import com.smartscript.platform.user.mapper.AuthorCapabilityAdminMapper;

/**
 * 换头像后回收被替换的旧文件（防止上传目录随换头像无限增长）。
 *
 * 关键不变量：仅删除平台自管的 /profile/upload|avatar 路径，且删除前无任何用户
 * 仍引用该路径；外部 http(s) 图片、仍被引用的头像、无旧头像时都不删除。
 */
class AppUserAvatarRetentionTest
{
    private final AppUserMapper userMapper = Mockito.mock(AppUserMapper.class);
    private final AppUserCenterMapper centerMapper = Mockito.mock(AppUserCenterMapper.class);
    private final AuthorCapabilityAdminMapper capabilityMapper = Mockito.mock(AuthorCapabilityAdminMapper.class);
    private final AppUserProfileService service = new AppUserProfileService(userMapper, centerMapper, capabilityMapper);

    private String originalProfile;

    @BeforeEach
    void captureProfile()
    {
        originalProfile = RuoYiConfig.getProfile();
    }

    @AfterEach
    void restoreProfile()
    {
        new RuoYiConfig().setProfile(originalProfile);
    }

    private AppUserRecord user(long id, String avatar)
    {
        AppUserRecord u = new AppUserRecord();
        u.setUserId(id);
        u.setUserName("app_user_" + id);
        u.setNickName("用户" + id);
        u.setUserType("01");
        u.setStatus("0");
        u.setDelFlag("0");
        u.setAvatar(avatar);
        return u;
    }

    private static Path seedFile(String profileRoot, String resourcePath) throws IOException
    {
        // /profile/upload/... -> <profileRoot>/upload/...
        Path file = Path.of(profileRoot, resourcePath.replace("/profile/", ""));
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2, 3});
        return file;
    }

    @Test
    void replacedManagedAvatarFileIsDeleted(@TempDir Path tmp) throws IOException
    {
        String root = tmp.toString();
        new RuoYiConfig().setProfile(root);
        String oldAvatar = "/profile/upload/2026/10/16/old.png";
        Path oldFile = seedFile(root, oldAvatar);

        when(userMapper.selectById(101L)).thenReturn(user(101L, oldAvatar));
        when(centerMapper.countAvatarReferences(oldAvatar)).thenReturn(0);

        UserProfileUpdateRequest req = new UserProfileUpdateRequest();
        req.setAvatar("/profile/upload/2026/10/16/new.png");
        service.updateProfile(101L, req);

        assertFalse(Files.exists(oldFile), "被替换的旧头像文件应在提交后被删除");
    }

    @Test
    void oldAvatarKeptWhenStillReferenced(@TempDir Path tmp) throws IOException
    {
        String root = tmp.toString();
        new RuoYiConfig().setProfile(root);
        String oldAvatar = "/profile/upload/2026/10/16/shared.png";
        Path oldFile = seedFile(root, oldAvatar);

        when(userMapper.selectById(102L)).thenReturn(user(102L, oldAvatar));
        when(centerMapper.countAvatarReferences(oldAvatar)).thenReturn(1);

        UserProfileUpdateRequest req = new UserProfileUpdateRequest();
        req.setAvatar("/profile/upload/2026/10/16/new2.png");
        service.updateProfile(102L, req);

        assertTrue(Files.exists(oldFile), "仍被引用的头像文件不得删除");
    }

    @Test
    void externalAvatarUrlNotTouched(@TempDir Path tmp) throws IOException
    {
        String root = tmp.toString();
        new RuoYiConfig().setProfile(root);
        when(userMapper.selectById(103L)).thenReturn(user(103L, "https://cdn.example.com/a/b.png"));
        when(centerMapper.countAvatarReferences(anyString())).thenReturn(0);

        UserProfileUpdateRequest req = new UserProfileUpdateRequest();
        req.setAvatar("/profile/upload/2026/10/16/new3.png");
        // 外部地址无法映射为本地文件，清理应静默跳过，不抛异常
        service.updateProfile(103L, req);

        assertTrue(Files.exists(tmp), "外部头像不做本地清理");
    }

    @Test
    void noOldAvatarNothingDeleted(@TempDir Path tmp) throws IOException
    {
        String root = tmp.toString();
        new RuoYiConfig().setProfile(root);
        when(userMapper.selectById(104L)).thenReturn(user(104L, null));

        UserProfileUpdateRequest req = new UserProfileUpdateRequest();
        req.setAvatar("/profile/upload/2026/10/16/new4.png");
        service.updateProfile(104L, req);
        // 无旧头像时不应查询引用或触碰磁盘
        Mockito.verify(centerMapper, Mockito.never()).countAvatarReferences(anyString());
    }
}
