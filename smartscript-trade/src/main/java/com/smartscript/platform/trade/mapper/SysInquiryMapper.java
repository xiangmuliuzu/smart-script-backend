package com.smartscript.platform.trade.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.trade.domain.SysInquiry;

public interface SysInquiryMapper
{
    List<SysInquiry> selectInquiryList(SysInquiry inquiry);
    SysInquiry selectInquiryById(Long inquiryId);
    int insertInquiry(SysInquiry inquiry);
    int updateInquiry(SysInquiry inquiry);
    /**
     * CAS 状态流转：仅当当前 status 属于 allowedFrom 时才更新为 target，返回影响行数（0 表示被并发抢先）。
     * 替代「先读校验、再按主键无条件写」的旧流程，消除并发覆盖窗口（问题5）。
     */
    int updateStatusCas(@Param("inquiryId") Long inquiryId,
                        @Param("target") String target,
                        @Param("allowedFrom") List<String> allowedFrom,
                        @Param("updateBy") String updateBy);
    /** P1-08：批量将 expire_at 已过期且非终态的询盘置 closed，返回影响行数 */
    int closeExpiredInquiries();
}
