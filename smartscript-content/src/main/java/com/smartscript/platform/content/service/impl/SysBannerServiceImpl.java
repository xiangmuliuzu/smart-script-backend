package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.smartscript.platform.content.domain.SysBanner;
import com.smartscript.platform.content.mapper.SysBannerMapper;
import com.smartscript.platform.content.service.ISysBannerService;

/**
 * Banner 服务层处理
 *
 * 位次规则：按展示位置（home_top/home_middle）分组，每组内 1..N 稠密唯一。
 * 新增默认追加末尾，修改不跳跃留空洞，删除后自动紧缩；无删除前校验。
 *
 * @author xiangsipeng
 */
@Service
public class SysBannerServiceImpl implements ISysBannerService
{
    /** 状态：下架 */
    private static final String STATUS_OFF = "off";

    @Autowired
    private SysBannerMapper bannerMapper;

    @Override
    public List<SysBanner> selectBannerList(SysBanner query)
    {
        return bannerMapper.selectBannerList(query);
    }

    @Override
    public SysBanner selectBannerById(Long bannerId)
    {
        return bannerMapper.selectBannerById(bannerId);
    }

    @Override
    @Transactional
    public int insertBanner(SysBanner banner)
    {
        String position = banner.getPosition();
        bannerMapper.lockSortScope(position);

        if (StringUtils.isNull(banner.getStatus()) || StringUtils.isEmpty(banner.getStatus()))
        {
            banner.setStatus(STATUS_OFF);
        }

        // 位次：未指定则追加到末尾；指定则占用该位次（已被占用时原 k..N 依次后移一位）
        int tail = nextTailSort(position);
        if (banner.getSortOrder() == null)
        {
            banner.setSortOrder(tail);
        }
        else
        {
            requirePositiveSort(banner.getSortOrder());
            int target = clampToTail(banner.getSortOrder(), tail);
            banner.setSortOrder(target);
            makeRoomForSort(target, tail, null, position);
        }

        int rows = bannerMapper.insertBanner(banner);
        assertSortUnique(banner.getSortOrder(), banner.getBannerId(), position);
        return rows;
    }

    @Override
    @Transactional
    public int updateBanner(SysBanner banner)
    {
        if (banner.getSortOrder() != null)
        {
            SysBanner old = bannerMapper.selectBannerById(banner.getBannerId());
            if (old == null)
            {
                throw new ServiceException("Banner不存在，无法修改位次");
            }
            String position = old.getPosition();
            bannerMapper.lockSortScope(position);
            requirePositiveSort(banner.getSortOrder());
            int oldSort = old.getSortOrder() == null ? 0 : old.getSortOrder();
            int target = clampToMax(banner.getSortOrder(), position);
            banner.setSortOrder(target);
            makeRoomForSort(target, oldSort, banner.getBannerId(), position);
        }

        int rows = bannerMapper.updateBanner(banner);

        if (banner.getSortOrder() != null)
        {
            SysBanner old = bannerMapper.selectBannerById(banner.getBannerId());
            String position = old != null ? old.getPosition() : null;
            assertSortUnique(banner.getSortOrder(), banner.getBannerId(), position);
        }
        return rows;
    }

    @Override
    public int updateBannerStatus(SysBanner banner)
    {
        SysBanner update = new SysBanner();
        update.setBannerId(banner.getBannerId());
        update.setStatus(banner.getStatus());
        update.setUpdateBy(banner.getUpdateBy());
        return bannerMapper.updateBannerStatus(update);
    }

    @Override
    @Transactional
    public int updateBannerSort(SysBanner banner)
    {
        SysBanner update = new SysBanner();
        update.setBannerId(banner.getBannerId());
        update.setSortOrder(banner.getSortOrder());
        update.setUpdateBy(banner.getUpdateBy());

        SysBanner old = bannerMapper.selectBannerById(update.getBannerId());
        if (old == null)
        {
            throw new ServiceException("Banner不存在，无法修改位次");
        }
        String position = old.getPosition();
        bannerMapper.lockSortScope(position);
        requirePositiveSort(update.getSortOrder());
        int oldSort = old.getSortOrder() == null ? 0 : old.getSortOrder();
        int target = clampToMax(update.getSortOrder(), position);
        update.setSortOrder(target);
        makeRoomForSort(target, oldSort, update.getBannerId(), position);

        int rows = bannerMapper.updateBanner(update);
        assertSortUnique(update.getSortOrder(), update.getBannerId(), position);
        return rows;
    }

    @Override
    @Transactional
    public int deleteBannerById(Long bannerId)
    {
        SysBanner db = bannerMapper.selectBannerById(bannerId);
        String position = db != null ? db.getPosition() : null;
        if (position != null)
        {
            bannerMapper.lockSortScope(position);
        }

        // 记录被删位次
        Integer deletedSort = (db != null) ? db.getSortOrder() : null;

        int rows = bannerMapper.deleteBannerById(bannerId);

        // 删除后紧缩
        if (deletedSort != null && position != null)
        {
            bannerMapper.shiftSortAfterDelete(deletedSort, position);
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // 位次辅助方法（按 position 分组）
    // ------------------------------------------------------------------

    private int nextTailSort(String position)
    {
        Integer maxSort = bannerMapper.selectMaxSort(position);
        return (maxSort == null ? 0 : maxSort) + 1;
    }

    private void requirePositiveSort(Integer sort)
    {
        if (sort == null || sort < 1)
        {
            throw new ServiceException("显示位次必须为大于 0 的整数");
        }
    }

    private int clampToTail(int newSort, int tail)
    {
        return Math.min(newSort, tail);
    }

    private int clampToMax(int newSort, String position)
    {
        Integer maxSort = bannerMapper.selectMaxSort(position);
        return Math.min(newSort, Math.max(maxSort == null ? 0 : maxSort, 1));
    }

    private void makeRoomForSort(int newSort, int oldSort, Long excludeId, String position)
    {
        if (bannerMapper.countBySort(newSort, excludeId, position) == 0)
        {
            return;
        }
        if (newSort < oldSort)
        {
            bannerMapper.shiftSortForward(newSort, oldSort, excludeId, position);
        }
        else if (newSort > oldSort)
        {
            bannerMapper.shiftSortBackward(newSort, oldSort, excludeId, position);
        }
    }

    private void assertSortUnique(Integer sort, Long bannerId, String position)
    {
        if (position != null && bannerMapper.countBySort(sort, bannerId, position) > 0)
        {
            throw new ServiceException("显示位次 " + sort + " 已被占用，位次必须唯一");
        }
    }
}
