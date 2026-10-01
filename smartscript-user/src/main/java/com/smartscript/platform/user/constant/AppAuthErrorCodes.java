package com.smartscript.platform.user.constant;

/**
 * A3-AUTH-CONTRACT-v1 business error codes.
 */
public final class AppAuthErrorCodes
{
    private AppAuthErrorCodes()
    {
    }

    public static final int PARAM = 40000;
    public static final int SMS_CODE_INVALID = 40001;
    public static final int SMS_CODE_EXPIRED = 40002;
    public static final int SMS_CODE_USED = 40901;
    public static final int PHONE_TAKEN = 40902;
    public static final int AGREEMENT_MISMATCH = 40903;
    public static final int PASSWORD_STATE_CONFLICT = 40904;
    public static final int UNAUTHORIZED = 40100;
    public static final int ACCESS_EXPIRED = 40101;
    public static final int REFRESH_INVALID = 40102;
    public static final int REFRESH_REPLAY = 40103;
    public static final int DOMAIN_OR_PERMISSION = 40300;
    public static final int ACCOUNT_DISABLED = 40301;
    public static final int SMS_COOLDOWN = 42901;
    public static final int SMS_RATE_LIMIT = 42902;
    public static final int OAUTH_NOT_OPEN = 50101;
    public static final int SYSTEM_ERROR = 50000;
}
