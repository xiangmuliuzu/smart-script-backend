package com.smartscript.platform.trade.service;

import java.util.List;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.trade.domain.SysQuote;
import com.smartscript.platform.trade.mapper.SysQuoteMapper;

/**
 * C module: quote/negotiation service.
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

    public int insertQuote(SysQuote quote)
    {
        return quoteMapper.insertQuote(quote);
    }

    public int updateQuoteStatus(Long quoteId, String status)
    {
        SysQuote quote = new SysQuote();
        quote.setQuoteId(quoteId);
        quote.setStatus(status);
        quote.setUpdateTime(new Date());
        return quoteMapper.updateQuote(quote);
    }
}
