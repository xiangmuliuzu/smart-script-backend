package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysInvoice;

/**
 * 发票 Mapper 接口。
 */
public interface SysInvoiceMapper
{
    List<SysInvoice> selectInvoiceList(SysInvoice query);
    SysInvoice selectInvoiceById(Long invoiceId);
    int insertInvoice(SysInvoice invoice);
}
