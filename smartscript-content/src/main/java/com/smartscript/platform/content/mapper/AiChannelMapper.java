package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.AiChannel;

/**
 * AI 调用渠道 Mapper（sys_ai_channel）
 *
 * @author xiangsipeng
 */
public interface AiChannelMapper
{
    /** 渠道列表（channelName 模糊 / status 精确；分页由 startPage 注入） */
    List<AiChannel> selectChannelList(AiChannel query);

    /** 渠道详情 */
    AiChannel selectChannelById(Long channelId);

    /** 名称唯一校验（排除自身） */
    AiChannel checkChannelNameUnique(@Param("channelName") String channelName,
                                     @Param("excludeId") Long excludeId);

    /** 启用渠道（status='0'，按 sort/id 升序） */
    List<AiChannel> selectEnabledChannelList();

    /** 新增 */
    int insertChannel(AiChannel channel);

    /** 修改 */
    int updateChannel(AiChannel channel);

    /** 删除 */
    int deleteChannelById(Long channelId);
}
