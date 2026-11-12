package com.smartscript.platform.trade.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ContractClauseDocument 纯函数单测：标记识别、白名单净化、纯文本口径，以及富文本条款的合同渲染。
 */
class ContractClauseDocumentTest
{
    @Test
    @DisplayName("标记识别：无标记的历史纯文本判为纯文本")
    void plainTextIsNotFormatted()
    {
        assertFalse(ContractClauseDocument.isFormatted("第一条 合同标的\n甲乙双方经友好协商。"));
        assertFalse(ContractClauseDocument.isFormatted(null));
        assertFalse(ContractClauseDocument.isFormatted("<p>不是我们的文档</p>"));
        assertTrue(ContractClauseDocument.isFormatted("<div data-contract-document=\"1\"><p>x</p></div>"));
    }

    @Test
    @DisplayName("净化：脚本、事件属性、图片与外链样式全部剔除，排版标签保留")
    void sanitizeKeepsLayoutAndDropsDangerousMarkup()
    {
        String risky = "<div data-contract-document=\"1\">"
                + "<h3 style=\"font-size:15px;text-align:center\">第一条 合同标的</h3>"
                + "<p onclick=\"steal()\"><strong>甲方</strong>应向乙方支付<script>alert(1)</script></p>"
                + "<img src=\"http://evil/x.png\" /><a href=\"javascript:1\">链接</a>"
                + "<p style=\"background:url(http://evil/y.png)\">被污染的段落</p>"
                + "<iframe src=\"http://evil\"></iframe>"
                + "<ul><li>1. 乙方保证著作权完整</li></ul>"
                + "</div>";
        String clean = ContractClauseDocument.sanitize(risky);
        assertTrue(ContractClauseDocument.isFormatted(clean));
        assertTrue(clean.contains("<h3 style=\"font-size:15px;text-align:center;\">第一条 合同标的</h3>"));
        assertTrue(clean.contains("<strong>甲方</strong>"));
        assertTrue(clean.contains("<ul><li>1. 乙方保证著作权完整</li></ul>"));
        assertFalse(clean.contains("script"));
        assertFalse(clean.contains("onclick"));
        assertFalse(clean.contains("<img"));
        assertFalse(clean.contains("<a"));
        assertFalse(clean.contains("<iframe"));
        assertFalse(clean.contains("url("));
        assertFalse(clean.contains("javascript"));
    }

    @Test
    @DisplayName("净化：style 只留排版属性，脚本表达式等属性名被丢弃")
    void sanitizeFiltersStyleProperties()
    {
        String styled = "<div data-contract-document=\"1\">"
                + "<p style=\"font-size:18px;behavior:url(x.htc);-moz-binding:url(x);color:#c00;line-height:2\">条款</p>"
                + "</div>";
        String clean = ContractClauseDocument.sanitize(styled);
        assertTrue(clean.contains("font-size:18px;"));
        assertTrue(clean.contains("color:#c00;"));
        assertTrue(clean.contains("line-height:2;"));
        assertFalse(clean.contains("behavior"));
        assertFalse(clean.contains("binding"));
    }

    @Test
    @DisplayName("净化：纯文本条款原样透传，空富文本净化后仍为空片段")
    void sanitizePassesThroughPlainAndHandlesEmptyBody()
    {
        String plain = "第一条 合同标的\n甲乙双方经友好协商。";
        assertEquals(plain, ContractClauseDocument.sanitize(plain));
        assertEquals("", ContractClauseDocument.bodyHtml(plain));
        String emptyRich = "<div data-contract-document=\"1\"><script>doBad()</script></div>";
        assertEquals("", ContractClauseDocument.bodyHtml(emptyRich));
        assertEquals("", ContractClauseDocument.text(emptyRich));
    }

    @Test
    @DisplayName("纯文本口径：富文本按块断行且实体还原，供字数校验")
    void textExtractsReadablePlainText()
    {
        String marked = "<div data-contract-document=\"1\"><h3>第一条 合同标的</h3>"
                + "<p>甲方&lt;乙方&gt;支付&nbsp;1000 元</p></div>";
        String text = ContractClauseDocument.text(marked);
        assertTrue(text.contains("第一条 合同标的\n"));
        assertTrue(text.contains("甲方<乙方>支付"));
        assertFalse(text.contains("<h3>"));
    }

