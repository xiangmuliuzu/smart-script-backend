package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppSubscriptionItem;
import com.smartscript.platform.content.mapper.AppSubscriptionMapper;
import com.smartscript.platform.content.service.IAppBookstoreService;
import com.smartscript.platform.content.service.IAppSubscriptionService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 追更订阅 服务实现（App 2.8.13 / 2.8.14）
 *
 * 依据：接口文档表 2-106 / 2-107 + 云端 script_platform_dev 库
 * sys_subscribe / sys_work 表（附件5.1 表3-35）。
 *
 * 反推处理点：
 * 1. 身份不做空值兜底：本组接口未登记 App 凭证域白名单，过滤器已保证非游客。
 * 2. 订阅前复用 {@link IAppBookstoreService#getWork(Long)} 判可见性，而不是在本服务
 *    另拼「已上架 + 未删除」条件，避免与 2.7.2 详情口径漂移（与 AppFavoriteServiceImpl 同一写法）。
 * 3. 列表分页用 PageHelper.startPage + PageInfo.getTotal，且必须在包装前取 total。
 * 4. notify_enabled 由 Mapper 插入语句固定写 1，本服务不涉及该列。
 *
 * @author xiangsipeng
 */
@Service
public class AppSubscriptionServiceImpl implements IAppSubscriptionService
{
    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数上限 */
    private static final int PAGE_SIZE_MAX = 50;

    @Autowired
    private AppSubscriptionMapper subscriptionMapper;

    @Autowired
    private IAppBookstoreService bookstoreService;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    public boolean subscribe(Long workId)
    {
        if (workId == null || bookstoreService.getWork(workId) == null)
        {
            return false;
        }
        subscriptionMapper.insertSubscribeIfAbsent(identityProvider.currentUserId(), workId);
        return true;
    }

    @Override
    public void unsubscribe(Long workId)
    {
        if (workId == null)
        {
            return;
        }
        subscriptionMapper.deleteSubscribe(identityProvider.currentUserId(), workId);
    }

    @Override
    public AppPageResult<AppSubscriptionItem> pageSubscriptions(int pageNum, int pageSize)
    {
        int safePageNum = pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, PAGE_SIZE_MAX);
        Long userId = identityProvider.currentUserId();
        PageHelper.startPage(safePageNum, safePageSize);
        List<AppSubscriptionItem> rows = subscriptionMapper.selectSubscriptionWorks(userId);
        long total = new PageInfo<>(rows).getTotal();
        return AppPageResult.of(total, rows);
    }
}