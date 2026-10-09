package com.smartscript.platform.content.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.dto.AppEpisodeDetailDto;
import com.smartscript.platform.content.dto.AppUnlockResultDto;
import com.smartscript.platform.content.dto.AppUnlockStatusDto;
import com.smartscript.platform.content.mapper.AppUnlockMapper;
import com.smartscript.platform.content.service.IAppUnlockService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 剧集解锁 服务实现（App 2.8.7 / 2.8.8 / 2.8.9）
 *
 * 依据：云端 script_platform_dev 库 sys_unlock_record 表（附件5.1 表3-40）+ 接口文档表 2-100 ~ 2-102。
 *
 * 反推处理点：
 * 1. 身份不做空值兜底：本组接口未登记 App 凭证域白名单（两段路径不命中 /content/episodes/* 单段规则），
 *    过滤器已保证非游客，currentUserId() 不会为 null。
 * 2. 幂等：先查记录，已存在直接回既有 unlockId；并由 insertUnlockIfAbsent 的 WHERE NOT EXISTS
 *    叠加唯一索引 uk_user_episode(user_id, episode_id) 兜底。
 * 3. unlock_type 写入顺序：payType（2.8.8 入参）→ 剧集配置 unlock_type → 'coin'；
 *    广告解锁固定写 'ad'。sys_unlock_record 无支付渠道列，故 pay_type 作为解锁方式落 unlock_type。
 * 4. amount 只在付费解锁写剧集价格；ad_task_id 只在广告解锁写入；order_id / expire_at 本批无契约来源，不写。
 *
 * @author xiangsipeng
 */
@Service
public class AppUnlockServiceImpl implements IAppUnlockService
{
    /** 免费标记（sys_episode.is_free tinyint 的字符串形态） */
    private static final String FREE_FLAG = "1";

    /** 广告解锁方式 */
    private static final String UNLOCK_TYPE_AD = "ad";

    /** 付费解锁方式的最终回退值 */
    private static final String UNLOCK_TYPE_FALLBACK = "coin";

    @Autowired
    private AppUnlockMapper unlockMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    public AppUnlockStatusDto getUnlockStatus(AppEpisodeDetailDto episode)
    {
        AppUnlockStatusDto dto = new AppUnlockStatusDto();
        dto.setUnlockType(episode.getUnlockType());
        if (FREE_FLAG.equals(episode.getIsFree()))
        {
            dto.setIsUnlocked(Boolean.TRUE);
            return dto;
        }
        dto.setIsUnlocked(findUnlockId(episode.getEpisodeId()) != null);
        return dto;
    }

    @Override
    public AppUnlockResultDto unlockPaid(AppEpisodeDetailDto episode, String payType)
    {
        Long existing = findUnlockId(episode.getEpisodeId());
        if (existing != null)
        {
            return new AppUnlockResultDto(existing, "该集已解锁");
        }
        unlockMapper.insertUnlockIfAbsent(identityProvider.currentUserId(), episode.getEpisodeId(),
                episode.getWorkId(), resolveUnlockType(episode, payType), episode.getPrice(), null);
        return new AppUnlockResultDto(findUnlockId(episode.getEpisodeId()), "解锁成功");
    }

    @Override
    public AppUnlockResultDto unlockByAd(AppEpisodeDetailDto episode, Integer adId)
    {
        Long existing = findUnlockId(episode.getEpisodeId());
        if (existing != null)
        {
            return new AppUnlockResultDto(existing, "该集已解锁");
        }
        unlockMapper.insertUnlockIfAbsent(identityProvider.currentUserId(), episode.getEpisodeId(),
                episode.getWorkId(), UNLOCK_TYPE_AD, null, adId);
        return new AppUnlockResultDto(findUnlockId(episode.getEpisodeId()), "解锁成功");
    }

    /** 查询当前登录身份在该剧集上的解锁记录ID */
    private Long findUnlockId(Long episodeId)
    {
        return unlockMapper.selectUnlockId(identityProvider.currentUserId(), episodeId);
    }

    /** 付费解锁方式：优先入参 payType，其次剧集配置，最后回退 coin */
    private String resolveUnlockType(AppEpisodeDetailDto episode, String payType)
    {
        if (payType != null && !payType.isBlank())
        {
            return payType;
        }
        String configured = episode.getUnlockType();
        return (configured != null && !configured.isBlank()) ? configured : UNLOCK_TYPE_FALLBACK;
    }
}