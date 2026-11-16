package com.smartscript.platform.content.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 阅读记录 数据层（sys_read_record，附件5.1 表3-31「阅读记录表」）。
 *
 * 用途：记录带时间戳的阅读事件（阅读行为统计）。
 * 热门榜已改为按累计 view_count 排序，不再从本表取 7 天窗口数据。
 * 库中该表已存在（无需改表结构），此前仅迁移/种子 SQL 引用，故本轮补写埋点。
 *
 * 反推处理点：
 * 1. 表无 (user_id, chapter_id) 唯一约束，幂等由 insertReadRecordIfAbsent 的
 *    WHERE NOT EXISTS 兜底（写法与 AppFavoriteMapper.insertFavoriteIfAbsent 一致，
 *    已在真实库验证：首次影响 1 行、重复影响 0 行）。
 * 2. read_duration / read_progress 为 NOT NULL，正文下发时刻无时长/进度可测，
 *    统一写 0 / 0.00；created_at / create_time 显式 now()，不依赖库默认值差异。
 * 3. 带去重的口径为「同一用户当天对同一章节只记一条」，避免客户端重复刷新把
 *    7 天窗口阅读量刷高，同时保证跨天复读仍能被窗口统计到。
 *
 * @author xiangsipeng
 */
public interface AppReadRecordMapper
{
    /**
     * 幂等新增阅读记录：同一用户当天已读过同一章节时不插入
     *
     * @param userId    阅读人ID（当前登录身份；游客不写）
     * @param workId    作品ID
     * @param chapterId 章节ID
     * @return 影响行数（1=新增成功，0=当天已记录过）
     */
    public int insertReadRecordIfAbsent(@Param("userId") Long userId,
                                        @Param("workId") Long workId,
                                        @Param("chapterId") Long chapterId);
}
