package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.TaskConfig;
import com.smartscript.platform.review.domain.UserTask;
import com.smartscript.platform.review.mapper.TaskConfigMapper;
import com.smartscript.platform.review.mapper.UserTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
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

    @Autowired
    private UserTaskMapper userTaskMapper;

    @Autowired
    private PointsService pointsService;

    public List<TaskConfig> selectTaskConfigList(TaskConfig taskConfig) {
        return taskConfigMapper.selectTaskConfigList(taskConfig);
    }

    public TaskConfig selectTaskConfigById(Long taskId) {
        return taskConfigMapper.selectTaskConfigById(taskId);
    }

    public TaskConfig selectTaskConfigByCode(String taskCode) {
        return taskConfigMapper.selectTaskConfigByCode(taskCode);
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

    /**
     * 用户领取任务奖励
     *
     * @param taskCode 任务编码（sys_task.task_code 唯一）
     * @param userId   用户ID
     * @return {code, msg, balance(领取后余额)}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> claimTask(String taskCode, Long userId) {
        Map<String, Object> result = new HashMap<>();
        if (taskCode == null || taskCode.isEmpty() || userId == null) {
            result.put("code", 400);
            result.put("msg", "taskCode与userId必填");
            return result;
        }
        TaskConfig task = taskConfigMapper.selectTaskConfigByCode(taskCode);
        if (task == null) {
            result.put("code", 404);
            result.put("msg", "任务不存在: " + taskCode);
            return result;
        }
        if (task.getStatus() == null || task.getStatus() != 1) {
            result.put("code", 400);
            result.put("msg", "任务未启用");
            return result;
        }
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        // 每日防重：DAILY 类任务按 周期(当天) 查重（唯一索引 uk_user_task_period 兜底）；其余任务按全量查重
        boolean dailyTask = "DAILY".equalsIgnoreCase(task.getTaskType());
        if (dailyTask) {
            if (userTaskMapper.countClaimed(userId, task.getTaskId().longValue(), today) > 0) {
                result.put("code", 400);
                result.put("msg", "今日已领取该任务奖励");
                return result;
            }
        } else {
            if (userTaskMapper.countClaimedAll(userId, task.getTaskId().longValue()) > 0) {
                result.put("code", 400);
                result.put("msg", "该任务奖励已领取");
                return result;
            }
        }
        // 发积分（账户不存在自动创建）
        int balance = pointsService.grantPoints(userId, task.getRewardPoints(), "TASK",
                task.getTaskId().longValue(), task.getTaskName());
        // 写领取记录
        UserTask ut = new UserTask();
        ut.setUserId(userId);
        ut.setTaskId(task.getTaskId().longValue());
        ut.setProgress(task.getTargetCount() == null ? 1 : task.getTargetCount());
        ut.setIsCompleted(1);
        ut.setIsClaimed(1);
        Date now = new Date();
        ut.setCompleteTime(now);
        ut.setClaimTime(now);
        try {
            ut.setPeriodDate(new SimpleDateFormat("yyyy-MM-dd").parse(dailyTask ? today : today));
        } catch (Exception ignored) {
        }
        userTaskMapper.insertUserTask(ut);

        result.put("code", 200);
        result.put("msg", "领取成功");
        result.put("balance", balance);
        result.put("rewardPoints", task.getRewardPoints());
        result.put("taskName", task.getTaskName());
        return result;
    }
}
