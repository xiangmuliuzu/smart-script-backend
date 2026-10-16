package com.smartscript.platform.content.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.smartscript.platform.content.domain.SysTag;
import com.smartscript.platform.content.mapper.SysTagMapper;
import com.smartscript.platform.content.service.ISysTagService;

/**
 * 标签 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_tag 表。
 * 反推处理点：use_count/status 云端列 NOT NULL 无默认值，新增时此处兜底 0/"0"；
 * sort 兜底为「当前最大位次 + 1」（追加到末尾）。
 *
 * 位次（sort）语义：全局显示顺序，槽位值互不重复，由本服务统一维护。
 *   - 目标位次 k 已被占用时做「插入式腾位」：原 k..N 的标签整体后移一位（新增、修改同规则）。
 *   - 目标位次 k 本身空闲时（存量数据存在空洞）直接落位，不扰动任何其它标签。
 *   - 写入后强校验位次唯一，一旦重复立即抛异常回滚，绝不落库重复位次。
 *
 * @author xiangsipeng
 */
@Service
public class SysTagServiceImpl implements ISysTagService
{
    /** 状态：正常 */
    private static final String STATUS_NORMAL = "0";

    /** 标签类型：已收敛为单一值（情节标签），与前端 contentEnum.js TAG_TYPE 保持一致 */
    private static final String TAG_TYPE_THEME = "theme";

    @Autowired
    private SysTagMapper tagMapper;

    @Override
    public List<SysTag> selectTagList(SysTag query)
    {
        return tagMapper.selectTagList(query);
    }

    @Override
    public SysTag selectTagById(Long tagId)
    {
        return tagMapper.selectTagById(tagId);
    }

