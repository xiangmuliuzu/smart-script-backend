package com.smartscript.platform.content.domain;

import org.springframework.web.util.HtmlUtils;

/** 剧本编辑器文档。仅带版本标记的内容解释为富文本，历史正文保持原样。 */
public final class ScriptDocument
{
    private static final String PREFIX = "<div data-script-document=\"1\">";

    private ScriptDocument()
    {
    }

    public static boolean isFormatted(String content)
    {
        return content != null && content.startsWith(PREFIX) && content.endsWith("</div>");
    }

    public static String text(String content)
    {
        if (!isFormatted(content))
        {
            return content;
        }
        String text = content.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>|</(?:p|div|h[1-6]|li|blockquote)>", "\n")
                .replaceAll("<[^>]*>", "");
        return HtmlUtils.htmlUnescape(text).stripTrailing();
    }
}
