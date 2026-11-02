package com.smartscript.platform.content.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.AiChannel;
import com.smartscript.platform.content.mapper.AiChannelMapper;
import com.smartscript.platform.content.service.IAiChannelService;

/**
 * AI 调用渠道服务实现
 *
 * 名称唯一校验：新增/修改均校验 channel_name（修改排除自身），重复抛
 * IllegalArgumentException（控制器转 400）。
 *
 * @author xiangsipeng
 */
@Service
public class AiChannelServiceImpl implements IAiChannelService
{
    @Autowired
    private AiChannelMapper aiChannelMapper;

    @Override
    public List<AiChannel> selectChannelList(AiChannel query)
    {
        return aiChannelMapper.selectChannelList(query);
    }

    @Override
    public AiChannel selectChannelById(Long channelId)
    {
        return aiChannelMapper.selectChannelById(channelId);
    }

    @Override
    public AiChannel insertChannel(AiChannel channel)
    {
        AiChannel hit = aiChannelMapper.checkChannelNameUnique(channel.getChannelName(), null);
        if (hit != null)
        {
            throw new IllegalArgumentException("渠道名称已存在: " + channel.getChannelName());
        }
        aiChannelMapper.insertChannel(channel);
        return channel;
    }

    @Override
    public AiChannel updateChannel(AiChannel channel)
    {
        AiChannel hit = aiChannelMapper.checkChannelNameUnique(channel.getChannelName(), channel.getChannelId());
        if (hit != null)
        {
            throw new IllegalArgumentException("渠道名称已存在: " + channel.getChannelName());
        }
        aiChannelMapper.updateChannel(channel);
        return channel;
    }

    @Override
    public void deleteChannelById(Long channelId)
    {
        aiChannelMapper.deleteChannelById(channelId);
    }

    @Override
    public List<AiChannel> selectEnabledChannelList()
    {
        return aiChannelMapper.selectEnabledChannelList();
    }

    @Override
    public AiChannel resolveChannelByModel(String model)
    {
        if (model == null || model.isBlank())
        {
            return null;
        }
        String target = model.trim();
        for (AiChannel channel : selectEnabledChannelList())
        {
            if (containsModel(channel.getModels(), target))
            {
                return channel;
            }
        }
        return null;
    }

    @Override
    public AiChannel resolveDefaultChannel()
    {
        List<AiChannel> enabled = selectEnabledChannelList();
        if (enabled.isEmpty())
        {
            return null;
        }
        AiChannel best = enabled.get(0);
        for (AiChannel channel : enabled)
        {
            int s = channel.getSort() == null ? 0 : channel.getSort();
            int bs = best.getSort() == null ? 0 : best.getSort();
            if (s < bs)
            {
                best = channel;
            }
        }
        return best;
    }

    @Override
    public List<Map<String, Object>> listEnabledModels()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiChannel channel : selectEnabledChannelList())
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("channelId", channel.getChannelId());
            row.put("channelName", channel.getChannelName());
            row.put("provider", channel.getProvider());
            row.put("models", splitModels(channel.getModels()));
            row.put("defaultModel", channel.getDefaultModel());
            result.add(row);
        }
        return result;
    }

    /** 逗号分隔模型串 → 列表（兼容空值） */
    private static List<String> splitModels(String models)
    {
        List<String> list = new ArrayList<>();
        if (models == null || models.isBlank())
        {
            return list;
        }
        for (String m : models.split(","))
        {
            if (m != null && !m.isBlank())
            {
                list.add(m.trim());
            }
        }
        return list;
    }

    /** models（逗号分隔）是否包含指定模型名 */
    private static boolean containsModel(String models, String target)
    {
        if (models == null || models.isBlank())
        {
            return false;
        }
        for (String m : models.split(","))
        {
            if (target.equals(m == null ? "" : m.trim()))
            {
                return true;
            }
        }
        return false;
    }
}
