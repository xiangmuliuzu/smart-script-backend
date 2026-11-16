package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysEscrow;

/**
 * 资金托管 Mapper 接口。
 */
public interface SysEscrowMapper
{
    List<SysEscrow> selectEscrowList(SysEscrow query);
    SysEscrow selectEscrowById(Long escrowId);
    int insertEscrow(SysEscrow escrow);
    int updateEscrow(SysEscrow escrow);
    SysEscrow selectEscrowByOrderId(Long orderId);
}
