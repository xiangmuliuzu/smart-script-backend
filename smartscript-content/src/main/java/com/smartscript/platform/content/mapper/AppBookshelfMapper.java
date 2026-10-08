package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.dto.AppWorkDto;

/**
 * 书架 数据层（App 书城 2.7.12 书架管理）
 *
 * 依据：云端 script_platform_dev 库 sys_bookshelf_record 表（附件5.1 表3-32）+
 * 接口文档表 2-92（GET/POST/DELETE /api/bookshelf）。
 *
 * 反推处理点：
 * 1. 所有语句强制带 user_id 条件，归属由服务层传入的当前登录身份决定。
 * 2. 表上有唯一索引 uk_user_work(user_id, work_id)（已在真实库核对：non_unique=0），
 *    幂等仍由 insertShelfIfAbsent 的 WHERE NOT EXISTS 兜底，与 AppFavoriteMapper 同一写法。
 * 3. is_deleted 不参与判定：移出书架走物理删除（与 sys_favorite 口径一致），
 *    本层写入的行 is_deleted 恒为 0，故查询/计数无需再过滤该列。
 * 4. last_read_chapter_id / last_read_at（阅读进度）不属本批范围，本层不读写。
 *
 * @author xiangsipeng
 */
public interface AppBookshelfMapper
{
    /**
     * 幂等加入书架：已存在 (user_id, work_id) 时不插入
     *
     * @param userId    书架归属人ID（当前登录身份）
     * @param workId    作品ID
     * @param addSource 加入来源（接口文档 shelf_type，缺省 app）
     * @return 影响行数（1=新增成功，0=此前已在书架）
     */
    public int insertShelfIfAbsent(@Param("userId") Long userId, @Param("workId") Long workId,
            @Param("addSource") String addSource);

    /**
     * 移出书架（物理删除；幂等：记录不存在时影响 0 行）
     *
     * @param userId 书架归属人ID
     * @param workId 作品ID
     * @return 影响行数
     */
    public int deleteShelf(@Param("userId") Long userId, @Param("workId") Long workId);

    /**
     * 统计书架关系是否存在
     *
     * @param userId 书架归属人ID
     * @param workId 作品ID
     * @return 记录数（0 或 1）
     */
    public int countShelf(@Param("userId") Long userId, @Param("workId") Long workId);

    /**
     * 书架作品列表（仅上架未删除作品，按加入时间倒序；分页由调用方 PageHelper 驱动）
     *
     * @param userId 书架归属人ID
     * @return 作品集合（AppWorkDto，字段与 2.7.1 作品列表一致）
     */
    public List<AppWorkDto> selectShelfWorks(@Param("userId") Long userId);
}