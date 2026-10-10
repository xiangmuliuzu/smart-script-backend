package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.dto.AppSearchHistoryItem;

/**
 * 搜索历史数据层（App 书城 2.7.4 列表 / 2.7.5 删除单条 / 2.7.6 清空）。
 *
 * 依据：云端 script_platform_dev 库 sys_search_history 表（附件5.1 表3-30）。
 *
 * 边界：所有语句都以 user_id 为过滤条件，归属由服务层传入的当前登录身份决定，
 * 调用方无法通过入参读写他人历史。
 *
 * 命名说明：刻意不叫 SysSearchHistoryMapper —— MapperScanner 按类名注册 bean，
 * 后续模块落地同表 Mapper 会与本类冲突（参见 SysContentWorkMapper 的同类问题）。
 *
 * @author xiangsipeng
 */
public interface AppSearchHistoryMapper
{
    /**
     * 查询某用户的搜索历史（按最近搜索时间倒序）
     *
     * @param userId 用户ID
     * @param limit  最多返回条数
     * @return 搜索历史条目集合
     */
    public List<AppSearchHistoryItem> selectHistoryByUser(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 命中原有关键词则累加次数并刷新时间
     *
     * 表上无 (user_id, keyword) 唯一索引（附件5.1 表3-30 索引表只有 idx_user_id / idx_keyword），
     * 故用「先 update 后按影响行数决定 insert」保证重复关键词不产生新行。
     *
     * @param userId  用户ID
     * @param keyword 搜索关键词（已 trim）
     * @return 影响行数（0 表示无同关键词记录）
     */
    public int touchHistory(@Param("userId") Long userId, @Param("keyword") String keyword);

    /**
     * 新增一条搜索历史
     *
     * @param userId  用户ID
     * @param keyword 搜索关键词（已 trim）
     * @return 影响行数
     */
    public int insertHistory(@Param("userId") Long userId, @Param("keyword") String keyword);

    /**
     * 删除某用户的一条搜索历史
     *
     * @param userId 用户ID
     * @param id     历史记录ID
     * @return 影响行数（0 表示记录不存在或不属于该用户）
     */
    public int deleteHistoryById(@Param("userId") Long userId, @Param("id") Long id);

    /**
     * 清空某用户的全部搜索历史
     *
     * @param userId 用户ID
     * @return 删除条数
     */
    public int deleteHistoryByUser(@Param("userId") Long userId);
}