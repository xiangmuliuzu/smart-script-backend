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
 * 福利任务领取核心逻辑单元测试
 * 覆盖：参数校验 / 任务不存在 / 任务未启用 / 每日防重 / 终身防重 / 领取成功
 */
@ExtendWith(MockitoExtension.class)
class TaskConfigClaimTest {

    @Mock
    private TaskConfigMapper taskConfigMapper;

    @Mock
    private UserTaskMapper userTaskMapper;

    @Mock
    private PointsService pointsService;

    @InjectMocks
    private TaskConfigService taskConfigService;

    private TaskConfig dailyTask;

    /** 构造一个当天周期已完成的 UserTask（DAILY 类型 claim 的前置条件） */
    private UserTask finishedToday(String taskType) {
        UserTask ut = new UserTask();
        ut.setId(1L);
        ut.setUserId(106L);
        ut.setTaskId(1L);
        ut.setProgress(1);
        ut.setIsCompleted(1);
        ut.setIsClaimed(0);
        try {
            ut.setPeriodDate(new SimpleDateFormat("yyyy-MM-dd").parse(
                    new SimpleDateFormat("yyyy-MM-dd").format(new Date())));
        } catch (Exception ignored) {
        }
        return ut;
    }

    @BeforeEach
    void setUp() {
        dailyTask = new TaskConfig();
        dailyTask.setTaskId(1L);
        dailyTask.setTaskName("每日登录任务");
        dailyTask.setTaskType("DAILY");
        dailyTask.setTaskCode("DAILY_LOGIN");
        dailyTask.setTargetCount(1);
        dailyTask.setRewardPoints(5);
        dailyTask.setStatus(1);
    }

    @Test
    void claim_缺参数_返回400() {
        Map<String, Object> r1 = taskConfigService.claimTask(null, 106L);
        assertEquals(400, r1.get("code"));
        Map<String, Object> r2 = taskConfigService.claimTask("DAILY_LOGIN", null);
        assertEquals(400, r2.get("code"));
    }

    @Test
    void claim_任务不存在_返回404() {
        when(taskConfigMapper.selectTaskConfigByCode("NOPE")).thenReturn(null);
        Map<String, Object> r = taskConfigService.claimTask("NOPE", 106L);
        assertEquals(404, r.get("code"));
    }

    @Test
    void claim_任务未启用_返回400() {
        dailyTask.setStatus(0);
        when(taskConfigMapper.selectTaskConfigByCode("DAILY_LOGIN")).thenReturn(dailyTask);
        Map<String, Object> r = taskConfigService.claimTask("DAILY_LOGIN", 106L);
        assertEquals(400, r.get("code"));
        verify(userTaskMapper, never()).countClaimed(anyLong(), anyLong(), anyString());
    }

