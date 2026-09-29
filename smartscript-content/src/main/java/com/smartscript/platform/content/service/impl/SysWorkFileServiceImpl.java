package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.SysWorkFile;
import com.smartscript.platform.content.domain.SysWorkVersion;
import com.smartscript.platform.content.mapper.SysWorkFileMapper;
import com.smartscript.platform.content.mapper.SysWorkVersionMapper;
import com.smartscript.platform.content.service.ISysWorkFileService;

/**
 * 作品文件 / 作品版本 服务层处理
 *
 * 依据：云端 script_platform_dev 库 sys_work_file + sys_work_version 表。
 * 作品上传资料查看页：PC 后台浏览作品相关文件及历史版本。
 *
 * @author xiangsipeng
 */
@Service
public class SysWorkFileServiceImpl implements ISysWorkFileService
{
    @Autowired
    private SysWorkFileMapper workFileMapper;

    @Autowired
    private SysWorkVersionMapper workVersionMapper;

    @Override
    public List<SysWorkFile> selectFileList(SysWorkFile query)
    {
        return workFileMapper.selectFileList(query);
    }

    @Override
    public SysWorkFile selectFileById(Long fileId)
    {
        return workFileMapper.selectFileById(fileId);
    }

    @Override
    public List<SysWorkVersion> selectVersionList(SysWorkVersion query)
    {
        return workVersionMapper.selectVersionList(query);
    }

    @Override
    public SysWorkVersion selectVersionById(Long versionId)
    {
        return workVersionMapper.selectVersionById(versionId);
    }
}
