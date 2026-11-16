package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.TaskConfig;
import com.smartscript.platform.review.domain.UserTask;
import com.smartscript.platform.review.mapper.TaskConfigMapper;
import com.smartscript.platform.review.mapper.UserTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 福利任务行为进度上报核心逻辑单元测试
 * 覆盖：参数校验 / 任务不存在 / 未启用 / 生效期 / 首次上报插入 / 同周期累加 /
 *       达到目标置完成 / 已领取幂等 / 跨天重置 / 非每日任务全周期累计
 */
@ExtendWith(MockitoExtension.class)
class TaskConfigProgressTest {

    @Mock
    private TaskConfigMapper taskConfigMapper;

    @Mock
    private UserTaskMapper userTaskMapper;

    @Mock
    private PointsService pointsService;

    @InjectMocks
    private TaskConfigService taskConfigService;

    private TaskConfig dailyTask;

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    private Date todayDate() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(today());
        } catch (Exception e) {
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        dailyTask = new TaskConfig();
        dailyTask.setTaskId(1L);
        dailyTask.setTaskName("每日阅读任务");
        dailyTask.setTaskType("DAILY");
        dailyTask.setTaskCode("READ_CHAPTER");
        dailyTask.setTargetCount(3);
        dailyTask.setRewardPoints(10);
        dailyTask.setStatus(1);
    }

    @Test
    void report_缺参数_返回400() {
        Map<String, Object> r1 = taskConfigService.reportProgress(null, 106L, 1);
        assertEquals(400, r1.get("code"));
        Map<String, Object> r2 = taskConfigService.reportProgress("READ_CHAPTER", null, 1);
        assertEquals(400, r2.get("code"));
    }

    @Test
    void report_increment非法_返回400() {
        Map<String, Object> r = taskConfigService.reportProgress("READ_CHAPTER", 106L, 0);
        assertEquals(400, r.get("code"));
        assertEquals("increment必须大于0", r.get("msg"));
    }

    @Test
    void report_任务不存在_返回404() {
        when(taskConfigMapper.selectTaskConfigByCode("NOPE")).thenReturn(null);
        Map<String, Object> r = taskConfigService.reportProgress("NOPE", 106L, 1);
        assertEquals(404, r.get("code"));
    }

    @Test
    void report_任务未启用_返回400() {
        dailyTask.setStatus(0);
        when(taskConfigMapper.selectTaskConfigByCode("READ_CHAPTER")).thenReturn(dailyTask);
        Map<String, Object> r = taskConfigService.reportProgress("READ_CHAPTER", 106L, 1);
        assertEquals(400, r.get("code"));
        verify(userTaskMapper, never()).insertUserTask(any());
    }

    @Test
    void report_未到生效期_返回400() {
        dailyTask.setStartTime(new Date(System.currentTimeMillis() + 100000));
        when(taskConfigMapper.selectTaskConfigByCode("READ_CHAPTER")).thenReturn(dailyTask);
        Map<String, Object> r = taskConfigService.reportProgress("READ_CHAPTER", 106L, 1);
        assertEquals(400, r.get("code"));
        assertEquals("任务尚未开始", r.get("msg"));
    }

    @Test
    void report_已过生效期_返回400() {
        dailyTask.setEndTime(new Date(System.currentTimeMillis() - 100000));
        when(taskConfigMapper.selectTaskConfigByCode("READ_CHAPTER")).thenReturn(dailyTask);
        Map<String, Object> r = taskConfigService.reportProgress("READ_CHAPTER", 106L, 1);
        assertEquals(400, r.get("code"));
        assertEquals("任务已结束", r.get("msg"));
    }

    @Test
    void report_首次上报_插入进度1未完成() {
        when(taskConfigMapper.selectTaskConfigByCode("READ_CHAPTER")).thenReturn(dailyTask);
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(null);
        when(userTaskMapper.insertUserTask(any())).thenReturn(1);

        Map<String, Object> r = taskConfigService.reportProgress("READ_CHAPTER", 106L, 1);
        assertEquals(200, r.get("code"));
        assertEquals(1, r.get("progress"));
        assertEquals(3, r.get("targetCount"));
        assertEquals(0, r.get("isCompleted"));
        verify(userTaskMapper, times(1)).insertUserTask(any());
        verify(userTaskMapper, never()).updateUserTaskProgress(any());
    }

    @Test
    void report_同周期累加_进度2未完成() {
        when(taskConfigMapper.selectTaskConfigByCode("READ_CHAPTER")).thenReturn(dailyTask);
        UserTask ut = new UserTask();
        ut.setId(5L);
        ut.setUserId(106L);
        ut.setTaskId(1L);
        ut.setProgress(1);
        ut.setIsCompleted(0);
        ut.setIsClaimed(0);
        ut.setPeriodDate(todayDate());
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(ut);
        when(userTaskMapper.updateUserTaskProgress(any())).thenReturn(1);

        Map<String, Object> r = taskConfigService.reportProgress("READ_CHAPTER", 106L, 1);
        assertEquals(200, r.get("code"));
        assertEquals(2, r.get("progress"));
        assertEquals(0, r.get("isCompleted"));
        verify(userTaskMapper, times(1)).updateUserTaskProgress(any());
        verify(userTaskMapper, never()).insertUserTask(any());
    }

    @Test
    void report_达到目标_置完成() {
        when(taskConfigMapper.selectTaskConfigByCode("READ_CHAPTER")).thenReturn(dailyTask);
        UserTask ut = new UserTask();
        ut.setId(5L);
        ut.setUserId(106L);
        ut.setTaskId(1L);
        ut.setProgress(2);
        ut.setIsCompleted(0);
        ut.setIsClaimed(0);
        ut.setPeriodDate(todayDate());
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(ut);
        when(userTaskMapper.updateUserTaskProgress(any())).thenReturn(1);

        Map<String, Object> r = taskConfigService.reportProgress("READ_CHAPTER", 106L, 1);
        assertEquals(200, r.get("code"));
        assertEquals(3, r.get("progress"));
        assertEquals(1, r.get("isCompleted"));
        assertEquals("任务已完成", r.get("msg"));
        verify(userTaskMapper, times(1)).updateUserTaskProgress(any());
    }

    @Test
    void report_已领取幂等_不重复累加() {
        when(taskConfigMapper.selectTaskConfigByCode("READ_CHAPTER")).thenReturn(dailyTask);
        UserTask ut = new UserTask();
        ut.setId(5L);
        ut.setUserId(106L);
        ut.setTaskId(1L);
        ut.setProgress(3);
        ut.setIsCompleted(1);
        ut.setIsClaimed(1);
        ut.setPeriodDate(todayDate());
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(ut);

        Map<String, Object> r = taskConfigService.reportProgress("READ_CHAPTER", 106L, 1);
        assertEquals(200, r.get("code"));
        assertEquals("该周期任务已领取，进度不再累加", r.get("msg"));
        assertEquals(3, r.get("progress"));
        verify(userTaskMapper, never()).updateUserTaskProgress(any());
        verify(userTaskMapper, never()).insertUserTask(any());
    }

    @Test
    void report_每日任务跨天_重新计数() {
        when(taskConfigMapper.selectTaskConfigByCode("READ_CHAPTER")).thenReturn(dailyTask);
        UserTask ut = new UserTask();
        ut.setId(5L);
        ut.setUserId(106L);
        ut.setTaskId(1L);
        ut.setProgress(2);
        ut.setIsCompleted(0);
        ut.setIsClaimed(0);
        try {
            ut.setPeriodDate(new SimpleDateFormat("yyyy-MM-dd").parse("2020-01-01"));
        } catch (Exception ignored) {
        }
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(ut);
        when(userTaskMapper.insertUserTask(any())).thenReturn(1);

        Map<String, Object> r = taskConfigService.reportProgress("READ_CHAPTER", 106L, 1);
        assertEquals(200, r.get("code"));
        assertEquals(1, r.get("progress")); // 跨天从 1 重新开始
        verify(userTaskMapper, times(1)).insertUserTask(any());
        verify(userTaskMapper, never()).updateUserTaskProgress(any());
    }

    @Test
    void report_非每日任务跨天_继续累计() {
        dailyTask.setTaskType("READING");
        when(taskConfigMapper.selectTaskConfigByCode("READ_CHAPTER")).thenReturn(dailyTask);
        UserTask ut = new UserTask();
        ut.setId(5L);
        ut.setUserId(106L);
        ut.setTaskId(1L);
        ut.setProgress(2);
        ut.setIsCompleted(0);
        ut.setIsClaimed(0);
        try {
            ut.setPeriodDate(new SimpleDateFormat("yyyy-MM-dd").parse("2020-01-01"));
        } catch (Exception ignored) {
        }
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(ut);
        when(userTaskMapper.updateUserTaskProgress(any())).thenReturn(1);

        Map<String, Object> r = taskConfigService.reportProgress("READ_CHAPTER", 106L, 1);
        assertEquals(200, r.get("code"));
        assertEquals(3, r.get("progress"));
        assertEquals(1, r.get("isCompleted"));
        verify(userTaskMapper, times(1)).updateUserTaskProgress(any());
    }

    @Test
    void report_进度封顶_不超过target() {
        dailyTask.setTargetCount(3);
        when(taskConfigMapper.selectTaskConfigByCode("READ_CHAPTER")).thenReturn(dailyTask);
        UserTask ut = new UserTask();
        ut.setId(5L);
        ut.setUserId(106L);
        ut.setTaskId(1L);
        ut.setProgress(3);
        ut.setIsCompleted(1);
        ut.setIsClaimed(0);
        ut.setPeriodDate(todayDate());
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(ut);
        when(userTaskMapper.updateUserTaskProgress(any())).thenReturn(1);

        Map<String, Object> r = taskConfigService.reportProgress("READ_CHAPTER", 106L, 5);
        assertEquals(200, r.get("code"));
        assertEquals(3, r.get("progress")); // 封顶 targetCount
        assertEquals(1, r.get("isCompleted"));
    }
}
