package com.smartscript.platform.content.controller.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppRankingResult;
import com.smartscript.platform.content.service.IAppBookstoreService;

/**
 * App 作品榜单（B 模块，接口文档 2.7.7 表 2-87「作品排行榜」）。
 *
 * 鉴权：公开接口，游客可读。
 * 返回：App 信封 {code, message, data:{list, updatedAt}}。
 *
 * 数据来源（方案 A：时间窗口 + 分级），均由 sys_work / 互动表实时取数，
 * 不依赖 sys_ranking_snapshot 快照是否已重算：
 *   view     热门榜 最近 7 天阅读量
 *   favorite 收藏榜 最近 7 天收藏数
 *   sale     交易热度榜 最近 7 天已支付订单数
 *   rating   评分榜 贝叶斯加权评分
 *   new      新书榜 最近 30 天上架、按阅读量排序
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content/rankings")
public class AppRankingController
{
    private final IAppBookstoreService bookstoreService;

    public AppRankingController(IAppBookstoreService bookstoreService)
    {
        this.bookstoreService = bookstoreService;
    }

    /**
     * 榜单列表
     *
     * @param type  榜单类型（可选：view 默认/favorite/sale/rating/new，非法值回落 view）
     * @param limit 取前 N 条（可选，默认 10，上限 50）
     * @return data:{list（rankNo 从 1 起）, updatedAt（榜单更新时间）}
     */
    @GetMapping
    public AppApiResponse<AppRankingResult> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer limit)
    {
        return AppApiResponse.ok(bookstoreService.listRankings(type, limit == null ? 10 : limit));
    }
}