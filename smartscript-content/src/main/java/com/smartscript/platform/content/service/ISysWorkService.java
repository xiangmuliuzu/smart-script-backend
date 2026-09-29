package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysWorkChapter;

/**
 * 作品（只读） 服务层
 *
 * 依据：云端 script_platform_dev 库 sys_work + sys_work_chapter 表 + PC 功能清单
 * （作品管理页：列表、详情、章节查看；清单未列作品新增/编辑/删除，作品录入走
 * 创作端，故本服务只读，写操作归书城作品服务 SysBookstoreService）。
 *
 * @author xiangsipeng
 */
public interface ISysWorkService
{
    /**
     * 查询作品列表（含 authorName/genreName）
     *
     * @param query 查询条件（title 模糊、authorId/genreId/status/workType/tradeType 精确，按需传入）
     * @return 作品集合
     */
    public List<SysWork> selectWorkList(SysWork query);

    /**
     * 通过作品ID查询详情（含 authorName/genreName）
     *
     * @param workId 作品ID
     * @return 作品对象
     */
    public SysWork selectWorkById(Long workId);

    /**
     * 按作品ID查询章节列表（分页，不含 content 全文）
     *
     * @param query 查询条件（workId 必传）
     * @return 章节集合
     */
    public List<SysWorkChapter> selectChapterListByWorkId(SysWorkChapter query);
}
