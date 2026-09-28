package com.smartscript.platform.content.mapper;

import java.util.List;
import com.smartscript.platform.content.domain.SysWorkChapter;

/**
 * 作品章节 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_work_chapter 表。
 * 作品管理页"章节查看"只读使用。
 *
 * @author xiangsipeng
 */
public interface SysWorkChapterMapper
{
    /**
     * 按作品ID查询章节列表（分页，不含 content 全文）
     *
     * @param query 查询条件（workId 必传）
     * @return 章节集合
     */
    public List<SysWorkChapter> selectChapterListByWorkId(SysWorkChapter query);
}
