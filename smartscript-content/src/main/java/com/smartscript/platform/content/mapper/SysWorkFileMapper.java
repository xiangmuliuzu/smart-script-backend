package com.smartscript.platform.content.mapper;

import java.util.List;
import com.smartscript.platform.content.domain.SysWorkFile;

/**
 * 作品文件 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_work_file 表。
 *
 * @author xiangsipeng
 */
public interface SysWorkFileMapper
{
    /**
     * 查询作品文件列表
     *
     * @param query 查询条件（workId 精确、fileType 精确、isPreview 精确，按需传入）
     * @return 作品文件集合
     */
    public List<SysWorkFile> selectFileList(SysWorkFile query);

    /**
     * 通过文件ID查询作品文件
     *
     * @param fileId 文件ID
     * @return 作品文件对象
     */
    public SysWorkFile selectFileById(Long fileId);

    /**
     * 查询作品的可预览文件（is_preview=1，按 sort 升序）
     *
     * App 端免费试读附件用；只读。
     *
     * @param workId 作品ID
     * @return 可预览文件集合
     */
    public List<SysWorkFile> selectPreviewFilesByWorkId(Long workId);
}
