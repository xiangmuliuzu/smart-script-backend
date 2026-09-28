package com.smartscript.platform.user.mapper;

import java.util.Date;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.AppUserRecord;

/**
 * App auth access to existing sys_user table only. No parallel user table.
 */
public interface AppUserMapper
{
    AppUserRecord selectByPhone(@Param("phone") String phone);

    /** PC 统一登录用：按用户名或手机号解析账号（仅未删除账号）。 */
    AppUserRecord selectByLoginIdentifier(@Param("identifier") String identifier);

    AppUserRecord selectById(@Param("userId") Long userId);

    int insertAppUser(AppUserRecord user);

    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    int updateLoginInfo(@Param("userId") Long userId, @Param("loginIp") String loginIp, @Param("loginDate") Date loginDate);

    String selectRealNameStatus(@Param("userId") Long userId);
}
