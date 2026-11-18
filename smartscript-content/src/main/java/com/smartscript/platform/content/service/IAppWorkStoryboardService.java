package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysWorkStoryboard;
import com.smartscript.platform.content.dto.AppStoryboardDto;

/** App/PC 创作者端作品分镜目录服务。 */
public interface IAppWorkStoryboardService
{
    List<AppStoryboardDto> list(Long userId, Long workId);

    int countPublic(Long workId);

    SysWorkStoryboard save(Long userId, Long workId, String chapterTitle,
            String content, String source);

    void delete(Long userId, Long workId, Long storyboardId);
}
