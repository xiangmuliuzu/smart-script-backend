package com.smartscript.platform.user.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.AppRefreshSession;

public interface AppRefreshSessionMapper
{
    int insertSession(AppRefreshSession session);

    AppRefreshSession selectByTokenHash(@Param("tokenHash") String tokenHash);

    AppRefreshSession selectByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    AppRefreshSession selectById(@Param("id") Long id);

    int revokeById(@Param("id") Long id, @Param("reason") String reason, @Param("now") Date now);

    int markReplaced(@Param("id") Long id, @Param("replacedById") Long replacedById);

    int revokeFamily(@Param("familyId") String familyId, @Param("reason") String reason, @Param("now") Date now);

    int revokeByUserId(@Param("userId") Long userId, @Param("reason") String reason, @Param("now") Date now);

    int revokeOtherSessions(@Param("userId") Long userId, @Param("keepId") Long keepId, @Param("reason") String reason, @Param("now") Date now);

    List<AppRefreshSession> selectActiveByUserId(@Param("userId") Long userId, @Param("now") Date now);
}
