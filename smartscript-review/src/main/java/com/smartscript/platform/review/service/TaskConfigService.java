package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.TaskConfig;
import com.smartscript.platform.review.mapper.TaskConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/**
 * 任务配置Service业务层处理
 *
 * @author smartscript
 */
@Service
public class TaskConfigService {

    @Autowired
    private TaskConfigMapper taskConfigMapper;

    public List<TaskConfig> selectTaskConfigList(TaskConfig taskConfig) {
        return taskConfigMapper.selectTaskConfigList(taskConfig);
    }

    public TaskConfig selectTaskConfigById(Long taskId) {
        return taskConfigMapper.selectTaskConfigById(taskId);
    }

    public int insertTaskConfig(TaskConfig taskConfig) {
        return taskConfigMapper.insertTaskConfig(taskConfig);
    }

    public int updateTaskConfig(TaskConfig taskConfig) {
        return taskConfigMapper.updateTaskConfig(taskConfig);
    }

    public int deleteTaskConfigById(Long taskId) {
        return taskConfigMapper.deleteTaskConfigById(taskId);
    }

    public List<Map<String, Object>> selectUserTaskList(String keyword, Long taskId, String periodDate) {
        return taskConfigMapper.selectUserTaskList(keyword, taskId, periodDate);
    }
}
