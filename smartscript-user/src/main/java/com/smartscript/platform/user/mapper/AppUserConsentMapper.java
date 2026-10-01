package com.smartscript.platform.user.mapper;

import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.AppUserConsent;

public interface AppUserConsentMapper
{
    int insertConsent(AppUserConsent consent);

    int countAcceptance(@Param("userId") Long userId, @Param("agreementType") String agreementType,
            @Param("agreementVersion") String agreementVersion);

    int countByUser(@Param("userId") Long userId);
}
