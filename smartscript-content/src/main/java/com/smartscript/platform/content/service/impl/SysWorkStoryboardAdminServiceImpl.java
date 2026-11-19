package com.smartscript.platform.content.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.content.domain.SysWorkStoryboard;
import com.smartscript.platform.content.mapper.SysWorkStoryboardMapper;
import com.smartscript.platform.content.service.ISysWorkStoryboardAdminService;

/** 管理端分镜目录管理与审核服务实现。 */
@Service
public class SysWorkStoryboardAdminServiceImpl implements ISysWorkStoryboardAdminService
{
    private final SysWorkStoryboardMapper storyboardMapper;

    public SysWorkStoryboardAdminServiceImpl(SysWorkStoryboardMapper storyboardMapper)
    {
        this.storyboardMapper = storyboardMapper;
    }

    @Override
    public List<SysWorkStoryboard> selectList(SysWorkStoryboard query)
    {
        return storyboardMapper.selectAdminList(query);
    }

    @Override
    public SysWorkStoryboard selectById(Long storyboardId)
    {
        return storyboardMapper.selectAdminById(storyboardId);
    }

    @Override
    @Transactional
    public int review(Long storyboardId, String status, String rejectReason,
            Long reviewerId, String updateBy)
    {
        if (!"approved".equals(status) && !"rejected".equals(status))
        {
            throw new IllegalArgumentException("审核结果只能选择通过或驳回");
        }
        SysWorkStoryboard current = storyboardMapper.selectAdminById(storyboardId);
        if (current == null)
        {
            throw new IllegalArgumentException("分镜不存在或已删除");
        }
        if (!"pending".equals(current.getStatus()))
        {
            throw new IllegalStateException("当前分镜不是待审核状态，不能重复审核");
        }
        if ("rejected".equals(status) && (rejectReason == null || rejectReason.trim().isEmpty()))
        {
            throw new IllegalArgumentException("驳回分镜时请填写驳回原因");
        }
        SysWorkStoryboard update = new SysWorkStoryboard();
        update.setStoryboardId(storyboardId);
        update.setStatus(status);
        update.setRejectReason("rejected".equals(status) ? rejectReason.trim() : null);
        update.setReviewerId(reviewerId);
        update.setReviewTime(new Date());
        update.setUpdateBy(updateBy);
        update.setUpdateTime(new Date());
        return storyboardMapper.review(update);
    }

    @Override
    @Transactional
    public int delete(Long storyboardId, String updateBy)
    {
        return storyboardMapper.softDelete(storyboardId, updateBy);
    }
}
