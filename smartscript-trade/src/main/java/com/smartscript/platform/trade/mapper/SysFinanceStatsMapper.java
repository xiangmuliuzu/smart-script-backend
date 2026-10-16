package com.smartscript.platform.trade.mapper;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 财务统计 Mapper 接口（查 sys_income_account / sys_creator_stats）。
 */
public interface SysFinanceStatsMapper
{
    /** 查作者收益账户 */
    Map<String, Object> selectIncomeAccount(Long userId);

    /** 查作者最新统计 */
    Map<String, Object> selectLatestCreatorStats(Long userId);

    /** 查可开票订单列表（已完成且未开过发票） */
    java.util.List<Map<String, Object>> selectInvoiceableOrders(Long userId);

    /** UPSERT 收益账户（可用+已结算增加） */
    int upsertIncomeAccount(Map<String, Object> params);

    /** INSERT 账单流水 */
    int insertBillRecord(Map<String, Object> params);

    /** UPSERT 作者统计（累加收益和订单数） */
    int upsertCreatorStats(Map<String, Object> params);
}
