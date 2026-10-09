package com.smartscript.platform.content.service;

import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppSubscriptionItem;

/**
 * 追更订阅 服务（App 2.8.13 追更订阅 / 2.8.14 我的追更列表）
 *
 * 依据：云端 script_platform_dev 库 sys_subscribe + sys_work 表 + 接口文档表 2-106 / 2-107。
 *
 * 边界：私有接口，归属一律取当前登录身份，写路径不接收 userId；
 * 订阅可见性与 2.7.2 作品详情同口径（未上架 / 已删除 / 不存在均不可订阅）。
 * 本批不含「更新提醒开关」（notify_enabled 无对应契约，订阅时固定写 1）。
 *
 * @author xiangsipeng
 */
public interface IAppSubscriptionService
{
    /**
     * 订阅（接口 2.8.13，幂等）
     *
     * @param workId 作品ID
     * @return true=订阅成功（含此前已订阅）；false=作品不存在/已删除/未上架，由控制层按 404 处理
     */
    public boolean subscribe(Long workId);

    /**
     * 取消订阅（接口 2.8.13）
     *
     * 幂等：未订阅时同样视为成功，不区分记录是否存在。
     *
     * @param workId 作品ID
     */
    public void unsubscribe(Long workId);

    /**
     * 我的追更列表（接口 2.8.14，分页）
     *
     * @param pageNum  页码（从 1 起）
     * @param pageSize 每页条数
     * @return 分页结果（total + list，仅含已上架未删除作品）
     */
    public AppPageResult<AppSubscriptionItem> pageSubscriptions(int pageNum, int pageSize);
}