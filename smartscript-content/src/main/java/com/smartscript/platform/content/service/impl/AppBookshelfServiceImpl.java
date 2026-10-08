package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.mapper.AppBookshelfMapper;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.service.IAppBookshelfService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 书架服务实现（App 书城 2.7.12）
 *
 * 依据：接口文档表 2-92 + 云端 script_platform_dev 库 sys_bookshelf_record 表（附件5.1 表3-32）。
 *
 * 反推处理点：
 * 1. 身份不做空值兜底：本组接口未登记 App 凭证域白名单，过滤器已保证非游客，
 *    因此 currentUserId() 不会为 null（IdentityProvider 约定：无认证信息才返回游客）。
 * 2. 加入书架前复用 {@link SysContentWorkMapper#selectAppWorkById} 判可见性，而不是在本服务
 *    另拼「已上架 + 未删除」条件，避免与 2.7.2 详情口径漂移。
 * 3. 列表分页用 PageHelper.startPage + PageInfo.getTotal（与 2.7.1 作品列表同一套写法），
 *    且必须在包装前取 total：PageInfo 依赖 PageHelper 返回的 Page 类型。
 * 4. 移出书架为物理删除（与 sys_favorite 口径一致），保证 uk_user_work 唯一索引不残留占位行，
 *    便于用户再次加入。
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

    /** 缺省加入来源：接口文档 shelf_type 未传时的落库值 */
    private static final String DEFAULT_ADD_SOURCE = "app";

    @Autowired
    private AppBookshelfMapper bookshelfMapper;

    @Autowired
    private SysContentWorkMapper workMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    public AppPageResult<AppWorkDto> pageShelf(int pageNum, int pageSize)
    {
        int safePageNum = pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, PAGE_SIZE_MAX);
        Long userId = identityProvider.currentUserId();
        PageHelper.startPage(safePageNum, safePageSize);
        List<AppWorkDto> rows = bookshelfMapper.selectShelfWorks(userId);
        long total = new PageInfo<>(rows).getTotal();
        return AppPageResult.of(total, rows);
    }

    @Override
    public boolean addShelf(Long workId, String addSource)
    {
        if (workId == null || workMapper.selectAppWorkById(workId) == null)
        {
            return false;
        }
        String source = addSource == null || addSource.trim().isEmpty()
                ? DEFAULT_ADD_SOURCE : addSource.trim();
        bookshelfMapper.insertShelfIfAbsent(identityProvider.currentUserId(), workId, source);
        return true;
    }

    @Override
    public void removeShelf(Long workId)
    {
        if (workId == null)
        {
            return;
        }
        bookshelfMapper.deleteShelf(identityProvider.currentUserId(), workId);
    }

    @Override
    public boolean isOnShelf(Long workId)
    {
        if (workId == null)
        {
            return false;
        }
        return bookshelfMapper.countShelf(identityProvider.currentUserId(), workId) > 0;
    }
}