package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.dto.AppShelfWorkDto;

/**
 * 书架 数据层（App 书城 2.7.12）
 *
 * 语义：书架 = 「已购买 / 已获授权」的作品库，不再支持手工加入普通作品。
 * 列表由 sys_order（已完成）与 sys_copyright_authorization（生效中）派生；
 * sys_bookshelf_record 仅承载阅读进度（last_read_chapter_id / last_read_at）。
 *
 * 反推处理点：
 * 1. 所有语句强制带 user_id 条件，归属由服务层传入的当前登录身份决定。
 * 2. 进度写入依赖唯一索引 uk_user_work(user_id, work_id)，用 ON DUPLICATE KEY 保证幂等。
 * 3. 列表不过滤 sys_work.status：已付费作品被运营下架后仍保留在书架，仅排除逻辑删除。
 *
 * @author xiangsipeng
 */
public interface AppBookshelfMapper
{
    /**
     * 已购作品列表（按最近成交时间倒序；分页由调用方 PageHelper 驱动）
     *
     * @param userId 当前登录用户ID
     * @return 作品集合（AppShelfWorkDto，字段为书城作品字段 + 阅读进度）
     */
    public List<AppShelfWorkDto> selectShelfWorks(@Param("userId") Long userId);

    /**
     * 记录阅读进度（upsert：首次阅读自动建立进度行，已存在则覆盖）
     *
     * @param userId    当前登录用户ID
     * @param workId    作品ID
     * @param chapterId 最近阅读章节ID
     * @return 影响行数（新增 1 / 更新 2）
     */
    public int saveProgress(@Param("userId") Long userId, @Param("workId") Long workId,
            @Param("chapterId") Long chapterId);
}
