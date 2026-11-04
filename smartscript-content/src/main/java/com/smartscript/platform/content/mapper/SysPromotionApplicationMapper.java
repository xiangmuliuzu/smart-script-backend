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

    /** 时间窗冲突数（排除自身） */
    int countConflicts(@Param("slotType") String slotType, @Param("slotNo") Integer slotNo,
            @Param("startTime") Date startTime, @Param("endTime") Date endTime,
            @Param("now") Date now, @Param("excludeId") Long excludeId);

    int insertApplication(SysPromotionApplication app);

    int updateAudit(SysPromotionApplication app);

    int updatePaid(@Param("applyId") Long applyId, @Param("payTime") Date payTime);

    int cancel(@Param("applyId") Long applyId, @Param("updateBy") String updateBy);

    /** 本月已支付金额（按资源位） */
    java.math.BigDecimal selectMonthRevenue(@Param("slotType") String slotType);

    int countPending(@Param("slotType") String slotType);

    /** 当前生效的轮播投放（paid 且处于投放时间窗、作品在架），用于首页轮播派生展示 */
    List<SysPromotionApplication> selectActiveBannerApplications(@Param("now") Date now);

    /** 指定资源位当前生效的付费投放（按位次升序），用于搜索页/热门精选按坑位合成 */
    List<SysPromotionApplication> selectActiveSlotApplications(@Param("slotType") String slotType,
            @Param("now") Date now);
}
