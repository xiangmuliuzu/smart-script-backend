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
        // 表单不再填默认模型：为空或不在模型列表时取列表第一个，保证调用有模型可用
        String dm = channel.getDefaultModel();
        if (dm == null || dm.isBlank() || !containsModel(channel.getModels(), dm.trim()))
        {
            channel.setDefaultModel(firstModel(channel.getModels()));
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
        AiChannel exist = aiChannelMapper.selectChannelById(channel.getChannelId());
        String models = (channel.getModels() != null && !channel.getModels().isBlank())
                ? channel.getModels().trim()
                : (exist == null ? null : exist.getModels());
        String dm = channel.getDefaultModel();
        if (dm == null || dm.isBlank())
        {
            dm = exist == null ? null : exist.getDefaultModel();
        }
        // 模型列表变更导致原默认模型失效时，自动落到列表第一个
        if (dm == null || dm.isBlank() || !containsModel(models, dm.trim()))
        {
            dm = firstModel(models);
        }
        channel.setDefaultModel(dm);
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
        // 优先管理员显式设置的全站默认渠道
        for (AiChannel channel : enabled)
        {
            if (channel.getIsDefault() != null && channel.getIsDefault() == 1)
            {
                return channel;
            }
        }
        // 兼容旧数据：未设置时回退 sort 最小者
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
    public void setGlobalDefault(Long channelId, String model)
    {
        AiChannel channel = aiChannelMapper.selectChannelById(channelId);
        if (channel == null)
        {
            throw new IllegalArgumentException("渠道不存在");
        }
        if (!"0".equals(channel.getStatus()))
        {
            throw new IllegalArgumentException("只能把启用的渠道设为默认");
        }
        String target = model == null ? "" : model.trim();
        if (target.isEmpty())
        {
            throw new IllegalArgumentException("默认模型不能为空");
        }
        if (!containsModel(channel.getModels(), target))
        {
            throw new IllegalArgumentException("默认模型必须属于该渠道的模型列表");
        }
        aiChannelMapper.clearDefaultFlag();
        aiChannelMapper.markDefault(channelId, target);
    }

    @Override
    public List<Map<String, Object>> listEnabledModels()
    {
        AiChannel defaultChannel = resolveDefaultChannel();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiChannel channel : selectEnabledChannelList())
        {
            boolean isDefault = defaultChannel != null
                    && channel.getChannelId().equals(defaultChannel.getChannelId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("channelId", channel.getChannelId());
            row.put("channelName", channel.getChannelName());
            row.put("provider", channel.getProvider());
            row.put("models", splitModels(channel.getModels()));
            // 只给全站默认渠道下发默认模型，用户端据此只标注一个「（默认）」
            row.put("defaultModel", isDefault ? channel.getDefaultModel() : "");
            row.put("isDefault", isDefault);
            result.add(row);
        }
        return result;
    }

    /** 模型列表第一个（空列表返回 null） */
    private static String firstModel(String models)
    {
        List<String> list = splitModels(models);
        return list.isEmpty() ? null : list.get(0);
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
