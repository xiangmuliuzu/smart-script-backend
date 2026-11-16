package com.smartscript.platform.content.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class ScriptDocumentTest
{
    @Test
    void formattedStorageKeepsPlainReadingAndRestorableFormatting() throws Exception
    {
        String html = "<div data-script-document=\"1\"><p><strong>林深</strong>：&lt;重逢&gt;</p><p style=\"text-align:center\">雨夜</p></div>";
        SysWorkChapter chapter = new SysWorkChapter();
        chapter.setStorageContent(html);
        assertEquals(html, chapter.getStorageContent());
        assertEquals(html, chapter.getFormattedContent());
        assertEquals("林深：<重逢>\n雨夜", chapter.getContent());
        var json = new ObjectMapper().valueToTree(chapter);
        assertEquals(chapter.getContent(), json.get("content").asText());
        assertEquals(html, json.get("formattedContent").asText());
        assertNull(json.get("storageContent"));
    }

    @Test
    void legacyTextIsNeverInterpretedAsMarkup()
    {
        SysWorkChapter chapter = new SysWorkChapter();
        String text = "人物：<林深>\n<script>剧本文字</script>\nA & B";
        chapter.setContent(text);
        assertEquals(text, chapter.getContent());
        assertEquals(text, chapter.getStorageContent());
        assertNull(chapter.getFormattedContent());
        assertNull(ScriptDocument.text(null));
    }
}
