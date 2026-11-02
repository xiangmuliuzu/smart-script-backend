package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.smartscript.platform.content.domain.SysCategory;
import com.smartscript.platform.content.mapper.SysCategoryMapper;
import com.smartscript.platform.content.service.impl.SysCategoryServiceImpl;

/**
 * 剧本分类服务层单元测试。
 *
 * 校验点：
 *   1) 新增时对云端 NOT NULL 无默认值列的兜底（parent_id/sort/status/category_type），
 *      避免前端未下发 category_type 时直接触发非空约束报错；
 *   2) 显示位次（sort）：插入式腾位区间、目标位次空闲（存量空洞）时不位移、默认追加末尾、
 *      位次必须为正整数、写入后位次唯一强校验（重复即抛异常回滚）。
 */
class SysCategoryServiceTest
{
    private SysCategoryMapper categoryMapper;
    private SysCategoryServiceImpl service;

    @BeforeEach
    void setUp()
    {
        categoryMapper = mock(SysCategoryMapper.class);
        service = new SysCategoryServiceImpl();
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
    }

    private SysCategory category(Long categoryId, Integer sort)
    {
        SysCategory category = new SysCategory();
        category.setCategoryId(categoryId);
        category.setCategoryName("都市");
        category.setSort(sort);
        return category;
    }

    @Test
    @DisplayName("新增未带类型：兜底为收敛后的单一类型（theme），避免 category_type 非空约束报错")
    void insertDefaultsCategoryType()
    {
        SysCategory category = new SysCategory();
        category.setCategoryName("都市");

        service.insertCategory(category);

        assertEquals("theme", category.getCategoryType());
        assertEquals(Long.valueOf(0L), category.getParentId());
        assertEquals("0", category.getStatus());
        verify(categoryMapper).insertCategory(category);
    }

    @Test
    @DisplayName("新增已带类型：保留调用方传入值，不做覆盖")
    void insertKeepsProvidedCategoryType()
    {
        SysCategory category = new SysCategory();
        category.setCategoryName("都市");
        category.setCategoryType("custom");

        service.insertCategory(category);

        assertEquals("custom", category.getCategoryType());
    }