    @Override
    public boolean checkTagNameUnique(SysTag tag)
    {
        Long tagId = StringUtils.isNull(tag.getTagId()) ? -1L : tag.getTagId();
        SysTag info = tagMapper.checkTagNameUnique(tag.getTagName());
        if (StringUtils.isNotNull(info) && info.getTagId().longValue() != tagId.longValue())
        {
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public int insertTag(SysTag tag)
    {
        // 位次维护前先串行化同类写入，避免并发下彼此看不到对方腾位结果而落库重复位次
        tagMapper.lockSortScope();

        // 云端列 NOT NULL 无默认值，兜底（无文档依据，反推）
        if (StringUtils.isNull(tag.getUseCount()))
        {
            tag.setUseCount(0);
        }
        if (StringUtils.isNull(tag.getStatus()) || StringUtils.isEmpty(tag.getStatus()))
        {
            tag.setStatus(STATUS_NORMAL);
        }
        // tag_type 云端列 NOT NULL 无默认值，且类型已收敛为单一值，缺失时兜底（无文档依据，反推）
        if (StringUtils.isEmpty(tag.getTagType()))
        {
            tag.setTagType(TAG_TYPE_THEME);
        }

        // 位次：未指定则追加到末尾；指定则占用该位次（已被占用时原 k..N 依次后移一位）
        int tail = nextTailSort();
        if (StringUtils.isNull(tag.getSort()))
        {
            tag.setSort(tail);
        }
        else
        {
            requirePositiveSort(tag.getSort());
            // 超出当前最大位次时夹取到末尾，避免跳到空洞之外留下尾部空洞
            int target = clampToTail(tag.getSort(), tail);
            tag.setSort(target);
            makeRoomForSort(target, tail, null);
        }

        int rows = tagMapper.insertTag(tag);

        // 位次唯一强校验：新增后若与既有标签重复，立即回滚
        assertSortUnique(tag.getSort(), tag.getTagId());
        return rows;
    }

    @Override
    @Transactional
    public int updateTag(SysTag tag)
    {
        if (tag.getSort() != null)
        {
            // 位次维护前先串行化同类写入，避免并发下位次重复
            tagMapper.lockSortScope();
            requirePositiveSort(tag.getSort());
            SysTag old = tagMapper.selectTagById(tag.getTagId());
            if (old == null)
            {
                throw new ServiceException("标签不存在，无法修改位次");
            }
            int oldSort = StringUtils.isNull(old.getSort()) ? 0 : old.getSort();
            // 目标位次不得超过当前最大位次，否则会遗留尾部空洞
            int target = clampToMax(tag.getSort());
            tag.setSort(target);
            makeRoomForSort(target, oldSort, tag.getTagId());
        }

        int rows = tagMapper.updateTag(tag);

        // 位次唯一强校验：写入后若与其它标签重复，立即回滚
        if (tag.getSort() != null)
        {
            assertSortUnique(tag.getSort(), tag.getTagId());
        }
        return rows;
    }

    /**
     * 末尾位次：当前最大位次 + 1（空表为 1）
     */
    private int nextTailSort()
    {
        Integer maxSort = tagMapper.selectMaxSort();
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
        Integer maxSort = tagMapper.selectMaxSort();
        return Math.min(newSort, Math.max(maxSort == null ? 0 : maxSort, 1));
    }

    /**
     * 占用目标位次：仅当该位次已被其它标签占用时才腾位，空闲则直接落位
     *
     * 存量数据的位次可能存在空洞；若目标位次本身空闲（没有其它标签占用），
     * 移过去不应扰动任何标签，故先判占用、再决定是否需要区间位移。
     *
     * @param newSort   目标位次（调用方保证 >= 1）
     * @param oldSort   当前占用位次；新增场景传「末尾位次」，即让出 [newSort, 最大位次]
     * @param excludeId 自身标签ID（修改场景排除自己；新增场景传 null）
     */
    private void makeRoomForSort(int newSort, int oldSort, Long excludeId)
    {
        // 目标位次空闲（存量数据存在空洞）：直接占用，不位移任何标签
        if (tagMapper.countBySort(newSort, excludeId) == 0)
        {
            return;
        }

        if (newSort < oldSort)
        {
            tagMapper.shiftTagSortForward(newSort, oldSort, excludeId);
        }
        else if (newSort > oldSort)
        {
            tagMapper.shiftTagSortBackward(newSort, oldSort, excludeId);
        }
    }

    /**
     * 位次唯一强校验：除自身外若仍存在占用该位次的标签，抛异常触发事务回滚
     */
    private void assertSortUnique(Integer sort, Long tagId)
    {
        if (tagMapper.countBySort(sort, tagId) > 0)
        {
            throw new ServiceException("显示位次 " + sort + " 已被占用，位次必须唯一");
        }
    }

    @Override
    @Transactional
    public int deleteTagByIds(Long[] tagIds)
    {
        // 位次维护前先串行化同类写入，避免并发下位次重复
        tagMapper.lockSortScope();

        // 校验用去重后的 ID，避免重复入参触发重复校验与重复紧缩
        java.util.Set<Long> uniqueIds = new java.util.LinkedHashSet<>(java.util.Arrays.asList(tagIds));
        for (Long tagId : uniqueIds)
        {
            // 删除前校验：被作品引用的标签不允许删除，避免 sys_work_tag 留下孤儿关联、作品静默丢标签
            int workCount = tagMapper.countWorkByTagId(tagId);
            if (workCount > 0)
            {
                throw new ServiceException(
                        "该标签已被 " + workCount + " 部作品使用，不允许删除；请先解除作品标签");
            }
        }

        // 记录被删位次（去重：重复 ID 若各紧缩一次会把其后元素多减一位，造成位次错乱）
        java.util.Set<Integer> deletedSorts = new java.util.HashSet<>();
        for (Long tagId : uniqueIds)
        {
            SysTag db = tagMapper.selectTagById(tagId);
            if (db != null && db.getSort() != null)
            {
                deletedSorts.add(db.getSort());
            }
        }

        int rows = tagMapper.deleteTagByIds(tagIds);

        // 删除后紧缩：按被删位次从大到小依次前移其后元素，保证位次连续无空洞
        List<Integer> orderedDeletedSorts = new ArrayList<>(deletedSorts);
        orderedDeletedSorts.sort(Comparator.reverseOrder());
        for (Integer deletedSort : orderedDeletedSorts)
        {
            tagMapper.shiftSortAfterDelete(deletedSort);
        }
        return rows;
    }
}
