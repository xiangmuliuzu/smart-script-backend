package com.smartscript.platform.content.dto;

import java.util.List;
import com.smartscript.platform.content.domain.WorkGenEpisode;

/**
 * 批量生成任务提交参数。
 *
 * episodes 与一次生成的范围一致：只提交「本次要生成」的集（已存在的集不重复生成）。
 *
 * @author smartscript
 */
public class AppWorkGenJobRequest
{
    /** 全剧事件卡（原文） */
    private String outline;

    /** 人物设定（注入 body，末尾 900 字） */
    private String characters;

    /** 人物白名单，顿号分隔 */
    private String roster;

    /** 待生成集（按集号升序） */
    private List<WorkGenEpisode> episodes;

    public String getOutline()
    {
        return outline;
    }

    public void setOutline(String outline)
    {
        this.outline = outline;
    }

    public String getCharacters()
    {
        return characters;
    }

    public void setCharacters(String characters)
    {
        this.characters = characters;
    }

    public String getRoster()
    {
        return roster;
    }

    public void setRoster(String roster)
    {
        this.roster = roster;
    }

    public List<WorkGenEpisode> getEpisodes()
    {
        return episodes;
    }

    public void setEpisodes(List<WorkGenEpisode> episodes)
    {
        this.episodes = episodes;
    }
}
