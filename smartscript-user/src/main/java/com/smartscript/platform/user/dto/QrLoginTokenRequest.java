package com.smartscript.platform.user.dto;

/**
 * 扫码登录的单参数请求体：票据令牌。
 *
 * 令牌一律走请求体而不是查询参数——查询串会被网关与服务端访问日志原文记录，
 * 而这张票据一旦泄露，轮询方就能取走该账号在本站的登录态。
 */
public class QrLoginTokenRequest
{
    private String qrToken;

    public String getQrToken()
    {
        return qrToken;
    }

    public void setQrToken(String qrToken)
    {
        this.qrToken = qrToken;
    }
}
