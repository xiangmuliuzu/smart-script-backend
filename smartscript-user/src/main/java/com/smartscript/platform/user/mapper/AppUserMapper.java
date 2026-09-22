package com.smartscript.platform.user.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.AppUserRecord;

/**
 * App auth access to existing sys_user table only. No parallel user table.
 */
public interface AppUserMapper
{
    AppUserRecord selectByPhone(@Param("phone") String phone);

    AppUserRecord selectById(@Param("userId") Long userId);

    int insertAppUser(AppUserRecord user);

    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    int updateLoginInfo(@Param("userId") Long userId, @Param("loginIp") String loginIp, @Param("loginDate") Date loginDate);

    String selectRealNameStatus(@Param("userId") Long userId);

    /**
     * App 用户的角色编码集合（A5 起供 CurrentUserDto.roles 使用）。
     *
     * 只取未删除角色，并在 SQL 层排除超级管理员角色：App 与 PC 共用
     * sys_user_role，排除动作必须落在数据访问层，不能依赖调用方自觉。
     */
    List<String> selectRoleKeys(@Param("userId") Long userId);
}
