package com.smartscript.platform.trade.support;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.web.util.HtmlUtils;

/**
 * 合同正文条款富文本文档。
 * <p>
 * 仅带版本标记的内容解释为富文本（编辑器产出的 HTML），历史纯文本保持原样按行渲染，
 * 因此老数据无需迁移。
 * <p>
 * 富文本会进 iframe 预览、APP WebView 与 openhtmltopdf，前端净化只是体验层，
 * 服务端在入库与渲染两处都过一次标签 + style 属性双白名单，防止存储型 XSS 与脏标签。
 */
public final class ContractClauseDocument
{
    private static final String PREFIX = "<div data-contract-document=\"1\">";
    private static final String SUFFIX = "</div>";

    /** 富文本 HTML 字节上限：clauses_text / contract_content 均为 TEXT（65535 字节），给信息区与签名区留余量。 */
    public static final int MAX_HTML_BYTES = 40000;

    /** 标签白名单：编辑器可产出且 openhtmltopdf 可渲染的标签。 */
    private static final String[] ALLOWED_TAGS = {
            "div", "p", "br", "span", "strong", "em", "u", "s", "sub", "sup",
            "h1", "h2", "h3", "ol", "ul", "li", "blockquote" };

    /** style 声明白名单：jsoup 的 Safelist 只能整体放行 style，CSS 属性名再单独筛一遍。 */
    private static final Set<String> ALLOWED_STYLE_PROPERTIES = new LinkedHashSet<>(Arrays.asList(
            "font-size", "font-weight", "font-style", "font-family", "text-decoration",
            "text-align", "text-indent", "line-height", "color", "background-color"));

    private static final Safelist ALLOWED_TAGS_WITH_STYLE = buildSafelist();

    private ContractClauseDocument()
    {
    }

    private static Safelist buildSafelist()
    {
        Safelist safelist = Safelist.none();
        for (String tag : ALLOWED_TAGS)
        {
            safelist.addTags(tag);
            safelist.addAttributes(tag, "style");
        }
        safelist.addAttributes("ol", "start");
        safelist.addAttributes("li", "value");
        return safelist;
    }

    /** 是否富文本条款（带版本标记）。 */
    public static boolean isFormatted(String clausesText)
    {
        return clausesText != null && clausesText.startsWith(PREFIX) && clausesText.endsWith(SUFFIX);
    }

    /**
     * 净化富文本条款并回填版本标记，得到可入库的规范串；非富文本原样返回。
     * <p>
     * 本方法不抛长度异常，字节上限由调用方判定，避免渲染历史超长正文时合同整体不可读。
     */
    public static String sanitize(String clausesText)
    {
        if (!isFormatted(clausesText))
        {
            return clausesText;
        }
        return PREFIX + sanitizeBody(inner(clausesText)) + SUFFIX;
    }

    /** 净化后的条款正文片段（不含版本标记外壳），空内容返回空串。 */
    public static String bodyHtml(String clausesText)
    {
        if (!isFormatted(clausesText))
        {
            return "";
        }
        return sanitizeBody(inner(clausesText));
    }

    /** 富文本条款的纯文本形态，用于字数口径校验；非富文本原样返回。 */
    public static String text(String clausesText)
    {
        if (!isFormatted(clausesText))
        {
            return clausesText;
        }
        // 以净化后的片段为口径，白名单外标签（含脚本内容）不计入正文字数
        String text = bodyHtml(clausesText)
                .replaceAll("(?i)<br\\s*/?>|</(?:p|div|h[1-6]|li|blockquote)>", "\n")
                .replaceAll("<[^>]*>", "");
        return HtmlUtils.htmlUnescape(text).stripTrailing();
    }

    /** 富文本 HTML 实际字节数（UTF-8），供 TEXT 列容量校验。 */
    public static int htmlBytes(String clausesText)
    {
        return clausesText == null ? 0 : clausesText.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String inner(String clausesText)
    {
        return clausesText.substring(PREFIX.length(), clausesText.length() - SUFFIX.length());
    }

    private static String sanitizeBody(String html)
    {
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String cleaned = Jsoup.clean(html, "", ALLOWED_TAGS_WITH_STYLE, outputSettings);
        Document document = Jsoup.parseBodyFragment(cleaned);
        document.outputSettings().prettyPrint(false);
        filterStyles(document.body());
        return document.body().html().trim();
    }

    /** 逐个元素裁剪 style：只保留白名单属性，且丢弃引用外部资源的取值。 */
    private static void filterStyles(Element root)
    {
        for (Element element : root.getAllElements())
        {
            String style = element.attr("style");
            if (style == null || style.isEmpty())
            {
                continue;
            }
            StringBuilder kept = new StringBuilder();
            for (String declaration : style.split(";"))
            {
                int colon = declaration.indexOf(':');
                if (colon <= 0)
                {
                    continue;
                }
                String property = declaration.substring(0, colon).trim().toLowerCase();
                String value = declaration.substring(colon + 1).trim();
                if (!ALLOWED_STYLE_PROPERTIES.contains(property) || value.isEmpty()
                        || value.contains("url(") || value.contains("<") || value.contains("@"))
                {
                    continue;
                }
                kept.append(property).append(':').append(value).append(';');
            }
            if (kept.length() == 0)
            {
                element.removeAttr("style");
            }
            else
            {
                element.attr("style", kept.toString());
            }
        }
    }
}
