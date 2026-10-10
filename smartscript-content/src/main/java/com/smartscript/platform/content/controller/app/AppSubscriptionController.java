package com.smartscript.platform.content.controller.app;

import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppSubscriptionItem;
import com.smartscript.platform.content.service.IAppSubscriptionService;

/**
 * App 追更订阅（B 模块，接口文档 2.8.13 表2-106 / 2.8.14 表2-107）。
 *
 * 鉴权：App 私有接口，需 App Access Token。
 *   - 订阅/取消订阅为 POST/DELETE，不会被 /content/works/** 的「GET-only」游客白名单误放；
 *   - /content/subscriptions 未登记白名单，走 App 凭证域 authenticated。
 * 归属一律取当前登录身份，不接收 userId 入参，避免读写他人追更。
 *
 * 路径说明：文档写的是 /api/works/:id/subscribe、/api/user/subscriptions，
 * B 模块既有接口统一收拢在 App 凭证域 /api/v1/content/** 下，故落此前缀。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content")
public class AppSubscriptionController
{
    /** 作品不存在或未上架 */
    private static final int CODE_NOT_FOUND = 404;

    /** 列表默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final IAppSubscriptionService subscriptionService;

    public AppSubscriptionController(IAppSubscriptionService subscriptionService)
    {
        this.subscriptionService = subscriptionService;
    }

    /**
     * 追更订阅（2.8.13，幂等：重复订阅不产生重复记录）
     *
     * @param workId 作品ID
     * @return data = {message}；作品不存在/已删除/未上架按 404 拒绝
     */
    @PostMapping("/works/{workId}/subscribe")
    public AppApiResponse<Map<String, Object>> subscribe(@PathVariable Long workId)
    {
        if (!subscriptionService.subscribe(workId))
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或已下架");
        }
        return AppApiResponse.ok(Map.of("message", "已追更"));
    }

    /**
     * 取消追更（2.8.13，幂等）
     *
     * 未订阅时同样返回成功：详情页可能连点，报错无意义。
     *
     * @param workId 作品ID
     * @return data = {message}
     */
    @DeleteMapping("/works/{workId}/subscribe")
    public AppApiResponse<Map<String, Object>> unsubscribe(@PathVariable Long workId)
    {
        subscriptionService.unsubscribe(workId);
        return AppApiResponse.ok(Map.of("message", "已取消追更"));
    }

    /**
     * 我的追更列表（2.8.14，分页）
     *
     * @param page     页码（可选，默认 1）
     * @param pageSize 每页条数（可选，默认 20，上限 50）
     * @return data = {total, list}，list 元素为追更作品信息
     */
    @GetMapping("/subscriptions")
    public AppApiResponse<AppPageResult<AppSubscriptionItem>> subscriptions(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize)
    {
        int pageNum = page == null ? 1 : page;
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        return AppApiResponse.ok(subscriptionService.pageSubscriptions(pageNum, size));
    }
}