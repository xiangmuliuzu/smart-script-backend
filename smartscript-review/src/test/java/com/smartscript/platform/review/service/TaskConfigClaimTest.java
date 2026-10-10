package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.TaskConfig;
import com.smartscript.platform.review.mapper.TaskConfigMapper;
import com.smartscript.platform.review.mapper.UserTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void claim_每日任务今日已领_返回400且不发积分() {
        when(taskConfigMapper.selectTaskConfigByCode("DAILY_LOGIN")).thenReturn(dailyTask);
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
        when(userTaskMapper.countClaimedAll(106L, 1L)).thenReturn(1);
        Map<String, Object> r = taskConfigService.claimTask("DAILY_LOGIN", 106L);
        assertEquals(400, r.get("code"));
        assertEquals("该任务奖励已领取", r.get("msg"));
        verify(pointsService, never()).grantPoints(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    void claim_首次领取_发积分写记录返回余额() {
        when(taskConfigMapper.selectTaskConfigByCode("DAILY_LOGIN")).thenReturn(dailyTask);
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
}
