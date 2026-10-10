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
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.service.IAppFavoriteService;

/**
 * App 收藏（B 模块书城，接口文档 2.7.10 表2-90 / 2.7.11 表2-91）。
 *
 * 鉴权：App 私有接口，需 App Access Token（未登记白名单，走 App 凭证域 authenticated）。
 * 归属一律取当前登录身份，不接收 userId 入参，避免读写他人收藏。
 *
 * 路径说明：文档写的是 /api/user/favorites，B 模块既有接口统一收拢在 App 凭证域
 * /api/v1/content/** 下（见 AppAuthSecurityConfig.APP_PATH_PREFIXES），故落此前缀。
 *
 * GET /{workId} 为文档外的补充接口：契约只定义了收藏/取消/列表，缺少「是否已收藏」查询，
 * 详情页无法回显收藏态（只能在列表页反查），按 sys_favorite 表补齐。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content/favorites")
public class AppFavoriteController
{
    /** 作品不存在或未上架 */
    private static final int CODE_NOT_FOUND = 404;

    /** 列表默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final IAppFavoriteService favoriteService;

    public AppFavoriteController(IAppFavoriteService favoriteService)
    {
        this.favoriteService = favoriteService;
    }

    /**
     * 收藏列表（2.7.11，分页）
     *
     * @param page     页码（可选，默认 1）
     * @param pageSize 每页条数（可选，默认 20，上限 50）
     * @return data = {total, list}，list 元素与 2.7.1 作品列表一致
     */
    @GetMapping
    public AppApiResponse<AppPageResult<AppWorkDto>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize)
    {
        int pageNum = page == null ? 1 : page;
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        return AppApiResponse.ok(favoriteService.pageFavorites(pageNum, size));
    }

    /**
     * 收藏（2.7.10，幂等：重复收藏不产生重复记录）
     *
     * @param workId 作品ID
     * @return data = {message}；作品不存在/已删除/未上架按 404 拒绝
     */
    @PostMapping("/{workId}")
    public AppApiResponse<Map<String, Object>> add(@PathVariable Long workId)
    {
        if (!favoriteService.favorite(workId))
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或已下架");
        }
        return AppApiResponse.ok(Map.of("message", "收藏成功"));
    }

    /**
     * 取消收藏（2.7.10，幂等）
     *
     * 未收藏时同样返回成功：详情页/列表页可能连点，报错无意义。
     *
     * @param workId 作品ID
     * @return data = {message}
     */
    @DeleteMapping("/{workId}")
    public AppApiResponse<Map<String, Object>> remove(@PathVariable Long workId)
    {
        favoriteService.unfavorite(workId);
        return AppApiResponse.ok(Map.of("message", "已取消收藏"));
    }

    /**
     * 是否已收藏（文档未定义，补充接口）
     *
     * @param workId 作品ID
     * @return data = {favorited}，供详情页回显收藏态
     */
    @GetMapping("/{workId}")
    public AppApiResponse<Map<String, Object>> status(@PathVariable Long workId)
    {
        return AppApiResponse.ok(Map.of("favorited", favoriteService.isFavorited(workId)));
    }
}