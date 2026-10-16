package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysBillRecord;

/**
 * 账单流水 Mapper 接口。
 */
public interface SysBillRecordMapper
{
    List<SysBillRecord> selectBillRecordList(SysBillRecord query);
    int insertBillRecord(SysBillRecord record);
}
