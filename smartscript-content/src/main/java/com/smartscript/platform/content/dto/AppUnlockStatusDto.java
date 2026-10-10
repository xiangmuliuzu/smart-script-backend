package com.smartscript.platform.content.dto;

/**
 * 剧集解锁状态（B 模块 2.8.7 表 2-100）。
 *
 * 依据：接口文档表 2-100 + 云端 script_platform_dev 库 sys_unlock_record 表（附件5.1 表3-40）。
 *
 * 反推处理点：
 * 1. 契约 data = {is_unlocked, unlock_type}；沿用 B 模块 camelCase 口径下发 isUnlocked / unlockType
 *    与既有 isFree 写法一致（字段 isUnlocked + getIsUnlocked，序列化为 isUnlocked）。
 * 2. 免费剧集直接视为已解锁，unlockType 沿用剧集配置（可为空）。
 * 3. 付费剧集以 sys_unlock_record(user_id, episode_id) 是否存在判定。
 *
 * @author xiangsipeng
 */
public class AppUnlockStatusDto
{
    /** 是否已解锁 */
    private Boolean isUnlocked;

    /** 解锁方式（取剧集配置的 unlock_type，可空） */
    private String unlockType;

    public Boolean getIsUnlocked()
    {
        return isUnlocked;
    }

    public void setIsUnlocked(Boolean isUnlocked)
    {
        this.isUnlocked = isUnlocked;
    }

    public String getUnlockType()
    {
        return unlockType;
    }

    public void setUnlockType(String unlockType)
    {
        this.unlockType = unlockType;
    }
}