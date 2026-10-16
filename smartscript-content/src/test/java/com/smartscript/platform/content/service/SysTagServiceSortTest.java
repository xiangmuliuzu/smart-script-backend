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
import com.smartscript.platform.content.domain.SysTag;
import com.smartscript.platform.content.mapper.SysTagMapper;
import com.smartscript.platform.content.service.impl.SysTagServiceImpl;

/**
 * 标签显示位次（sort）服务层单元测试。
 *
 * 校验点：插入式腾位区间、目标位次空闲（存量空洞）时不位移、默认追加末尾、
 * 位次必须为正整数、写入后位次唯一强校验（重复即抛异常回滚）。
 */
class SysTagServiceSortTest
{
    private SysTagMapper tagMapper;
    private SysTagServiceImpl service;

    @BeforeEach
    void setUp()
    {
        tagMapper = mock(SysTagMapper.class);
        service = new SysTagServiceImpl();
        ReflectionTestUtils.setField(service, "tagMapper", tagMapper);
    }

    private SysTag tag(Long tagId, Integer sort)
    {
        SysTag tag = new SysTag();
        tag.setTagId(tagId);
        tag.setTagName("标签");
        tag.setSort(sort);
        return tag;
    }

    @Test
    @DisplayName("新增未带类型：兜底为收敛后的单一类型（theme），避免 tag_type 非空约束报错")
    void insertDefaultsTagType()
    {
        when(tagMapper.selectMaxSort()).thenReturn(0);

        SysTag tag = tag(null, null);
        service.insertTag(tag);

        assertEquals("theme", tag.getTagType());
        verify(tagMapper).insertTag(tag);
    }

