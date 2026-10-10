package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.SysWorkReviewRecord;

/**
 * 作品审核记录 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_review_record 表。
 * 供 PC 用户端「作品详情-审核历史」查询与「提交审核」写入使用。
 * 与 smartscript-review 模块的 ReviewRecordMapper 相互独立（避免跨模块依赖）。
 *
 * @author smartscript
 */
public interface SysWorkReviewRecordMapper
{
    /**
     * 按作品ID查询审核记录（target_type = 'work'，按创建时间倒序）
     *
     * @param workId 作品ID
     * @return 审核记录集合（含提交人/审核人昵称）
     */
    public List<SysWorkReviewRecord> selectByWorkId(@Param("workId") Long workId);

    /**
     * 批量查询多部作品的审核记录（用于「我的作品列表」派生状态）
     *
     * 仅取 target_type = 'work' 且在给定作品集合内，按创建时间倒序，
     * 服务层按作品ID分组后取每组第一条即最新记录。
     *
     * @param workIds 作品ID集合（非空）
     * @return 审核记录集合
     */
    public List<SysWorkReviewRecord> selectByWorkIds(@Param("workIds") java.util.Collection<Long> workIds);

    /**
     * 查询作品是否存在审核中记录（防止审核中重复提交）
     *
     * @param workId 作品ID
     * @return 记录数（0=无）
     */
    public int countPendingByWorkId(@Param("workId") Long workId);

    /**
     * 统计当天已生成的审核编号数量（review_no 前缀，如 RV20260920）
     *
     * @param prefix 前缀（RV + yyyyMMdd）
     * @return 当天已有数量
     */
    public int countTodayByPrefix(@Param("prefix") String prefix);

    /**
     * 新增审核记录
     *
     * @param record 审核记录（review_no/targetType/targetId/submitterId/status 必填）
     * @return 影响行数
     */
    public int insertRecord(SysWorkReviewRecord record);
}
