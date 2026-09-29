package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.SysExternalDrama;
import com.smartscript.platform.content.mapper.SysExternalDramaMapper;
import com.smartscript.platform.content.service.ISysDramaBindService;

/**
 * 外部视频绑定管理 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama 表（related_work_id 字段）。
 * 反推处理点：
 * 1. bindWork 防御性裁剪：只放行 dramaId + relatedWorkId + updateBy，避免误传其他字段。
 * 2. unbindWork 不直接接收 DTO 全字段，service 内构造新 SysExternalDrama：
 *    dramaId + relatedWorkId=null + updateBy，调用 updateRelatedWorkId，
 *    XML SET related_work_id = #{relatedWorkId} 会将其置为 NULL。
 *
 * @author xiangsipeng
 */
@Service
public class SysDramaBindServiceImpl implements ISysDramaBindService
{
    @Autowired
    private SysExternalDramaMapper dramaMapper;

    @Override
    public List<SysExternalDrama> selectBindList(SysExternalDrama query)
    {
        // 绑定列表与内容管理列表共用 selectDramaList 查询，差异仅在过滤项（unboundOnly 等）
        return dramaMapper.selectDramaList(query);
    }

    @Override
    public SysExternalDrama selectDramaById(Long dramaId)
    {
        return dramaMapper.selectDramaById(dramaId);
    }

    @Override
    public int bindWork(SysExternalDrama drama)
    {
        // 防御性裁剪：只放行 dramaId + relatedWorkId + updateBy
        SysExternalDrama update = new SysExternalDrama();
        update.setDramaId(drama.getDramaId());
        update.setRelatedWorkId(drama.getRelatedWorkId());
        update.setUpdateBy(drama.getUpdateBy());
        return dramaMapper.updateRelatedWorkId(update);
    }

    @Override
    public int unbindWork(SysExternalDrama drama)
    {
        // 防御性裁剪 + 显式置 null：service 构造新对象，仅放行 dramaId + relatedWorkId=null + updateBy
        SysExternalDrama update = new SysExternalDrama();
        update.setDramaId(drama.getDramaId());
        update.setRelatedWorkId(null);
        update.setUpdateBy(drama.getUpdateBy());
        return dramaMapper.updateRelatedWorkId(update);
    }
}
