package com.smartscript.platform.user.mapper;

import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.AppSmsCode;

public interface AppSmsCodeMapper
{
    int insertSmsCode(AppSmsCode record);

    AppSmsCode selectLatest(@Param("phone") String phone, @Param("scene") String scene);

    int consumeCode(@Param("id") Long id, @Param("now") java.util.Date now);

    int incrementFailed(@Param("id") Long id);

    int countPhoneToday(@Param("phone") String phone, @Param("scene") String scene, @Param("start") java.util.Date start);

    int countIpLastHour(@Param("ip") String ip, @Param("start") java.util.Date start);
}
