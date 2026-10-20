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
     * 用户上报任务行为进度（App 端真实完成行为后调用，如阅读/上传/登录）
     *
     * 规则：
     *  - 任务不存在 / 未启用 / 未到生效期 → 400；
     *  - 当日周期内已有已领取记录 → 幂等返回（不重复累加）；
     *  - 每日任务（DAILY）按当天周期独立累计，跨天重新计数；
     *  - 其余类型全周期累计；
     *  - 进度达到 targetCount → 置 is_completed=1，之后才允许领取。
     *
     * @param taskCode  任务编码（sys_task.task_code 唯一）
     * @param userId    用户ID
     * @param increment 本次行为贡献的进度（默认 1）
     * @return {code, msg, progress, targetCount, isCompleted}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportProgress(String taskCode, Long userId, int increment) {
        Map<String, Object> result = new HashMap<>();
        if (taskCode == null || taskCode.isEmpty() || userId == null) {
            result.put("code", 400);
            result.put("msg", "taskCode与userId必填");
            return result;
        }
        if (increment <= 0) {
            result.put("code", 400);
            result.put("msg", "increment必须大于0");
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
        Date now = new Date();
        if (task.getStartTime() != null && now.before(task.getStartTime())) {
            result.put("code", 400);
            result.put("msg", "任务尚未开始");
            return result;
        }
        if (task.getEndTime() != null && now.after(task.getEndTime())) {
            result.put("code", 400);
            result.put("msg", "任务已结束");
            return result;
        }
        String today = new SimpleDateFormat("yyyy-MM-dd").format(now);
        long taskIdVal = task.getTaskId().longValue();
        int target = task.getTargetCount() == null ? 1 : task.getTargetCount();
        boolean isDaily = "DAILY".equalsIgnoreCase(task.getTaskType());

        // 查该用户该任务的最新进度记录
        UserTask latest = userTaskMapper.selectByUserAndTask(userId, taskIdVal);
        boolean samePeriod = latest != null && latest.getPeriodDate() != null
                && today.equals(new SimpleDateFormat("yyyy-MM-dd").format(latest.getPeriodDate()));
        // 是否复用最新记录：每日任务仅当天周期内复用；非每日任务全周期累计、可跨天复用
        boolean reuse = latest != null && (isDaily ? samePeriod : true);
        // 当前周期内已领取过 → 幂等返回（不重复累加）
        if (latest != null && latest.getIsClaimed() != null && latest.getIsClaimed() == 1
                && (isDaily ? samePeriod : true)) {
            result.put("code", 200);
            result.put("msg", "该周期任务已领取，进度不再累加");
            result.put("progress", latest.getProgress() == null ? 0 : latest.getProgress());
            result.put("targetCount", target);
            result.put("isCompleted", latest.getIsCompleted());
            return result;
        }

        int base = reuse ? (latest.getProgress() == null ? 0 : latest.getProgress()) : 0;
        int newProgress = Math.min(base + increment, target);
        int isCompleted = newProgress >= target ? 1 : 0;
        Date completeTime = isCompleted == 1 ? now : null;

        if (reuse && latest.getId() != null) {
            // 同周期：累加进度
            latest.setProgress(newProgress);
            latest.setIsCompleted(isCompleted);
            latest.setCompleteTime(completeTime);
            userTaskMapper.updateUserTaskProgress(latest);
        } else {
            // 新周期：插入一条进度记录（未领取）
            UserTask ut = new UserTask();
            ut.setUserId(userId);
            ut.setTaskId(taskIdVal);
            ut.setProgress(newProgress);
            ut.setIsCompleted(isCompleted);
            ut.setIsClaimed(0);
            ut.setCompleteTime(completeTime);
            try {
                ut.setPeriodDate(new SimpleDateFormat("yyyy-MM-dd").parse(today));
            } catch (Exception ignored) {
            }
            userTaskMapper.insertUserTask(ut);
        }

        result.put("code", 200);
        result.put("msg", isCompleted == 1 ? "任务已完成" : "进度已更新");
        result.put("progress", newProgress);
        result.put("targetCount", target);
        result.put("isCompleted", isCompleted);
        return result;
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
        // 生效期校验
        Date now = new Date();
        if (task.getStartTime() != null && now.before(task.getStartTime())) {
            result.put("code", 400);
            result.put("msg", "任务尚未开始");
            return result;
        }
        if (task.getEndTime() != null && now.after(task.getEndTime())) {
            result.put("code", 400);
            result.put("msg", "任务已结束");
            return result;
        }
        String today = new SimpleDateFormat("yyyy-MM-dd").format(now);
        long taskIdVal = task.getTaskId().longValue();

        // 真实行为完成校验：未完成的任务不允许领取（DAILY 还要求当天周期已完成）
        int target = task.getTargetCount() == null ? 1 : task.getTargetCount();
        UserTask progressRec = userTaskMapper.selectByUserAndTask(userId, taskIdVal);
        boolean finished = progressRec != null && progressRec.getProgress() != null
                && progressRec.getProgress() >= target
                && progressRec.getIsCompleted() != null && progressRec.getIsCompleted() == 1;
        if (!finished) {
            result.put("code", 400);
            result.put("msg", "任务未完成，请先完成对应行为");
            return result;
        }
        if ("DAILY".equalsIgnoreCase(task.getTaskType())) {
            // 每日任务：必须当天周期已完成（防止领昨天的完成记录）
            boolean todayFinished = progressRec.getPeriodDate() != null
                    && today.equals(new SimpleDateFormat("yyyy-MM-dd").format(progressRec.getPeriodDate()));
            if (!todayFinished) {
                result.put("code", 400);
                result.put("msg", "请先完成今日任务");
                return result;
            }
        }

        Integer dailyLimit = task.getDailyLimit();
        if (dailyLimit != null && dailyLimit > 0) {
            // 显式每日上限：当天已领次数须 < dailyLimit
            if (userTaskMapper.countClaimed(userId, taskIdVal, today) >= dailyLimit) {
                result.put("code", 400);
                result.put("msg", "今日领取次数已达上限");
                return result;
            }
        } else if ("DAILY".equalsIgnoreCase(task.getTaskType())) {
            // 每日任务未设上限时默认每日 1 次
            if (userTaskMapper.countClaimed(userId, taskIdVal, today) > 0) {
                result.put("code", 400);
                result.put("msg", "今日已领取该任务奖励");
                return result;
            }
        } else {
            // 其他任务全周期限领 1 次
            if (userTaskMapper.countClaimedAll(userId, taskIdVal) > 0) {
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
        ut.setCompleteTime(now);
        ut.setClaimTime(now);
        try {
            ut.setPeriodDate(new SimpleDateFormat("yyyy-MM-dd").parse(today));
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
