package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysWork;

/**
 * 书城作品管理 服务层
 *
 * 依据：云端 script_platform_dev 库 sys_work 表 + PC 功能清单（书城作品管理页：
 * 列表、详情、上下架状态、交易开关、扩展JSON 调整；清单未列删除，故不提供）。
 * 复用 SysWorkMapper（书城与作品管理共享 sys_work 表），不单建 BookstoreMapper。
 *
 * @author xiangsipeng
 */
public interface ISysBookstoreService
{
    /**
     * 书城作品列表（含从 ext_json 解析的 recommendStatus/showScope）
     *
     * @param query 查询条件（title 模糊、recommendStatus 走 JSON_EXTRACT、status/tradeEnabled 精确）
     * @return 作品集合
     */
    public List<SysWork> selectBookstoreList(SysWork query);

    /**
     * 书城作品详情（含从 ext_json 解析的 recommendStatus/showScope）
     *
     * @param workId 作品ID
     * @return 作品对象
     */
    public SysWork selectBookstoreById(Long workId);

    /**
     * 修改作品上下架状态（只更新 status 字段）
     *
     * @param work 仅携带 workId + status + updateBy
     * @return 影响行数
     */
    public int updateWorkStatus(SysWork work);

    /**
     * 修改作品交易开关（只更新 trade_enabled 字段）
     *
     * @param work 仅携带 workId + tradeEnabled + updateBy
     * @return 影响行数
     */
    public int updateWorkTradeEnabled(SysWork work);

    /**
     * 修改作品扩展JSON（整体替换 ext_json）
     *
     * @param work 仅携带 workId + extJson + updateBy
     * @return 影响行数
     */
    public int updateWorkExtJson(SysWork work);
}
