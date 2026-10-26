package com.smartscript.platform.trade.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ContractHtmlSupport 纯函数单测：模板生成、签名/印章注入（新/旧模板 × 双方有无图组合）、路径解析。
 */
class ContractHtmlSupportTest
{
    private static final String BUYER_SIG = "/profile/contract/1/buyer_sig.png";
    private static final String BUYER_SEAL = "/profile/contract/1/buyer_seal.png";
    private static final String SELLER_SIG = "/profile/contract/1/seller_sig.png";
    private static final String SELLER_SEAL = "/profile/contract/1/seller_seal.png";
    private static final String BUYER_TIME = "2026-10-21 10:00:00";
    private static final String SELLER_TIME = "2026-10-21 11:00:00";

    @Test
    @DisplayName("生成模板：含四个签名占位符与 table 双栏布局")
    void buildContractHtmlContainsPlaceholdersAndTableLayout()
    {
        String html = ContractHtmlSupport.buildContractHtml("CT001", "ORD001", "测试作品",
                "买方甲", "卖方乙", new BigDecimal("1000.00"), "非独家", "TPL_COPYRIGHT_LICENSE");
        assertTrue(html.contains("<!-- buyer_sign_overlay -->"));
        assertTrue(html.contains("<!-- seller_sign_overlay -->"));
        assertTrue(html.contains("<!-- buyer_sign_time -->"));
        assertTrue(html.contains("<!-- seller_sign_time -->"));
        assertTrue(html.contains("display: table;"));
        assertTrue(html.contains("display: table-cell;"));
        assertFalse(html.contains("display: flex"));
        assertTrue(html.contains("@page"));
        assertTrue(html.contains("CT001"));
        assertTrue(html.contains("测试作品"));
    }

    @Test
    @DisplayName("生成模板：签名区只留签字盖章空间不预填姓名，基本信息区仍展示双方姓名")
    void buildContractHtmlSignatureAreaBlank()
    {
        String html = ContractHtmlSupport.buildContractHtml("CT001", "ORD001", "测试作品",
                "买方甲", "卖方乙", new BigDecimal("1000.00"), "非独家", "TPL_COPYRIGHT_LICENSE");
        assertTrue(html.contains("<strong>甲方（买方）签字：</strong>"));
        assertTrue(html.contains("<strong>乙方（卖方）签字：</strong>"));
        // 签名区不预填姓名：strong 标记后直接跟图片占位符
        assertFalse(html.contains("签字：</strong>买方甲"));
        assertFalse(html.contains("签字：</strong>卖方乙"));
        // 姓名仍出现在基本信息区（label span 后）
        assertTrue(html.contains("甲方（买方）：</span>买方甲"));
        assertTrue(html.contains("乙方（卖方）：</span>卖方乙"));
    }

    @Test
    @DisplayName("生成模板：第三条支付文案为托管流程表述（P2-5 回归，授权/转让双分支）")
    void buildContractHtmlPaymentWording()
    {
        String license = ContractHtmlSupport.buildContractHtml("CT001", "ORD001", "测试作品",
                "买方甲", "卖方乙", new BigDecimal("1000.00"), "非独家", "TPL_COPYRIGHT_LICENSE");
        String transfer = ContractHtmlSupport.buildContractHtml("CT001", "ORD001", "测试作品",
                "买方甲", "卖方乙", new BigDecimal("1000.00"), "非独家", "TPL_COPYRIGHT_TRANSFER");
        for (String html : new String[] {license, transfer})
        {
            assertTrue(html.contains("该费用由甲方按平台托管流程支付，在订单完成后由平台结算给乙方。"));
            assertFalse(html.contains("已通过平台完成支付"));
        }
    }

    @Test
    @DisplayName("注入：新模板占位符全部被替换（双方签名+印章）")
    void injectSignaturesNewTemplateBothParties()
    {
        String html = ContractHtmlSupport.buildContractHtml("CT001", "ORD001", "测试作品",
                "买方甲", "卖方乙", new BigDecimal("1000.00"), "非独家", "TPL_COPYRIGHT_LICENSE");
        String injected = ContractHtmlSupport.injectSignatures(html, BUYER_SIG, BUYER_SEAL, SELLER_SIG, SELLER_SEAL, BUYER_TIME, SELLER_TIME);
        assertFalse(injected.contains("<!-- buyer_sign_overlay -->"));
        assertFalse(injected.contains("<!-- seller_sign_overlay -->"));
        assertTrue(injected.contains(BUYER_TIME));
        assertTrue(injected.contains(SELLER_TIME));
        assertEquals(4, injected.split("src=\"/profile/", -1).length - 1);
        // 图片出现在各自方的占位符位置（买方签名在买方 box，卖方签名在卖方 box）
        assertTrue(injected.indexOf(BUYER_SIG) < injected.indexOf(SELLER_SIG));
    }

    @Test
    @DisplayName("注入：旧模板双方签名+印章按标记定位插入")
    void injectSignaturesLegacyTemplateBothParties()
    {
        String html = legacyTemplateHtml();
        String injected = ContractHtmlSupport.injectSignatures(html, BUYER_SIG, BUYER_SEAL, SELLER_SIG, SELLER_SEAL, BUYER_TIME, SELLER_TIME);
        assertLegacyInjection(injected, BUYER_SIG, SELLER_SIG);
    }

