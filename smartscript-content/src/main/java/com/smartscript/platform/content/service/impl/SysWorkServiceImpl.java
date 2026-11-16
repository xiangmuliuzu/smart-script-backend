package com.smartscript.platform.content.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.dto.AppWorkTagDto;
import com.smartscript.platform.content.dto.CopyrightAssetDto;
import com.smartscript.platform.content.dto.CopyrightAssetQuery;
import com.smartscript.platform.content.mapper.SysTagMapper;
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

    @Autowired
    private SysTagMapper tagMapper;

    @Override
    public List<SysWork> selectWorkList(SysWork query)
    {
        List<SysWork> works = workMapper.selectWorkList(query);
        fillTags(works);
        return works;
    }

    @Override
    public SysWork selectWorkById(Long workId)
    {
        SysWork work = workMapper.selectWorkById(workId);
        if (work != null)
        {
            fillTags(List.of(work));
        }
        return work;
    }

    /** 批量回填作品标签，避免列表逐作品查询造成 N+1 */
    private void fillTags(List<SysWork> works)
    {
        if (works == null || works.isEmpty())
        {
            return;
        }
        List<Long> workIds = works.stream()
                .map(SysWork::getWorkId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (workIds.isEmpty())
        {
            return;
        }
        Map<Long, List<AppWorkTagDto>> grouped = tagMapper.selectWorkTagRows(workIds).stream()
                .collect(Collectors.groupingBy(AppWorkTagDto::getWorkId));
        for (SysWork work : works)
        {
            work.setTags(grouped.getOrDefault(work.getWorkId(), List.of()));
        }
    }

    @Override
    public List<SysWorkChapter> selectChapterListByWorkId(SysWorkChapter query)
    {
        return chapterMapper.selectChapterListByWorkId(query);
    }

    @Override
    public List<CopyrightAssetDto> selectCopyrightAssetList(CopyrightAssetQuery query)
    {
        return workMapper.selectCopyrightAssetList(query);
    }

    @Override
    public CopyrightAssetDto selectCopyrightAssetById(Long workId)
    {
        return selectCopyrightAssetById(workId, null, null);
    }

    @Override
    public CopyrightAssetDto selectCopyrightAssetById(Long workId, Long authorId, Boolean onlyPublished)
    {
        return workMapper.selectCopyrightAssetById(workId, authorId, onlyPublished);
    }

    @Override
    public int updateCopyrightAssetStatus(Long workId, String status, String updateBy)
    {
        return updateCopyrightAssetStatus(workId, status, updateBy, null);
    }

    @Override
    public int updateCopyrightAssetStatus(Long workId, String status, String updateBy, Long authorId)
    {
        return workMapper.updateCopyrightAssetStatus(workId, status, updateBy, authorId);
    }
}


