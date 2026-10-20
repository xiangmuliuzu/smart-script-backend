package com.smartscript.platform.review.util;

import org.springframework.web.util.HtmlUtils;

/**
 * 章节正文转纯文本（审核展示用）。
 * 与 content 模块 ScriptDocument 解析口径一致：带版本标记的富文本提取纯文本，
 * 历史正文原样返回。此处独立实现，避免 review 模块跨依赖 content 模块。
 */
public final class ReviewContentText {

    private static final String PREFIX = "<div data-script-document=\"1\">";

    private ReviewContentText() {
    }

    public static boolean isFormatted(String content) {
        return content != null && content.startsWith(PREFIX) && content.endsWith("</div>");
    }

    public static String plain(String content) {
        if (content == null) {
            return "";
        }
        if (!isFormatted(content)) {
            return content;
        }
        String text = content.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>|</(?:p|div|h[1-6]|li|blockquote)>", "\n")
                .replaceAll("<[^>]*>", "");
        return HtmlUtils.htmlUnescape(text).stripTrailing();
    }
}
