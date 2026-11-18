package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.SysWorkStoryboard;

/** 作品分镜目录数据层。 */
public interface SysWorkStoryboardMapper
{
    List<SysWorkStoryboard> selectByWorkId(Long workId);

    SysWorkStoryboard selectById(Long storyboardId);

    SysWorkStoryboard selectByWorkIdAndChapterId(@Param("workId") Long workId,
            @Param("chapterId") Long chapterId);

    int insert(SysWorkStoryboard storyboard);

    int update(SysWorkStoryboard storyboard);
}
