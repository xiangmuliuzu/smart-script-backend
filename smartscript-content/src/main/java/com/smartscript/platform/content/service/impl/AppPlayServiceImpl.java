package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.content.dto.AppEpisodeDetailDto;
import com.smartscript.platform.content.dto.AppEpisodeItem;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppPlayHistoryItem;
import com.smartscript.platform.content.dto.AppPlayProgressDto;
import com.smartscript.platform.content.mapper.AppEpisodeMapper;
import com.smartscript.platform.content.mapper.AppPlayHistoryMapper;
import com.smartscript.platform.content.mapper.AppPlayProgressMapper;
import com.smartscript.platform.content.service.IAppPlayService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 剧集播放 服务实现（App 2.8.2 ~ 2.8.6）
 *
 * 依据：接口文档表 2-95 ~ 2-99 + 云端 script_platform_dev 库
 * sys_episode / sys_play_progress / sys_play_history 表。
 *
 * 反推处理点：
 * 1. 身份不做空值兜底：2.8.4 / 2.8.5 / 2.8.6 未登记 App 凭证域白名单，过滤器已保证非游客，
 *    因此 currentUserId() 不会为 null（IdentityProvider 约定：无认证信息才返回游客）。
 * 2. 进度写入「先 update，影响 0 行再 insert」：sys_play_progress 有唯一索引
 *    uk_user_episode(user_id, episode_id) 兜底，避免同一用户同一剧集出现重复行。
 * 3. 播放历史写入「先 touch，影响 0 行再 insert」：sys_play_history 无唯一索引，
 *    touch 会一次性刷新该用户该剧集的全部历史行，避免列表里同一剧集重复出现。
 * 4. 进度与历史在同一事务内写入（@Transactional），避免只落其一导致「继续播放」与历史不一致。
 *
 * @author xiangsipeng
 */
@Service
public class AppPlayServiceImpl implements IAppPlayService
{
    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数上限 */
    private static final int PAGE_SIZE_MAX = 50;

    @Autowired
    private AppEpisodeMapper episodeMapper;

    @Autowired
    private AppPlayProgressMapper progressMapper;

    @Autowired
    private AppPlayHistoryMapper historyMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    public List<AppEpisodeItem> listEpisodes(Long workId)
    {
        if (workId == null)
        {
            return List.of();
        }
        return episodeMapper.selectAppEpisodesByWorkId(workId);
    }

    @Override
    public AppEpisodeDetailDto getEpisode(Long episodeId)
    {
        if (episodeId == null)
        {
            return null;
        }
        return episodeMapper.selectAppEpisodeById(episodeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveProgress(Long episodeId, Integer progress, Integer duration)
    {
        AppEpisodeDetailDto episode = episodeMapper.selectAppEpisodeById(episodeId);
        if (episode == null)
        {
            return false;
        }
        Long userId = identityProvider.currentUserId();
        if (progressMapper.updateProgress(userId, episodeId, progress, duration) == 0)
        {
            progressMapper.insertProgress(userId, episodeId, episode.getWorkId(), progress, duration);
        }
        if (historyMapper.touchHistory(userId, episodeId) == 0)
        {
            historyMapper.insertHistory(userId, episodeId, episode.getWorkId());
        }
        return true;
    }

    @Override
    public AppPlayProgressDto getProgress(Long episodeId)
    {
        AppEpisodeDetailDto episode = episodeId == null ? null : episodeMapper.selectAppEpisodeById(episodeId);
        if (episode == null)
        {
            return null;
        }
        AppPlayProgressDto progress = progressMapper.selectProgress(identityProvider.currentUserId(), episodeId);
        // 无进度记录（首次播放）不是错误：按契约返回 progress=0、duration=null
        return progress == null ? new AppPlayProgressDto(0, null) : progress;
    }

    @Override
    public AppPageResult<AppPlayHistoryItem> pageHistory(int pageNum, int pageSize)
    {
        int safePageNum = pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, PAGE_SIZE_MAX);
        Long userId = identityProvider.currentUserId();
        PageHelper.startPage(safePageNum, safePageSize);
        List<AppPlayHistoryItem> rows = historyMapper.selectPlayHistoryByUser(userId);
        long total = new PageInfo<>(rows).getTotal();
        return AppPageResult.of(total, rows);
    }
}