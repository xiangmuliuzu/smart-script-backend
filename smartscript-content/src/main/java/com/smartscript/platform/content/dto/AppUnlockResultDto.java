package com.smartscript.platform.content.dto;

/**
 * 剧集解锁结果（B 模块 2.8.8 表 2-101 / 2.8.9 表 2-102）。
 *
 * 依据：接口文档表 2-101 / 2-102 + 云端 script_platform_dev 库 sys_unlock_record 表（附件5.1 表3-40）。
 *
 * 反推处理点：
 * 1. 契约 data = {unlock_id, message}，为 camelCase 口径下发 unlockId / message。
 * 2. unlockId 取自 sys_unlock_record.unlock_id；重复解锁（幂等）返回既有记录ID。
 *
 * @author xiangsipeng
 */
public class AppUnlockResultDto
{
    /** 解锁记录ID（sys_unlock_record.unlock_id） */
    private Long unlockId;

    /** 操作结果文案 */
    private String message;

    public AppUnlockResultDto()
    {
    }

    public AppUnlockResultDto(Long unlockId, String message)
    {
        this.unlockId = unlockId;
        this.message = message;
    }

    public Long getUnlockId()
    {
        return unlockId;
    }

    public void setUnlockId(Long unlockId)
    {
        this.unlockId = unlockId;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }
}