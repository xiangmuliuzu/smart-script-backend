package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.mapper.AppFavoriteMapper;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.service.IAppFavoriteService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 收藏服务实现（App 书城 2.7.10 / 2.7.11）
 *
 * 依据：接口文档表 2-90 / 2-91 + 云端 script_platform_dev 库 sys_favorite 表（附件5.1 表3-28）。
 *
 * 反推处理点：
 * 1. 身份不做空值兜底：本组接口未登记 App 凭证域白名单，过滤器已保证非游客，
 *    因此 currentUserId() 不会为 null（IdentityProvider 约定：无认证信息才返回游客）。
 * 2. 收藏前复用 {@link SysContentWorkMapper#selectAppWorkById} 判可见性，而不是在本服务
 *    另拼「已上架 + 未删除」条件，避免与 2.7.2 详情口径漂移。
 * 3. 列表分页用 PageHelper.startPage + PageInfo.getTotal（与 2.7.1 作品列表同一套写法），
 *    且必须在包装前取 total：PageInfo 依赖 PageHelper 返回的 Page 类型。
 *
 * @author xiangsipeng
 */
@Service
public class AppFavoriteServiceImpl implements IAppFavoriteService
{
    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数上限 */
    private static final int PAGE_SIZE_MAX = 50;

    @Autowired
    private AppFavoriteMapper favoriteMapper;

    @Autowired
    private SysContentWorkMapper workMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    public AppPageResult<AppWorkDto> pageFavorites(int pageNum, int pageSize)
    {
        int safePageNum = pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, PAGE_SIZE_MAX);
        Long userId = identityProvider.currentUserId();
        PageHelper.startPage(safePageNum, safePageSize);
        List<AppWorkDto> rows = favoriteMapper.selectFavoriteWorks(userId);
        long total = new PageInfo<>(rows).getTotal();
        return AppPageResult.of(total, rows);
    }

    @Override
    public boolean favorite(Long workId)
    {
        if (workId == null || workMapper.selectAppWorkById(workId) == null)
        {
            return false;
        }
        favoriteMapper.insertFavoriteIfAbsent(identityProvider.currentUserId(), workId);
        return true;
    }

    @Override
    public void unfavorite(Long workId)
    {
        if (workId == null)
        {
            return;
        }
        favoriteMapper.deleteFavorite(identityProvider.currentUserId(), workId);
    }

    @Override
    public boolean isFavorited(Long workId)
    {
        if (workId == null)
        {
            return false;
        }
        return favoriteMapper.countFavorite(identityProvider.currentUserId(), workId) > 0;
    }
}