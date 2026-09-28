package com.smartscript.platform.content.mapper;

import java.util.List;
import com.smartscript.platform.content.domain.SysDramaChannel;

/**
 * 外部视频渠道 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_drama_channel 表。
 *
 * @author xiangsipeng
 */
public interface SysDramaChannelMapper
{
    /**
     * 查询渠道列表
     *
     * @param query 查询条件（channelName 模糊、platform/status 精确，按需传入）
     * @return 渠道集合
     */
    public List<SysDramaChannel> selectChannelList(SysDramaChannel query);

    /**
     * 通过渠道ID查询渠道
     *
     * @param channelId 渠道ID
     * @return 渠道对象
     */
    public SysDramaChannel selectChannelById(Long channelId);

    /**
     * 新增渠道
     *
     * @param channel 渠道
     * @return 影响行数
     */
    public int insertChannel(SysDramaChannel channel);

    /**
     * 修改渠道（动态 set，仅更新非空字段）
     *
     * @param channel 渠道
     * @return 影响行数
     */
    public int updateChannel(SysDramaChannel channel);

    /**
     * 启用/停用渠道（单一职责：仅改 status）
     *
     * @param channel 仅携带 channelId + status + updateBy
     * @return 影响行数
     */
    public int updateChannelStatus(SysDramaChannel channel);
}
