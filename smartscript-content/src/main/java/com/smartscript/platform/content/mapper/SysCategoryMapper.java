package com.smartscript.platform.content.mapper;

import java.util.List;
import com.smartscript.platform.content.domain.SysCategory;

/**
 * 剧本分类 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_category 表。
 *
 * @author xiangsipeng
 */
public interface SysCategoryMapper
{
    /**
     * 查询剧本分类列表
     *
     * @param query 查询条件（categoryName 模糊、categoryType/status 精确，按需传入）
     * @return 剧本分类集合
     */
    public List<SysCategory> selectCategoryList(SysCategory query);

    /**
     * 通过分类ID查询剧本分类
     *
     * @param categoryId 分类ID
     * @return 剧本分类对象
     */
    public SysCategory selectCategoryById(Long categoryId);

    /**
     * 校验分类名称是否唯一
     *
     * @param categoryName 分类名称
     * @return 命中的分类对象（无则 null）
     */
    public SysCategory checkCategoryNameUnique(String categoryName);

    /**
     * 新增剧本分类
     *
     * @param category 剧本分类
     * @return 影响行数
     */
    public int insertCategory(SysCategory category);

    /**
     * 修改剧本分类（动态 set，仅更新非空字段；也用于改状态/改排序）
     *
     * @param category 剧本分类
     * @return 影响行数
     */
    public int updateCategory(SysCategory category);

    /**
     * 批量删除剧本分类（物理删除）
     *
     * @param categoryIds 分类ID数组
     * @return 影响行数
     */
    public int deleteCategoryByIds(Long[] categoryIds);

    /**
     * 统计有多少作品引用了该分类（genre_id）
     *
     * @param categoryId 分类ID
     * @return 作品数量
     */
    public int countWorkByCategoryId(Long categoryId);

    /**
     * 前移重排：把 sort 在 [newSort, oldSort-1] 范围内的其他分类 +1
     * （当前分类从 oldSort 移到更靠前的 newSort）
     *
     * excludeId 为自身分类ID；新增场景该位次尚无自身行，传 null 时不追加排除条件。
     */
    public int shiftSortForward(@org.apache.ibatis.annotations.Param("newSort") int newSort,
                                 @org.apache.ibatis.annotations.Param("oldSort") int oldSort,
                                 @org.apache.ibatis.annotations.Param("excludeId") Long excludeId);

    /**
     * 后移重排：把 sort 在 [oldSort+1, newSort] 范围内的其他分类 -1
     * （当前分类从 oldSort 移到更靠后的 newSort）
     *
     * excludeId 说明同 {@link #shiftSortForward}。
     */
    public int shiftSortBackward(@org.apache.ibatis.annotations.Param("newSort") int newSort,
                                  @org.apache.ibatis.annotations.Param("oldSort") int oldSort,
                                  @org.apache.ibatis.annotations.Param("excludeId") Long excludeId);

    /**
     * 当前最大位次
     *
     * 新增分类时以「最大位次 + 1」作为默认位次，保证追加到末尾且不与既有位次重复。
     *
     * @return 最大 sort；表中无分类时返回 null
     */
    public Integer selectMaxSort();

    /**
     * 统计占用指定位次的其它分类数
     *
     * 位次唯一性强校验用：excludeId 传自身分类ID以排除自己；结果 > 0 即表示位次重复，
     * 调用方（Service）应据此抛异常回滚，绝不落库重复位次。
     *
     * @param sort      位次
     * @param excludeId 需排除的分类ID（可为 null）
     * @return 占用该位次的分类数
     */
    public int countBySort(@org.apache.ibatis.annotations.Param("sort") int sort,
                           @org.apache.ibatis.annotations.Param("excludeId") Long excludeId);

    /**
     * 删除后紧缩：把位次大于 deletedSort 的分类整体前移一位，消除删除留下的位次空洞
     *
     * 与书城「编辑推荐」移除推荐位后的重排同规则（被删位次之后的元素统一前移一位），
     * 保证位次始终「1..N 稠密无空洞」；批量删除时由 Service 按被删位次从大到小依次调用。
     *
     * @param deletedSort 被删除分类的原位次
     * @return 影响行数
     */
    public int shiftSortAfterDelete(@org.apache.ibatis.annotations.Param("deletedSort") int deletedSort);

    /**
     * 统计以该分类为父级的子分类数
     *
     * 删除前校验用：存在子分类时不允许删除，避免子分类 parent_id 悬空。
     *
     * @param categoryId 分类ID
     * @return 子分类数量
     */
    public int countChildrenByParentId(@org.apache.ibatis.annotations.Param("categoryId") Long categoryId);

    /**
     * 锁定位次写入作用域（串行化并发位次维护）
     *
     * 位次是整表共享的槽位序列，并发下「判占用 → 腾位 → 写入」的中间结果彼此不可见，
     * 会导致两个事务都通过写入后唯一校验而落库重复位次；本方法在事务内对该表行加排他锁，
     * 使同一时刻只有一个位次维护事务在跑（调用方均已 {@code @Transactional}）。
     *
     * @return 全部分类ID（仅取锁语义，调用方忽略返回值）
     */
    public List<Long> lockSortScope();
}
