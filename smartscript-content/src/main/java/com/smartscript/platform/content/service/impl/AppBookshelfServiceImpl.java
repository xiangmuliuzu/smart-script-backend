package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppShelfWorkDto;
import com.smartscript.platform.content.mapper.AppBookshelfMapper;
import com.smartscript.platform.content.mapper.AppWorkAuthorizationMapper;
import com.smartscript.platform.content.service.IAppBookshelfService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 书架服务实现（App 书城 2.7.12）
 *
 * 语义：书架 = 「已购买 / 已获授权」的作品库。
 *
 * 反推处理点：
 * 1. 身份不做空值兜底：本组接口未登记 App 凭证域白名单，过滤器已保证非游客。
 * 2. 列表分页用 PageHelper.startPage + PageInfo.getTotal，必须在包装前取 total。
 * 3. 已购判定与章节解锁同口径：sys_order 已完成订单，或 sys_copyright_authorization 生效授权。
 * 4. 阅读进度改为 upsert，不再要求作品预先存在书架行（书架已不承载「加入」语义）。
 *
 * @author xiangsipeng
 */
@Service
public class AppBookshelfServiceImpl implements IAppBookshelfService
{
    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数上限 */
    private static final int PAGE_SIZE_MAX = 50;

    @Autowired
    private AppBookshelfMapper bookshelfMapper;

    @Autowired
    private AppWorkAuthorizationMapper authorizationMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    public AppPageResult<AppShelfWorkDto> pageShelf(int pageNum, int pageSize)
    {
        int safePageNum = pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, PAGE_SIZE_MAX);
        Long userId = identityProvider.currentUserId();
        PageHelper.startPage(safePageNum, safePageSize);
        List<AppShelfWorkDto> rows = bookshelfMapper.selectShelfWorks(userId);
        long total = new PageInfo<>(rows).getTotal();
        return AppPageResult.of(total, rows);
    }

    @Override
    public boolean isAuthorized(Long workId)
    {
        if (workId == null)
        {
            return false;
        }
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return false;
        }
        return authorizationMapper.countEffectiveAuthorization(workId, userId) > 0
                || authorizationMapper.countPurchasedOrder(workId, userId) > 0;
    }

    @Override
    public boolean saveProgress(Long workId, Long chapterId)
    {
        if (workId == null || chapterId == null)
        {
            return false;
        }
        bookshelfMapper.saveProgress(identityProvider.currentUserId(), workId, chapterId);
        return true;
    }
}