    @Test
    void claim_任务未完成_返回400不发积分() {
        when(taskConfigMapper.selectTaskConfigByCode("DAILY_LOGIN")).thenReturn(dailyTask);
        // 无进度记录 = 未完成
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(null);
        Map<String, Object> r = taskConfigService.claimTask("DAILY_LOGIN", 106L);
        assertEquals(400, r.get("code"));
        assertEquals("任务未完成，请先完成对应行为", r.get("msg"));
        verify(pointsService, never()).grantPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    void claim_进度不足_返回400不发积分() {
        when(taskConfigMapper.selectTaskConfigByCode("DAILY_LOGIN")).thenReturn(dailyTask);
        UserTask ut = finishedToday("DAILY");
        ut.setProgress(0);
        ut.setIsCompleted(0);
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(ut);
        Map<String, Object> r = taskConfigService.claimTask("DAILY_LOGIN", 106L);
        assertEquals(400, r.get("code"));
        assertEquals("任务未完成，请先完成对应行为", r.get("msg"));
        verify(pointsService, never()).grantPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    void claim_每日任务非当天周期完成_返回400() {
        when(taskConfigMapper.selectTaskConfigByCode("DAILY_LOGIN")).thenReturn(dailyTask);
        UserTask ut = finishedToday("DAILY");
        try {
            ut.setPeriodDate(new SimpleDateFormat("yyyy-MM-dd").parse("2020-01-01"));
        } catch (Exception ignored) {
        }
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(ut);
        Map<String, Object> r = taskConfigService.claimTask("DAILY_LOGIN", 106L);
        assertEquals(400, r.get("code"));
        assertEquals("请先完成今日任务", r.get("msg"));
        verify(pointsService, never()).grantPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    void claim_每日任务今日已领_返回400且不发积分() {
        when(taskConfigMapper.selectTaskConfigByCode("DAILY_LOGIN")).thenReturn(dailyTask);
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(finishedToday("DAILY"));
        when(userTaskMapper.countClaimed(eq(106L), eq(1L), anyString())).thenReturn(1);
        Map<String, Object> r = taskConfigService.claimTask("DAILY_LOGIN", 106L);
        assertEquals(400, r.get("code"));
        assertEquals("今日已领取该任务奖励", r.get("msg"));
        verify(pointsService, never()).grantPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    void claim_终身任务已领过_返回400() {
        dailyTask.setTaskType("新手任务");
        when(taskConfigMapper.selectTaskConfigByCode("DAILY_LOGIN")).thenReturn(dailyTask);
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(finishedToday("其他"));
        when(userTaskMapper.countClaimedAll(106L, 1L)).thenReturn(1);
        Map<String, Object> r = taskConfigService.claimTask("DAILY_LOGIN", 106L);
        assertEquals(400, r.get("code"));
        assertEquals("该任务奖励已领取", r.get("msg"));
        verify(pointsService, never()).grantPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    void claim_首次领取_发积分写记录返回余额() {
        when(taskConfigMapper.selectTaskConfigByCode("DAILY_LOGIN")).thenReturn(dailyTask);
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(finishedToday("DAILY"));
        when(userTaskMapper.countClaimed(eq(106L), eq(1L), anyString())).thenReturn(0);
        when(pointsService.grantPoints(106L, 5, "TASK", 1L, "每日登录任务")).thenReturn(15);
        when(userTaskMapper.insertUserTask(any())).thenReturn(1);

        Map<String, Object> r = taskConfigService.claimTask("DAILY_LOGIN", 106L);
        assertEquals(200, r.get("code"));
        assertEquals(15, r.get("balance"));
        assertEquals(5, r.get("rewardPoints"));
        verify(pointsService, times(1)).grantPoints(106L, 5, "TASK", 1L, "每日登录任务");
        verify(userTaskMapper, times(1)).insertUserTask(any());
    }

    @Test
    void claim_任务未到生效开始时间_返回400() {
        dailyTask.setStartTime(new Date(System.currentTimeMillis() + 100000));
        when(taskConfigMapper.selectTaskConfigByCode("DAILY_LOGIN")).thenReturn(dailyTask);
        Map<String, Object> r = taskConfigService.claimTask("DAILY_LOGIN", 106L);
        assertEquals(400, r.get("code"));
        assertEquals("任务尚未开始", r.get("msg"));
        verify(pointsService, never()).grantPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    void claim_任务已过生效结束时间_返回400() {
        dailyTask.setEndTime(new Date(System.currentTimeMillis() - 100000));
        when(taskConfigMapper.selectTaskConfigByCode("DAILY_LOGIN")).thenReturn(dailyTask);
        Map<String, Object> r = taskConfigService.claimTask("DAILY_LOGIN", 106L);
        assertEquals(400, r.get("code"));
        assertEquals("任务已结束", r.get("msg"));
        verify(pointsService, never()).grantPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    void claim_每日上限已达_返回400() {
        dailyTask.setDailyLimit(2);
        when(taskConfigMapper.selectTaskConfigByCode("DAILY_LOGIN")).thenReturn(dailyTask);
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(finishedToday("DAILY"));
        when(userTaskMapper.countClaimed(eq(106L), eq(1L), anyString())).thenReturn(2);
        Map<String, Object> r = taskConfigService.claimTask("DAILY_LOGIN", 106L);
        assertEquals(400, r.get("code"));
        assertEquals("今日领取次数已达上限", r.get("msg"));
        verify(pointsService, never()).grantPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    void claim_每日上限未达_可继续领取() {
        dailyTask.setDailyLimit(2);
        when(taskConfigMapper.selectTaskConfigByCode("DAILY_LOGIN")).thenReturn(dailyTask);
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(finishedToday("DAILY"));
        when(userTaskMapper.countClaimed(eq(106L), eq(1L), anyString())).thenReturn(1);
        when(pointsService.grantPoints(106L, 5, "TASK", 1L, "每日登录任务")).thenReturn(20);
        when(userTaskMapper.insertUserTask(any())).thenReturn(1);
        Map<String, Object> r = taskConfigService.claimTask("DAILY_LOGIN", 106L);
        assertEquals(200, r.get("code"));
        assertEquals(20, r.get("balance"));
    }

    @Test
    void claim_非每日任务完成_可领取() {
        dailyTask.setTaskType("READING");
        dailyTask.setTaskCode("READ_CHAPTER");
        when(taskConfigMapper.selectTaskConfigByCode("READ_CHAPTER")).thenReturn(dailyTask);
        // 终身任务：周期日期非当天也可领取，只要已完成
        UserTask ut = finishedToday("READING");
        try {
            ut.setPeriodDate(new SimpleDateFormat("yyyy-MM-dd").parse("2020-01-01"));
        } catch (Exception ignored) {
        }
        when(userTaskMapper.selectByUserAndTask(106L, 1L)).thenReturn(ut);
        when(userTaskMapper.countClaimedAll(106L, 1L)).thenReturn(0);
        when(pointsService.grantPoints(106L, 5, "TASK", 1L, "每日登录任务")).thenReturn(30);
        when(userTaskMapper.insertUserTask(any())).thenReturn(1);
        Map<String, Object> r = taskConfigService.claimTask("READ_CHAPTER", 106L);
        assertEquals(200, r.get("code"));
        assertEquals(30, r.get("balance"));
    }
}
