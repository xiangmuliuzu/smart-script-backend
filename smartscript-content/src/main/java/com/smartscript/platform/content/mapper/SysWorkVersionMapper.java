package com.smartscript.platform.content.mapper;

import java.util.List;
import com.smartscript.platform.content.domain.SysWorkVersion;

/**
 * 作品版本 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_work_version 表。
 * 反推处理点：creatorName 通过 left join sys_user 取 nickname 扩展返回。
 *
 * @author xiangsipeng
 */
public interface SysWorkVersionMapper
{
    /**
     * 查询作品版本列表（不含 content 全文，避免 longtext 拉全表回列表）
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
