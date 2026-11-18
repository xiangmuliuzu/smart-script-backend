package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysWorkStoryboard;

/** App/PC 创作者端作品分镜目录服务。 */
public interface IAppWorkStoryboardService
{
    List<SysWorkStoryboard> list(Long userId, Long workId);

    SysWorkStoryboard save(Long userId, Long workId, String chapterTitle,
            String content, String source);
}
