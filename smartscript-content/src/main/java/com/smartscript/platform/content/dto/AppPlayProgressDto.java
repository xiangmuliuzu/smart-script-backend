package com.smartscript.platform.content.dto;

/**
 * 播放进度（B 模块 2.8.5 表 2-98 出参 / 2.8.4 表 2-97 入参）。
 *
 * 依据：云端 script_platform_dev 库 sys_play_progress 表 + 接口文档表 2-97 / 2-98。
 *
 * 反推处理点：
 * 1. 契约字段 progress / duration；落库列名为 progress_seconds / total_duration，
 *    DTO 保留契约命名，Mapper 内做列名映射。
 * 2. 无进度记录时（首次播放）返回 progress=0、duration=null，不报错。
 *
 * @author xiangsipeng
 */
public class AppPlayProgressDto
{
    /** 播放进度（秒） */
    private Integer progress;

    /** 总时长（秒，可空） */
    private Integer duration;

    public AppPlayProgressDto()
    {
    }

    public AppPlayProgressDto(Integer progress, Integer duration)
    {
        this.progress = progress;
        this.duration = duration;
    }

    public Integer getProgress()
    {
        return progress;
    }

    public void setProgress(Integer progress)
    {
        this.progress = progress;
    }

    public Integer getDuration()
    {
        return duration;
    }

    public void setDuration(Integer duration)
    {
        this.duration = duration;
    }
}