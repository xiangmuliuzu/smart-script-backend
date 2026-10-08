package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.dto.AppRankingItem;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.dto.AppWorkQuery;

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
     * 用户端「我的作品」列表（含 authorName/genreName，按更新时间倒序）
     *
     * 固定条件：is_deleted = 0，作者为当前用户。
     * 审核派生状态由服务层依据 sys_review_record 最新记录计算，此处不参与过滤。
     *
     * @param authorId 作者用户ID
     * @return 作品集合
     */
    public List<SysWork> selectWorkListByAuthor(@Param("authorId") Long authorId);

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

    /**
     * 书城作品列表（App 侧只读）
     *
     * 固定条件：is_deleted = 0 且 status = 'on_shelf'（书城只展示已上架作品）。
     *
     * @param query 查询条件（keyword 模糊、categoryId 精确、tagId 走 sys_work_tag EXISTS、sort 决定排序）
     * @return 作品集合（AppWorkDto，分页由调用方 PageHelper 驱动）
     */
    public List<AppWorkDto> selectAppWorkList(AppWorkQuery query);

    /**
     * 书城作品详情（App 侧只读）
     *
     * 固定条件：is_deleted = 0 且 status = 'on_shelf'；额外下发 core_setting/character_setting。
     *
     * @param workId 作品ID
     * @return 作品（AppWorkDto，未上架或已删除返回 null）
     */
    public AppWorkDto selectAppWorkById(Long workId);

    /**
     * 书城榜单（App 侧只读，接口文档 2.7.7「作品排行榜（4种排序）」）
     *
     * 固定条件：is_deleted = 0 且 status = 'on_shelf'。
     *
     * @param type  榜单类型：view（默认）/favorite/sale/rating
     * @param limit 取前 N 条
     * @return 榜单条目（rankNo 由服务层按顺序填充）
     */
    public List<AppRankingItem> selectAppRankingList(@Param("type") String type, @Param("limit") int limit);

    /**
     * 用户端修改作品基本信息（单一职责：仅改 title/summary/genre_id）
     *
     * 仅未在审核中的作品允许调用，状态校验在服务层。
     *
     * @param work 仅携带 workId + title/summary/genreId + updateBy/updateTime
     * @return 影响行数
     */
    public int updateWorkBaseInfo(SysWork work);
}
