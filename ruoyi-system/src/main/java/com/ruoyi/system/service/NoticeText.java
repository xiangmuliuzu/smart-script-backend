package com.ruoyi.system.service;

import org.springframework.web.util.HtmlUtils;

/** 历史 HTML 公告转换为可读文本；客户端仍必须使用文本渲染。 */
public final class NoticeText
{
    private NoticeText() { }

    public static String plainText(String content)
    {
        if (content == null) return "";
        if (!content.matches("(?s).*<[a-zA-Z][^>]*>.*")) return content.strip();
        String text = content.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>", "")
                .replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("(?i)<br\\s*/?>|</(?:p|div|li|h[1-6]|tr)\\s*>", "\n")
                .replaceAll("(?s)<[^>]*>", "");
        return HtmlUtils.htmlUnescape(text).replace('\u00a0', ' ').strip();
    }
}
