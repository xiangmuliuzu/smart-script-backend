package com.smartscript.platform.trade.mapper;

import java.util.List;
import com.smartscript.platform.trade.domain.SysDemandTag;

public interface SysDemandTagMapper
{
    List<SysDemandTag> selectDemandTagList(SysDemandTag tag);
    SysDemandTag selectDemandTagById(Long tagId);
    int insertDemandTag(SysDemandTag tag);
    int updateDemandTag(SysDemandTag tag);
    int deleteDemandTagByIds(Long[] tagIds);
}
