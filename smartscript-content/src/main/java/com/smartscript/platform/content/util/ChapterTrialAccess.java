package com.smartscript.platform.content.util;

import com.smartscript.platform.content.domain.SysWorkChapter;

/** 章节明确关闭优先于旧作品配置的试读范围；版权授权独立判断。 */
public final class ChapterTrialAccess
{
    public static final String EXPLICITLY_CLOSED = "2";

    private ChapterTrialAccess()
    {
    }

    public static boolean isOpen(SysWorkChapter chapter, String workPreviewEnabled, Integer workPreviewEpisodes)
    {
        if (!WorkStatusDeriver.isChapterReaderVisible(chapter.getStatus())
                || EXPLICITLY_CLOSED.equals(chapter.getIsFree()))
        {
            return false;
        }
        return "1".equals(chapter.getIsFree())
                || ("1".equals(workPreviewEnabled) && workPreviewEpisodes != null && workPreviewEpisodes > 0
                    && chapter.getChapterNo() != null && chapter.getChapterNo() <= workPreviewEpisodes);
    }
}
