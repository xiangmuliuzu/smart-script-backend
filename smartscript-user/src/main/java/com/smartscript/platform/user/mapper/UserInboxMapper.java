package com.smartscript.platform.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.UserMessage;

/** 普通用户通知与公告合并读取，数据库按来源、个人身份和范围过滤。portalRole 为门户受众（BUYER/SELLER），空则不过滤。 */
public interface UserInboxMapper
{
    List<UserMessage> selectInbox(@Param("userId") Long userId, @Param("type") String type,
            @Param("portalRole") String portalRole);
}
