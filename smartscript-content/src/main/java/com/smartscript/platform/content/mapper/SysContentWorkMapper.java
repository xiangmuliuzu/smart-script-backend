package com.smartscript.platform.content.mapper;

import java.util.List;
import com.smartscript.platform.content.domain.SysWork;

/**
 * 作品 数据层
 *
 * 依据：云端 script_platform_dev 库 sys_work 表。
 * 同时服务于作品管理（只读）和书城作品管理（写 status/trade_enabled/ext_json）。
 *
 * 命名说明：原 SysWorkMapper 与 smartscript-trade 模块的 SysWorkMapper 类名短名相同，
 * MyBatis MapperScanner 按类名注册 bean（sysWorkMapper）导致冲突启动失败，
 * 故 content 模块改名为 SysContentWorkMapper，bean 名 sysContentWorkMapper 唯一。
 *
 * @author xiangsipeng
 */
public interface SysContentWorkMapper
{
    /**
     * 查询作品列表（含 authorName/genreName，JOIN sys_user + sys_category）
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
     * 修改作品上下架状态（单一职责：仅改 status）
     *
     * @param work 仅携带 workId + status + updateBy
     * @return 影响行数
     */
    public int updateWorkStatus(SysWork work);

    /**
     * 修改作品交易开关（单一职责：仅改 trade_enabled）
     *
     * @param work 仅携带 workId + tradeEnabled + updateBy
     * @return 影响行数
     */
    public int updateWorkTradeEnabled(SysWork work);

    /**
     * 修改作品扩展JSON（单一职责：整体替换 ext_json）
     *
     * @param work 仅携带 workId + extJson + updateBy
     * @return 影响行数
     */
    public int updateWorkExtJson(SysWork work);
}
