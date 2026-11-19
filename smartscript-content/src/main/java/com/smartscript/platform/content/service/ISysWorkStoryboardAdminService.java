package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysWorkStoryboard;

/** 管理端分镜目录管理与审核服务。 */
public interface ISysWorkStoryboardAdminService
{
    List<SysWorkStoryboard> selectList(SysWorkStoryboard query);

    SysWorkStoryboard selectById(Long storyboardId);

    int review(Long storyboardId, String status, String rejectReason,
            Long reviewerId, String updateBy);

    int delete(Long storyboardId, String updateBy);
}
