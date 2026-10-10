package com.smartscript.platform.content.dto;

/**
 * 广告解锁请求体（B 模块 2.8.9 表 2-102）。
 *
 * 依据：接口文档表 2-102。契约入参 {id（路径）, ad_id}。
 *
 * 反推处理点：
 * 1. adId 对应 sys_unlock_record.ad_task_id（即为该列语义），可选。
 *
 * @author xiangsipeng
 */
public class AppAdUnlockRequest
{
    /** 广告任务ID（对应 sys_unlock_record.ad_task_id，可选） */
    private Integer adId;

    public Integer getAdId()
    {
        return adId;
    }

    public void setAdId(Integer adId)
    {
        this.adId = adId;
    }
}