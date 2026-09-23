package com.smartscript.platform.trade.service;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.SecurityUtils;
import com.smartscript.platform.trade.domain.SysDemandTag;
import com.smartscript.platform.trade.mapper.SysDemandTagMapper;

/**
 * C module: demand tag CRUD service.
 */
@Service
public class TradeDemandTagService
{
    @Autowired
    private SysDemandTagMapper demandTagMapper;

    public List<SysDemandTag> selectDemandTagList(SysDemandTag tag)
    {
        return demandTagMapper.selectDemandTagList(tag);
    }

    public SysDemandTag selectDemandTagById(Long tagId)
    {
        return demandTagMapper.selectDemandTagById(tagId);
    }

    public int insertDemandTag(SysDemandTag tag)
    {
        tag.setCreateBy(SecurityUtils.getUsername());
        tag.setCreateTime(new Date());
        if (tag.getUsedCount() == null) { tag.setUsedCount(0); }
        return demandTagMapper.insertDemandTag(tag);
    }

    public int updateDemandTag(SysDemandTag tag)
    {
        tag.setUpdateBy(SecurityUtils.getUsername());
        tag.setUpdateTime(new Date());
        return demandTagMapper.updateDemandTag(tag);
    }

    public int deleteDemandTagByIds(Long[] tagIds)
    {
        return demandTagMapper.deleteDemandTagByIds(tagIds);
    }
}
