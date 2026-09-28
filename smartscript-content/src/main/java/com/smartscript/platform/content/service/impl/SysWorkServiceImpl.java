package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.service.ISysWorkService;

/**
 * 作品（只读） 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_work + sys_work_chapter 表。
 * 反推处理点：作品管理页只读，写操作走书城作品服务 SysBookstoreService；
 * 章节列表查询委托 SysWorkChapterMapper。
 *
 * @author xiangsipeng
 */
@Service
public class SysWorkServiceImpl implements ISysWorkService
{
    @Autowired
    private SysContentWorkMapper workMapper;

    @Autowired
    private SysWorkChapterMapper chapterMapper;

    @Override
    public List<SysWork> selectWorkList(SysWork query)
    {
        return workMapper.selectWorkList(query);
    }

    @Override
    public SysWork selectWorkById(Long workId)
    {
        return workMapper.selectWorkById(workId);
    }

    @Override
    public List<SysWorkChapter> selectChapterListByWorkId(SysWorkChapter query)
    {
        return chapterMapper.selectChapterListByWorkId(query);
    }
}
