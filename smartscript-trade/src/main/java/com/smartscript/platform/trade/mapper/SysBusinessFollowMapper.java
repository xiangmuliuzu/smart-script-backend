package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysBusinessFollow;

public interface SysBusinessFollowMapper
{
    List<SysBusinessFollow> selectFollowList(SysBusinessFollow follow);
    SysBusinessFollow selectFollowById(Long followId);
    int insertFollow(SysBusinessFollow follow);
}
