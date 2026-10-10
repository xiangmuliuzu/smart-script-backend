package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysBanner;

/**
 * Banner 服务层
 *
 * 依据：云端 script_platform_dev 库 sys_banner 表 + PC 功能清单（Banner 管理页：
 * 新增、编辑、启用/停用、排序；清单未列删除，故不提供）。
 *
 * @author xiangsipeng
 */
public interface ISysBannerService
{
    /**
     * 查询Banner列表
     *
     * @param query 查询条件（title 模糊、position/status 精确）
     * @return Banner集合
     */
    public List<SysBanner> selectBannerList(SysBanner query);

    /**
     * 通过BannerID查询详情
     *
     * @param bannerId BannerID
     * @return Banner对象
     */
    public SysBanner selectBannerById(Long bannerId);

    /**
     * 新增Banner
     *
     * @param banner Banner对象
     * @return 影响行数
     */
    public int insertBanner(SysBanner banner);

    /**
     * 修改Banner（动态 set，仅更新非空字段）
     *
     * @param banner Banner对象
     * @return 影响行数
     */
    public int updateBanner(SysBanner banner);

    /**
     * 修改Banner状态（只更新 status 字段）
     *
     * @param banner 仅携带 bannerId + status + updateBy
     * @return 影响行数
     */
    public int updateBannerStatus(SysBanner banner);

    /**
     * 修改Banner排序（只更新 sort_order 字段）
     *
     * @param banner 仅携带 bannerId + sortOrder + updateBy
     * @return 影响行数
     */
    public int updateBannerSort(SysBanner banner);
}
