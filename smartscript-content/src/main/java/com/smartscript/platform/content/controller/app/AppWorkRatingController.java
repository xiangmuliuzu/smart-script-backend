package com.smartscript.platform.content.controller.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppWorkRatingDto;
import com.smartscript.platform.content.dto.AppWorkRatingRequest;
import com.smartscript.platform.content.service.IAppWorkRatingService;

/**
 * App 作品读者评分（B 模块书城读者评分通道）。
 *
 * 鉴权：两种方法均需 App Access Token。
 *   - POST 天然落入 AppAuthSecurityConfig 的 /api/v1/content/** → authenticated()；
 *   - GET 因 /api/v1/content/works/** 被 permitAll 覆盖，已在公开白名单之前单独声明
 *     为 authenticated()（Spring Security 首匹配优先，见 AppAuthSecurityConfig）。
 * 归属一律取当前登录身份，请求体不接受 userId，避免为他人评分。
 *
 * 路径说明：设计文档未定义评分接口，本通道按 sys_work.rating 语义补齐，路径与
 * 2.7.2 作品详情同族（/works/{workId}/rating），便于与详情/收藏/书架一致地做可见性判定。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content/works")
public class AppWorkRatingController
{
    /** 入参非法（分值缺失或越界） */
    private static final int CODE_BAD_REQUEST = 400;

    /** 作品不存在或未上架 */
    private static final int CODE_NOT_FOUND = 404;

    /** 评分下限（1 星 = 2 分） */
    private static final int MIN_SCORE = 2;

    /** 评分上限（5 星 = 10 分） */
    private static final int MAX_SCORE = 10;

    private final IAppWorkRatingService ratingService;

    public AppWorkRatingController(IAppWorkRatingService ratingService)
    {
        this.ratingService = ratingService;
    }

    /**
     * 查询评分态（当前身份视角）
     *
     * @param workId 作品ID
     * @return data = {workId, rating, ratingCount, myScore}；
     *         作品不存在/已下架按 404 拒绝（不区分「不存在」与「未上架」）
     */
    @GetMapping("/{workId}/rating")
    public AppApiResponse<AppWorkRatingDto> status(@PathVariable Long workId)
    {
        AppWorkRatingDto dto = ratingService.getRating(workId);
        if (dto == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或已下架");
        }
        return AppApiResponse.ok(dto);
    }

    /**
     * 提交评分（一人一评，重复提交即覆盖）
     *
     * @param workId  作品ID
     * @param request 请求体 {score}（2~10 的整数：每星 2 分，满分 10 分）
     * @return data = 提交后的 {workId, rating, ratingCount, myScore}
     */
    @PostMapping("/{workId}/rating")
    public AppApiResponse<AppWorkRatingDto> rate(@PathVariable Long workId,
            @RequestBody(required = false) AppWorkRatingRequest request)
    {
        Integer score = request == null ? null : request.getScore();
        if (score == null || score < MIN_SCORE || score > MAX_SCORE)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST,
                    "评分需为 " + MIN_SCORE + "~" + MAX_SCORE + " 的整数（每星 2 分，满分 10 分）");
        }
        AppWorkRatingDto dto = ratingService.rate(workId, score);
        if (dto == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或已下架");
        }
        return AppApiResponse.ok(dto);
    }
}
