package com.ruoyi.framework.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 私有材料鉴权链接单测。
 *
 * <p>{@code /profile/**} 在安全配置里匿名可访问，合同 PDF 里是双方真名与成交金额，
 * 所以「哪些地址必须带签名」与「签名能不能被篡改」是这里唯一要锁死的两件事。</p>
 */
class ProfileAccessSignerTest
{
    private static final String SECRET = "unit-test-secret-long-enough-for-hmac-32bytes";

    private final ProfileAccessSigner signer = new ProfileAccessSigner(SECRET);

    @Test
    void refusesToExistWithoutASecret()
    {
        // 退化成匿名可读比直接起不来危险得多，宁可启动即失败
        assertThrows(IllegalStateException.class, () -> new ProfileAccessSigner("  "));
    }

    @Test
    void signsContractArtifactsAndLeavesEverythingElse()
    {
        String signed = signer.sign("/profile/upload/contract/2026/11/c16/u3f9a1b2c_source.pdf");
        assertTrue(signed.startsWith("/profile/upload/contract/2026/11/c16/u3f9a1b2c_source.pdf?exp="));
        assertTrue(signed.contains("&sig="));

        // 旧平铺目录同样要盖住：历史合同的签章图不能因为搬家前没签名就继续匿名可读
        assertTrue(signer.sign("/profile/contract/9/CT20261020145911A006_signed.pdf").contains("sig="));

        // 封面、头像等公开资源不加参数，否则列表页缓存与既有链接全被打穿
        assertEquals("/profile/upload/2026/10/27/cover.png", signer.sign("/profile/upload/2026/10/27/cover.png"));
        assertEquals("https://cdn.example.com/a.png", signer.sign("https://cdn.example.com/a.png"));
        assertEquals("", signer.sign(""));
    }

    @Test
    void signingIsIdempotent()
    {
        String once = signer.sign("/profile/contract/9/page_0.png");
        assertEquals(once, signer.sign(once), "重复签名会把已签好的地址挂上第二个查询串");
    }

    @Test
    void verifiesOnlyItsOwnUnexpiredSignature()
    {
        String signed = signer.sign("/profile/contract/9/page_0.png");
        String query = signed.substring(signed.indexOf('?') + 1);
        String exp = value(query, ProfileAccessSigner.PARAM_EXPIRE);
        String sig = value(query, ProfileAccessSigner.PARAM_SIGNATURE);

        assertTrue(signer.verified("contract/9/page_0.png", exp, sig));
        assertFalse(signer.verified("contract/9/page_1.png", exp, sig), "签名必须绑定具体对象");
        assertFalse(signer.verified("contract/9/page_0.png", String.valueOf(Long.parseLong(exp) + 3600), sig));
        assertFalse(signer.verified("contract/9/page_0.png", exp, sig.substring(0, sig.length() - 2) + "AA"));
        assertFalse(signer.verified("contract/9/page_0.png", "not-a-number", sig));
        assertFalse(signer.verified("contract/9/page_0.png", exp, null));
    }

    @Test
    void signatureExpires()
    {
        String signed = signer.sign("/profile/contract/9/page_0.png");
        long exp = Long.parseLong(value(signed.substring(signed.indexOf('?') + 1), ProfileAccessSigner.PARAM_EXPIRE));
        assertTrue(exp > Instant.now().getEpochSecond(), "签发时间必须落在未来");
        assertTrue(exp - Instant.now().getEpochSecond() <= 6 * 3600L);
        assertFalse(signer.verified("contract/9/page_0.png",
                String.valueOf(Instant.now().getEpochSecond() - 1), "whatever"));
    }

    @Test
    void anotherSecretCannotPass()
    {
        String signed = new ProfileAccessSigner("a-completely-different-secret-value-here")
                .sign("/profile/contract/9/page_0.png");
        String query = signed.substring(signed.indexOf('?') + 1);
        assertFalse(signer.verified("contract/9/page_0.png",
                value(query, ProfileAccessSigner.PARAM_EXPIRE), value(query, ProfileAccessSigner.PARAM_SIGNATURE)));
    }

    @Test
    void signsInlineImagesInContractHtml()
    {
        String html = "<p>甲方</p><img src=\"/profile/contract/9/buyer_signature_1.png\">"
                + "<img src=\"/profile/upload/2026/10/27/cover.png\">"
                + "<img src=\"/profile/upload/contract/2026/11/c9/u3f9a1b2c_page_0.png\">";
        String signed = signer.signHtml(html);

        assertTrue(signed.contains("buyer_signature_1.png?exp="));
        assertTrue(signed.contains("u3f9a1b2c_page_0.png?exp="));
        assertTrue(signed.contains("cover.png\""), "公开封面不该被改写");
        assertNotEquals(html, signed);
    }

    @Test
    void leavesHtmlWithoutPrivateImagesUntouched()
    {
        String html = "<p>仅正文</p><img src=\"/profile/upload/2026/10/27/cover.png\">";
        assertEquals(html, signer.signHtml(html));
        assertNull(signer.signHtml(null));
    }

    @Test
    void guardsOnlyContractPrefixes()
    {
        assertTrue(ProfileAccessSigner.isGuarded("contract/9/page_0.png"));
        assertTrue(ProfileAccessSigner.isGuarded("upload/contract/2026/11/c9/u3f9a1b2c_page_0.png"));
        assertFalse(ProfileAccessSigner.isGuarded("upload/2026/10/27/cover.png"));
        assertFalse(ProfileAccessSigner.isGuarded("avatar/1.png"));
        // 前缀必须是目录级匹配，否则 contractx/ 这类键会被误伤
        assertFalse(ProfileAccessSigner.isGuarded("contracts/index.pdf"));
        assertFalse(ProfileAccessSigner.isGuarded(null));
    }

    private static String value(String query, String name)
    {
        for (String pair : query.split("&"))
        {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(0, equals).equals(name))
            {
                return pair.substring(equals + 1);
            }
        }
        return null;
    }
}
