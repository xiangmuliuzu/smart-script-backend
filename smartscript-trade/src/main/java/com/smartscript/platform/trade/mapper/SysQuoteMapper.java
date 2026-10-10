package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysQuote;

public interface SysQuoteMapper
{
    List<SysQuote> selectQuoteList(SysQuote quote);
    SysQuote selectQuoteById(Long quoteId);
    int insertQuote(SysQuote quote);
    int updateQuote(SysQuote quote);
}
