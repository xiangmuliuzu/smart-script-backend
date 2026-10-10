package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.SysExternalDrama;
import com.smartscript.platform.content.mapper.SysExternalDramaMapper;
import com.smartscript.platform.content.service.ISysDramaStatusService;

/**
 * 外部视频状态管理 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama 表（status/sync_status 字段）。
 * 反推处理点：
 * 1. changeStatus 防御性裁剪：只放行 dramaId + status + updateBy。
 * 2. syncDramas 批量置 sync_status=syncing。
 *
 * @author xiangsipeng
 */
@Service
public class SysDramaStatusServiceImpl implements ISysDramaStatusService
{
    /** 同步状态：同步中 */
    private static final String SYNC_STATUS_SYNCING = "syncing";

    @Autowired
    private SysExternalDramaMapper dramaMapper;

    @Override
    public List<SysExternalDrama> selectStatusList(SysExternalDrama query)
    {
        return dramaMapper.selectDramaList(query);
    }

    @Override
    public int changeStatus(SysExternalDrama drama)
    {
        // 防御性裁剪：只放行 dramaId + status + updateBy
        SysExternalDrama update = new SysExternalDrama();
        update.setDramaId(drama.getDramaId());
        update.setStatus(drama.getStatus());
        update.setUpdateBy(drama.getUpdateBy());
        return dramaMapper.updateDramaStatus(update);
    }

    @Override
    public int syncDramas(Long[] dramaIds, String updateBy)
    {
        return dramaMapper.batchUpdateSyncStatus(dramaIds, SYNC_STATUS_SYNCING, updateBy);
    }
}
