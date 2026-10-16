package com.smartscript.platform.content.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.smartscript.platform.content.domain.SysCategory;
import com.smartscript.platform.content.mapper.SysCategoryMapper;
import com.smartscript.platform.content.service.ISysCategoryService;

/**
 * 剧本分类 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_category 表。
 * 反推处理点：parent_id/sort/status 云端列 NOT NULL 无默认值，新增时此处兜底 0/0/"0"；
 * sort 兜底为「当前最大位次 + 1」（追加到末尾）。
 *
 * 位次（sort）语义：全局显示顺序，槽位值互不重复，由本服务统一维护。
 *   - 目标位次 k 已被占用时做「插入式腾位」：原 k..N 的分类整体后移一位（新增、修改同规则）。
 *   - 目标位次 k 本身空闲时（存量数据存在空洞）直接落位，不扰动任何其它分类。
 *   - 写入后强校验位次唯一，一旦重复立即抛异常回滚，绝不落库重复位次。
 *
 * @author xiangsipeng
 */
@Service
public class SysCategoryServiceImpl implements ISysCategoryService
{
    /** 状态：正常 */
    private static final String STATUS_NORMAL = "0";

    /** 分类类型：已收敛为单一值（题材），与前端 contentEnum.js CATEGORY_TYPE 保持一致 */
    private static final String CATEGORY_TYPE_THEME = "theme";

    @Autowired
    private SysCategoryMapper categoryMapper;

    @Override
    public List<SysCategory> selectCategoryList(SysCategory query)
    {
        return categoryMapper.selectCategoryList(query);
    }

    @Override
    public SysCategory selectCategoryById(Long categoryId)
    {
        return categoryMapper.selectCategoryById(categoryId);
    }

