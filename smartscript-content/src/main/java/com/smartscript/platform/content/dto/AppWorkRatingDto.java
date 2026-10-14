package com.smartscript.platform.content.dto;

import java.math.BigDecimal;

/**
 * 作品读者评分（App 侧下发对象）。
 *
 * 依据：云端 script_platform_dev 库 sys_work_rating 表 + sys_work.rating（附件5.1 表3-14）。
 * 本载荷同时承担「读评分态」与「提交评分后的回执」两种用途，字段口径一致：
 *   - rating    作品平均分，即 sys_work.rating（无评分时为 0）
 *   - ratingCount 评分数（sys_work_rating 中该作品的记录数，一人一评故等于评分人数）
 *   - myScore   当前登录身份对该作品的评分；未评分返回 null
 *
 * @author xiangsipeng
 */
public class AppWorkRatingDto
{
    /** 作品ID */
    private Long workId;

    /** 作品平均分（sys_work.rating，保留一位小数；无人评分时为 0） */
    private BigDecimal rating;

    /** 评分数（评分人数，一人一评） */
    private Integer ratingCount;

    /** 我的评分（2~10，每星 2 分）；未评分返回 null */
    private Integer myScore;

    public AppWorkRatingDto()
    {
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public BigDecimal getRating()
    {
        return rating;
    }

    public void setRating(BigDecimal rating)
    {
        this.rating = rating;
    }

    public Integer getRatingCount()
    {
        return ratingCount;
    }

    public void setRatingCount(Integer ratingCount)
    {
        this.ratingCount = ratingCount;
    }

    public Integer getMyScore()
    {
        return myScore;
    }

    public void setMyScore(Integer myScore)
    {
        this.myScore = myScore;
    }
}
