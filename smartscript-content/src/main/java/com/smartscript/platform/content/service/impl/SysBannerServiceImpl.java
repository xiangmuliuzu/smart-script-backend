package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.StringUtils;
import com.smartscript.platform.content.domain.SysBanner;
import com.smartscript.platform.content.mapper.SysBannerMapper;
import com.smartscript.platform.content.service.ISysBannerService;

/**
 * Banner 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_banner 表。
 * 反推处理点：sort_order/status 云端列 NOT NULL 无默认值，新增时此处兜底 0/"off"；
 * status/sort_order 调整做防御性裁剪，避免调用方误传其他字段被动态 SQL 一并更新。
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
    public int insertBanner(SysBanner banner)
    {
        // 云端列 NOT NULL 无默认值，兜底（无文档依据，反推）
        if (StringUtils.isNull(banner.getSortOrder()))
        {
            banner.setSortOrder(0);
        }
        if (StringUtils.isNull(banner.getStatus()) || StringUtils.isEmpty(banner.getStatus()))
        {
            banner.setStatus(STATUS_OFF);
        }
        return bannerMapper.insertBanner(banner);
    }

    @Override
    public int updateBanner(SysBanner banner)
    {
        return bannerMapper.updateBanner(banner);
    }

    @Override
    public int updateBannerStatus(SysBanner banner)
    {
        // 防御性裁剪：只放行 status
        SysBanner update = new SysBanner();
        update.setBannerId(banner.getBannerId());
        update.setStatus(banner.getStatus());
        update.setUpdateBy(banner.getUpdateBy());
        return bannerMapper.updateBannerStatus(update);
    }

    @Override
    public int updateBannerSort(SysBanner banner)
    {
        // 防御性裁剪：只放行 sortOrder
        SysBanner update = new SysBanner();
        update.setBannerId(banner.getBannerId());
        update.setSortOrder(banner.getSortOrder());
        update.setUpdateBy(banner.getUpdateBy());
        return bannerMapper.updateBannerSort(update);
    }
}
