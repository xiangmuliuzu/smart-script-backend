package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /**
     * F07 复验补充：字符分类必须按 Unicode 码点判断，与前端
     * \p{L}/\p{Nd}（码点语义）一致；长度仍按 UTF-16 代码单元计。
     */
    @Test
    void passwordPolicyClassifiesByCodePointNotUtf16Unit()
    {
        // 复现输入一：补充平面字母（𠮷 Lo，UTF-16 长度 8）
        assertDoesNotThrow(() -> AppAuthenticationService.validatePasswordPolicy("𠮷𠮷𠮷12"));
        // 复现输入二：BMP 字母 + 补充平面十进制数字（𞥐 Nd，UTF-16 长度 9）
        assertDoesNotThrow(() -> AppAuthenticationService.validatePasswordPolicy("a𞥐𞥐𞥐𞥐"));
        // 汉字 + 数字 / 全角数字与前端同口径
        assertDoesNotThrow(() -> AppAuthenticationService.validatePasswordPolicy("汉字密码测试甲1"));
        assertThrows(AppAuthException.class,
                () -> AppAuthenticationService.validatePasswordPolicy("汉字密码甲乙丙丁"));
        assertThrows(AppAuthException.class,
                () -> AppAuthenticationService.validatePasswordPolicy("１２３４５６７８"));
        // emoji 既非字母也非数字
        assertThrows(AppAuthException.class,
                () -> AppAuthenticationService.validatePasswordPolicy("🔥🔥🔥🔥1"));
        // 长度边界仍按 UTF-16 代码单元：64 单元通过，65 单元拒绝
        assertDoesNotThrow(() -> AppAuthenticationService.validatePasswordPolicy("a".repeat(63) + "1"));
        assertThrows(AppAuthException.class,
                () -> AppAuthenticationService.validatePasswordPolicy("a".repeat(64) + "1"));
    }

    /**
     * F08：BCrypt 输入上限（72 UTF-8 字节）纳入统一策略，
     * 超限返回明确参数错误，不在编码阶段抛 500；与前端同口径。
     */
    @Test
    void passwordPolicyEnforcesUtf8ByteLimitForBcrypt()
    {
        // 复验报告复现值：72 字节（38 单元）通过
        assertDoesNotThrow(() -> AppAuthenticationService.validatePasswordPolicy("𠮷".repeat(17) + "ab12"));
        // 73 字节（39 单元）：字符规则合法但超过 BCrypt 上限，策略层拒绝（400/40000）
        AppAuthException ex = assertThrows(AppAuthException.class,
                () -> AppAuthenticationService.validatePasswordPolicy("𠮷".repeat(17) + "abc12"));
        assertTrue(ex.getCode() == AppAuthErrorCodes.PARAM);
        assertEquals(400, ex.getHttpStatus());
        // 63 个 ASCII 字符 + 1 数字 = 64 字节，不受影响
        assertDoesNotThrow(() -> AppAuthenticationService.validatePasswordPolicy("a".repeat(63) + "1"));
        // 64 单元的全补充平面密码 = 126 字节，被字节上限拒绝（而非编码阶段 500）
        assertThrows(AppAuthException.class,
                () -> AppAuthenticationService.validatePasswordPolicy("𠮷".repeat(31) + "a1"));
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