    @Override
    public boolean checkCategoryNameUnique(SysCategory category)
    {
        Long categoryId = StringUtils.isNull(category.getCategoryId()) ? -1L : category.getCategoryId();
        SysCategory info = categoryMapper.checkCategoryNameUnique(category.getCategoryName());
        if (StringUtils.isNotNull(info) && info.getCategoryId().longValue() != categoryId.longValue())
        {
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public int insertCategory(SysCategory category)
    {
        // 位次维护前先串行化同类写入，避免并发下彼此看不到对方腾位结果而落库重复位次
        categoryMapper.lockSortScope();

        // 云端列 NOT NULL 无默认值，兜底（无文档依据，反推）
        if (StringUtils.isNull(category.getParentId()))
        {
            category.setParentId(0L);
        }
        if (StringUtils.isNull(category.getStatus()) || StringUtils.isEmpty(category.getStatus()))
        {
            category.setStatus(STATUS_NORMAL);
        }
        // category_type 云端列 NOT NULL 无默认值，且类型已收敛为单一值，缺失时兜底（无文档依据，反推）
        if (StringUtils.isEmpty(category.getCategoryType()))
        {
            category.setCategoryType(CATEGORY_TYPE_THEME);
        }

        // 位次：未指定则追加到末尾；指定则占用该位次（已被占用时原 k..N 依次后移一位）
        int tail = nextTailSort();
        if (StringUtils.isNull(category.getSort()))
        {
            category.setSort(tail);
        }
        else
        {
            requirePositiveSort(category.getSort());
            // 超出当前最大位次时夹取到末尾，避免跳到空洞之外留下尾部空洞
            int target = clampToTail(category.getSort(), tail);
            category.setSort(target);
            makeRoomForSort(target, tail, null);
        }

        int rows = categoryMapper.insertCategory(category);

        // 位次唯一强校验：新增后若与既有分类重复，立即回滚
        assertSortUnique(category.getSort(), category.getCategoryId());
        return rows;
    }

    @Override
    @Transactional
    public int updateCategory(SysCategory category)
    {
        if (category.getSort() != null)
        {
            // 位次维护前先串行化同类写入，避免并发下位次重复
            categoryMapper.lockSortScope();
            requirePositiveSort(category.getSort());
            SysCategory old = categoryMapper.selectCategoryById(category.getCategoryId());
            if (old == null)
            {
                throw new ServiceException("分类不存在，无法修改位次");
            }
            int oldSort = StringUtils.isNull(old.getSort()) ? 0 : old.getSort();
            // 目标位次不得超过当前最大位次，否则会遗留尾部空洞
            int target = clampToMax(category.getSort());
            category.setSort(target);
            makeRoomForSort(target, oldSort, category.getCategoryId());
        }

        int rows = categoryMapper.updateCategory(category);

        // 位次唯一强校验：写入后若与其它分类重复，立即回滚
        if (category.getSort() != null)
        {
            assertSortUnique(category.getSort(), category.getCategoryId());
        }
        return rows;
    }

    @Override
    public int updateCategoryStatus(SysCategory category)
    {
        // 防御性裁剪：只放行 status，避免调用方误传其他字段被动态 SQL 一并更新
        SysCategory update = new SysCategory();
        update.setCategoryId(category.getCategoryId());
        update.setStatus(category.getStatus());
        update.setUpdateBy(category.getUpdateBy());
        return categoryMapper.updateCategory(update);
    }

    @Override
    @Transactional
    public int updateCategorySort(SysCategory category)
    {
        // 防御性裁剪：只放行 sort（位次唯一约束不得被本快捷入口绕过，故同走腾位 + 强校验）
        SysCategory update = new SysCategory();
        update.setCategoryId(category.getCategoryId());
        update.setSort(category.getSort());
        update.setUpdateBy(category.getUpdateBy());

        // 位次维护前先串行化同类写入，避免并发下位次重复
        categoryMapper.lockSortScope();
        requirePositiveSort(update.getSort());
        SysCategory old = categoryMapper.selectCategoryById(update.getCategoryId());
        if (old == null)
        {
            throw new ServiceException("分类不存在，无法修改位次");
        }
        int oldSort = StringUtils.isNull(old.getSort()) ? 0 : old.getSort();
        // 目标位次不得超过当前最大位次，否则会遗留尾部空洞
        int target = clampToMax(update.getSort());
        update.setSort(target);
        makeRoomForSort(target, oldSort, update.getCategoryId());

        int rows = categoryMapper.updateCategory(update);
        assertSortUnique(update.getSort(), update.getCategoryId());
        return rows;
    }

    /**
     * 末尾位次：当前最大位次 + 1（空表为 1）
     */
    private int nextTailSort()
    {
        Integer maxSort = categoryMapper.selectMaxSort();
        return (maxSort == null ? 0 : maxSort) + 1;
    }

    /**
     * 位次合法性：必须为大于 0 的整数
     */
    private void requirePositiveSort(Integer sort)
    {
        if (sort == null || sort < 1)
        {
            throw new ServiceException("显示位次必须为大于 0 的整数");
        }
    }

    /**
     * 新增位次夹取：目标位次不得超过「当前最大位次 + 1」
     *
     * 超出时夹到末尾，否则元素会直接跳到空洞之外，在尾部留下位次空洞。
     */
    private int clampToTail(int newSort, int tail)
    {
        return Math.min(newSort, tail);
    }

    /**
     * 修改位次夹取：目标位次不得超过当前最大位次
     *
     * 超出时夹到末尾（即移到最后一个槽位），否则会遗留尾部空洞。
     */
    private int clampToMax(int newSort)
    {
        Integer maxSort = categoryMapper.selectMaxSort();
        return Math.min(newSort, Math.max(maxSort == null ? 0 : maxSort, 1));
    }

    /**
     * 占用目标位次：仅当该位次已被其它分类占用时才腾位，空闲则直接落位
     *
     * 存量数据的位次可能存在空洞；若目标位次本身空闲（没有其它分类占用），
     * 移过去不应扰动任何分类，故先判占用、再决定是否需要区间位移。
     *
     * @param newSort   目标位次（调用方保证 >= 1）
     * @param oldSort   当前占用位次；新增场景传「末尾位次」，即让出 [newSort, 最大位次]
     * @param excludeId 自身分类ID（修改场景排除自己；新增场景传 null）
     */
    private void makeRoomForSort(int newSort, int oldSort, Long excludeId)
    {
        // 目标位次空闲（存量数据存在空洞）：直接占用，不位移任何分类
        if (categoryMapper.countBySort(newSort, excludeId) == 0)
        {
            return;
        }

        if (newSort < oldSort)
        {
            categoryMapper.shiftSortForward(newSort, oldSort, excludeId);
        }
        else if (newSort > oldSort)
        {
            categoryMapper.shiftSortBackward(newSort, oldSort, excludeId);
        }
    }

    /**
     * 位次唯一强校验：除自身外若仍存在占用该位次的分类，抛异常触发事务回滚
     */
    private void assertSortUnique(Integer sort, Long categoryId)
    {
        if (categoryMapper.countBySort(sort, categoryId) > 0)
        {
            throw new ServiceException("显示位次 " + sort + " 已被占用，位次必须唯一");
        }
    }

    @Override
    @Transactional
    public int deleteCategoryByIds(Long[] categoryIds)
    {
        // 位次维护前先串行化同类写入，避免并发下位次重复
        categoryMapper.lockSortScope();

        // 校验用去重后的 ID，避免重复入参触发重复校验与重复紧缩
        java.util.Set<Long> uniqueIds = new java.util.LinkedHashSet<>(java.util.Arrays.asList(categoryIds));
        for (Long categoryId : uniqueIds)
        {
            // 删除前校验：存在子分类不允许删除，避免子分类 parent_id 悬空
            int childCount = categoryMapper.countChildrenByParentId(categoryId);
            if (childCount > 0)
            {
                throw new ServiceException(
                        "该分类下还有 " + childCount + " 个子分类，不允许删除；请先删除或迁移子分类");
            }
            // 删除前校验：被作品引用的分类不允许删除，避免作品 genre_id 悬空
            int workCount = categoryMapper.countWorkByCategoryId(categoryId);
            if (workCount > 0)
            {
                throw new ServiceException(
                        "该分类下已有 " + workCount + " 部作品，不允许删除；请先迁移作品分类");
            }
        }

        // 记录被删位次（去重：重复 ID 若各紧缩一次会把其后元素多减一位，造成位次错乱）
        java.util.Set<Integer> deletedSorts = new java.util.HashSet<>();
        for (Long categoryId : uniqueIds)
        {
            SysCategory db = categoryMapper.selectCategoryById(categoryId);
            if (db != null && db.getSort() != null)
            {
                deletedSorts.add(db.getSort());
            }
        }

        int rows = categoryMapper.deleteCategoryByIds(categoryIds);

        // 删除后紧缩：按被删位次从大到小依次前移其后元素，保证位次连续无空洞
        List<Integer> orderedDeletedSorts = new ArrayList<>(deletedSorts);
        orderedDeletedSorts.sort(Comparator.reverseOrder());
        for (Integer deletedSort : orderedDeletedSorts)
        {
            categoryMapper.shiftSortAfterDelete(deletedSort);
        }
        return rows;
    }
}
