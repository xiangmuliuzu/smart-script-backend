package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysWorkFile;
import com.smartscript.platform.content.domain.SysWorkVersion;

/**
 * 作品文件 / 作品版本 服务层
 *
 * 依据：云端 script_platform_dev 库 sys_work_file + sys_work_version 表。
 * 作品上传资料查看页：PC 后台浏览作品相关文件及历史版本。
 *
 * @author xiangsipeng
 */
public interface ISysWorkFileService
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
     * 查询作品版本列表（不含 content 全文）
     *
     * @param query 查询条件（workId 必填）
     * @return 作品版本集合（content 字段为 null）
     */
    public List<SysWorkVersion> selectVersionList(SysWorkVersion query);

    /**
     * 通过版本ID查询作品版本详情（含 content 与 file_url 全字段）
     *
     * @param versionId 版本ID
     * @return 作品版本对象
     */
    public SysWorkVersion selectVersionById(Long versionId);
}
