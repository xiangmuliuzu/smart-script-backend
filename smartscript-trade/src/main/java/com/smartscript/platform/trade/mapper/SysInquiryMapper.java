package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysInquiry;

public interface SysInquiryMapper
{
    List<SysInquiry> selectInquiryList(SysInquiry inquiry);
    SysInquiry selectInquiryById(Long inquiryId);
    int insertInquiry(SysInquiry inquiry);
    int updateInquiry(SysInquiry inquiry);
    /** P1-08：批量将 expire_at 已过期且非终态的询盘置 closed，返回影响行数 */
    int closeExpiredInquiries();
}
