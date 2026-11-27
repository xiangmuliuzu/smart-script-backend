package com.smartscript.platform.user.dto;

/**
 * 扫码登录票据（Redis 中的临时状态）。
 *
 * 只保存「谁在哪个浏览器上等登录」与「谁扫码、确认了」，**不保存令牌**：
 * PC 令牌在轮询取走票据时才签发，票据随即删除，令牌从不落到 Redis 的这处键上。
 *
 * 类名必须是全限定名可见的独立类型：公共 RedisTemplate 用 FastJson2 序列化并写入
 * 类型信息（{@code @type}），而反序列化白名单只允许 {@code com.ruoyi} 前缀，
 * 所以这里不能复用内部类或匿名结构。
 */
public class QrLoginTicket
{
    /** 登录端：创作者中心。 */
    public static final String PORTAL_CREATOR = "creator";

    /** 登录端：企业端（个人版权采购中心）。 */
    public static final String PORTAL_ENTERPRISE = "enterprise";

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_SCANNED = "SCANNED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String status;

    /** creator / enterprise，决定扫码确认页展示的站点文案与准入校验。 */
    private String portal;

    /** 浏览器与操作系统：供 App 确认页展示，让用户能识别是不是自己打开的那个窗口。 */
    private String browser;
    private String os;

    /** 扫码账号（App 身份），确认时必须与之一致。 */
    private Long scannedUserId;
    private String scannedNickname;
    private String scannedPhoneMasked;

    /** 确认登录的账号；轮询取走令牌时用它签发会话。 */
    private Long confirmedUserId;

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getPortal()
    {
        return portal;
    }

    public void setPortal(String portal)
    {
        this.portal = portal;
    }

    public String getBrowser()
    {
        return browser;
    }

    public void setBrowser(String browser)
    {
        this.browser = browser;
    }

    public String getOs()
    {
        return os;
    }

    public void setOs(String os)
    {
        this.os = os;
    }

    public Long getScannedUserId()
    {
        return scannedUserId;
    }

    public void setScannedUserId(Long scannedUserId)
    {
        this.scannedUserId = scannedUserId;
    }

    public String getScannedNickname()
    {
        return scannedNickname;
    }

    public void setScannedNickname(String scannedNickname)
    {
        this.scannedNickname = scannedNickname;
    }

    public String getScannedPhoneMasked()
    {
        return scannedPhoneMasked;
    }

    public void setScannedPhoneMasked(String scannedPhoneMasked)
    {
        this.scannedPhoneMasked = scannedPhoneMasked;
    }

    public Long getConfirmedUserId()
    {
        return confirmedUserId;
    }

    public void setConfirmedUserId(Long confirmedUserId)
    {
        this.confirmedUserId = confirmedUserId;
    }
}
