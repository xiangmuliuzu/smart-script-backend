package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.SysEpisode;
import com.smartscript.platform.content.domain.SysExternalDrama;
import com.smartscript.platform.content.mapper.SysEpisodeMapper;
import com.smartscript.platform.content.mapper.SysExternalDramaMapper;
import com.smartscript.platform.content.service.ISysExternalDramaService;

/**
 * 外部视频内容管理 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama + sys_episode 表。
 * 反推处理点：selectDramaById 详情接口需返回 drama + episodes 剧集列表，
 * episodes 由本服务委托 SysEpisodeMapper 按 related_work_id 查询拼装。
 *
 * @author xiangsipeng
 */
@Service
public class SysExternalDramaServiceImpl implements ISysExternalDramaService
{
    @Autowired
    private SysExternalDramaMapper dramaMapper;

    @Autowired
    private SysEpisodeMapper episodeMapper;

    @Override
    public List<SysExternalDrama> selectDramaList(SysExternalDrama query)
    {
        return dramaMapper.selectDramaList(query);
    }

    @Override
    public SysExternalDrama selectDramaById(Long dramaId)
    {
        SysExternalDrama drama = dramaMapper.selectDramaById(dramaId);
        if (drama != null && drama.getRelatedWorkId() != null)
        {
            // 委托 SysEpisodeMapper 按 related_work_id 查询剧集列表拼装到 episodes 字段
            SysEpisode query = new SysEpisode();
            query.setWorkId(drama.getRelatedWorkId());
            List<SysEpisode> episodes = episodeMapper.selectEpisodeListByWorkId(query);
            drama.setEpisodes(episodes);
        }
        return drama;
    }

    @Override
    public int insertDrama(SysExternalDrama drama)
    {
        return dramaMapper.insertDrama(drama);
    }

    @Override
    public int updateDrama(SysExternalDrama drama)
    {
        return dramaMapper.updateDrama(drama);
    }
}
