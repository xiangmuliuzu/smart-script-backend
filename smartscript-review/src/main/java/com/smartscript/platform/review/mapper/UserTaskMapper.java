package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.UserTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户任务完成记录Mapper接口（sys_user_task）
 *
 * @author smartscript
 */
@Mapper
public interface UserTaskMapper {

    /**
     * 统计某用户某任务某周期的领取记录数（>0 表示该周期已领取，唯一索引兜底）
     */
    int countClaimed(@Param("userId") Long userId,
                     @Param("taskId") Long taskId,
                     @Param("periodDate") String periodDate);

    /**
     * 统计某用户某任务全量已领取记录数（>0 表示终身任务已领取）
     */
    int countClaimedAll(@Param("userId") Long userId,
                        @Param("taskId") Long taskId);

    int insertUserTask(UserTask userTask);
}
