package com.smartscript.platform.content.controller.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppWorkRatingDto;
import com.smartscript.platform.content.dto.AppWorkRatingRequest;
import com.smartscript.platform.content.service.IAppWorkRatingService;

/**
 * 读者评分接口入参刻度测试（每星 2 分、满分 10 分）。
 *
 * 覆盖 spec 的刻度边界：2~10 放行，缺失/越界按 400 拒绝且不触达服务层。
 * 直接调控制器方法（无需 Spring 上下文），与既有控制器测试风格一致。
 */
class AppWorkRatingControllerTest
{
    private static final Long WORK_ID = 1001L;

    private final IAppWorkRatingService service = mock(IAppWorkRatingService.class);

    private final AppWorkRatingController controller = new AppWorkRatingController(service);

    private static AppWorkRatingRequest requestOf(Integer score)
    {
        AppWorkRatingRequest request = new AppWorkRatingRequest();
        request.setScore(score);
        return request;
    }

    @Test
    @DisplayName("分值缺失或越界按 400 拒绝")
    void rejectsMissingOrOutOfRangeScore()
    {
        Integer[] invalidScores = {null, 0, 1, 11, 12};
        for (Integer score : invalidScores)
        {
            AppApiResponse<AppWorkRatingDto> response = controller.rate(WORK_ID, requestOf(score));
            assertEquals(400, response.getCode(), "score=" + score + " 应被拒绝");
        }
        // 连请求体都没有时同样按 400 处理
        assertEquals(400, controller.rate(WORK_ID, null).getCode());
    }

    @Test
    @DisplayName("刻度边界放行：1 星=2 分、5 星=10 分")
    void acceptsBoundaryScores()
    {
        when(service.rate(WORK_ID, 2)).thenReturn(new AppWorkRatingDto());
        when(service.rate(WORK_ID, 10)).thenReturn(new AppWorkRatingDto());

        assertEquals(200, controller.rate(WORK_ID, requestOf(2)).getCode());
        assertEquals(200, controller.rate(WORK_ID, requestOf(10)).getCode());
    }
}
