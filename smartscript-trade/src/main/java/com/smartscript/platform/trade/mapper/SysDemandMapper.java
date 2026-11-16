package com.smartscript.platform.trade.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.trade.domain.SysDemand;

public interface SysDemandMapper
{
    List<SysDemand> selectDemandList(SysDemand demand);

    /** APP 任务广场真分页：走 SQL LIMIT/OFFSET + 白名单排序（query 内需设 offset/limit/sortBy/sortDir）。 */
    List<SysDemand> selectDemandPage(SysDemand demand);

    /** 与 selectDemandPage 成对的独立总数（同 where）。 */
    long countDemandList(SysDemand demand);

    /** Hero 轻量统计（广场口径 status=open）：availableCount/prizePoolYuan/todayNewCount。 */
    java.util.Map<String, Object> selectDemandSquareStats();

    /** 题材分类数量角标：公开可接单口径（status=open），与广场列表同口径。 */
    List<java.util.Map<String, Object>> selectGenreCounts();

    /** 任务类型数量角标：与 selectGenreCounts 同口径。 */
    List<java.util.Map<String, Object>> selectTypeCounts();

    /** 发布分镜任务选择源剧本：创作者本人未删除作品列表（workId/title/status/updateTime）。 */
    List<java.util.Map<String, Object>> selectSourceWorks(@Param("authorId") Long authorId);

    SysDemand selectDemandById(Long demandId);

    /** 行锁读取（demand_id/client_id/status/task_type/源剧本三列）：编辑任务与抢单串行化用，须在事务内调用。 */
    SysDemand selectDemandForUpdate(Long demandId);
    int insertDemand(SysDemand demand);

    /** 编辑任务：仅更新非空可编辑字段（由 service 保证仅 open 未接单时可调） */
    int updateDemand(SysDemand demand);
    int updateStatus(@Param("demandId") Long demandId, @Param("status") String status, @Param("updateBy") String updateBy);

    /** 状态 CAS：仅当当前状态仍为 expectStatus 时生效；返回 0 表示已被并发变更 */
    int updateStatusCas(@Param("demandId") Long demandId, @Param("status") String status,
                        @Param("updateBy") String updateBy, @Param("expectStatus") String expectStatus);
    int incrementSubmissionCount(@Param("demandId") Long demandId);

    /** 抢单：仅当任务仍为 open 时原子锁定；返回受影响行数（0 表示已被抢） */
    int claimDemand(@Param("demandId") Long demandId, @Param("creatorId") Long creatorId,
                    @Param("acceptedAt") Date acceptedAt, @Param("dueAt") Date dueAt,
                    @Param("updateBy") String updateBy);

    /** 状态与失败信息流转：仅更新非空字段（status/failReason） */
    int updateStatusAndFailReason(@Param("demandId") Long demandId, @Param("status") String status,
                        @Param("failReason") String failReason);

    /** 镜像当前活动轮次的截止/逾期到 sys_demand.due_at/is_overdue */
    int mirrorRoundState(@Param("demandId") Long demandId, @Param("dueAt") Date dueAt, @Param("overdue") Integer overdue);

    /** 逾期标记 */
    int markOverdue(@Param("demandId") Long demandId);

    /**
     * 超时失败终止（条件更新 CAS）：仅当任务仍为 in_progress 才落失败态。
     * 读路径惰性判定与定时扫描可能同时命中同一任务，靠本条件保证只终止一次；
     * 返回 0 表示已被并发处理，调用侧据此跳过重复通知。
     */
    int failDemandIfInProgress(@Param("demandId") Long demandId,
                        @Param("status") String status,
                        @Param("failReason") String failReason);
}
