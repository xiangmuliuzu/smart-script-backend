package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.StringUtils;
import com.smartscript.platform.content.domain.SysTag;
import com.smartscript.platform.content.mapper.SysTagMapper;
import com.smartscript.platform.content.service.ISysTagService;

/**
 * 标签 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_tag 表。
 * 反推处理点：use_count/sort/status 云端列 NOT NULL 无默认值，新增时此处兜底 0/0/"0"。
 *
 * @author xiangsipeng
 */
@Service
public class SysTagServiceImpl implements ISysTagService
{
    /** 状态：正常 */
    private static final String STATUS_NORMAL = "0";

    @Autowired
    private SysTagMapper tagMapper;

    @Override
    public List<SysTag> selectTagList(SysTag query)
    {
        return tagMapper.selectTagList(query);
    }

    @Override
    public SysTag selectTagById(Long tagId)
    {
        return tagMapper.selectTagById(tagId);
    }

    @Override
    public boolean checkTagNameUnique(SysTag tag)
    {
        Long tagId = StringUtils.isNull(tag.getTagId()) ? -1L : tag.getTagId();
        SysTag info = tagMapper.checkTagNameUnique(tag.getTagName());
        if (StringUtils.isNotNull(info) && info.getTagId().longValue() != tagId.longValue())
        {
            return false;
        }
        return true;
    }

    @Override
    public int insertTag(SysTag tag)
    {
        // 云端列 NOT NULL 无默认值，兜底（无文档依据，反推）
        if (StringUtils.isNull(tag.getUseCount()))
        {
            tag.setUseCount(0);
        }
        if (StringUtils.isNull(tag.getSort()))
        {
            tag.setSort(0);
        }
        if (StringUtils.isNull(tag.getStatus()) || StringUtils.isEmpty(tag.getStatus()))
        {
            tag.setStatus(STATUS_NORMAL);
        }
        return tagMapper.insertTag(tag);
    }

    @Override
    public int updateTag(SysTag tag)
    {
        return tagMapper.updateTag(tag);
    }

    @Override
    public int deleteTagByIds(Long[] tagIds)
    {
        return tagMapper.deleteTagByIds(tagIds);
    }
}
