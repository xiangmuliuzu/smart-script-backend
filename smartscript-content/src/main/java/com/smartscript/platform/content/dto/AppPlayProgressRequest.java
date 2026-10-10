package com.smartscript.platform.content.dto;

/**
 * 保存播放进度入参（B 模块 2.8.4 表 2-97）。
 *
 * 依据：接口文档表 2-97（输入 progress 必填 Int、duration 可选 Int）。
 *
 * @author xiangsipeng
 */
public class AppPlayProgressRequest
{
    /** 播放进度（秒，必填，>=0） */
    private Integer progress;

    /** 总时长（秒，可选，>=0） */
    private Integer duration;

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