    @Test
    @DisplayName("渲染：富文本条款整段落位并带上 clause-rich 容器与排版样式")
    void buildContractHtmlRendersRichClauses()
    {
        String marked = "<div data-contract-document=\"1\">"
                + "<h3 style=\"text-align:center\">第一条 合同标的</h3>"
                + "<p><strong>甲乙双方</strong>经友好协商。</p></div>";
        String html = ContractHtmlSupport.buildContractHtml("CT001", "ORD001", "测试作品",
                "买方甲", "卖方乙", new BigDecimal("1000.00"), "非独家", "TPL_COPYRIGHT_LICENSE", marked);
        assertTrue(html.contains("<div class=\"section clause-rich\">"));
        assertTrue(html.contains("<h3 style=\"text-align:center;\">第一条 合同标的</h3>"));
        assertTrue(html.contains("<strong>甲乙双方</strong>经友好协商。"));
        // 富文本分支不再逐行包装 content，也不回退默认条款
        assertFalse(html.contains("甲乙双方经友好协商，就作品《测试作品》"));
    }

    @Test
    @DisplayName("渲染：富文本净化后为空时不落条款区，历史纯文本仍逐行渲染")
    void buildContractHtmlFallsBackForLegacy()
    {
        String emptyRich = "<div data-contract-document=\"1\"><iframe src=\"http://evil\"></iframe></div>";
        String richHtml = ContractHtmlSupport.buildContractHtml("CT001", "ORD001", "测试作品",
                "买方甲", "卖方乙", new BigDecimal("1000.00"), "非独家", "TPL_COPYRIGHT_LICENSE", emptyRich);
        assertFalse(richHtml.contains("section clause-rich"));
        assertFalse(richHtml.contains("<iframe"));

        String legacyHtml = ContractHtmlSupport.buildContractHtml("CT001", "ORD001", "测试作品",
                "买方甲", "卖方乙", new BigDecimal("1000.00"), "非独家", "TPL_COPYRIGHT_LICENSE",
                "第一条 合同标的\n甲乙双方经友好协商。");
        assertTrue(legacyHtml.contains("<div class=\"section-title\">第一条 合同标的</div>"));
        assertTrue(legacyHtml.contains("<div class=\"content\">甲乙双方经友好协商。</div>"));
        assertFalse(legacyHtml.contains("section clause-rich"));
    }

    @Test
    @DisplayName("渲染：富文本条款在签署重渲染链路（回喂 clauses_text）中保持格式")
    void richClausesSurviveRerender()
    {
        String marked = ContractClauseDocument.sanitize("<div data-contract-document=\"1\">"
                + "<p style=\"font-size:20px\">第三条 合同金额及支付</p></div>");
        String first = ContractHtmlSupport.buildContractHtml("CT001", "ORD001", "测试作品",
                "买方甲", "卖方乙", new BigDecimal("1000.00"), "非独家", "TPL_COPYRIGHT_LICENSE", marked);
        String again = ContractHtmlSupport.buildContractHtml("CT001", "ORD001", "测试作品",
                "买方实名", "卖方实名", new BigDecimal("1000.00"), "非独家", "TPL_COPYRIGHT_LICENSE", marked);
        assertTrue(first.contains("<p style=\"font-size:20px;\">第三条 合同金额及支付</p>"));
        assertTrue(again.contains("<p style=\"font-size:20px;\">第三条 合同金额及支付</p>"));
        // 重渲染只换信息区姓名，条款区两次一致
        String clauseStart = "section clause-rich";
        String signatureStart = "<div class=\"signature\">";
        assertEquals(first.substring(first.indexOf(clauseStart), first.indexOf(signatureStart)),
                again.substring(again.indexOf(clauseStart), again.indexOf(signatureStart)));
    }

    @Test
    @DisplayName("体积：HTML 字节数按 UTF-8 计，供 TEXT 列容量校验")
    void htmlBytesUsesUtf8()
    {
        assertEquals(0, ContractClauseDocument.htmlBytes(null));
        assertEquals(3, ContractClauseDocument.htmlBytes("abc"));
        assertTrue(ContractClauseDocument.htmlBytes("中文") > 4);
    }

    @Test
    @DisplayName("PDF：富文本条款经 openhtmltopdf 渲染成功出件")
    void richClausesRenderToPdf() throws Exception
    {
        String marked = ContractClauseDocument.sanitize("<div data-contract-document=\"1\">"
                + "<h2 style=\"text-align:center\">第一条 合同标的</h2>"
                + "<p style=\"font-size:18px;line-height:2\"><strong>甲乙双方</strong>经友好协商，"
                + "就作品《测试作品》的<em>授权</em>达成一致。</p>"
                + "<ol><li>乙方保证著作权完整</li><li>甲方按托管流程支付</li></ol>"
                + "<blockquote>协商不成的可向平台所在地法院起诉</blockquote></div>");
        String html = ContractHtmlSupport.buildContractHtml("CT001", "ORD001", "测试作品",
                "买方甲", "卖方乙", new BigDecimal("1000.00"), "独家授权", "TPL_COPYRIGHT_LICENSE", marked);
        byte[] pdf = new ContractPdfRenderer().render(html);
        assertTrue(pdf.length > 1000, "PDF 体积过小，疑似未出件");
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }
}
