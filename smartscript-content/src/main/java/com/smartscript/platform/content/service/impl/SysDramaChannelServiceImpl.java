package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.StringUtils;
import com.smartscript.platform.content.domain.SysDramaChannel;
import com.smartscript.platform.content.mapper.SysDramaChannelMapper;
import com.smartscript.platform.content.service.ISysDramaChannelService;

/**
 * 外部视频渠道 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_drama_channel 表。
 * 反推处理点：is_auto_distribute/status/sort 云端列 NOT NULL 无默认值，
 * 新增时此处兜底 "0"/"0"/0。
 *
 * @author xiangsipeng
 */
@Service
public class SysDramaChannelServiceImpl implements ISysDramaChannelService
{
    /** 状态：正常 */
    private static final String STATUS_NORMAL = "0";

    /** 是否自动分发：否 */
    private static final String AUTO_DISTRIBUTE_NO = "0";

    @Autowired
    private SysDramaChannelMapper channelMapper;

    @Override
    public List<SysDramaChannel> selectChannelList(SysDramaChannel query)
    {
        return channelMapper.selectChannelList(query);
    }

    @Override
    public SysDramaChannel selectChannelById(Long channelId)
    {
        return channelMapper.selectChannelById(channelId);
    }

    @Override
    public int insertChannel(SysDramaChannel channel)
    {
        // 云端列 NOT NULL 无默认值，兜底（无文档依据，反推）
        if (StringUtils.isNull(channel.getSort()))
        {
            channel.setSort(0);
        }
        if (StringUtils.isEmpty(channel.getStatus()))
        {
            channel.setStatus(STATUS_NORMAL);
        }
        if (StringUtils.isEmpty(channel.getIsAutoDistribute()))
        {
            channel.setIsAutoDistribute(AUTO_DISTRIBUTE_NO);
        }
        return channelMapper.insertChannel(channel);
    }

    @Override
    public int updateChannel(SysDramaChannel channel)
    {
        return channelMapper.updateChannel(channel);
    }

    @Override
    public int updateChannelStatus(SysDramaChannel channel)
    {
        // 防御性裁剪：只放行 status，避免调用方误传其他字段被动态 SQL 一并更新
        SysDramaChannel update = new SysDramaChannel();
        update.setChannelId(channel.getChannelId());
        update.setStatus(channel.getStatus());
        update.setUpdateBy(channel.getUpdateBy());
        return channelMapper.updateChannel(update);
    }
}
