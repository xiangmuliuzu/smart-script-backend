package com.smartscript.platform.content.mapper;

import java.util.List;
import com.smartscript.platform.content.domain.SysBanner;

/**
 * Banner 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_banner 表。
 *
 * @author xiangsipeng
 */
public interface SysBannerMapper
{
    /**
     * 查询Banner列表
     *
     * @param query 查询条件（title 模糊、position/status 精确）
     * @return Banner集合
     */
    public List<SysBanner> selectBannerList(SysBanner query);

    /**
     * App 首页 Banner 轮播列表（只读）
     *
     * 固定条件：status = 'on'，且在展示时间窗内（start_time 为空或已到，end_time 为空或未过）。
     *
     * @param query 查询条件（position 精确，可选）
     * @return Banner集合（按 sort_order 升序）
     */
    public List<SysBanner> selectAppBannerList(SysBanner query);

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
     * 修改Banner状态（单一职责：仅改 status）
     *
     * @param banner 仅携带 bannerId + status + updateBy
     * @return 影响行数
     */
    public int updateBannerStatus(SysBanner banner);

    /**
     * 修改Banner排序（单一职责：仅改 sort_order）
     *
     * @param banner 仅携带 bannerId + sortOrder + updateBy
     * @return 影响行数
     */
    public int updateBannerSort(SysBanner banner);

    /**
     * 删除Banner（物理删除，sys_banner 无逻辑删除列）
     *
     * @param bannerId BannerID
     * @return 影响行数
     */
    public int deleteBannerById(Long bannerId);

    // ------------------------------------------------------------------
    // 位次管理（与分类/标签同构）
    // ------------------------------------------------------------------

    /**
     * 串行化位次写入：SELECT ... FOR UPDATE 锁定位次区间，防止并发下位次重复
     * @param position 展示位置（按位置分组排序）
     */
    public List<Long> lockSortScope(String position);

    /**
     * 当前最大位次（空表返回 null）
     * @param position 展示位置
     */
    public Integer selectMaxSort(String position);

    /**
     * 统计某位次被多少条记录占用（排除自身）
     * @param position 展示位置
     */
    public int countBySort(Integer sortOrder, Long excludeId, String position);

    /**
     * 向前腾位：[newSort, oldSort) 区间的记录 sort_order +1（插入时用）
     * @param position 展示位置
     */
    public void shiftSortForward(int newSort, int oldSort, Long excludeId, String position);

    /**
     * 向后腾位：(oldSort, newSort] 区间的记录 sort_order -1（移动时用）
     * @param position 展示位置
     */
    public void shiftSortBackward(int newSort, int oldSort, Long excludeId, String position);

    /**
     * 删除后紧缩：sort_order > deletedSort 的记录全部 -1
     * @param position 展示位置
     */
    public void shiftSortAfterDelete(int deletedSort, String position);
}
