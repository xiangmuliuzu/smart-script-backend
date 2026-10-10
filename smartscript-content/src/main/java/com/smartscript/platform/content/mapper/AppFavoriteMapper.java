package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.dto.AppWorkDto;

/**
 * 收藏 数据层（App 书城 2.7.10 收藏/取消收藏、2.7.11 收藏列表）
 *
 * 依据：云端 script_platform_dev 库 sys_favorite 表（附件5.1 表3-28）+ 接口文档表 2-90 / 2-91。
 *
 * 反推处理点：
 * 1. 所有语句强制带 user_id 条件，归属由服务层传入的当前登录身份决定。
 * 2. sys_favorite 仅有 PRIMARY(id) 与 user_id / work_id 单列索引，无 (user_id, work_id)
 *    唯一约束，故幂等由 insertFavoriteIfAbsent 的 WHERE NOT EXISTS 兜底（已在真实库验证：
 *    首次影响 1 行、重复影响 0 行）。
 * 3. sys_work.favorite_count 由库内触发器 trg_sys_favorite_ai_work_count /
 *    trg_sys_favorite_ad_work_count 自动维护，本层不写该列。
 *
 * @author xiangsipeng
 */
public interface AppFavoriteMapper
{
    /**
     * 幂等新增收藏：已存在 (user_id, work_id) 时不插入
     *
     * @param userId 收藏人ID（当前登录身份）
     * @param workId 作品ID
     * @return 影响行数（1=新增成功，0=此前已收藏）
     */
    public int insertFavoriteIfAbsent(@Param("userId") Long userId, @Param("workId") Long workId);

    /**
     * 取消收藏（幂等：记录不存在时影响 0 行）
     *
     * @param userId 收藏人ID
     * @param workId 作品ID
     * @return 影响行数
     */
    public int deleteFavorite(@Param("userId") Long userId, @Param("workId") Long workId);

    /**
     * 统计收藏关系是否存在
     *
     * @param userId 收藏人ID
     * @param workId 作品ID
     * @return 记录数（0 或 1）
     */
    public int countFavorite(@Param("userId") Long userId, @Param("workId") Long workId);

    /**
     * 收藏的作品列表（仅上架未删除作品，按收藏时间倒序；分页由调用方 PageHelper 驱动）
     *
     * @param userId 收藏人ID
     * @return 作品集合（AppWorkDto，字段与 2.7.1 作品列表一致）
     */
    public List<AppWorkDto> selectFavoriteWorks(@Param("userId") Long userId);
}