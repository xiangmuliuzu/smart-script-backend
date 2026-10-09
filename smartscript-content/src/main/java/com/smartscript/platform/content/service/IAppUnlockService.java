package com.smartscript.platform.content.service;

import com.smartscript.platform.content.dto.AppEpisodeDetailDto;
import com.smartscript.platform.content.dto.AppUnlockResultDto;
import com.smartscript.platform.content.dto.AppUnlockStatusDto;

/**
 * 剧集解锁 服务（App 2.8.7 解锁状态 / 2.8.8 付费解锁 / 2.8.9 广告解锁）
 *
 * 依据：云端 script_platform_dev 库 sys_unlock_record 表（附件5.1 表3-40）+ 接口文档表 2-100 ~ 2-102。
 *
 * 边界：
 * 1. 私有接口，归属一律取当前登录身份，写路径不接收 userId。
 * 2. 剧集的存在性与免费/付费判定不由本服务查库，复用调用方持有的 2.8.3 剧集详情结果，
 *    避免与剧集浏览口径漂移；本服务只负责解锁记录的读写。
 * 3. 免费剧集无需解锁，由控制层按 400 拒绝（{@link #getUnlockStatus} 中免费集直接判为已解锁）。
 * 4. 付费解锁不含真实支付：B 模块内无支付网关，本批只登记 sys_unlock_record 记录。
 *
 * @author xiangsipeng
 */
public interface IAppUnlockService
{
    /**
     * 剧集解锁状态（接口 2.8.7）
     *
     * 免费剧集直接返回已解锁；付费剧集以 sys_unlock_record(user_id, episode_id) 是否存在判定。
     *
     * @param episode 剧集详情（调用方已确认存在）
     * @return 解锁状态（isUnlocked + 剧集配置的 unlockType）
     */
    public AppUnlockStatusDto getUnlockStatus(AppEpisodeDetailDto episode);

    /**
     * 付费解锁（接口 2.8.8，幂等）
     *
     * @param episode 剧集详情（调用方已确认存在且为付费集）
     * @param payType 支付方式（可选；作为解锁方式写入 unlock_type，为空时回退剧集配置）
     * @return 解锁结果（unlockId + message）；重复解锁返回既有记录
     */
    public AppUnlockResultDto unlockPaid(AppEpisodeDetailDto episode, String payType);

    /**
     * 广告解锁（接口 2.8.9，幂等）
     *
     * @param episode 剧集详情（调用方已确认存在且为付费集）
     * @param adId    广告任务ID（可选，写入 ad_task_id）
     * @return 解锁结果（unlockId + message）；重复解锁返回既有记录
     */
    public AppUnlockResultDto unlockByAd(AppEpisodeDetailDto episode, Integer adId);
}