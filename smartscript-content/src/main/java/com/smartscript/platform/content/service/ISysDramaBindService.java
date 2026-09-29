package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysExternalDrama;

/**
 * 外部视频绑定管理 服务层
 *
 * 依据：云端 script_platform_dev 库 sys_external_drama 表（related_work_id 字段）。
 * 反推处理点：绑定/解绑独立成接口，与内容管理/状态管理解耦。
 *
 * @author xiangsipeng
 */
public interface ISysDramaBindService
{
    /**
     * 查询绑定列表（含 relatedWorkTitle；可设 unboundOnly="1" 仅查未绑定记录）
     *
     * @param query 查询条件（channelId/title 模糊/unboundOnly，按需传入）
     * @return 外部视频集合
     */
    public List<SysExternalDrama> selectBindList(SysExternalDrama query);

    /**
     * 通过短剧ID查询详情（用于绑定前的 drama 信息回显）
     *
     * @param dramaId 短剧ID
     * @return 外部视频对象
     */
    public SysExternalDrama selectDramaById(Long dramaId);

    /**
     * 绑定作品（单一职责：仅改 related_work_id）
     *
     * @param drama 仅携带 dramaId + relatedWorkId + updateBy
     * @return 影响行数
     */
    public int bindWork(SysExternalDrama drama);

    /**
     * 解绑作品（将 related_work_id 置为 NULL）。
     * service 内部构造 SysExternalDrama：dramaId + relatedWorkId=null + updateBy，
     * 调用 updateRelatedWorkId（XML SET related_work_id = #{relatedWorkId} 会置 NULL）。
     *
     * @param drama 仅携带 dramaId + updateBy（relatedWorkId 由 service 置 null）
     * @return 影响行数
     */
    public int unbindWork(SysExternalDrama drama);
}
