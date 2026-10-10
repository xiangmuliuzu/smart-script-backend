package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.TaskConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/**
 * 任务配置Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface TaskConfigMapper {

    List<TaskConfig> selectTaskConfigList(TaskConfig taskConfig);

    TaskConfig selectTaskConfigById(Long taskId);

    /** 按任务编码查询（task_code 唯一） */
    TaskConfig selectTaskConfigByCode(@Param("taskCode") String taskCode);

    int insertTaskConfig(TaskConfig taskConfig);

    int updateTaskConfig(TaskConfig taskConfig);

    int deleteTaskConfigById(Long taskId);

    /** 用户任务领取记录（预留） */
    List<Map<String, Object>> selectUserTaskList(@Param("keyword") String keyword,
                                                 @Param("taskId") Long taskId,
                                                 @Param("periodDate") String periodDate);
}
