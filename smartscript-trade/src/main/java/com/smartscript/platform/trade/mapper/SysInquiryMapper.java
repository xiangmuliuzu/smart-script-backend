package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysInquiry;

public interface SysInquiryMapper
{
    List<SysInquiry> selectInquiryList(SysInquiry inquiry);
    SysInquiry selectInquiryById(Long inquiryId);
    int insertInquiry(SysInquiry inquiry);
    int updateInquiry(SysInquiry inquiry);
}
