package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.TaskConfig;
import com.smartscript.platform.review.service.TaskConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * 福利任务配置Controller
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/welfare/task")
public class TaskConfigController {

    @Autowired
    private TaskConfigService taskConfigService;

    /**
     * 查询任务配置列表
     */
    @GetMapping("/list")
    public Map<String, Object> list(TaskConfig taskConfig) {
        Map<String, Object> result = new HashMap<>();
        List<TaskConfig> list = taskConfigService.selectTaskConfigList(taskConfig);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 新增任务配置
     */
    @PostMapping
    public Map<String, Object> add(@RequestBody TaskConfig taskConfig) {
        Map<String, Object> result = new HashMap<>();
        if (taskConfig.getSort() == null) {
            taskConfig.setSort(0);
        }
        if (taskConfig.getStatus() == null) {
            taskConfig.setStatus(1);
        }
        if (taskConfig.getTargetCount() == null) {
            taskConfig.setTargetCount(1);
        }
        if (taskConfig.getRewardPoints() == null) {
            taskConfig.setRewardPoints(0);
        }
        if (taskConfig.getIcon() == null) {
            taskConfig.setIcon("");
        }
        taskConfigService.insertTaskConfig(taskConfig);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 修改任务配置
     */
    @PutMapping
    public Map<String, Object> edit(@RequestBody TaskConfig taskConfig) {
        Map<String, Object> result = new HashMap<>();
        taskConfigService.updateTaskConfig(taskConfig);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 删除任务配置
     */
    @DeleteMapping("/{taskId}")
    public Map<String, Object> remove(@PathVariable("taskId") Long taskId) {
        Map<String, Object> result = new HashMap<>();
        taskConfigService.deleteTaskConfigById(taskId);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 用户任务领取记录（预留：sys_user_task 联 sys_user/sys_task）
     */
    @GetMapping("/user-task/list")
    public Map<String, Object> userTaskList(@RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "taskId", required = false) Long taskId,
                                            @RequestParam(value = "periodDate", required = false) String periodDate) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> list = taskConfigService.selectUserTaskList(keyword, taskId, periodDate);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }
}
