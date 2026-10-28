package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysDeliveryFile;

/**
 * 版权交割文件 Mapper。
 */
public interface SysDeliveryFileMapper
{
    List<SysDeliveryFile> selectFilesByDeliveryId(Long deliveryId);

    int insertDeliveryFile(SysDeliveryFile file);

    int deleteFilesByDeliveryId(Long deliveryId);
}
