package com.smartscript.platform.trade.service;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.SecurityUtils;
import com.smartscript.platform.trade.domain.SysBusinessFollow;
import com.smartscript.platform.trade.mapper.SysBusinessFollowMapper;

/**
 * C module: business follow-up record service.
 */
@Service
public class TradeFollowUpService
{
    @Autowired
    private SysBusinessFollowMapper followMapper;

    public List<SysBusinessFollow> selectFollowList(SysBusinessFollow follow)
    {
        return followMapper.selectFollowList(follow);
    }

    public SysBusinessFollow selectFollowById(Long followId)
    {
        return followMapper.selectFollowById(followId);
    }

    public int insertFollow(SysBusinessFollow follow)
    {
        follow.setCreateBy(SecurityUtils.getUsername());
        follow.setCreateTime(new Date());
        // Default follower to current user
        if (follow.getFollowerId() == null)
        {
            follow.setFollowerId(SecurityUtils.getUserId());
        }
        if (follow.getFollowTime() == null)
        {
            follow.setFollowTime(new Date());
        }
        return followMapper.insertFollow(follow);
    }
}
