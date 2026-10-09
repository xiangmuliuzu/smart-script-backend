package com.smartscript.platform.content.controller.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppAdUnlockRequest;
import com.smartscript.platform.content.dto.AppEpisodeDetailDto;
import com.smartscript.platform.content.dto.AppEpisodeItem;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppPlayHistoryItem;
import com.smartscript.platform.content.dto.AppPlayProgressDto;
import com.smartscript.platform.content.dto.AppPlayProgressRequest;
import com.smartscript.platform.content.dto.AppUnlockRequest;
import com.smartscript.platform.content.dto.AppUnlockResultDto;
import com.smartscript.platform.content.dto.AppUnlockStatusDto;
import com.smartscript.platform.content.service.IAppPlayService;
import com.smartscript.platform.content.service.IAppUnlockService;

/**
 * App 剧集播放（B 模块，接口文档 2.8.2 表2-95 ~ 2.8.9 表2-102）。
 *
 * 鉴权：
 *   - 2.8.2 剧集列表 / 2.8.3 剧集详情：公开（游客可读）。白名单登记
 *     /content/works/**（既有，GET）与 /content/episodes/*（单段，不会误放 /progress）；
 *   - 2.8.4 保存进度 / 2.8.5 获取进度 / 2.8.6 播放历史：私有，走 App 凭证域 authenticated；
 *   - 2.8.7 解锁状态 / 2.8.8 付费解锁 / 2.8.9 广告解锁：私有，均为两段路径
 *     （/episodes/{id}/xxx），不命中 /content/episodes/* 单段白名单，自动走 authenticated。
 *
 * 路径说明：文档写的是 /api/works/:id/episodes、/api/episodes/:id[/progress]、/api/user/play-history，
 * B 模块既有接口统一收拢在 App 凭证域 /api/v1/content/** 下，故落此前缀。
 *
 * 归属：进度与历史一律取当前登录身份，不接收请求体 userId。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content")
public class AppPlayController
{
    /** 剧集不存在 */
    private static final int CODE_NOT_FOUND = 404;

    /** 入参非法 */
    private static final int CODE_BAD_REQUEST = 400;

    /** 免费标记（sys_episode.is_free tinyint 的字符串形态） */
    private static final String FREE_FLAG = "1";

    private final IAppPlayService playService;

    private final IAppUnlockService unlockService;

    public AppPlayController(IAppPlayService playService, IAppUnlockService unlockService)
    {
        this.playService = playService;
        this.unlockService = unlockService;
    }

    /**
     * 剧集列表（2.8.2）
     *
     * @param workId 作品ID
     * @return data = {list}（按集号升序；无剧集时为空数组）
     */
    @GetMapping("/works/{workId}/episodes")
    public AppApiResponse<Map<String, Object>> episodes(@PathVariable Long workId)
    {
        List<AppEpisodeItem> list = playService.listEpisodes(workId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return AppApiResponse.ok(data);
    }

    /**
     * 剧集详情（2.8.3）
     *
     * @param episodeId 剧集ID
     * @return 详情；不存在按 404
     */
    @GetMapping("/episodes/{episodeId}")
    public AppApiResponse<AppEpisodeDetailDto> episode(@PathVariable Long episodeId)
    {
        AppEpisodeDetailDto dto = playService.getEpisode(episodeId);
        if (dto == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "剧集不存在");
        }
        return AppApiResponse.ok(dto);
    }

    /**
     * 保存播放进度（2.8.4，同时刷新播放历史）
     *
     * @param episodeId 剧集ID
     * @param request   请求体 {progress 必填>=0, duration 可选>=0}
     * @return data = {message}；progress 缺失/为负按 400；剧集不存在按 404
     */
    @PostMapping("/episodes/{episodeId}/progress")
    public AppApiResponse<Map<String, Object>> saveProgress(
            @PathVariable Long episodeId,
            @RequestBody(required = false) AppPlayProgressRequest request)
    {
        Integer progress = request == null ? null : request.getProgress();
        Integer duration = request == null ? null : request.getDuration();
        if (progress == null || progress < 0)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "progress 不能为空且不能为负数");
        }
        if (duration != null && duration < 0)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "duration 不能为负数");
        }
        if (!playService.saveProgress(episodeId, progress, duration))
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "剧集不存在");
        }
        return AppApiResponse.ok(Map.of("message", "已保存播放进度"));
    }

    /**
     * 获取播放进度（2.8.5）
     *
     * @param episodeId 剧集ID
     * @return data = {progress, duration}；无记录时 progress=0、duration=null；剧集不存在按 404
     */
    @GetMapping("/episodes/{episodeId}/progress")
    public AppApiResponse<AppPlayProgressDto> progress(@PathVariable Long episodeId)
    {
        AppPlayProgressDto dto = playService.getProgress(episodeId);
        if (dto == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "剧集不存在");
        }
        return AppApiResponse.ok(dto);
    }

    /**
     * 播放历史（2.8.6，分页）
     *
     * @param page     页码（可选，默认 1）
     * @param pageSize 每页条数（可选，默认 20，上限 50）
     * @return data = {total, list}
     */
    @GetMapping("/play-history")
    public AppApiResponse<AppPageResult<AppPlayHistoryItem>> playHistory(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize)
    {
        int pageNum = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        return AppApiResponse.ok(playService.pageHistory(pageNum, size));
    }

    /**
     * 剧集解锁状态（2.8.7）
     *
     * 免费剧集直接返回已解锁；付费剧集以解锁记录判定。
     *
     * @param episodeId 剧集ID
     * @return data = {isUnlocked, unlockType}；剧集不存在按 404
     */
    @GetMapping("/episodes/{episodeId}/unlock-status")
    public AppApiResponse<AppUnlockStatusDto> unlockStatus(@PathVariable Long episodeId)
    {
        AppEpisodeDetailDto episode = playService.getEpisode(episodeId);
        if (episode == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "剧集不存在");
        }
        return AppApiResponse.ok(unlockService.getUnlockStatus(episode));
    }

    /**
     * 付费解锁（2.8.8，幂等）
     *
     * 不含真实支付：B 模块内无支付网关，本接口只登记解锁记录（sys_unlock_record），不扣款。
     *
     * @param episodeId 剧集ID
     * @param request   请求体 {payType 可选}
     * @return data = {unlockId, message}；剧集不存在按 404，免费剧集按 400
     */
    @PostMapping("/episodes/{episodeId}/unlock")
    public AppApiResponse<AppUnlockResultDto> unlock(
            @PathVariable Long episodeId,
            @RequestBody(required = false) AppUnlockRequest request)
    {
        AppEpisodeDetailDto episode = playService.getEpisode(episodeId);
        if (episode == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "剧集不存在");
        }
        if (FREE_FLAG.equals(episode.getIsFree()))
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "该集为免费剧集，无需解锁");
        }
        return AppApiResponse.ok(unlockService.unlockPaid(episode, request == null ? null : request.getPayType()));
    }

    /**
     * 广告解锁（2.8.9，幂等）
     *
     * @param episodeId 剧集ID
     * @param request   请求体 {adId 可选}
     * @return data = {unlockId, message}；剧集不存在按 404，免费剧集按 400
     */
    @PostMapping("/episodes/{episodeId}/ad-unlock")
    public AppApiResponse<AppUnlockResultDto> adUnlock(
            @PathVariable Long episodeId,
            @RequestBody(required = false) AppAdUnlockRequest request)
    {
        AppEpisodeDetailDto episode = playService.getEpisode(episodeId);
        if (episode == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "剧集不存在");
        }
        if (FREE_FLAG.equals(episode.getIsFree()))
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "该集为免费剧集，无需解锁");
        }
        return AppApiResponse.ok(unlockService.unlockByAd(episode, request == null ? null : request.getAdId()));
    }
}