    @Test
    @DisplayName("新增未指定位次：追加到末尾（最大位次 + 1），不做腾位")
    void insertWithoutSortAppendsToTail()
    {
        when(categoryMapper.selectMaxSort()).thenReturn(5);

        SysCategory category = category(null, null);
        service.insertCategory(category);

        assertEquals(6, category.getSort());
        verify(categoryMapper, never()).shiftSortForward(anyInt(), anyInt(), any());
        verify(categoryMapper, never()).shiftSortBackward(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("新增指定位次 1：原 1~N 整体后移一位，腾位于 1")
    void insertWithSortOneShiftsOthersDown()
    {
        when(categoryMapper.selectMaxSort()).thenReturn(5);
        // 位次 1 已被占用 → 需要腾位；腾位后该位次空闲（供写入后唯一性校验）
        when(categoryMapper.countBySort(eq(1), any())).thenReturn(1, 0);

        SysCategory category = category(null, 1);
        service.insertCategory(category);

        assertEquals(1, category.getSort());
        // 腾位区间 [1, 最大位次 5]：整体 +1
        verify(categoryMapper).shiftSortForward(eq(1), eq(6), any());
        verify(categoryMapper).insertCategory(category);
    }

    @Test
    @DisplayName("新增指定位次为空缺位次：直接落位，不位移任何分类")
    void insertIntoSortGapDoesNotShift()
    {
        when(categoryMapper.selectMaxSort()).thenReturn(5);

        // 位次 3 空闲（存量数据中间有空洞）
        SysCategory category = category(null, 3);
        service.insertCategory(category);

        assertEquals(3, category.getSort());
        verify(categoryMapper, never()).shiftSortForward(anyInt(), anyInt(), any());
        verify(categoryMapper, never()).shiftSortBackward(anyInt(), anyInt(), any());
        verify(categoryMapper).insertCategory(category);
    }

    @Test
    @DisplayName("新增指定位次小于 1：抛异常且不落库")
    void insertWithNonPositiveSortRejected()
    {
        SysCategory category = category(null, 0);

        assertThrows(ServiceException.class, () -> service.insertCategory(category));
        verify(categoryMapper, never()).insertCategory(any());
    }

    @Test
    @DisplayName("新增写入后位次仍被占用：抛异常触发回滚")
    void insertWithDuplicateSortRollsBack()
    {
        when(categoryMapper.selectMaxSort()).thenReturn(3);
        when(categoryMapper.countBySort(anyInt(), any())).thenReturn(1);

        SysCategory category = category(null, 2);

        assertThrows(ServiceException.class, () -> service.insertCategory(category));
    }

    @Test
    @DisplayName("修改位次前移（改小）：区间 [newSort, oldSort-1] 整体 +1，排除自身")
    void updateMoveForwardShiftsRange()
    {
        when(categoryMapper.selectCategoryById(9L)).thenReturn(category(9L, 4));
        // 目标位次 1 已被占用 → 需要腾位
        when(categoryMapper.countBySort(eq(1), eq(9L))).thenReturn(1, 0);

        SysCategory category = category(9L, 1);
        service.updateCategory(category);

        verify(categoryMapper).shiftSortForward(eq(1), eq(4), eq(9L));
        verify(categoryMapper).updateCategory(category);
    }

    @Test
    @DisplayName("修改位次后移（改大）：区间 [oldSort+1, newSort] 整体 -1，排除自身")
    void updateMoveBackwardShiftsRange()
    {
        when(categoryMapper.selectCategoryById(9L)).thenReturn(category(9L, 2));
        // 当前最大位次为 8，目标位次 6 未越界
        when(categoryMapper.selectMaxSort()).thenReturn(8);
        // 目标位次 6 已被占用 → 需要腾位
        when(categoryMapper.countBySort(eq(6), eq(9L))).thenReturn(1, 0);

        SysCategory category = category(9L, 6);
        service.updateCategory(category);

        verify(categoryMapper).shiftSortBackward(eq(6), eq(2), eq(9L));
        verify(categoryMapper).updateCategory(category);
    }

    @Test
    @DisplayName("修改到空缺位次：直接落位，不位移任何分类")
    void updateIntoSortGapDoesNotShift()
    {
        // 自身当前位次 7（也是当前最大位次），目标位次 4 空闲（存量数据中间有空洞）
        when(categoryMapper.selectCategoryById(9L)).thenReturn(category(9L, 7));
        when(categoryMapper.selectMaxSort()).thenReturn(7);

        SysCategory category = category(9L, 4);
        service.updateCategory(category);

        verify(categoryMapper, never()).shiftSortForward(anyInt(), anyInt(), any());
        verify(categoryMapper, never()).shiftSortBackward(anyInt(), anyInt(), any());
        verify(categoryMapper).updateCategory(category);
    }

    @Test
    @DisplayName("修改不存在的分类：抛异常且不落库")
    void updateMissingCategoryRejected()
    {
        when(categoryMapper.selectCategoryById(99L)).thenReturn(null);

        SysCategory category = category(99L, 1);

        assertThrows(ServiceException.class, () -> service.updateCategory(category));
        verify(categoryMapper, never()).updateCategory(any());
    }

    @Test
    @DisplayName("修改写入后位次与其它分类重复：抛异常触发回滚")
    void updateWithDuplicateSortRollsBack()
    {
        when(categoryMapper.selectCategoryById(9L)).thenReturn(category(9L, 4));
        when(categoryMapper.countBySort(anyInt(), any())).thenReturn(1);

        SysCategory category = category(9L, 1);

        assertThrows(ServiceException.class, () -> service.updateCategory(category));
    }

    @Test
    @DisplayName("修改未带位次：不查旧位次、不腾位")
    void updateWithoutSortKeepsOthersUntouched()
    {
        SysCategory category = category(9L, null);
        category.setCategoryName("改名");

        service.updateCategory(category);

        verify(categoryMapper, never()).selectCategoryById(anyLong());
        verify(categoryMapper, never()).shiftSortForward(anyInt(), anyInt(), any());
        verify(categoryMapper, never()).shiftSortBackward(anyInt(), anyInt(), any());
        verify(categoryMapper).updateCategory(category);
    }

    @Test
    @DisplayName("快捷改位次：同走腾位 + 唯一强校验，不允许绕过约束")
    void changeSortGoesThroughReorderAndUniqueCheck()
    {
        when(categoryMapper.selectCategoryById(9L)).thenReturn(category(9L, 4));
        when(categoryMapper.countBySort(eq(1), eq(9L))).thenReturn(1, 0);

        SysCategory category = category(9L, 1);
        service.updateCategorySort(category);

        verify(categoryMapper).shiftSortForward(eq(1), eq(4), eq(9L));
        verify(categoryMapper).updateCategory(any(SysCategory.class));
    }

    @Test
    @DisplayName("快捷改位次小于 1：抛异常且不落库")
    void changeSortWithNonPositiveSortRejected()
    {
        SysCategory category = category(9L, 0);

        assertThrows(ServiceException.class, () -> service.updateCategorySort(category));
        verify(categoryMapper, never()).updateCategory(any());
    }

    @Test
    @DisplayName("删除后紧缩：被删位次之后的分类整体前移一位，消除位次空洞")
    void deleteCompactsSortGap()
    {
        when(categoryMapper.selectCategoryById(2L)).thenReturn(category(2L, 2));

        service.deleteCategoryByIds(new Long[] { 2L });

        verify(categoryMapper).deleteCategoryByIds(new Long[] { 2L });
        verify(categoryMapper).shiftSortAfterDelete(2);
    }

    @Test
    @DisplayName("批量删除：按被删位次从大到小依次紧缩，避免多次前移错位")
    void deleteBatchCompactsInDescendingOrder()
    {
        when(categoryMapper.selectCategoryById(2L)).thenReturn(category(2L, 2));
        when(categoryMapper.selectCategoryById(5L)).thenReturn(category(5L, 5));

        service.deleteCategoryByIds(new Long[] { 2L, 5L });

        InOrder inOrder = inOrder(categoryMapper);
        inOrder.verify(categoryMapper).shiftSortAfterDelete(5);
        inOrder.verify(categoryMapper).shiftSortAfterDelete(2);
    }

    @Test
    @DisplayName("删除被作品引用的分类：抛异常且不删除、不紧缩")
    void deleteReferencedCategoryRejected()
    {
        when(categoryMapper.countWorkByCategoryId(2L)).thenReturn(3);

        assertThrows(ServiceException.class, () -> service.deleteCategoryByIds(new Long[] { 2L }));
        verify(categoryMapper, never()).deleteCategoryByIds(any());
        verify(categoryMapper, never()).shiftSortAfterDelete(anyInt());
    }

    @Test
    @DisplayName("新增指定位次超出当前最大位次：夹取到末尾，不留尾部空洞")
    void insertClampsToTailWhenSortExceedsMax()
    {
        // 当前最大位次 5 → 末尾位次 6；请求位次 100 应被夹到 6
        when(categoryMapper.selectMaxSort()).thenReturn(5);

        SysCategory category = category(null, 100);
        service.insertCategory(category);

        assertEquals(6, category.getSort());
        verify(categoryMapper, never()).shiftSortForward(anyInt(), anyInt(), any());
        verify(categoryMapper, never()).shiftSortBackward(anyInt(), anyInt(), any());
        verify(categoryMapper).insertCategory(category);
    }

    @Test
    @DisplayName("修改位次超出当前最大位次：夹取到末尾，不留尾部空洞")
    void updateClampsToMaxWhenSortExceedsMax()
    {
        when(categoryMapper.selectCategoryById(9L)).thenReturn(category(9L, 2));
        // 当前最大位次 5 → 请求位次 100 夹到 5
        when(categoryMapper.selectMaxSort()).thenReturn(5);
        // 位次 5 已被占用 → 腾位；腾位后该位次空闲（供写入后唯一性校验）
        when(categoryMapper.countBySort(eq(5), eq(9L))).thenReturn(1, 0);

        SysCategory category = category(9L, 100);
        service.updateCategory(category);

        assertEquals(5, category.getSort());
        verify(categoryMapper).shiftSortBackward(eq(5), eq(2), eq(9L));
        verify(categoryMapper).updateCategory(category);
    }

    @Test
    @DisplayName("删除存在子分类的分类：抛异常且不删除、不紧缩")
    void deleteCategoryWithChildrenRejected()
    {
        when(categoryMapper.countChildrenByParentId(2L)).thenReturn(4);

        assertThrows(ServiceException.class, () -> service.deleteCategoryByIds(new Long[] { 2L }));
        verify(categoryMapper, never()).deleteCategoryByIds(any());
        verify(categoryMapper, never()).shiftSortAfterDelete(anyInt());
    }

    @Test
    @DisplayName("删除入参含重复ID：仅紧缩一次，避免其后元素被多减一位")
    void deleteDuplicateIdsCompactOnce()
    {
        when(categoryMapper.selectCategoryById(2L)).thenReturn(category(2L, 2));

        service.deleteCategoryByIds(new Long[] { 2L, 2L });

        verify(categoryMapper, times(1)).shiftSortAfterDelete(2);
    }

    private SysCategory parent(Long categoryId, Long parentId, String status)
    {
        SysCategory category = new SysCategory();
        category.setCategoryId(categoryId);
        category.setParentId(parentId);
        category.setStatus(status);
        return category;
    }

    @Test
    @DisplayName("新增在第三级分类下：拒绝，分类最多三级")
    void insertUnderThirdLevelRejected()
    {
        when(categoryMapper.selectCategoryById(101L)).thenReturn(parent(101L, 25L, "0"));
        when(categoryMapper.selectCategoryLevel(101L)).thenReturn(3);

        SysCategory category = new SysCategory();
        category.setCategoryName("新细分");
        category.setParentId(101L);

        assertThrows(ServiceException.class, () -> service.insertCategory(category));
        verify(categoryMapper, never()).insertCategory(any());
    }

    @Test
    @DisplayName("新增在停用的父级下：拒绝")
    void insertUnderDisabledParentRejected()
    {
        when(categoryMapper.selectCategoryById(25L)).thenReturn(parent(25L, 146L, "1"));

        SysCategory category = new SysCategory();
        category.setCategoryName("新题材");
        category.setParentId(25L);

        assertThrows(ServiceException.class, () -> service.insertCategory(category));
        verify(categoryMapper, never()).insertCategory(any());
    }

    @Test
    @DisplayName("新增在第二级题材下：允许（成为第三级细分）")
    void insertUnderSecondLevelAllowed()
    {
        when(categoryMapper.selectCategoryById(25L)).thenReturn(parent(25L, 146L, "0"));
        when(categoryMapper.selectCategoryLevel(25L)).thenReturn(2);
        when(categoryMapper.selectMaxSort()).thenReturn(69);

        SysCategory category = new SysCategory();
        category.setCategoryName("东方玄幻子类");
        category.setParentId(25L);

        service.insertCategory(category);

        verify(categoryMapper).insertCategory(category);
    }

    @Test
    @DisplayName("修改父级为自身：拒绝，防止成环")
    void updateParentToSelfRejected()
    {
        when(categoryMapper.selectSubtreeHeight(9L)).thenReturn(1);
        when(categoryMapper.selectCategoryById(9L)).thenReturn(parent(9L, 0L, "0"));

        SysCategory category = category(9L, null);
        category.setParentId(9L);

        assertThrows(ServiceException.class, () -> service.updateCategory(category));
        verify(categoryMapper, never()).updateCategory(any());
    }

    @Test
    @DisplayName("修改父级为自身后代：拒绝，防止成环")
    void updateParentToDescendantRejected()
    {
        when(categoryMapper.selectSubtreeHeight(146L)).thenReturn(2);
        when(categoryMapper.selectCategoryById(25L)).thenReturn(parent(25L, 146L, "0"));

        SysCategory category = category(146L, null);
        category.setParentId(25L);

        assertThrows(ServiceException.class, () -> service.updateCategory(category));
        verify(categoryMapper, never()).updateCategory(any());
    }

    @Test
    @DisplayName("移动子树后层级超过三级：拒绝")
    void updateMovingSubtreeExceedLevelRejected()
    {
        when(categoryMapper.selectSubtreeHeight(25L)).thenReturn(2);
        when(categoryMapper.selectCategoryById(32L)).thenReturn(parent(32L, 147L, "0"));
        when(categoryMapper.selectCategoryById(147L)).thenReturn(parent(147L, 0L, "0"));
        when(categoryMapper.selectCategoryLevel(32L)).thenReturn(2);

        SysCategory category = category(25L, null);
        category.setParentId(32L);

        assertThrows(ServiceException.class, () -> service.updateCategory(category));
        verify(categoryMapper, never()).updateCategory(any());
    }
}
