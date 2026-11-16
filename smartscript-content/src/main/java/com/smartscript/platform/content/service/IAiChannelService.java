package com.smartscript.platform.content.service;

import java.util.List;
import java.util.Map;
import com.smartscript.platform.content.domain.AiChannel;

/**
 * AI 调用渠道服务（E 模块）
 *
 * 渠道 = AI 服务商接入配置（baseUrl / apiKey / 可用模型 / 默认模型 / 启停），
 * 由管理员端「AI 创作 Skill 和模型配置」页维护；用户端按模型名路由到对应渠道。
 *
 * @author xiangsipeng
 */
public interface IAiChannelService
{
    /** 渠道列表（channelName 模糊 / status 精确；分页） */
    List<AiChannel> selectChannelList(AiChannel query);

    /** 渠道详情 */
    AiChannel selectChannelById(Long channelId);

    /** 新增（channelName 唯一校验） */
    AiChannel insertChannel(AiChannel channel);

    /** 修改（channelName 唯一校验，排除自身） */
    AiChannel updateChannel(AiChannel channel);

    /** 删除（物理删除） */
    void deleteChannelById(Long channelId);

    /** 启用渠道列表（status='0'，按 sort/id 升序） */
    List<AiChannel> selectEnabledChannelList();

    /**
     * 按模型名解析渠道：在启用渠道中，models（逗号分隔）包含指定模型名 → 返回该渠道；
     * 未命中返回 null（调用方回退默认环境配置）。
     */
    AiChannel resolveChannelByModel(String model);

    /**
     * 全站默认渠道：优先取 is_default=1 的启用渠道；无标记时回退 sort 最小者；
     * 无启用渠道返回 null。
     */
    AiChannel resolveDefaultChannel();

    /**
     * 设置全站默认模型：把指定渠道标记为默认（其余清除），并把其默认模型设为 model。
     * 渠道须存在且启用，model 须属于该渠道 models，否则抛 IllegalArgumentException。
     */
    void setGlobalDefault(Long channelId, String model);

    /**
     * 用户端可选模型列表（不含 apiKey）：
     * [{channelId, channelName, provider, models: [...], defaultModel, isDefault}, ...]
     * 其中只有全站默认渠道的 defaultModel 非空（用户端据此标注唯一「默认」）。
     */
    List<Map<String, Object>> listEnabledModels();
}