    @Test
    @DisplayName("新增未指定位次：追加到末尾（最大位次 + 1），不做腾位")
    void insertWithoutSortAppendsToTail()
    {
        when(tagMapper.selectMaxSort()).thenReturn(5);

        SysTag tag = tag(null, null);
        service.insertTag(tag);

        assertEquals(6, tag.getSort());
        verify(tagMapper, never()).shiftTagSortForward(anyInt(), anyInt(), any());
        verify(tagMapper, never()).shiftTagSortBackward(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("新增指定位次 1：原 1~N 整体后移一位，腾位于 1")
    void insertWithSortOneShiftsOthersDown()
    {
        when(tagMapper.selectMaxSort()).thenReturn(5);
        // 位次 1 已被占用 → 需要腾位；腾位后该位次空闲（供写入后唯一性校验）
        when(tagMapper.countBySort(eq(1), any())).thenReturn(1, 0);

        SysTag tag = tag(null, 1);
        service.insertTag(tag);

        assertEquals(1, tag.getSort());
        // 腾位区间 [1, 最大位次 5]：整体 +1
        verify(tagMapper).shiftTagSortForward(eq(1), eq(6), any());
        verify(tagMapper).insertTag(tag);
    }

    @Test
    @DisplayName("新增指定位次为空缺位次：直接落位，不位移任何标签")
    void insertIntoSortGapDoesNotShift()
    {
        when(tagMapper.selectMaxSort()).thenReturn(5);

        // 位次 3 空闲（存量数据中间有空洞）
        SysTag tag = tag(null, 3);
        service.insertTag(tag);

        assertEquals(3, tag.getSort());
        verify(tagMapper, never()).shiftTagSortForward(anyInt(), anyInt(), any());
        verify(tagMapper, never()).shiftTagSortBackward(anyInt(), anyInt(), any());
        verify(tagMapper).insertTag(tag);
    }

    @Test
    @DisplayName("新增指定位次小于 1：抛异常且不落库")
    void insertWithNonPositiveSortRejected()
    {
        SysTag tag = tag(null, 0);

        assertThrows(ServiceException.class, () -> service.insertTag(tag));
        verify(tagMapper, never()).insertTag(any());
    }

    @Test
    @DisplayName("新增写入后位次仍被占用：抛异常触发回滚")
    void insertWithDuplicateSortRollsBack()
    {
        when(tagMapper.selectMaxSort()).thenReturn(3);
        when(tagMapper.countBySort(anyInt(), any())).thenReturn(1);

        SysTag tag = tag(null, 2);

        assertThrows(ServiceException.class, () -> service.insertTag(tag));
    }

    @Test
    @DisplayName("修改位次前移（改小）：区间 [newSort, oldSort-1] 整体 +1，排除自身")
    void updateMoveForwardShiftsRange()
    {
        when(tagMapper.selectTagById(9L)).thenReturn(tag(9L, 4));
        // 目标位次 1 已被占用 → 需要腾位
        when(tagMapper.countBySort(eq(1), eq(9L))).thenReturn(1, 0);

        SysTag tag = tag(9L, 1);
        service.updateTag(tag);

        verify(tagMapper).shiftTagSortForward(eq(1), eq(4), eq(9L));
        verify(tagMapper).updateTag(tag);
    }

    @Test
    @DisplayName("修改位次后移（改大）：区间 [oldSort+1, newSort] 整体 -1，排除自身")
    void updateMoveBackwardShiftsRange()
    {
        when(tagMapper.selectTagById(9L)).thenReturn(tag(9L, 2));
        // 当前最大位次为 8，目标位次 6 未越界
        when(tagMapper.selectMaxSort()).thenReturn(8);
        // 目标位次 6 已被占用 → 需要腾位
        when(tagMapper.countBySort(eq(6), eq(9L))).thenReturn(1, 0);

        SysTag tag = tag(9L, 6);
        service.updateTag(tag);

        verify(tagMapper).shiftTagSortBackward(eq(6), eq(2), eq(9L));
        verify(tagMapper).updateTag(tag);
    }

    @Test
    @DisplayName("修改到空缺位次：直接落位，不位移任何标签")
    void updateIntoSortGapDoesNotShift()
    {
        // 自身当前位次 7（也是当前最大位次），目标位次 4 空闲（存量数据中间有空洞）
        when(tagMapper.selectTagById(9L)).thenReturn(tag(9L, 7));
        when(tagMapper.selectMaxSort()).thenReturn(7);

        SysTag tag = tag(9L, 4);
        service.updateTag(tag);

        verify(tagMapper, never()).shiftTagSortForward(anyInt(), anyInt(), any());
        verify(tagMapper, never()).shiftTagSortBackward(anyInt(), anyInt(), any());
        verify(tagMapper).updateTag(tag);
    }

    @Test
    @DisplayName("修改不存在的标签：抛异常且不落库")
    void updateMissingTagRejected()
    {
        when(tagMapper.selectTagById(99L)).thenReturn(null);

        SysTag tag = tag(99L, 1);

        assertThrows(ServiceException.class, () -> service.updateTag(tag));
        verify(tagMapper, never()).updateTag(any());
    }

    @Test
    @DisplayName("修改写入后位次与其它标签重复：抛异常触发回滚")
    void updateWithDuplicateSortRollsBack()
    {
        when(tagMapper.selectTagById(9L)).thenReturn(tag(9L, 4));
        when(tagMapper.countBySort(anyInt(), any())).thenReturn(1);

        SysTag tag = tag(9L, 1);

        assertThrows(ServiceException.class, () -> service.updateTag(tag));
    }

    @Test
    @DisplayName("修改未带位次：不查旧位次、不腾位")
    void updateWithoutSortKeepsOthersUntouched()
    {
        SysTag tag = tag(9L, null);
        tag.setTagName("改名");

        service.updateTag(tag);

        verify(tagMapper, never()).selectTagById(anyLong());
        verify(tagMapper, never()).shiftTagSortForward(anyInt(), anyInt(), any());
        verify(tagMapper, never()).shiftTagSortBackward(anyInt(), anyInt(), any());
        verify(tagMapper).updateTag(tag);
    }

    @Test
    @DisplayName("删除后紧缩：被删位次之后的标签整体前移一位，消除位次空洞")
    void deleteCompactsSortGap()
    {
        when(tagMapper.selectTagById(2L)).thenReturn(tag(2L, 2));

        service.deleteTagByIds(new Long[] { 2L });

        verify(tagMapper).deleteTagByIds(new Long[] { 2L });
        verify(tagMapper).shiftSortAfterDelete(2);
    }

    @Test
    @DisplayName("批量删除：按被删位次从大到小依次紧缩，避免多次前移错位")
    void deleteBatchCompactsInDescendingOrder()
    {
        when(tagMapper.selectTagById(2L)).thenReturn(tag(2L, 2));
        when(tagMapper.selectTagById(5L)).thenReturn(tag(5L, 5));

        service.deleteTagByIds(new Long[] { 2L, 5L });

        InOrder inOrder = inOrder(tagMapper);
        inOrder.verify(tagMapper).shiftSortAfterDelete(5);
        inOrder.verify(tagMapper).shiftSortAfterDelete(2);
    }

    @Test
    @DisplayName("新增指定位次超出当前最大位次：夹取到末尾，不留尾部空洞")
    void insertClampsToTailWhenSortExceedsMax()
    {
        // 当前最大位次 5 → 末尾位次 6；请求位次 100 应被夹到 6
        when(tagMapper.selectMaxSort()).thenReturn(5);

        SysTag tag = tag(null, 100);
        service.insertTag(tag);

        assertEquals(6, tag.getSort());
        verify(tagMapper, never()).shiftTagSortForward(anyInt(), anyInt(), any());
        verify(tagMapper, never()).shiftTagSortBackward(anyInt(), anyInt(), any());
        verify(tagMapper).insertTag(tag);
    }

    @Test
    @DisplayName("修改位次超出当前最大位次：夹取到末尾，不留尾部空洞")
    void updateClampsToMaxWhenSortExceedsMax()
    {
        when(tagMapper.selectTagById(9L)).thenReturn(tag(9L, 2));
        // 当前最大位次 5 → 请求位次 100 夹到 5
        when(tagMapper.selectMaxSort()).thenReturn(5);
        // 位次 5 已被占用 → 腾位；腾位后该位次空闲（供写入后唯一性校验）
        when(tagMapper.countBySort(eq(5), eq(9L))).thenReturn(1, 0);

        SysTag tag = tag(9L, 100);
        service.updateTag(tag);

        assertEquals(5, tag.getSort());
        verify(tagMapper).shiftTagSortBackward(eq(5), eq(2), eq(9L));
        verify(tagMapper).updateTag(tag);
    }

    @Test
    @DisplayName("删除被作品引用的标签：抛异常且不删除、不紧缩")
    void deleteReferencedTagRejected()
    {
        when(tagMapper.countWorkByTagId(2L)).thenReturn(2);

        assertThrows(ServiceException.class, () -> service.deleteTagByIds(new Long[] { 2L }));
        verify(tagMapper, never()).deleteTagByIds(any());
        verify(tagMapper, never()).shiftSortAfterDelete(anyInt());
    }

    @Test
    @DisplayName("删除入参含重复ID：仅紧缩一次，避免其后元素被多减一位")
    void deleteDuplicateIdsCompactOnce()
    {
        when(tagMapper.selectTagById(2L)).thenReturn(tag(2L, 2));

        service.deleteTagByIds(new Long[] { 2L, 2L });

        verify(tagMapper, times(1)).shiftSortAfterDelete(2);
    }
}
