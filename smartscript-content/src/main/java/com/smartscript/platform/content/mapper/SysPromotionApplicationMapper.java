package com.smartscript.platform.content.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.SysPromotionApplication;

/** 推广申请 / 投放记录 */
public interface SysPromotionApplicationMapper
{
    List<SysPromotionApplication> selectApplicationList(SysPromotionApplication query);

    SysPromotionApplication selectApplicationById(@Param("applyId") Long applyId);

    /** 某资源位下所有未结束的占用（paid 全部 + approved 且锁坑未过期） */
    List<SysPromotionApplication> selectOpenOccupancies(@Param("slotType") String slotType, @Param("now") Date now);

    /** 时间窗冲突数（排除自身；仅比较同锁定类型的投放：锁定单只拦锁定单，兜底单只拦兜底单） */
    int countConflicts(@Param("slotType") String slotType, @Param("slotNo") Integer slotNo,
            @Param("startTime") Date startTime, @Param("endTime") Date endTime,
            @Param("now") Date now, @Param("excludeId") Long excludeId,
            @Param("exclusive") Integer exclusive);

    int insertApplication(SysPromotionApplication app);

    int updateAudit(SysPromotionApplication app);

    int updatePaid(@Param("applyId") Long applyId, @Param("payTime") Date payTime);

    int cancel(@Param("applyId") Long applyId, @Param("updateBy") String updateBy);

    /** 切换锁定标记（仅投放中的记录） */
    int updateExclusive(@Param("applyId") Long applyId, @Param("exclusive") Integer exclusive,
            @Param("updateBy") String updateBy);

    /** 自动下架：生效中的不锁定投放若与生效中的锁定投放重叠，置为已下架 */
    int cancelCoveredFree(@Param("now") Date now);

    /** 本月已支付金额（按资源位） */
    java.math.BigDecimal selectMonthRevenue(@Param("slotType") String slotType);

    int countPending(@Param("slotType") String slotType);

    /** 当前生效的轮播投放（paid 且处于投放时间窗、作品在架），用于首页轮播派生展示 */
    List<SysPromotionApplication> selectActiveBannerApplications(@Param("now") Date now);

    /** 指定资源位当前生效的付费投放（按位次升序），用于搜索页/热门精选按坑位合成 */
    List<SysPromotionApplication> selectActiveSlotApplications(@Param("slotType") String slotType,
            @Param("now") Date now);
}
