package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.content.dto.AppDramaFeedItem;
import com.smartscript.platform.content.dto.AppExternalDramaDetailDto;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppRelatedWorkDto;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.mapper.AppDramaMapper;
import com.smartscript.platform.content.service.IAppBookstoreService;
import com.smartscript.platform.content.service.IAppDramaService;

/**
 * 外部视频浏览 服务实现（App 2.8.1 / 2.8.15 / 2.8.16）
 *
 * 依据：接口文档表 2-94 / 2-108 / 2-109 + 云端 script_platform_dev 库
 * sys_external_drama / sys_drama_channel / sys_work 表。
 *
 * 反推处理点：
 * 1. 分页用 PageHelper.startPage + PageInfo.getTotal（与 2.7.1 作品列表同一套写法），
 *    且必须在包装前取 total：PageInfo 依赖 PageHelper 返回的 Page 类型。
 * 2. 找同款剧本的 work 复用 {@link IAppBookstoreService#getWork(Long)}（2.7.2 详情口径），
 *    从而自动获得「未删除 + 已上架」可见性；作品不可见时 work 为 null，但 hasRelatedWork
 *    仍按 related_work_id 非空判定（三个接口同一口径），客户端据此提示「原著暂不可见」。
 *
 * @author xiangsipeng
 */
@Service
public class AppDramaServiceImpl implements IAppDramaService
{
    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数上限 */
    private static final int PAGE_SIZE_MAX = 50;

    @Autowired
    private AppDramaMapper dramaMapper;

    @Autowired
    private IAppBookstoreService bookstoreService;

    @Override
    public AppPageResult<AppDramaFeedItem> pageFeed(int pageNum, int pageSize)
    {
        int safePageNum = pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, PAGE_SIZE_MAX);
        PageHelper.startPage(safePageNum, safePageSize);
        List<AppDramaFeedItem> rows = dramaMapper.selectDramaFeed();
        long total = new PageInfo<>(rows).getTotal();
        return AppPageResult.of(total, rows);
    }

    @Override
    public AppExternalDramaDetailDto getDramaDetail(Long dramaId)
    {
        if (dramaId == null)
        {
            return null;
        }
        return dramaMapper.selectDramaDetailById(dramaId);
    }

    @Override
    public AppRelatedWorkDto getRelatedWork(Long dramaId)
    {
        if (dramaId == null)
        {
            return null;
        }
        AppExternalDramaDetailDto drama = dramaMapper.selectDramaDetailById(dramaId);
        if (drama == null)
        {
            return null;
        }
        AppRelatedWorkDto dto = new AppRelatedWorkDto();
        dto.setDramaId(drama.getDramaId());
        Long relatedWorkId = drama.getRelatedWorkId();
        dto.setHasRelatedWork(relatedWorkId != null);
        if (relatedWorkId != null)
        {
            AppWorkDto work = bookstoreService.getWork(relatedWorkId);
            dto.setWork(work);
        }
        return dto;
    }
}