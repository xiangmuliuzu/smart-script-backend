package com.smartscript.platform.content.dto;

/**
 * 提交评分请求体（App 书城读者评分通道）。
 *
 * 只接受分值，归属由服务层取当前登录身份，请求体不接受 userId / workId
 * （workId 走路径参数），避免为他人评分。
 *
 * @author xiangsipeng
 */
public class AppWorkRatingRequest
{
    /** 评分（2~10，每星 2 分）；为空或越界由控制层按 400 拒绝 */
    private Integer score;

    public AppWorkRatingRequest()
    {
    }

    public Integer getScore()
    {
        return score;
    }

    public void setScore(Integer score)
    {
        this.score = score;
    }
}
