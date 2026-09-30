package com.smartscript.platform.content.controller.app;

import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.service.IAppBookstoreService;

/**
 * App 作品榜单（B 模块，接口文档 2.7.7 表 2-87「作品排行榜（4种排序）」）。
 *
 * 鉴权：公开接口，游客可读。
 * 返回：App 信封 {code, message, data:{list}}。
 *
 * 数据来源：sys_work 真实指标列（view_count/favorite_count/sale_count/rating），
 * 不依赖 sys_ranking_snapshot 快照是否已重算；四种排序即 type 的四个取值。
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
     * @param type  榜单类型（可选：view 默认/favorite/sale/rating，非法值回落 view）
     * @param limit 取前 N 条（可选，默认 10，上限 50）
     */
    @GetMapping
    public AppApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer limit)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", bookstoreService.listRankings(type, limit == null ? 10 : limit));
        return AppApiResponse.ok(data);
    }
}