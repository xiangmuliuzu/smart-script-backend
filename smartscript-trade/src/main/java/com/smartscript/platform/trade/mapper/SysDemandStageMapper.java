package com.smartscript.platform.trade.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.trade.domain.SysDemandStage;

/**
 * C module: demand acceptance stage mapper.
 */
public interface SysDemandStageMapper
{
    int insertBatch(@Param("list") List<SysDemandStage> stages);

    /** 编辑任务重建轮次：删除该任务现有全部轮次（仅未托管未接单时可调用） */
    int deleteByDemandId(@Param("demandId") Long demandId);

    List<SysDemandStage> selectByDemandId(@Param("demandId") Long demandId);

    SysDemandStage selectByStageId(@Param("stageId") Long stageId);

    int updateDeliverable(@Param("stageId") Long stageId,
                          @Param("status") String status,
                          @Param("deliverableDesc") String deliverableDesc,
                          @Param("deliverableUrl") String deliverableUrl,
                          @Param("workId") Long workId,
                          @Param("updateBy") String updateBy);

    /**
     * 人工验收写回（通过/驳回）：仅当本轮仍为 submitted 时生效。
     *
     * @return 影响行数；0 表示本轮已被并发路径处理完，调用方必须放弃放款等后续副作用
     */
    int updateReview(@Param("stageId") Long stageId,
                     @Param("status") String status,
                     @Param("rejectReason") String rejectReason,
                     @Param("releaseFlag") Integer releaseFlag,
                     @Param("updateBy") String updateBy);

    /**
     * AI 审核完成写回：落分数/结论/完成时间并流转状态（submitted=进人工验收 / rejected=自动打回）。
     * 仅当该轮仍为 ai_reviewing 时生效，返回影响行数（0 表示已被处理）。
     */
    int updateAiReview(@Param("stageId") Long stageId,
                       @Param("status") String status,
                       @Param("aiScore") Integer aiScore,
                       @Param("aiResult") String aiResult,
                       @Param("rejectReason") String rejectReason,
                       @Param("updateBy") String updateBy);

    /** 启动恢复用：全部卡在 ai_reviewing 的轮次 */
    List<SysDemandStage> selectAiReviewingStages();

    /**
     * 定时扫描用：全部「已过本轮截止且仍在计时」的活动轮（任务跨全库，不限单一 demand）。
     * 口径与 selectActiveStage + refreshRoundState 完全一致：
     * 只取任务 in_progress 的活动轮，排除已提交/AI 审核中（计时已暂停）与有待处理延时申请（同样暂停）的轮次。
     */
    List<SysDemandStage> selectOverdueActiveStages();

    /** AI 审核取文：交付作品在本轮验收集数内的章节正文列表（原始富文本，服务层转纯文本） */
    List<String> selectChapterContents(@Param("workId") Long workId,
                                       @Param("episodeTo") Integer episodeTo);

    /** 平台验收查看：交付作品概要（不含正文） */
    Map<String, Object> selectDeliveredWorkBrief(@Param("workId") Long workId);

    /**
     * 平台验收查看：交付作品章节目录，正文按本轮集数截断。
     *
     * 一章即一集（sys_work.episode_count 由 sys_work_chapter 行数汇总），episodeTo 直接当可读上界；
     * 截断放在 SQL 的 CASE 里而不是服务层循环，避免后续改动漏判把未交付轮次的全文发给发布者。
     */
    List<Map<String, Object>> selectChaptersForReview(@Param("workId") Long workId,
                                                      @Param("episodeTo") Integer episodeTo);

    /** 激活某轮：写 started_at/due_at，清零逾期 */
    int activateStage(@Param("stageId") Long stageId,
                      @Param("startedAt") Date startedAt,
                      @Param("dueAt") Date dueAt,
                      @Param("updateBy") String updateBy);

    /** 本轮逾期标记 */
    int markStageOverdue(@Param("stageId") Long stageId);

    /** 当前活动轮（已激活且未通过，取最小 stage_order） */
    SysDemandStage selectActiveStage(@Param("demandId") Long demandId);

    /** 下一待激活轮（stage_order > prevOrder 最小者） */
    SysDemandStage selectNextPending(@Param("demandId") Long demandId, @Param("prevOrder") Integer prevOrder);

    /** 顺延某轮截止时间（驳回恢复计时/延期批准/发布者自主延期共用），并清零逾期标记 */
    int pushDue(@Param("stageId") Long stageId,
                @Param("dueAt") Date dueAt,
                @Param("updateBy") String updateBy);

    /** 创作者发起延期申请：写最近一次申请快照（列表/审核入口用），历史另存 sys_demand_extend_apply */
    int requestExtend(@Param("stageId") Long stageId,
                      @Param("days") Integer days,
                      @Param("hours") Integer hours,
                      @Param("reason") String reason,
                      @Param("updateBy") String updateBy);

    /** 发布者处理延期申请：更新快照状态，dueAt 非空时同步顺延截止（批准），为空仅落决定（驳回） */
    int decideExtend(@Param("stageId") Long stageId,
                     @Param("extendStatus") String extendStatus,
                     @Param("dueAt") Date dueAt,
                     @Param("updateBy") String updateBy);

    /** 交付校验用：读作品归属与上架状态（workId/authorId/status/title） */
    Map<String, Object> selectWorkBrief(@Param("workId") Long workId);

    /** 可交付作品列表：本人的、非已上架售卖（status<>'on_shelf'）且未删除的作品 */
    List<Map<String, Object>> selectDeliverableWorks(@Param("authorId") Long authorId);
}
