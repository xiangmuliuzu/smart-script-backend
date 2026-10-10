package com.smartscript.platform.user.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.util.AppHashes;

/**
 * Mock SMS provider. Only registered when SMS_PROVIDER=mock.
 * Startup guard rejects mock outside local/test profiles.
 */
@Component
@ConditionalOnProperty(name = "app.auth.sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsProvider implements SmsProvider
{
    private static final Logger log = LoggerFactory.getLogger(MockSmsProvider.class);

    private final AppAuthProperties properties;

    public MockSmsProvider(AppAuthProperties properties)
    {
        this.properties = properties;
    }

    @Override
    public void send(String phone, String scene, String code)
    {
        // Never log the code. Integration tests use APP_SMS_MOCK_CODE when set.
        log.info("mock-sms sent phone={} scene={}", AppHashes.maskPhone(phone), scene);
    }

    public String resolveCode(String phone, String scene, String randomCode)
    {
        String fixed = properties.getSms().getMockFixedCode();
        if (fixed != null && !fixed.isBlank())
        {
            return fixed.trim();
        }
        return randomCode;
    }
}
