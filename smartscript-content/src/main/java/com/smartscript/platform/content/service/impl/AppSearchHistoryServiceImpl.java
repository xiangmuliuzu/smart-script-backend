package com.smartscript.platform.content.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.dto.AppSearchHistoryListDto;
import com.smartscript.platform.content.mapper.AppSearchHistoryMapper;
import com.smartscript.platform.content.service.IAppSearchHistoryService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 搜索历史服务实现（App 书城 2.7.4 ~ 2.7.6）。
 *
 * 依据：接口文档 2.7 书城模块 + 云端 script_platform_dev 库 sys_search_history 表（附件5.1 表3-30）。
 *
 * 反推处理点：
 * 1. 列表不分页：契约只给 data.list、无 total，按 last_search_at 倒序取最近 HISTORY_LIMIT 条。
 * 2. 身份不做空值兜底：本组接口未登记 App 凭证域白名单，过滤器已保证非游客，
 *    因此 currentUserId() 不会为 null（IdentityProvider 约定：无认证信息才返回游客）。
 * 3. 重复关键词靠 touchHistory 的影响行数驱动 insert，不依赖唯一索引。
 *
 * @author xiangsipeng
 */
@Service
public class AppSearchHistoryServiceImpl implements IAppSearchHistoryService
{
    /** 列表下发条数上限（契约无 total，搜索面板展示足够） */
    private static final int HISTORY_LIMIT = 20;

    @Autowired
    private AppSearchHistoryMapper searchHistoryMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    public AppSearchHistoryListDto listHistory()
    {
        Long userId = identityProvider.currentUserId();
        return new AppSearchHistoryListDto(searchHistoryMapper.selectHistoryByUser(userId, HISTORY_LIMIT));
    }

    @Override
    public int recordHistory(String keyword)
    {
        Long userId = identityProvider.currentUserId();
        String trimmed = keyword.trim();
        int touched = searchHistoryMapper.touchHistory(userId, trimmed);
        if (touched > 0)
        {
            return touched;
        }
        return searchHistoryMapper.insertHistory(userId, trimmed);
    }

    @Override
    public boolean removeHistory(Long id)
    {
        Long userId = identityProvider.currentUserId();
        return searchHistoryMapper.deleteHistoryById(userId, id) > 0;
    }

    @Override
    public int clearHistory()
    {
        Long userId = identityProvider.currentUserId();
        return searchHistoryMapper.deleteHistoryByUser(userId);
    }
}