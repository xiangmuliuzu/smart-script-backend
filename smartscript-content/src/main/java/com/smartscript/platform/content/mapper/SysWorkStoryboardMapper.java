package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.SysWorkStoryboard;

/** 作品分镜目录数据层。 */
public interface SysWorkStoryboardMapper
{
    List<SysWorkStoryboard> selectByWorkId(Long workId);

    /** 公开目录：只取审核通过且关联正常章节的分镜。 */
    List<SysWorkStoryboard> selectApprovedByWorkId(Long workId);

    /** 公开目录中审核通过的分镜数量。 */
    int countApprovedByWorkId(Long workId);

    SysWorkStoryboard selectById(Long storyboardId);

    SysWorkStoryboard selectByWorkIdAndChapterId(@Param("workId") Long workId,
            @Param("chapterId") Long chapterId);

    List<SysWorkStoryboard> selectAdminList(SysWorkStoryboard query);

    SysWorkStoryboard selectAdminById(Long storyboardId);

    int insert(SysWorkStoryboard storyboard);

    int update(SysWorkStoryboard storyboard);

    int review(SysWorkStoryboard storyboard);

    int updateAiReview(SysWorkStoryboard storyboard);

    int softDelete(@Param("storyboardId") Long storyboardId,
            @Param("updateBy") String updateBy);
}
