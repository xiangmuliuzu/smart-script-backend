package com.smartscript.platform.content.dto;

/**
 * 付费解锁请求体（B 模块 2.8.8 表 2-101）。
 *
 * 依据：接口文档表 2-101。契约入参 {id（路径）, pay_type}。
 *
 * 反推处理点：
 * 1. pay_type 为可选参数；sys_unlock_record 无独立支付渠道列，故作为「解锁方式」写入 unlock_type，
 *    为空时回退剧集配置的 unlock_type（再回退 'coin'）。
 *
 * @author xiangsipeng
 */
public class AppUnlockRequest
{
    /** 支付方式（可选；写入 unlock_type，为空时回退剧集配置） */
    private String payType;

    public String getPayType()
    {
        return payType;
    }

    public void setPayType(String payType)
    {
        this.payType = payType;
    }
}