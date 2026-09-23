package com.smartscript.platform.trade.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.trade.domain.SysQuote;
import com.smartscript.platform.trade.mapper.SysQuoteMapper;

/**
 * C module: quote/negotiation query service.
 */
@Service
public class TradeQuoteService
{
    @Autowired
    private SysQuoteMapper quoteMapper;

    public List<SysQuote> selectQuoteList(SysQuote quote)
    {
        return quoteMapper.selectQuoteList(quote);
    }

    public SysQuote selectQuoteById(Long quoteId)
    {
        return quoteMapper.selectQuoteById(quoteId);
    }
}
