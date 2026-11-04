package com.smartscript.platform.trade.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.trade.domain.SysDemandExtendApply;

/**
 * C module: demand stage extend application history mapper.
 * Every application is a new row; decided rows are kept as history.
 */
public interface SysDemandExtendApplyMapper
{
    /** 新增一条延时记录（request 落 pending / self 落 approved），回填自增 applyId */
    int insert(SysDemandExtendApply apply);

    /** 锁定轮次行（FOR UPDATE）：串行化同轮并发申请，保证同时只有一个 pending */
    Long lockStage(@Param("stageId") Long stageId);

    /** 查某轮当前待处理申请（最多一条），按申请时间倒序取最新 */
    SysDemandExtendApply selectPendingByStageId(@Param("stageId") Long stageId);

    /**
     * 定时扫描用：发布者超过 hours 小时未处理的申请延时（仅 apply_type=request、仍 pending）。
     * 自主延时写入即生效、不存在待处理态，所以按类型排除。
     */
    List<SysDemandExtendApply> selectStalePendingApplies(@Param("hours") int hours);

    /**
     * 处理申请：仅当仍是 pending 才落决定（条件更新，防并发重复处理）。
     * @param dueAfter 同意时的新截止时间，驳回传 null
     * @return 1=本次处理生效，0=已被处理过
     */
    int decide(@Param("applyId") Long applyId,
               @Param("status") String status,
               @Param("decidedBy") Long decidedBy,
               @Param("dueAfter") Date dueAfter);

    /** 某轮全部延时记录（申请延时 + 自主延时），按申请时间倒序 */
    List<SysDemandExtendApply> selectByStageId(@Param("stageId") Long stageId);

    /** 某任务全部延时记录（申请延时 + 自主延时，含各轮），按申请时间倒序，附带轮次序号 */
    List<SysDemandExtendApply> selectByDemandId(@Param("demandId") Long demandId);
}
