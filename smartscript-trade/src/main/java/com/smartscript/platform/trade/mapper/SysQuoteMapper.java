package com.smartscript.platform.trade.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.trade.domain.SysQuote;

public interface SysQuoteMapper
{
    List<SysQuote> selectQuoteList(SysQuote quote);
    SysQuote selectQuoteById(Long quoteId);
    int insertQuote(SysQuote quote);
    int updateQuote(SysQuote quote);

    /** 状态 CAS 写回：仅当当前状态仍为 expectStatus 时生效；返回 0 表示已被并发处理 */
    int updateQuoteStatusCas(@Param("quoteId") Long quoteId,
                             @Param("status") String status,
                             @Param("expectStatus") String expectStatus,
                             @Param("updateBy") String updateBy);
}
