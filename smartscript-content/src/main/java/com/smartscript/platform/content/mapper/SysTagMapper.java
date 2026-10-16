package com.smartscript.platform.content.mapper;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.SysTag;
import com.smartscript.platform.content.dto.AppWorkTagDto;

/**
 * 标签 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_tag 表。
 *
 * @author xiangsipeng
 */
public interface SysTagMapper
{
    /**
     * 查询标签列表
     *
     * @param query 查询条件（tagName 模糊、tagType/status 精确，按需传入）
     * @return 标签集合
     */
    public List<SysTag> selectTagList(SysTag query);

    /**
     * App 侧标签列表（只读）
     *
     * 固定条件：status = '0'（'0'=正常 '1'=停用，与标签管理页同口径）；
     * 按 sort 升序（位次决定展示顺序，不再由 use_count 热度覆盖位次）。
     * categoryId 有值时只返回该分类下已上架作品关联过的标签（接口文档表 2-126 的 category_id）。
     *
     * @param tagType    标签类型（可选）
     * @param categoryId 分类ID（可选，按分类收敛标签）
     * @return 标签集合
     */
    public List<SysTag> selectAppTagList(@Param("tagType") String tagType, @Param("categoryId") Long categoryId);

    /**
     * 按作品批量查询关联标签（App 侧只读）
     *
     * 固定条件：仅启用标签（sys_tag.status = '0'）；按 sort 升序（展示顺序）。
     * 由调用方收集作品ID一次批量查询后按 workId 分组回填，避免逐作品查询造成 N+1。
     *
     * @param workIds 作品ID集合（不可为空）
     * @return 作品-标签关联行集合
     */
    public List<AppWorkTagDto> selectWorkTagRows(@Param("workIds") Collection<Long> workIds);

    /**
     * 清空某作品的全部标签关联（作品标签全量覆盖的第一步）
     *
     * 接口文档 2.3.3：tagIds 为全量覆盖语义，故先删后插。
     *
     * @param workId 作品ID
     * @return 删除行数
     */
    public int deleteWorkTagByWorkId(@Param("workId") Long workId);

    /**
     * 批量写入作品标签关联
     *
     * @param workId   作品ID
     * @param tagIds   标签ID集合（不可为空，调用方需先去重）
     * @param createBy 操作人（当前登录用户ID）
     * @return 插入行数
     */
    public int batchInsertWorkTag(@Param("workId") Long workId, @Param("tagIds") Collection<Integer> tagIds,
            @Param("createBy") String createBy);

    /**
     * 通过标签ID查询标签
     *
     * @param tagId 标签ID
     * @return 标签对象
     */
    public SysTag selectTagById(Long tagId);

    /**
     * 校验标签名称是否唯一
     *
     * @param tagName 标签名称
     * @return 命中的标签对象（无则 null）
     */
    public SysTag checkTagNameUnique(String tagName);

    /**
     * 新增标签
     *
     * @param tag 标签
     * @return 影响行数
     */
    public int insertTag(SysTag tag);

    /**
     * 修改标签（动态 set，仅更新非空字段）
     *
     * @param tag 标签
     * @return 影响行数
     */
    public int updateTag(SysTag tag);

    /**
     * 批量删除标签（物理删除，无文档依据，反推：若依原生 sys_post 同为物理删除）
     *
     * @param tagIds 需要删除的标签ID数组
     * @return 影响行数
     */
    public int deleteTagByIds(Long[] tagIds);

    /**
     * 统计有多少作品引用了该标签（sys_work_tag）
     *
     * 删除前校验用：被引用时不允许删除，避免 sys_work_tag 留下孤儿关联、作品静默丢标签。
     * 仅统计未删除作品（is_deleted = 0），与 use_count 口径一致。
     *
     * @param tagId 标签ID
     * @return 引用该标签的作品数量
     */
    public int countWorkByTagId(@Param("tagId") Long tagId);

    /**
     * 前移重排：sort 在 [newSort, oldSort-1] 的其他标签 +1
     *
     * excludeId 为自身标签ID；新增场景该位次尚无自身行，传 null 时不追加排除条件。
     */
    public int shiftTagSortForward(@Param("newSort") int newSort,
                                   @Param("oldSort") int oldSort,
                                   @Param("excludeId") Long excludeId);

    /**
     * 后移重排：sort 在 [oldSort+1, newSort] 的其他标签 -1
     *
     * excludeId 说明同 {@link #shiftTagSortForward}。
     */
    public int shiftTagSortBackward(@Param("newSort") int newSort,
                                    @Param("oldSort") int oldSort,
                                    @Param("excludeId") Long excludeId);

    /**
     * 当前最大位次
     *
     * 新增标签时以「最大位次 + 1」作为默认位次，保证追加到末尾且不与既有位次重复。
     *
     * @return 最大 sort；表中无标签时返回 null
     */
    public Integer selectMaxSort();

    /**
     * 统计占用指定位次的其它标签数
     *
     * 位次唯一性强校验用：excludeId 传自身标签ID以排除自己；结果 > 0 即表示位次重复，
     * 调用方（Service）应据此抛异常回滚，绝不落库重复位次。
     *
     * @param sort      位次
     * @param excludeId 需排除的标签ID（可为 null）
     * @return 占用该位次的标签数
     */
    public int countBySort(@Param("sort") int sort, @Param("excludeId") Long excludeId);

    /**
     * 删除后紧缩：把位次大于 deletedSort 的标签整体前移一位，消除删除留下的位次空洞
     *
     * 与书城「编辑推荐」移除推荐位后的重排同规则（被删位次之后的元素统一前移一位），
     * 保证位次始终「1..N 稠密无空洞」；批量删除时由 Service 按被删位次从大到小依次调用。
     *
     * @param deletedSort 被删除标签的原位次
     * @return 影响行数
     */
    public int shiftSortAfterDelete(@Param("deletedSort") int deletedSort);

    /**
     * 全量刷新标签使用数量（use_count）
     *
     * 口径：use_count = 该标签在 sys_work_tag 中被「未删除作品（sys_work.is_deleted=0）」引用的行数；
     * 无引用的标签置 0。
     *
     * 触发时机：作品标签被全量覆盖（设置/清空）、作品被逻辑删除——标签引用随之变化的写入路径。
     * 用全量刷新而非增量加减，避免并发/漏算导致计数漂移；标签表数据量小，整表刷新成本可忽略。
     * 与迁移脚本 B_20261018_003__backfill_tag_use_count.sql 为同一口径。
     *
     * @return 影响的标签行数
     */
    public int syncTagUseCount();

    /**
     * 锁定位次写入作用域（串行化并发位次维护）
     *
     * 位次是整表共享的槽位序列，并发下「判占用 → 腾位 → 写入」的中间结果彼此不可见，
     * 会导致两个事务都通过写入后唯一校验而落库重复位次；本方法在事务内对该表行加排他锁，
     * 使同一时刻只有一个位次维护事务在跑（调用方均已 {@code @Transactional}）。
     *
     * @return 全部标签ID（仅取锁语义，调用方忽略返回值）
     */
    public List<Long> lockSortScope();
}
