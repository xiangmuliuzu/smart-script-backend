package com.smartscript.platform.trade.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.trade.domain.BuyerCopyrightRecord;
import com.smartscript.platform.trade.domain.SysDeliveryFile;

/**
 * 版权交割文件 Mapper。
 */
public interface SysDeliveryFileMapper
{
    List<SysDeliveryFile> selectFilesByDeliveryId(Long deliveryId);

    SysDeliveryFile selectFileById(Long fileId);

    /**
     * 买方已完成交割中的 cert 文件列表（企业端「版权」栏）。
     */
    List<BuyerCopyrightRecord> selectBuyerCopyrightList(@Param("buyerId") Long buyerId,
                                                       @Param("keyword") String keyword);

    int insertDeliveryFile(SysDeliveryFile file);

    int deleteFilesByDeliveryId(Long deliveryId);
}
