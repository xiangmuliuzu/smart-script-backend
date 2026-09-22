package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.util.AppHashes;

class AppAuthCoreUnitTest
{
    @Test
    void passwordPolicyRequiresLetterAndDigit()
    {
        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> AppAuthenticationService.validatePasswordPolicy("allletters"));
        assertTrue(ex.getCode() == AppAuthErrorCodes.PARAM);
        assertThrows(AppAuthException.class,
                () -> AppAuthenticationService.validatePasswordPolicy("12345678"));
        assertThrows(AppAuthException.class,
                () -> AppAuthenticationService.validatePasswordPolicy("sh0rt"));
        assertDoesNotThrow(() -> AppAuthenticationService.validatePasswordPolicy("Passw0rd"));
    }

    @Test
    void sha256HashIsStableAndHex64()
    {
        String h1 = AppHashes.sha256Hex("123456");
        String h2 = AppHashes.sha256Hex("123456");
        assertTrue(h1.equals(h2));
        assertTrue(h1.length() == 64);
        assertTrue(!h1.contains("123456"));
    }

    @Test
    void phoneMaskHidesMiddleDigits()
    {
        assertTrue("138****8000".equals(AppHashes.maskPhone("13812348000")));
        assertTrue("***".equals(AppHashes.maskPhone("12")));
    }

    @Test
    void userTypeNormalizedToContractCodes()
    {
        assertTrue("01".equals(AppAuthenticationService.normalizeUserType("01")));
        assertTrue("02".equals(AppAuthenticationService.normalizeUserType("02")));
        assertTrue("03".equals(AppAuthenticationService.normalizeUserType("03")));
        assertTrue("01".equals(AppAuthenticationService.normalizeUserType("00")));
        assertTrue("01".equals(AppAuthenticationService.normalizeUserType(null)));
    }
}
