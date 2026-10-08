package com.smartscript.platform.trade.service;

import java.util.Calendar;
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
        // 修复 BIZ_FOLLOW_001：sys_business_follow 的 partner_id/follow_type/content/status/follow_time 均 NOT NULL，
        // 前端未填时在此做必填校验，把底层 SQL 异常（Column 'xxx' cannot be null）转为友好中文提示。
        if (follow == null || follow.getPartnerId() == null)
        {
            throw new RuntimeException("合作方不能为空");
        }
        if (follow.getFollowType() == null || follow.getFollowType().isEmpty())
        {
            throw new RuntimeException("跟进方式不能为空");
        }
        if (follow.getContent() == null || follow.getContent().trim().isEmpty())
        {
            throw new RuntimeException("跟进内容不能为空");
        }
        if (follow.getStatus() == null || follow.getStatus().isEmpty())
        {
            follow.setStatus("ongoing");
        }
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
        // 修复 BIZ_FOLLOW_002：下次跟进日期不得早于跟进日期（按天比较，同日允许）
        if (follow.getNextFollowDate() != null
                && startOfDay(follow.getNextFollowDate()).before(startOfDay(follow.getFollowTime())))
        {
            throw new RuntimeException("下次跟进时间不能早于跟进时间");
        }
        return followMapper.insertFollow(follow);
    }

    private static Date startOfDay(Date d)
    {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
