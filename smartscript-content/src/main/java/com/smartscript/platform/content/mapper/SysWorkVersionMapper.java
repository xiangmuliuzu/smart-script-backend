package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
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

    // ==================== 上传与创作（App 接口文档 2.9.7~2.9.9） ====================

    /**
     * 取作品当前最大版本号（App 接口文档 2.9.8）
     *
     * version_no 为 varchar，按数值比较取最大值；无版本时返回 0。
     *
     * @param workId 作品ID
     * @return 当前最大版本号数值
     */
    public Integer selectMaxVersionNo(@Param("workId") Long workId);

    /**
     * 清除作品的「当前版本」标记（新建版本前调用，单一职责：仅改 is_current）
     *
     * @param workId 作品ID
     * @return 影响行数
     */
    public int clearCurrentFlag(@Param("workId") Long workId);

    /**
     * 新增作品版本（App 接口文档 2.9.8）
     *
     * @param version 版本对象（version_no 由服务层生成；creator_id 取当前身份）
     * @return 影响行数（主键回填 versionId）
     */
    public int insertVersion(SysWorkVersion version);
}
