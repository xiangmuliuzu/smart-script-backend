package com.smartscript.platform.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * App credential-domain configuration. Secrets come from environment variables only.
 */
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AppAuthProperties
{
    private String env = "local";
    private String tokenSecret = "";
    private String tokenIssuer = "";
    private String tokenAudience = "";
    private long accessTokenTtlSeconds = 1800L;
    private long refreshTokenTtlSeconds = 1209600L;
    private String redisKeyPrefix = "";
    private Sms sms = new Sms();
    private Agreement agreement = new Agreement();

    public static class Sms
    {
        private String provider = "mock";
        private String accessKeyId = "";
        private String accessKeySecret = "";
        private String signName = "";
        private String templateLogin = "";
        private String templateRegister = "";
        private String templateSetPassword = "";
        private String templateResetPassword = "";
        private String templateChangePhoneOld = "";
        private String templateChangePhoneNew = "";
        private String mockFixedCode = "";
        private int cooldownSeconds = 60;
        private int codeTtlSeconds = 300;
        private int maxFailedAttempts = 5;
        private int phoneDailyLimit = 10;
        private int ipHourlyLimit = 30;
        private int codeLength = 6;

        public String getProvider()
        {
            return provider;
        }

        public void setProvider(String provider)
        {
            this.provider = provider;
        }

        public String getAccessKeyId()
        {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId)
        {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret()
        {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret)
        {
            this.accessKeySecret = accessKeySecret;
        }

        public String getSignName()
        {
            return signName;
        }

        public void setSignName(String signName)
        {
            this.signName = signName;
        }

        public String getTemplateLogin()
        {
            return templateLogin;
        }

        public void setTemplateLogin(String templateLogin)
        {
            this.templateLogin = templateLogin;
        }

        public String getTemplateRegister()
        {
            return templateRegister;
        }

        public void setTemplateRegister(String templateRegister)
        {
            this.templateRegister = templateRegister;
        }

        public String getTemplateSetPassword()
        {
            return templateSetPassword;
        }

        public void setTemplateSetPassword(String templateSetPassword)
        {
            this.templateSetPassword = templateSetPassword;
        }

        public String getTemplateResetPassword()
        {
            return templateResetPassword;
        }

        public void setTemplateResetPassword(String templateResetPassword)
        {
            this.templateResetPassword = templateResetPassword;
        }

        public String getTemplateChangePhoneOld()
        {
            return templateChangePhoneOld;
        }

        public void setTemplateChangePhoneOld(String templateChangePhoneOld)
        {
            this.templateChangePhoneOld = templateChangePhoneOld;
        }

        public String getTemplateChangePhoneNew()
        {
            return templateChangePhoneNew;
        }

        public void setTemplateChangePhoneNew(String templateChangePhoneNew)
        {
            this.templateChangePhoneNew = templateChangePhoneNew;
        }

        public String getMockFixedCode()
        {
            return mockFixedCode;
        }

        public void setMockFixedCode(String mockFixedCode)
        {
            this.mockFixedCode = mockFixedCode;
        }

        public int getCooldownSeconds()
        {
            return cooldownSeconds;
        }

        public void setCooldownSeconds(int cooldownSeconds)
        {
            this.cooldownSeconds = cooldownSeconds;
        }

        public int getCodeTtlSeconds()
        {
            return codeTtlSeconds;
        }

        public void setCodeTtlSeconds(int codeTtlSeconds)
        {
            this.codeTtlSeconds = codeTtlSeconds;
        }

        public int getMaxFailedAttempts()
        {
            return maxFailedAttempts;
        }

        public void setMaxFailedAttempts(int maxFailedAttempts)
        {
            this.maxFailedAttempts = maxFailedAttempts;
        }

        public int getPhoneDailyLimit()
        {
            return phoneDailyLimit;
        }

        public void setPhoneDailyLimit(int phoneDailyLimit)
        {
            this.phoneDailyLimit = phoneDailyLimit;
        }

        public int getIpHourlyLimit()
        {
            return ipHourlyLimit;
        }

        public void setIpHourlyLimit(int ipHourlyLimit)
        {
            this.ipHourlyLimit = ipHourlyLimit;
        }

        public int getCodeLength()
        {
            return codeLength;
        }

        public void setCodeLength(int codeLength)
        {
            this.codeLength = codeLength;
        }
    }

    public static class Agreement
    {
        private String userVersion = "1.0.0";
        private String privacyVersion = "1.0.0";
        private String userUrl = "https://example.invalid/app/user-agreement";
        private String privacyUrl = "https://example.invalid/app/privacy-policy";
        private String userTitle = "用户协议";
        private String privacyTitle = "隐私政策";

        public String getUserVersion()
        {
            return userVersion;
        }

        public void setUserVersion(String userVersion)
        {
            this.userVersion = userVersion;
        }

        public String getPrivacyVersion()
        {
            return privacyVersion;
        }

        public void setPrivacyVersion(String privacyVersion)
        {
            this.privacyVersion = privacyVersion;
        }

        public String getUserUrl()
        {
            return userUrl;
        }

        public void setUserUrl(String userUrl)
        {
            this.userUrl = userUrl;
        }

        public String getPrivacyUrl()
        {
            return privacyUrl;
        }

        public void setPrivacyUrl(String privacyUrl)
        {
            this.privacyUrl = privacyUrl;
        }

        public String getUserTitle()
        {
            return userTitle;
        }

        public void setUserTitle(String userTitle)
        {
            this.userTitle = userTitle;
        }

        public String getPrivacyTitle()
        {
            return privacyTitle;
        }

        public void setPrivacyTitle(String privacyTitle)
        {
            this.privacyTitle = privacyTitle;
        }
    }

    public String getEnv()
    {
        return env;
    }

    public void setEnv(String env)
    {
        this.env = env;
    }

    public String getTokenSecret()
    {
        return tokenSecret;
    }

    public void setTokenSecret(String tokenSecret)
    {
        this.tokenSecret = tokenSecret;
    }

    public String getTokenIssuer()
    {
        return tokenIssuer;
    }

    public void setTokenIssuer(String tokenIssuer)
    {
        this.tokenIssuer = tokenIssuer;
    }

    public String getTokenAudience()
    {
        return tokenAudience;
    }

    public void setTokenAudience(String tokenAudience)
    {
        this.tokenAudience = tokenAudience;
    }

    public long getAccessTokenTtlSeconds()
    {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds)
    {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds()
    {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds)
    {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public String getRedisKeyPrefix()
    {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix)
    {
        this.redisKeyPrefix = redisKeyPrefix;
    }

    public Sms getSms()
    {
        return sms;
    }

    public void setSms(Sms sms)
    {
        this.sms = sms;
    }

    public Agreement getAgreement()
    {
        return agreement;
    }

    public void setAgreement(Agreement agreement)
    {
        this.agreement = agreement;
    }

    public boolean isLocalOrTestProfile()
    {
        String e = env == null ? "" : env.trim().toLowerCase();
        return "local".equals(e) || "test".equals(e);
    }
}