    @Test
    @DisplayName("注入：旧模板仅买方签名（无印章），卖方不插入")
    void injectSignaturesLegacyTemplateBuyerOnly()
    {
        String html = legacyTemplateHtml();
        String injected = ContractHtmlSupport.injectSignatures(html, BUYER_SIG, null, null, null, BUYER_TIME, null);
        // 买方签名图插在买方签署时间前；卖方区无图片
        int buyerImg = injected.indexOf(BUYER_SIG);
        assertTrue(buyerImg > injected.indexOf("<strong>甲方（买方）：</strong>"));
        assertTrue(buyerImg < injected.indexOf("签署时间", injected.indexOf("<strong>甲方（买方）：</strong>")));
        assertFalse(injected.contains(SELLER_SIG));
        assertFalse(injected.contains(BUYER_SEAL));
    }

    @Test
    @DisplayName("注入：旧模板仅卖方印章（无签名）")
    void injectSignaturesLegacyTemplateSellerSealOnly()
    {
        String html = legacyTemplateHtml();
        String injected = ContractHtmlSupport.injectSignatures(html, null, null, null, SELLER_SEAL, null, SELLER_TIME);
        int sealImg = injected.indexOf(SELLER_SEAL);
        assertTrue(sealImg > injected.indexOf("<strong>乙方（卖方）：</strong>"));
        assertTrue(sealImg < injected.indexOf("签署时间", injected.indexOf("<strong>乙方（卖方）：</strong>")));
        assertFalse(injected.contains(BUYER_SIG));
    }

    @Test
    @DisplayName("注入：双方均无图片时原样返回")
    void injectSignaturesNoImagesReturnsUnchanged()
    {
        String legacy = legacyTemplateHtml();
        assertEquals(legacy, ContractHtmlSupport.injectSignatures(legacy, null, null, null, null, null, null));
        String legacyWithEmpty = legacyTemplateHtml();
        assertEquals(legacyWithEmpty,
                ContractHtmlSupport.injectSignatures(legacyWithEmpty, "", "", "", "", "", ""));
    }

    @Test
    @DisplayName("注入：模板不含任何标记时原样返回（防御性）")
    void injectSignaturesUnrecognizedTemplateReturnsUnchanged()
    {
        String plain = "<html><body>无签名区</body></html>";
        assertEquals(plain, ContractHtmlSupport.injectSignatures(plain, BUYER_SIG, null, null, null, null, null));
        // null 输入原样透传
        assertEquals(null, ContractHtmlSupport.injectSignatures(null, BUYER_SIG, null, null, null, null, null));
    }

    @Test
    @DisplayName("resolveProfileImages：/profile/ 相对路径转 file:/// 绝对路径")
    void resolveProfileImagesConvertsToAbsoluteUri()
    {
        // 纯单测无 Spring 上下文，静态 profile 字段需显式赋值
        new com.ruoyi.common.config.RuoYiConfig().setProfile("uploadPath");
        String html = "<img src=\"/profile/contract/1/a.png\" /><img src=\"/profile/contract/1/b.png\" />";
        String resolved = ContractHtmlSupport.resolveProfileImages(html);
        assertTrue(resolved.startsWith("<img src=\"file:///"));
        assertFalse(resolved.contains("src=\"/profile/"));
        assertEquals(2, resolved.split("file:///", -1).length - 1);
        // 非 profile 图片不受影响
        assertEquals("<img src=\"http://x/y.png\" />",
                ContractHtmlSupport.resolveProfileImages("<img src=\"http://x/y.png\" />"));
    }

    /** 管理端历史旧模板样例：签名区有 strong 标记，无占位注释。 */
    private static String legacyTemplateHtml()
    {
        return "<html><body>"
                + "<div class=\"info-row\"><span class=\"label\">甲方（买方）：</span>买方甲</div>"
                + "<div class=\"info-row\"><span class=\"label\">乙方（卖方）：</span>卖方乙</div>"
                + "<div class=\"signature\">"
                + "<div class=\"signature-row\">"
                + "<div class=\"signature-box\"><strong>甲方（买方）：</strong>买方甲"
                + "<br/>签署时间：___________</div>"
                + "<div class=\"signature-box\"><strong>乙方（卖方）：</strong>卖方乙"
                + "<br/>签署时间：___________</div>"
                + "</div>"
                + "</div>"
                + "</body></html>";
    }

    /** 旧模板断言：图片位于对应方 strong 标记之后、该方"签署时间"之前；不落回基本信息区。 */
    private static void assertLegacyInjection(String injected, String buyerSig, String sellerSig)
    {
        assertFalse(injected.contains("<!-- buyer_signature_placeholder -->"));
        int buyerStrong = injected.indexOf("<strong>甲方（买方）：</strong>");
        int buyerSignTime = injected.indexOf("签署时间", buyerStrong);
        int sellerStrong = injected.indexOf("<strong>乙方（卖方）：</strong>");
        int sellerSignTime = injected.indexOf("签署时间", sellerStrong);
        assertTrue(injected.indexOf(buyerSig) > buyerStrong);
        assertTrue(injected.indexOf(buyerSig) < buyerSignTime);
        assertTrue(injected.indexOf(sellerSig) > sellerStrong);
        assertTrue(injected.indexOf(sellerSig) < sellerSignTime);
    }
}
