package com.smartscript.platform.user.dto;

/**
 * A5 换绑第 1 步结果（契约 §1.4）。
 *
 * 只返回不透明凭证与有效期；凭证一次性、短时、绑定用户与设备，
 * 客户端不得持久化或写入日志。
 */
public class PhoneStepUpResult
{
    private String stepUpToken;
    private long expiresIn;

    public PhoneStepUpResult()
    {
    }

    public PhoneStepUpResult(String stepUpToken, long expiresIn)
    {
        this.stepUpToken = stepUpToken;
        this.expiresIn = expiresIn;
    }

    public String getStepUpToken()
    {
        return stepUpToken;
    }

    public void setStepUpToken(String stepUpToken)
    {
        this.stepUpToken = stepUpToken;
    }

    public long getExpiresIn()
    {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn)
    {
        this.expiresIn = expiresIn;
    }
}
