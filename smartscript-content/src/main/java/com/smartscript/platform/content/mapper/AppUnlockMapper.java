package com.smartscript.platform.content.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

/**
 * 剧集解锁 数据层（App 2.8.7 解锁状态 / 2.8.8 付费解锁 / 2.8.9 广告解锁）
 *
 * 依据：云端 script_platform_dev 库 sys_unlock_record 表（附件5.1 表3-40）+ 接口文档表 2-100 ~ 2-102。
 *
 * 反推处理点：
 * 1. 所有语句强制带 user_id 条件，归属由服务层传入的当前登录身份决定。
 * 2. 表上有唯一索引 uk_user_episode(user_id, episode_id)（实测 non_unique=0），
 *    幂等仍由 insertUnlockIfAbsent 的 WHERE NOT EXISTS 兜底（与 AppSubscriptionMapper 同一写法）。
 * 3. 剧集的存在性与免费/付费判定不在本层，由调用方复用 2.8.3 剧集详情口径，避免重复拼条件。
 *
 * @author xiangsipeng
 */
public interface AppUnlockMapper
{
    /**
     * 幂等写入解锁记录：已存在 (user_id, episode_id) 时不插入
     *
     * @param userId     解锁人ID（当前登录身份）
     * @param episodeId  剧集ID
     * @param workId     所属作品ID
     * @param unlockType 解锁方式
     * @param amount     解锁金额（广告解锁为 null）
     * @param adTaskId   广告任务ID（付费解锁为 null）
     * @return 影响行数（1=新增成功，0=此前已解锁）
     */
    public int insertUnlockIfAbsent(@Param("userId") Long userId,
                                    @Param("episodeId") Long episodeId,
                                    @Param("workId") Long workId,
                                    @Param("unlockType") String unlockType,
                                    @Param("amount") BigDecimal amount,
                                    @Param("adTaskId") Integer adTaskId);

    /**
     * 查询解锁记录ID
     *
     * @param userId    解锁人ID
     * @param episodeId 剧集ID
     * @return 解锁记录ID；未解锁为 null
     */
    public Long selectUnlockId(@Param("userId") Long userId, @Param("episodeId") Long episodeId);
}